package com.androidfung.departureboard.data.repository.region.au

import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TransitRegion
import com.androidfung.departureboard.data.model.au.AuStationDirectory
import com.androidfung.departureboard.data.model.isAuTransitId
import com.androidfung.departureboard.data.repository.region.RegionalTransitProvider

/**
 * Australia / Sydney (Transport for NSW) regional transit provider.
 */
object AuRegionalTransitProvider : RegionalTransitProvider {
    override val region: TransitRegion = TransitRegion.AU

    override fun ownsStationId(stationId: String): Boolean = stationId.isAuTransitId

    override suspend fun searchStations(query: String): Result<List<Station>> =
        Result.success(AuStationDirectory.searchStations(query))

    override suspend fun getStationById(stationId: String): Station? =
        AuStationDirectory.getStationById(stationId)
}
