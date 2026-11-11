package com.androidfung.departureboard.data.repository.region.jp

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.isJpTransitId
import com.androidfung.departureboard.data.model.jp.JpDefaultStations
import com.androidfung.departureboard.data.model.jp.JpStationDirectory
import com.androidfung.departureboard.data.repository.DepartureSource

/**
 * Departure source for Tokyo transit (Tokyo Metro & JR East).
 */
class JpDepartureSource : DepartureSource {

    override fun canHandle(stationId: String, stationName: String, isBusStop: Boolean): Boolean {
        return stationId.isJpTransitId ||
            (!isBusStop && JpStationDirectory.getStationById(stationId) != null)
    }

    override suspend fun fetchDepartures(
        stationId: String,
        stationName: String,
        isBusStop: Boolean
    ): Result<List<Departure>> {
        return Result.success(JpDefaultStations.getFallbackDepartures(stationId, stationName))
    }
}
