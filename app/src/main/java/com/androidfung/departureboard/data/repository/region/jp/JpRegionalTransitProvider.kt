package com.androidfung.departureboard.data.repository.region.jp

import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TransitRegion
import com.androidfung.departureboard.data.model.isJpTransitId
import com.androidfung.departureboard.data.model.jp.JpStationDirectory
import com.androidfung.departureboard.data.repository.region.RegionalTransitProvider

/**
 * Japan / Tokyo (JR East & Tokyo Metro) regional transit provider.
 */
object JpRegionalTransitProvider : RegionalTransitProvider {
    override val region: TransitRegion = TransitRegion.JP

    override fun ownsStationId(stationId: String): Boolean = stationId.isJpTransitId

    override suspend fun searchStations(query: String): Result<List<Station>> =
        Result.success(JpStationDirectory.searchStations(query))

    override suspend fun getStationById(stationId: String): Station? =
        JpStationDirectory.getStationById(stationId)
}
