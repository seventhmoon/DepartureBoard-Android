package com.androidfung.departureboard.data.repository.region.sg

import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TransitRegion
import com.androidfung.departureboard.data.model.isSgTransitId
import com.androidfung.departureboard.data.model.sg.SgStationDirectory
import com.androidfung.departureboard.data.repository.region.RegionalTransitProvider

/**
 * Singapore (LTA / MRT) regional transit provider.
 */
object SgRegionalTransitProvider : RegionalTransitProvider {
    override val region: TransitRegion = TransitRegion.SG

    override fun ownsStationId(stationId: String): Boolean = stationId.isSgTransitId

    override suspend fun searchStations(query: String): Result<List<Station>> =
        Result.success(SgStationDirectory.searchStations(query))

    override suspend fun getStationById(stationId: String): Station? =
        SgStationDirectory.getStationById(stationId)
}
