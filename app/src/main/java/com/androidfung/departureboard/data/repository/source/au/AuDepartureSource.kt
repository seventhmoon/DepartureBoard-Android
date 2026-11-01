package com.androidfung.departureboard.data.repository.source.au

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.au.AuDefaultStations
import com.androidfung.departureboard.data.model.isAuTransitId
import com.androidfung.departureboard.data.repository.source.DepartureSource

/**
 * Departure source for Sydney / NSW transit.
 */
class AuDepartureSource : DepartureSource {

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
        return Result.success(AuDefaultStations.getFallbackDepartures(stationId, stationName))
    }
}
