package com.androidfung.departureboard.data.repository.source.jp

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.isJpTransitId
import com.androidfung.departureboard.data.model.jp.JpDefaultStations
import com.androidfung.departureboard.data.repository.source.DepartureSource

/**
 * Departure source for Tokyo transit (Tokyo Metro & JR East).
 */
class JpDepartureSource : DepartureSource {

    override fun canHandle(stationId: String, stationName: String, isBusStop: Boolean): Boolean {
        if (isBusStop) return false
        return stationId.isJpTransitId ||
                stationName.contains("Tokyo", ignoreCase = true) ||
                stationName.contains("東京") ||
                stationName.contains("Metro")
    }

    override suspend fun fetchDepartures(
        stationId: String,
        stationName: String,
        isBusStop: Boolean
    ): Result<List<Departure>> {
        return Result.success(JpDefaultStations.getFallbackDepartures(stationId, stationName))
    }
}
