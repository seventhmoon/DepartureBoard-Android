package com.androidfung.departureboard.data.repository.region.jp

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TransitRegion
import com.androidfung.departureboard.data.model.isJpTransitId
import com.androidfung.departureboard.data.model.jp.JpStationDirectory
import com.androidfung.departureboard.data.repository.DepartureSource
import com.androidfung.departureboard.data.repository.region.RegionalTransitProvider
import com.androidfung.departureboard.data.topology.CallingPointsResolver

/**
 * Japan / Tokyo (JR East & Tokyo Metro) regional transit provider.
 */
object JpRegionalTransitProvider : RegionalTransitProvider {
    override val region: TransitRegion = TransitRegion.JP

    private val departureSource = JpDepartureSource()
    private val callingPointsResolver = JpCallingPointsResolver()

    override val departureSources: List<DepartureSource> = listOf(departureSource)
    override val callingPointsResolvers: List<CallingPointsResolver> = listOf(callingPointsResolver)

    override fun ownsStationId(stationId: String): Boolean = stationId.isJpTransitId

    override suspend fun searchStations(query: String): Result<List<Station>> =
        Result.success(JpStationDirectory.searchStations(query))

    override suspend fun getStationById(stationId: String): Station? =
        JpStationDirectory.getStationById(stationId)

    override suspend fun getDepartures(stationId: String, stationName: String): Result<List<Departure>> {
        return departureSource.fetchDepartures(stationId, stationName, isBusStop = false)
    }
}
