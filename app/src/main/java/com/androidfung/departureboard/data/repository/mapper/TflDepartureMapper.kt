package com.androidfung.departureboard.data.repository.mapper

import com.androidfung.departureboard.data.model.DefaultStations
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.TflArrivalPrediction
import com.androidfung.departureboard.data.model.TflLineColors
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.repository.PlatformFormatter
import com.androidfung.departureboard.data.repository.StationNameFormatter
import com.androidfung.departureboard.data.repository.TurnaroundTimeTracker
import com.androidfung.departureboard.data.repository.direction.DestinationResolver
import com.androidfung.departureboard.data.repository.direction.ElizabethLineDirectionResolver
import com.androidfung.departureboard.data.repository.direction.ThameslinkDirectionResolver

/**
 * Transforms raw TfL API arrival prediction models into clean domain [Departure] models,
 * applying deduplication, destination resolution, platform formatting, and direction inferences.
 */
object TflDepartureMapper {

    /**
     * Deduplicates raw arrivals based on vehicleId, or line + destination + 30-sec time bucket.
     * When a stationId/stationName is provided and TfL returns both an inbound terminating prediction
     * and an outbound departure prediction for the same vehicleId, prefers the outbound prediction.
     */
    fun deduplicate(
        rawArrivals: List<TflArrivalPrediction>,
        stationId: String = "",
        stationName: String = ""
    ): List<TflArrivalPrediction> {
        if (stationId.isBlank() && stationName.isBlank()) {
            return rawArrivals.distinctBy { item ->
                if (!item.vehicleId.isNullOrBlank()) {
                    item.vehicleId
                } else {
                    "${item.lineId}_${item.destinationName}_${item.timeToStation / 30}"
                }
            }
        }

        val orderedKeys = mutableListOf<String>()
        val bestByKey = LinkedHashMap<String, TflArrivalPrediction>()

        for (item in rawArrivals) {
            val key = if (!item.vehicleId.isNullOrBlank()) {
                "veh_${item.vehicleId}"
            } else {
                "${item.lineId}_${item.destinationName}_${item.timeToStation / 30}"
            }
            val existing = bestByKey[key]
            if (existing == null) {
                orderedKeys.add(key)
                bestByKey[key] = item
            } else if (!item.vehicleId.isNullOrBlank()) {
                val existingTerminating = DestinationResolver.isTerminatingAtStation(
                    stationId = stationId,
                    stationName = stationName,
                    itemDestination = existing.destinationName,
                    itemTowards = existing.towards,
                    destinationNaptanId = existing.destinationNaptanId,
                    itemNaptanId = existing.naptanId
                )
                val candidateTerminating = DestinationResolver.isTerminatingAtStation(
                    stationId = stationId,
                    stationName = stationName,
                    itemDestination = item.destinationName,
                    itemTowards = item.towards,
                    destinationNaptanId = item.destinationNaptanId,
                    itemNaptanId = item.naptanId
                )
                if (existingTerminating && !candidateTerminating) {
                    bestByKey[key] = item
                }
            }
        }

        return orderedKeys.mapNotNull { bestByKey[it] }
    }

    /**
     * Maps an individual prediction to a domain Departure.
     */
    fun mapToDeparture(
        item: TflArrivalPrediction,
        stationId: String,
        stationName: String,
        wasTerminatingHere: Boolean = false,
        scheduledDepartureEpochSeconds: Long? = null
    ): Departure {
        val effectiveStationId = item.naptanId ?: stationId
        val lineName = item.lineName ?: item.lineId ?: "Transit"
        val badge = TflLineColors.getLineBadge(
            lineId = item.lineId,
            lineName = item.lineName,
            modeName = item.modeName
        )

        val isTerminatingHere = wasTerminatingHere || DestinationResolver.isTerminatingAtStation(
            stationId = stationId,
            stationName = stationName,
            itemDestination = item.destinationName,
            itemTowards = item.towards,
            destinationNaptanId = item.destinationNaptanId,
            itemNaptanId = item.naptanId
        )

        val resolvedDest = DestinationResolver.resolve(
            stationName = stationName,
            itemDestination = item.destinationName,
            itemTowards = item.towards,
            lineId = item.lineId.orEmpty(),
            platformName = item.platformName,
            stationId = effectiveStationId,
            destinationNaptanId = item.destinationNaptanId
        )
        val isBus = item.modeName.equals(TransitMode.BUS.id, ignoreCase = true) || item.lineId?.toIntOrNull() != null
        val terminusOutboundDirection = if (!isBus) {
            DestinationResolver.resolveTerminusOutboundDirection(
                stationId = effectiveStationId,
                stationName = stationName,
                lineId = item.lineId
            )
        } else null
        val platformDisplay = PlatformFormatter.format(
            rawPlatform = item.platformName,
            towards = item.towards,
            isBus = isBus,
            overrideDirection = terminusOutboundDirection
        )
        val cleanStation = StationNameFormatter.clean(stationName)
        val rawCleanTowards = item.towards?.takeIf { !it.trim().equals("null", ignoreCase = true) }?.let { StationNameFormatter.clean(it) }
        val cleanTowards = if (isTerminatingHere && rawCleanTowards != null && (
                rawCleanTowards.equals(cleanStation, ignoreCase = true) ||
                rawCleanTowards.startsWith("$cleanStation ", ignoreCase = true)
            )
        ) {
            resolvedDest.ifBlank { null }
        } else {
            rawCleanTowards
        }
        val resolvedDirection = terminusOutboundDirection ?: resolveCardinalDirection(item, resolvedDest, stationName)

        val finalTimeToStationSeconds = if (isTerminatingHere && !isBus) {
            val buffer = TurnaroundTimeTracker.getEstimatedTurnaroundSeconds(stationId.ifBlank { stationName }, item.lineId ?: "")
            val bufferedCountdown = item.timeToStation + buffer
            if (scheduledDepartureEpochSeconds != null) {
                val nowEpochSeconds = System.currentTimeMillis() / 1000L
                val scheduledCountdown = (scheduledDepartureEpochSeconds - nowEpochSeconds).toInt()
                maxOf(bufferedCountdown, scheduledCountdown)
            } else {
                bufferedCountdown
            }
        } else {
            item.timeToStation
        }

        val addedTurnaroundSeconds = (finalTimeToStationSeconds - item.timeToStation).coerceAtLeast(0)
        val finalExpectedArrivalIso = if (addedTurnaroundSeconds > 0 && !item.expectedArrival.isNullOrBlank()) {
            try {
                java.time.Instant.parse(item.expectedArrival)
                    .plusSeconds(addedTurnaroundSeconds.toLong())
                    .toString()
            } catch (_: Exception) {
                item.expectedArrival
            }
        } else {
            item.expectedArrival
        }

        val rawDestStationId = item.destinationNaptanId?.takeIf { id ->
            id.isNotBlank() &&
                !id.equals(stationId, ignoreCase = true) &&
                !id.equals(item.naptanId, ignoreCase = true)
        }
        val finalDestinationStationId = rawDestStationId
            ?: if (!isBus) DestinationResolver.resolveDestinationNaptanId(resolvedDest, item.lineId) else null

        if (!item.vehicleId.isNullOrBlank()) {
            if (isTerminatingHere) {
                TurnaroundTimeTracker.recordVehicleArrival(stationId, item.vehicleId)
            } else {
                TurnaroundTimeTracker.recordVehicleDeparture(stationId, item.lineId ?: "", item.vehicleId)
            }
        }

        return Departure(
            id = item.id,
            stationId = effectiveStationId,
            stationName = StationNameFormatter.clean(item.stationName ?: stationName),
            lineId = item.lineId ?: "transit",
            lineName = lineName,
            platformName = platformDisplay,
            destinationName = resolvedDest.ifBlank { "Destination" },
            towards = cleanTowards,
            direction = resolvedDirection,
            timeToStationSeconds = finalTimeToStationSeconds,
            expectedArrivalIso = finalExpectedArrivalIso,
            currentLocation = item.currentLocation?.takeIf { !it.trim().equals("null", ignoreCase = true) },
            modeName = item.modeName ?: if (isBus) TransitMode.BUS.id else TransitMode.TUBE.id,
            lineBadge = badge,
            vehicleId = item.vehicleId,
            destinationStationId = finalDestinationStationId
        )
    }

