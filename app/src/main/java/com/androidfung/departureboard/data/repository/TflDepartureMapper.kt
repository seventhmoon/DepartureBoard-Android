package com.androidfung.departureboard.data.repository

import com.androidfung.departureboard.data.model.DefaultStations
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.TflArrivalPrediction
import com.androidfung.departureboard.data.model.TflLineColors
import com.androidfung.departureboard.data.model.TransitMode

/**
 * Transforms raw TfL API arrival prediction models into clean domain [Departure] models,
 * applying deduplication, destination resolution, platform formatting, and direction inferences.
 */
internal object TflDepartureMapper {

    /**
     * Deduplicates raw arrivals based on vehicleId, or line + destination + 30-sec time bucket.
     */
    fun deduplicate(rawArrivals: List<TflArrivalPrediction>): List<TflArrivalPrediction> {
        return rawArrivals.distinctBy { item ->
            if (!item.vehicleId.isNullOrBlank()) {
                item.vehicleId
            } else {
                "${item.lineId}_${item.destinationName}_${item.timeToStation / 30}"
            }
        }
    }

    /**
     * Maps an individual prediction to a domain Departure.
     */
    fun mapToDeparture(
        item: TflArrivalPrediction,
        stationId: String,
        stationName: String
    ): Departure {
        val lineName = item.lineName ?: item.lineId ?: "Transit"
        val badge = TflLineColors.getLineBadge(
            lineId = item.lineId,
            lineName = item.lineName,
            modeName = item.modeName
        )
        val resolvedDest = DestinationResolver.resolve(
            stationName = stationName,
            itemDestination = item.destinationName,
            itemTowards = item.towards,
            lineId = item.lineId,
            platformName = item.platformName,
            stationId = item.naptanId ?: stationId,
            destinationNaptanId = item.destinationNaptanId
        )
        val isBus = item.modeName.equals(TransitMode.BUS.id, ignoreCase = true) || item.lineId?.toIntOrNull() != null
        val platformDisplay = PlatformFormatter.format(item.platformName, item.towards, isBus)
        val cleanTowards = item.towards?.takeIf { !it.trim().equals("null", ignoreCase = true) }?.let { StationNameFormatter.clean(it) }
        val resolvedDirection = resolveCardinalDirection(item, resolvedDest, stationName)

        // Check if this was an inbound train terminating at this station
        val rawInput = item.destinationName ?: item.towards ?: ""
        val cleanDest = StationNameFormatter.clean(rawInput)
        val cleanStation = StationNameFormatter.clean(stationName)
        val isTerminatingHere = cleanDest.isNotBlank() && (
            cleanDest.equals(cleanStation, ignoreCase = true) ||
            cleanDest.startsWith("$cleanStation ", ignoreCase = true)
        )

        val finalTimeToStationSeconds = if (isTerminatingHere && !isBus) {
            val buffer = TurnaroundTimeTracker.getEstimatedTurnaroundSeconds(stationName, item.lineId ?: "")
            item.timeToStation + buffer
        } else {
            item.timeToStation
        }

        // Record vehicle for empirical turnaround tracking
        if (!item.vehicleId.isNullOrBlank()) {
            if (isTerminatingHere) {
                TurnaroundTimeTracker.recordVehicleArrival(stationId, item.vehicleId)
            } else {
                TurnaroundTimeTracker.recordVehicleDeparture(stationId, item.lineId ?: "", item.vehicleId)
            }
        }

        return Departure(
            id = item.id,
            stationId = item.naptanId ?: stationId,
            stationName = StationNameFormatter.clean(item.stationName ?: stationName),
            lineId = item.lineId ?: "transit",
            lineName = lineName,
            platformName = platformDisplay,
            destinationName = resolvedDest.ifBlank { "Destination" },
            towards = cleanTowards,
            direction = resolvedDirection,
            timeToStationSeconds = finalTimeToStationSeconds,
            expectedArrivalIso = item.expectedArrival,
            currentLocation = item.currentLocation?.takeIf { !it.trim().equals("null", ignoreCase = true) },
            modeName = item.modeName ?: if (isBus) TransitMode.BUS.id else TransitMode.TUBE.id,
            lineBadge = badge
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
            isThameslink -> ThameslinkDirectionResolver.resolve(
                destinationName = resolvedDest
            )
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
        // Fast O(1) CRS/NaPTAN machine identifier check
        val resolved = ElizabethLineDirectionResolver.resolve(
            destinationNaptanId = item.destinationNaptanId,
            platformName = item.platformName,
            destinationName = resolvedDest,
            apiDirection = item.direction
        )?.displayName
        if (resolved != null) return resolved

        // Fallback to relative longitude calculation
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
