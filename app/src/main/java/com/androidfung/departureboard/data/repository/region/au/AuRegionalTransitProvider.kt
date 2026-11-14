package com.androidfung.departureboard.data.repository.region.au

import com.androidfung.departureboard.BuildConfig
import com.androidfung.departureboard.data.model.AlertCause
import com.androidfung.departureboard.data.model.AlertEffect
import com.androidfung.departureboard.data.model.AlertSeverity
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.InformedEntity
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TransitAlert
import com.androidfung.departureboard.data.model.TransitRegion
import com.androidfung.departureboard.data.model.au.AuStationDirectory
import com.androidfung.departureboard.data.model.isAuTransitId
import com.androidfung.departureboard.data.network.au.AuTfnswAddInfoResponse
import com.androidfung.departureboard.data.network.au.AuTfnswApiService
import com.androidfung.departureboard.data.network.au.AuTfnswNetworkClient
import com.androidfung.departureboard.data.repository.DepartureSource
import com.androidfung.departureboard.data.repository.region.RegionalTransitProvider
import com.androidfung.departureboard.data.topology.CallingPointsResolver
import com.androidfung.departureboard.util.CoalescingTimedCache
import com.androidfung.departureboard.util.getOrPutCoalescedSafe
import com.androidfung.departureboard.util.runSuspendCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Instant

/**
 * Australia / Sydney (Transport for NSW) regional transit provider.
 */
object AuRegionalTransitProvider : RegionalTransitProvider {
    override val region: TransitRegion = TransitRegion.AU

    private val departureSource = AuDepartureSource()
    private val callingPointsResolver = AuCallingPointsResolver()
    private val providerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val alertsCache = CoalescingTimedCache<String, List<TransitAlert>>(ttlMillis = 5 * 60 * 1000L)

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

    override suspend fun getTransitAlerts(): List<TransitAlert> {
        if (BuildConfig.TFNSW_API_KEY.isBlank()) return emptyList()
        return alertsCache.getOrPutCoalescedSafe(
            key = "au_tfnsw_add_info",
            scope = providerScope,
            defaultValue = emptyList()
        ) {
            fetchTransitAlerts(AuTfnswNetworkClient.apiService)
        }
    }

    internal suspend fun fetchTransitAlerts(apiService: AuTfnswApiService): List<TransitAlert> =
        runSuspendCatching { mapAddInfoToAlerts(apiService.getServiceAlerts()) }
            .getOrElse { emptyList() }

    internal fun mapAddInfoToAlerts(response: AuTfnswAddInfoResponse): List<TransitAlert> {
        val items = response.infos?.current ?: return emptyList()
        return items.mapIndexedNotNull { idx, item ->
            val header = item.subtitle?.trim()?.takeIf(String::isNotEmpty)
                ?: item.title?.trim()?.takeIf(String::isNotEmpty)
                ?: return@mapIndexedNotNull null
            val cleanDesc = TransitAlert.stripHtml(
                item.description?.trim()?.takeIf(String::isNotEmpty) ?: item.content
            )
            val severity = when (item.priority?.trim()?.lowercase()) {
                "veryhigh", "high" -> AlertSeverity.SEVERE
                "low", "verylow" -> AlertSeverity.INFO
                else -> AlertSeverity.WARNING
            }
            val (cause, effect) = TransitAlert.inferCauseAndEffect("$header ${cleanDesc.orEmpty()}")

            TransitAlert(
                id = "au_tp_${item.id ?: idx}",
                cause = cause,
                effect = effect,
                severity = severity,
                headerText = header,
                descriptionText = cleanDesc,
                url = item.infoLinks.firstOrNull { !it.url.isNullOrBlank() }?.url,
                activePeriodStart = item.publicationStart?.let { runCatching { Instant.parse(it) }.getOrNull() },
                activePeriodEnd = item.publicationEnd?.let { runCatching { Instant.parse(it) }.getOrNull() },
                informedEntities = listOf(InformedEntity(agencyId = "TfNSW"))
            )
        }
    }

    override suspend fun checkStationServiceHours(
        stationId: String,
        lineId: String?
    ): com.androidfung.departureboard.data.model.StationServiceHoursInfo? =
        com.androidfung.departureboard.util.LastTrainServiceCalculator.evaluateServiceHours(stationId, lineId)
}