    private fun resolveCardinalDirection(
        item: TflArrivalPrediction,
        resolvedDest: String,
        currentStationName: String
    ): String? {
        val rawPlatform = item.platformName?.trim() ?: ""
        val isElizabeth = item.lineId?.contains("elizabeth", ignoreCase = true) == true ||
                item.lineName?.contains("elizabeth", ignoreCase = true) == true
        val isThameslink = item.lineId?.contains("thameslink", ignoreCase = true) == true ||
                item.lineName?.contains("thameslink", ignoreCase = true) == true

        return when {
            rawPlatform.contains("Eastbound", ignoreCase = true) -> "Eastbound"
            rawPlatform.contains("Westbound", ignoreCase = true) -> "Westbound"
            rawPlatform.contains("Northbound", ignoreCase = true) -> "Northbound"
            rawPlatform.contains("Southbound", ignoreCase = true) -> "Southbound"
            isThameslink -> {
                val destCrs = item.destinationNaptanId?.let { naptan ->
                    if (naptan.startsWith("910G", ignoreCase = true) && naptan.length >= 7) {
                        naptan.substring(4, 7).uppercase()
                    } else if (naptan.length == 3 && naptan.all { it.isLetter() }) {
                        naptan.uppercase()
                    } else null
                }
                ThameslinkDirectionResolver.resolve(
                    destinationCrs = destCrs,
                    destinationName = resolvedDest
                )
            }
            isElizabeth -> resolveElizabethLineDirection(item, resolvedDest, currentStationName)
            item.direction?.equals("inbound", ignoreCase = true) == true -> "Inbound"
            item.direction?.equals("outbound", ignoreCase = true) == true -> "Outbound"
            else -> null
        }
    }

    private fun resolveElizabethLineDirection(
        item: TflArrivalPrediction,
        resolvedDest: String,
        currentStationName: String
    ): String? {
        val resolved = ElizabethLineDirectionResolver.resolve(
            destinationNaptanId = item.destinationNaptanId,
            platformName = item.platformName,
            destinationName = resolvedDest,
            apiDirection = item.direction
        )?.displayName
        if (resolved != null) return resolved

        val destLon = findStationLongitude(resolvedDest, item.destinationNaptanId)
        val currentLon = findStationLongitude(currentStationName, item.naptanId)

        if (destLon != null && currentLon != null) {
            val lonDiff = destLon - currentLon
            if (kotlin.math.abs(lonDiff) > 0.005) {
                return if (lonDiff > 0) "Eastbound" else "Westbound"
            }
        }

        return null
    }

    private fun findStationLongitude(nameOrCleaned: String, stationId: String?): Double? {
        if (!stationId.isNullOrBlank()) {
            DefaultStations.POPULAR_STATIONS.firstOrNull { it.id.equals(stationId, ignoreCase = true) }?.lon?.let {
                return it
            }
        }
        val clean = StationNameFormatter.clean(nameOrCleaned).lowercase()
        return DefaultStations.POPULAR_STATIONS.firstOrNull {
            val popularClean = StationNameFormatter.clean(it.name).lowercase()
            clean.contains(popularClean) || popularClean.contains(clean)
        }?.lon
    }
}
