package com.androidfung.departureboard.data.repository.region.sg

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TransitRegion
import com.androidfung.departureboard.data.model.isSgTransitId
import com.androidfung.departureboard.data.model.sg.SgStationDirectory
import com.androidfung.departureboard.data.repository.DepartureSource
import com.androidfung.departureboard.data.repository.region.RegionalTransitProvider
import com.androidfung.departureboard.data.topology.CallingPointsResolver

/**
 * Singapore (LTA / MRT) regional transit provider.
 */
object SgRegionalTransitProvider : RegionalTransitProvider {
    override val region: TransitRegion = TransitRegion.SG

    private val departureSource = SgDepartureSource()
    private val callingPointsResolver = SgCallingPointsResolver()

    override val departureSources: List<DepartureSource> = listOf(departureSource)
    override val callingPointsResolvers: List<CallingPointsResolver> = listOf(callingPointsResolver)

    override fun ownsStationId(stationId: String): Boolean = stationId.isSgTransitId

    override suspend fun searchStations(query: String): Result<List<Station>> =
        Result.success(SgStationDirectory.searchStations(query))

    override suspend fun getStationById(stationId: String): Station? =
        SgStationDirectory.getStationById(stationId)

    override suspend fun getDepartures(stationId: String, stationName: String): Result<List<Departure>> {
        return departureSource.fetchDepartures(stationId, stationName, isBusStop = false)
    }

    override suspend fun checkStationServiceHours(
        stationId: String,
        lineId: String?
    ): com.androidfung.departureboard.data.model.StationServiceHoursInfo? =
        com.androidfung.departureboard.util.LastTrainServiceCalculator.evaluateServiceHours(stationId, lineId)
}
