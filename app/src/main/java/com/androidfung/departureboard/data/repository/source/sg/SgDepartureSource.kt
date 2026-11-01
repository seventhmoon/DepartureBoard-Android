package com.androidfung.departureboard.data.repository.source.sg

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.isSgTransitId
import com.androidfung.departureboard.data.model.sg.SgDefaultStations
import com.androidfung.departureboard.data.repository.source.DepartureSource

/**
 * Departure source for Singapore transit (MRT).
 */
class SgDepartureSource : DepartureSource {

    override fun canHandle(stationId: String, stationName: String, isBusStop: Boolean): Boolean {
        if (isBusStop) return false
        return stationId.isSgTransitId ||
                stationName.contains("MRT") ||
                stationName.contains("地鐵")
    }

    override suspend fun fetchDepartures(
        stationId: String,
        stationName: String,
        isBusStop: Boolean
    ): Result<List<Departure>> {
        return Result.success(SgDefaultStations.getFallbackDepartures(stationId, stationName))
    }
}
