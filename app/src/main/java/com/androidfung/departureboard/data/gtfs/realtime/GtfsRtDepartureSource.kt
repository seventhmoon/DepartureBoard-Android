package com.androidfung.departureboard.data.gtfs.realtime

import com.androidfung.departureboard.data.gtfs.db.GtfsDao
import com.androidfung.departureboard.data.model.CallingPoint
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.repository.DepartureSource
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Functional provider for retrieving real-time GTFS-RT feed updates.
 */
fun interface GtfsRtFeedProvider {
    suspend fun getFeed(): Result<GtfsRtFeedMessage>
}

/**
 * Universal, standard-compliant [DepartureSource] driven by GTFS Realtime specifications.
 * Works seamlessly with any transit agency publishing GTFS-RT feeds.
 */
open class GtfsRtDepartureSource(
    private val regionPrefix: String,
    private val feedProvider: GtfsRtFeedProvider,
    private val gtfsDao: GtfsDao,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000L }
) : DepartureSource {

    override fun canHandle(stationId: String, stationName: String, isBusStop: Boolean): Boolean {
        return stationId.startsWith(regionPrefix, ignoreCase = true)
    }

    override suspend fun fetchDepartures(
        stationId: String,
        stationName: String,
        isBusStop: Boolean
    ): Result<List<Departure>> {
        val feedResult = feedProvider.getFeed()
        if (feedResult.isFailure) {
            return Result.failure(feedResult.exceptionOrNull() ?: Exception("Failed to fetch GTFS-RT feed"))
        }

        val feed = feedResult.getOrNull() ?: return Result.success(emptyList())
        val nowSeconds = clock()

        // Find station and any platform stops associated with it
        val targetStation = gtfsDao.getStopById(stationId)
        val platformStops = gtfsDao.getPlatformsForStation(stationId)
        val targetStopIds = buildSet {
            add(stationId.trim().uppercase())
            targetStation?.let { add(it.stopId.trim().uppercase()) }
            targetStation?.stopCode?.let { add(it.trim().uppercase()) }
            platformStops.forEach {
                add(it.stopId.trim().uppercase())
                it.stopCode?.let { code -> add(code.trim().uppercase()) }
            }
        }

        // Cache routes to avoid multiple queries
        val routeCache = mutableMapOf<String, LineBadgeInfo>()

        // Index vehicle positions by tripId
        val vehicleByTrip = feed.vehiclePositions
            .filter { it.trip?.tripId != null }
            .associateBy { it.trip!!.tripId!! }

        val departures = mutableListOf<Departure>()

        for (tripUpdate in feed.tripUpdates) {
            val tripId = tripUpdate.trip.tripId ?: continue
            val routeId = tripUpdate.trip.routeId ?: ""

            // Find matching stop time update for this station
            val matchingIndex = tripUpdate.stopTimeUpdates.indexOfFirst { stu ->
                val stuStopId = stu.stopId?.trim()?.uppercase()
                stuStopId != null && targetStopIds.contains(stuStopId)
            }
            if (matchingIndex == -1) continue

            val matchingStu = tripUpdate.stopTimeUpdates[matchingIndex]
            val eventTime = matchingStu.departure?.time ?: matchingStu.arrival?.time
            val timeToStationSeconds = if (eventTime != null) {
                (eventTime - nowSeconds).coerceAtLeast(0).toInt()
            } else {
                val delay = matchingStu.departure?.delay ?: matchingStu.arrival?.delay ?: 0
                delay.coerceAtLeast(0)
            }

            // Resolve LineBadgeInfo
            val lineBadge = routeCache.getOrPut(routeId) {
                val routeEntity = gtfsDao.getRouteById(routeId)
                if (routeEntity != null) {
                    routeEntity.toGtfsRoute().toLineBadgeInfo()
                } else {
                    LineBadgeInfo(
                        lineId = routeId,
                        displayName = routeId,
                        backgroundColor = androidx.compose.ui.graphics.Color(0xFF1E88E5),
                        mode = TransitMode.OTHER
                    )
                }
            }

            // Associate vehicle position
            val vehicle = vehicleByTrip[tripId]
            val occupancy = vehicle?.occupancyStatus
            val vehicleStatus = vehicle?.currentStatus ?: matchingStu.scheduleRelationship.name

            val expectedArrivalIso = if (eventTime != null && eventTime > 0) {
                try {
                    DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochSecond(eventTime))
                } catch (_: Exception) {
                    null
                }
            } else null

            // Build downstream calling points
            val downstreamUpdates = tripUpdate.stopTimeUpdates.drop(matchingIndex)
            val callingPoints = downstreamUpdates.mapIndexed { idx, stu ->
                val stopEntity = stu.stopId?.let { gtfsDao.getStopById(it) }
                val callingName = stopEntity?.stopName ?: (stu.stopId ?: "Unknown")
                val callTime = stu.departure?.time ?: stu.arrival?.time
                val isoTime = callTime?.let {
                    try {
                        DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochSecond(it))
                    } catch (_: Exception) {
                        null
                    }
                }

                CallingPoint(
                    stationName = callingName,
                    scheduledTime = isoTime,
                    estimatedTime = isoTime,
                    isCurrentStation = idx == 0,
                    isDestination = idx == downstreamUpdates.lastIndex,
                    stationId = stu.stopId,
                    modes = listOf(lineBadge.mode.id)
                )
            }

            val destName = callingPoints.lastOrNull()?.stationName
                ?: (gtfsDao.getTripById(tripId)?.tripHeadsign ?: "Destination")

            val matchedPlatformCode = platformStops.firstOrNull {
                it.stopId.equals(matchingStu.stopId, ignoreCase = true)
            }?.platformCode

            val platformDisplay = when {
                !matchedPlatformCode.isNullOrBlank() -> "Platform $matchedPlatformCode"
                else -> ""
            }

            departures.add(
                Departure(
                    id = "${tripId}_${matchingStu.stopSequence ?: matchingIndex}",
                    stationId = stationId,
                    stationName = stationName,
                    lineId = routeId,
                    lineName = lineBadge.displayName,
                    platformName = platformDisplay,
                    destinationName = destName,
                    towards = destName,
                    direction = if (tripUpdate.trip.directionId == 0) "Outbound" else "Inbound",
                    timeToStationSeconds = timeToStationSeconds,
                    expectedArrivalIso = expectedArrivalIso,
                    currentLocation = vehicleStatus,
                    modeName = lineBadge.mode.id,
                    lineBadge = lineBadge,
                    callingPoints = callingPoints,
                    status = DepartureStatus.fromString(vehicleStatus, timeToStationSeconds),
                    vehicleId = vehicle?.vehicle?.id ?: tripUpdate.vehicle?.id,
                    destinationStationId = downstreamUpdates.lastOrNull()?.stopId,
                    occupancyStatus = occupancy?.name,
                    scheduleRelationship = matchingStu.scheduleRelationship,
                    occupancy = occupancy,
                    tripId = tripId,
                    routeId = routeId,
                    stopSequence = matchingStu.stopSequence
                )
            )
        }

        return Result.success(departures.sortedBy { it.timeToStationSeconds })
    }
}
