package com.androidfung.departureboard.data.gtfs

import com.androidfung.departureboard.data.gtfs.db.GtfsDao
import com.androidfung.departureboard.data.model.CallingPoint
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.topology.CallingPointsResolver

/**
 * Standard GTFS-driven calling points resolver that fetches calling points
 * directly from stop_times.txt and stops.txt sequences via [GtfsDao].
 */
class GtfsCallingPointsResolver(
    private val gtfsDao: GtfsDao
) : CallingPointsResolver {

    override fun canHandle(departure: Departure): Boolean {
        return !departure.tripId.isNullOrBlank()
    }

    override suspend fun resolveCallingPoints(departure: Departure): List<CallingPoint> {
        val tripId = departure.tripId ?: return emptyList()
        val records = gtfsDao.getCallingPointsForTrip(tripId)
        if (records.isEmpty()) return emptyList()

        val currentStopId = departure.stationId.trim().uppercase()
        val currentStopName = departure.stationName.trim().lowercase()
        val destName = departure.destinationName.trim().lowercase()

        // Find starting index matching either stationId or stationName
        val originIdx = records.indexOfFirst { record ->
            record.stopId.equals(currentStopId, ignoreCase = true) ||
                    record.stopName.trim().lowercase() == currentStopName
        }.takeIf { it != -1 } ?: 0

        // Find destination index
        val destIdx = records.indexOfLast { record ->
            record.stopName.trim().lowercase() == destName ||
                    record.stopHeadsign?.trim()?.lowercase() == destName
        }.takeIf { it != -1 } ?: (records.size - 1)

        val sliced = if (originIdx <= destIdx) {
            records.subList(originIdx, (destIdx + 1).coerceAtMost(records.size))
        } else {
            records.subList(destIdx, (originIdx + 1).coerceAtMost(records.size)).reversed()
        }

        return sliced.mapIndexed { idx, record ->
            val isCurrent = idx == 0
            val isDestination = idx == sliced.lastIndex

            CallingPoint(
                stationName = record.stopName,
                scheduledTime = record.departureTime ?: record.arrivalTime,
                estimatedTime = null,
                isCurrentStation = isCurrent,
                isDestination = isDestination,
                stationId = record.stopId,
                modes = listOf(departure.modeName)
            )
        }
    }
}
