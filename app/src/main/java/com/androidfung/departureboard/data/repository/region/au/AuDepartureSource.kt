package com.androidfung.departureboard.data.repository.region.au

import com.androidfung.departureboard.data.gtfs.realtime.GtfsRtDepartureSource
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.au.AuDefaultStations
import com.androidfung.departureboard.data.model.isAuTransitId
import com.androidfung.departureboard.data.repository.DepartureSource

/**
 * Departure source for Sydney / NSW transit powered by GTFS-RT with fallback support.
 */
class AuDepartureSource(
    private val gtfsRtSource: GtfsRtDepartureSource? = null
) : DepartureSource {

    override fun canHandle(stationId: String, stationName: String, isBusStop: Boolean): Boolean {
        if (isBusStop) return false
        return stationId.isAuTransitId ||
                stationName.contains("Sydney", ignoreCase = true) ||
                stationName.contains("NSW", ignoreCase = true)
    }

    override suspend fun fetchDepartures(
        stationId: String,
        stationName: String,
        isBusStop: Boolean
    ): Result<List<Departure>> {
        if (gtfsRtSource != null && gtfsRtSource.canHandle(stationId, stationName, isBusStop)) {
            val result = gtfsRtSource.fetchDepartures(stationId, stationName, isBusStop)
            if (result.isSuccess && !result.getOrNull().isNullOrEmpty()) {
                return result
            }
        }
        return Result.success(AuDefaultStations.getFallbackDepartures(stationId, stationName))
    }
}
