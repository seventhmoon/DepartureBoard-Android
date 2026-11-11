package com.androidfung.departureboard.data.repository.region.au

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TransitRegion
import com.androidfung.departureboard.data.model.au.AuStationDirectory
import com.androidfung.departureboard.data.model.isAuTransitId
import com.androidfung.departureboard.data.repository.DepartureSource
import com.androidfung.departureboard.data.repository.region.RegionalTransitProvider
import com.androidfung.departureboard.data.topology.CallingPointsResolver

/**
 * Australia / Sydney (Transport for NSW) regional transit provider.
 */
object AuRegionalTransitProvider : RegionalTransitProvider {
    override val region: TransitRegion = TransitRegion.AU

    private val departureSource = AuDepartureSource()
    private val callingPointsResolver = AuCallingPointsResolver()

    override val departureSources: List<DepartureSource> = listOf(departureSource)
    override val callingPointsResolvers: List<CallingPointsResolver> = listOf(callingPointsResolver)

    override fun ownsStationId(stationId: String): Boolean = stationId.isAuTransitId

    override suspend fun searchStations(query: String): Result<List<Station>> =
        Result.success(AuStationDirectory.searchStations(query))

    override suspend fun getStationById(stationId: String): Station? =
        com.androidfung.departureboard.data.model.au.AuDefaultStations.POPULAR_STATIONS.firstOrNull {
            it.id.equals(stationId, ignoreCase = true)
        } ?: AuStationDirectory.getStationById(stationId)

    override suspend fun getDepartures(stationId: String, stationName: String): Result<List<Departure>> {
        val isBusStop = stationId.startsWith("AU_BUS_", ignoreCase = true)
        return departureSource.fetchDepartures(stationId, stationName, isBusStop = isBusStop)
    }

    override suspend fun checkStationServiceHours(
        stationId: String,
        lineId: String?
    ): com.androidfung.departureboard.data.model.StationServiceHoursInfo? =
        com.androidfung.departureboard.util.LastTrainServiceCalculator.evaluateServiceHours(stationId, lineId)
}
