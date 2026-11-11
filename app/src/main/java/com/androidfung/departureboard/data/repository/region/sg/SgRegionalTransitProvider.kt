package com.androidfung.departureboard.data.repository.region.sg

import com.androidfung.departureboard.BuildConfig
import com.androidfung.departureboard.data.model.AlertCause
import com.androidfung.departureboard.data.model.AlertEffect
import com.androidfung.departureboard.data.model.AlertSeverity
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.InformedEntity
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.StationIdPrefixes
import com.androidfung.departureboard.data.model.TransitAlert
import com.androidfung.departureboard.data.model.TransitRegion
import com.androidfung.departureboard.data.model.isSgTransitId
import com.androidfung.departureboard.data.model.sg.SgDefaultStations
import com.androidfung.departureboard.data.model.sg.SgStationDirectory
import com.androidfung.departureboard.data.network.sg.SgLtaNetworkClient
import com.androidfung.departureboard.data.repository.DepartureSource
import com.androidfung.departureboard.data.repository.region.RegionalTransitProvider
import com.androidfung.departureboard.data.topology.CallingPointsResolver
import com.androidfung.departureboard.util.CoalescingTimedCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Singapore (LTA DataMall BusArrivalv2, PCDRealTime Station Crowd & MRT) regional transit provider.
 */
object SgRegionalTransitProvider : RegionalTransitProvider {
    override val region: TransitRegion = TransitRegion.SG

    private val departureSource = SgDepartureSource()
    private val callingPointsResolver = SgCallingPointsResolver()
    private val providerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val crowdAlertsCache = CoalescingTimedCache<String, List<TransitAlert>>(ttlMillis = 5 * 60 * 1000L)

    override val departureSources: List<DepartureSource> = listOf(departureSource)
    override val callingPointsResolvers: List<CallingPointsResolver> = listOf(callingPointsResolver)

    override fun ownsStationId(stationId: String): Boolean = stationId.isSgTransitId

    override suspend fun searchStations(query: String): Result<List<Station>> =
        Result.success(SgStationDirectory.searchStations(query))

    override suspend fun getStationById(stationId: String): Station? =
        SgDefaultStations.POPULAR_STATIONS.firstOrNull { it.id.equals(stationId, ignoreCase = true) }
            ?: SgStationDirectory.getStationById(stationId)

    override suspend fun getDepartures(stationId: String, stationName: String): Result<List<Departure>> {
        val isBusStop = stationId.startsWith(StationIdPrefixes.SG_BUS, ignoreCase = true)
        return departureSource.fetchDepartures(stationId, stationName, isBusStop = isBusStop)
    }

    override suspend fun getTransitAlerts(): List<TransitAlert> {
        if (BuildConfig.LTA_DATAMALL_API_KEY.isBlank()) return emptyList()
        return crowdAlertsCache.getOrPutCoalesced(key = "sg_pcd_crowd", scope = providerScope) {
            val alerts = mutableListOf<TransitAlert>()
            val probeLines = listOf("NSL", "EWL", "NEL", "CCL", "DTL", "TEL")
            for (line in probeLines) {
                try {
                    val res = SgLtaNetworkClient.apiService.getPlatformCrowdDensity(line)
                    res.value.filter { it.crowdLevel.equals("h", ignoreCase = true) }.forEach { crowd ->
                        alerts.add(
                            TransitAlert(
                                id = "sg_pcd_${line}_${crowd.stationCode}",
                                headerText = "$line ${crowd.stationCode} — High Platform Crowd",
                                descriptionText = "LTA real-time platform crowd density is currently high at ${crowd.stationCode}. Move toward outer train cars for faster boarding.",
                                cause = AlertCause.OTHER_CAUSE,
                                effect = AlertEffect.SIGNIFICANT_DELAYS,
                                severity = AlertSeverity.WARNING,
                                informedEntities = listOf(InformedEntity(agencyId = "LTA", routeId = line))
                            )
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {}
            }
            alerts
        }
    }

    override suspend fun checkStationServiceHours(
        stationId: String,
        lineId: String?
    ): com.androidfung.departureboard.data.model.StationServiceHoursInfo? =
        com.androidfung.departureboard.util.LastTrainServiceCalculator.evaluateServiceHours(stationId, lineId)
}
