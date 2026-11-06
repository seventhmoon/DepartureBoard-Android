package com.androidfung.departureboard.data.repository.region.au

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.au.AuDefaultStations
import com.androidfung.departureboard.data.model.au.AuStationDirectory
import com.androidfung.departureboard.data.model.isAuTransitId
import com.androidfung.departureboard.data.repository.DepartureSource

/**
 * Departure source for Sydney / NSW transit.
 */
class AuDepartureSource : DepartureSource {

    override fun canHandle(stationId: String, stationName: String, isBusStop: Boolean): Boolean {
        if (isBusStop) return false
        return stationId.isAuTransitId || AuStationDirectory.getStationById(stationId) != null
    }

    override suspend fun fetchDepartures(
        stationId: String,
        stationName: String,
        isBusStop: Boolean
    ): Result<List<Departure>> {
        return Result.success(AuDefaultStations.getFallbackDepartures(stationId, stationName))
    }
}
