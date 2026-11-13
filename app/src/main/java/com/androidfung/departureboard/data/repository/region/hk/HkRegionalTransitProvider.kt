package com.androidfung.departureboard.data.repository.region.hk

import android.content.Context
import com.androidfung.departureboard.data.model.AlertCause
import com.androidfung.departureboard.data.model.AlertEffect
import com.androidfung.departureboard.data.model.AlertSeverity
import com.androidfung.departureboard.data.model.CallingPoint
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.InformedEntity
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.StationServiceHoursInfo
import com.androidfung.departureboard.data.model.TransitAlert
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.model.TransitRegion
import com.androidfung.departureboard.data.model.gb.TflLineStatusDetail
import com.androidfung.departureboard.data.model.gb.TflLineStatusItem
import com.androidfung.departureboard.data.model.hk.HkBusStopDirectory
import com.androidfung.departureboard.data.model.hk.HkDefaultStations
import com.androidfung.departureboard.data.model.hk.HkMtrLine
import com.androidfung.departureboard.data.model.hk.HkMtrStationRegistry
import com.androidfung.departureboard.data.model.hk.HkStationDirectory
import com.androidfung.departureboard.data.model.hk.extractRawHkBusStopId
import com.androidfung.departureboard.data.model.hk.parseHkMtrStationId
import com.androidfung.departureboard.data.model.isHkBusStationId
import com.androidfung.departureboard.data.model.isHkTransitId
import com.androidfung.departureboard.data.network.hk.HkMtrApiService
import com.androidfung.departureboard.data.network.hk.HkMtrNetworkClient
import com.androidfung.departureboard.data.repository.DepartureSource
import com.androidfung.departureboard.data.repository.region.RegionalTransitProvider
import com.androidfung.departureboard.data.repository.region.hk.search.HkBusSearchEngine
import com.androidfung.departureboard.data.topology.CallingPointsResolver
import com.androidfung.departureboard.util.CoalescingTimedCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Hong Kong (MTR, KMB, Citybus) regional transit provider.
 * Wraps both MTR rail and franchised bus departure sources behind a single regional interface.
 */
class HkRegionalTransitProvider(
    private val context: Context? = null,
    private val hkBusSearchEngine: HkBusSearchEngine = HkBusSearchEngine(),
    private val mtrApiService: HkMtrApiService = HkMtrNetworkClient.apiService
) : RegionalTransitProvider {

    override val region: TransitRegion = TransitRegion.HK

    private val hkMtrDepartureSource = HkMtrDepartureSource(mtrApiService)
    private val hkLightRailDepartureSource = HkLightRailDepartureSource()
    private val hkGmbDepartureSource = HkGmbDepartureSource()
    private val hkBusDepartureSource = HkBusDepartureSource()
    private val hkMtrCallingPointsResolver = HkMtrCallingPointsResolver()
    private val hkBusCallingPointsResolver = HkBusCallingPointsResolver()

    override val departureSources: List<DepartureSource> = listOf(
        hkMtrDepartureSource,
        hkLightRailDepartureSource,
        hkGmbDepartureSource,
        hkBusDepartureSource
    )

    override val callingPointsResolvers: List<CallingPointsResolver> = listOf(
        hkMtrCallingPointsResolver,
        hkBusCallingPointsResolver
    )

    private val lineStatusesCache = CoalescingTimedCache<String, Map<String, TflLineStatusItem>>(
        ttlMillis = 3 * 60 * 1000L
    )
    private val providerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Representative probe stations for MTR lines to check live service status / delay flags
    private val lineProbeStations = mapOf(
        "ISL" to "CEN",
        "TWL" to "MKG",
        "KTL" to "KOT",
        "EAL" to "SHT",
        "TML" to "DIH",
        "TCL" to "TSY",
        "AEL" to "HOK",
        "SIL" to "ADM",
        "TKL" to "TIK",
        "DRL" to "SUN"
    )

    override fun ownsStationId(stationId: String): Boolean =
        stationId.isHkTransitId

    override suspend fun searchStations(query: String): Result<List<Station>> {
        val routeStops = if (hkBusSearchEngine.isRouteQuery(query)) {
            hkBusSearchEngine.searchRouteStops(query)
        } else {
            emptyList()
        }
        val directoryBusStops = context?.let { ctx ->
            HkBusStopDirectory.searchStops(ctx, query)
        }.orEmpty()
        val dynamicBusStops = (routeStops + directoryBusStops).distinctBy { it.id }
        return Result.success(HkStationDirectory.searchHkStations(query, dynamicBusStops))
    }

    override suspend fun getStationById(stationId: String): Station? {
        HkDefaultStations.POPULAR_STATIONS.firstOrNull {
            it.id.equals(stationId, ignoreCase = true)
        }?.let { return it }

        val mtrRef = stationId.parseHkMtrStationId()
        val mtrStation = HkMtrStationRegistry.getStation(mtrRef.stationCode)
        if (mtrStation != null) {
            return HkStationDirectory.mtrToStation(mtrStation, mtrRef.lineCode)
        }

        if (stationId.startsWith("HK_BUS_KMB_", ignoreCase = true)) {
            val rawStopId = extractRawHkBusStopId(stationId)
            val localStop = context?.let { HkBusStopDirectory.findStopById(it, rawStopId) }
            if (localStop != null) {
                return localStop.toStation()
            }

            return try {
                val detail = hkBusSearchEngine.fetchStopDetail(rawStopId)
                val nameTc = detail.localizedNames["zh-Hant"].orEmpty().trim()
                val nameEn = detail.localizedNames["en"].orEmpty().trim()
                val displayName = when {
                    nameTc.isNotBlank() && nameEn.isNotBlank() -> "$nameEn ($nameTc)"
                    nameTc.isNotBlank() -> nameTc
                    nameEn.isNotBlank() -> nameEn
                    else -> "Bus Stop"
                }
                Station(
                    id = stationId,
                    name = displayName,
                    modes = listOf(TransitMode.BUS.id),
                    zone = null,
                    lat = detail.lat?.toDoubleOrNull(),
                    lon = detail.long?.toDoubleOrNull(),
                    isFavorite = false
                )
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }

        return null
    }

    override suspend fun getDepartures(stationId: String, stationName: String): Result<List<Departure>> = coroutineScope {
        val isBusStop = stationId.isHkBusStationId
        val matchingSources = departureSources.filter { it.canHandle(stationId, stationName, isBusStop) }
        if (matchingSources.isEmpty()) {
            return@coroutineScope Result.success(emptyList())
        }

        val deferreds = matchingSources.map { source ->
            async { source.fetchDepartures(stationId, stationName, isBusStop) }
        }

        val results = deferreds.map { it.await() }
        val merged = mutableListOf<Departure>()
        var lastError: Throwable? = null

        for (res in results) {
            res.fold(
                onSuccess = { merged.addAll(it) },
                onFailure = { lastError = it }
            )
        }

        val err = lastError
        if (merged.isEmpty() && err != null) {
            Result.failure(err)
        } else {
            Result.success(merged.sortedBy { it.timeToStationSeconds })
        }
    }

    override suspend fun checkStationServiceHours(
        stationId: String,
        lineId: String?
    ): StationServiceHoursInfo? {
        return if (stationId.isHkBusStationId) {
            HkBusServiceHoursResolver.checkStationServiceHours(stationId)
        } else {
            HkMtrServiceHoursResolver.checkStationServiceHours(stationId, lineId)
        }
    }

    override suspend fun getLineStatuses(): Map<String, TflLineStatusItem> = withContext(Dispatchers.IO) {
        lineStatusesCache.getOrPutCoalesced(
            key = "hk_line_statuses",
            scope = providerScope,
            shouldCache = { it.isNotEmpty() }
        ) {
            coroutineScope {
                val results = mutableMapOf<String, TflLineStatusItem>()
                val lineDeferreds = HkMtrLine.entries.map { mtrLine ->
                    val probeStation = lineProbeStations[mtrLine.code] ?: "CEN"
                    async {
                        try {
                            val resp = mtrApiService.getSchedule(line = mtrLine.code, station = probeStation)
                            val isDelay = resp.isDelay.equals("Y", ignoreCase = true)
                            val severity = if (isDelay) 9 else 10 // 9 = Delays, 10 = Good Service
                            val severityDesc = if (isDelay) "Delays" else "Good Service"
                            val reason = if (isDelay) (resp.message ?: "Delays reported on ${mtrLine.lineName}") else null

                            mtrLine.code.lowercase() to TflLineStatusItem(
                                id = mtrLine.code.lowercase(),
                                name = mtrLine.lineName,
                                modeName = "mtr",
                                lineStatuses = listOf(
                                    TflLineStatusDetail(
                                        id = 0,
                                        statusSeverity = severity,
                                        statusSeverityDescription = severityDesc,
                                        reason = reason
                                    )
                                )
                            )
                        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            // Fallback to Good Service if query fails
                            mtrLine.code.lowercase() to TflLineStatusItem(
                                id = mtrLine.code.lowercase(),
                                name = mtrLine.lineName,
                                modeName = "mtr",
                                lineStatuses = listOf(
                                    TflLineStatusDetail(
                                        id = 0,
                                        statusSeverity = 10,
                                        statusSeverityDescription = "Good Service",
                                        reason = null
                                    )
                                )
                            )
                        }
                    }
                }

                lineDeferreds.forEach { deferred ->
                    val (key, item) = deferred.await()
                    results[key] = item
                }
                results
            }
        }
    }

    override suspend fun getTransitAlerts(): List<TransitAlert> {
        val statuses = getLineStatuses()
        return statuses.values.flatMap { item ->
            val disrupted = item.lineStatuses.filter { it.statusSeverity != 10 }
            disrupted.mapIndexed { index, status ->
                val lineDef = HkMtrLine.getLine(item.id.uppercase())
                TransitAlert(
                    id = "hk-mtr-alert-${item.id.lowercase()}-$index",
                    cause = AlertCause.UNKNOWN_CAUSE,
                    effect = AlertEffect.SIGNIFICANT_DELAYS,
                    severity = AlertSeverity.WARNING,
                    headerText = "${lineDef?.displayName ?: item.name}: ${status.statusSeverityDescription}",
                    descriptionText = status.reason,
                    informedEntities = listOf(
                        InformedEntity(
                            agencyId = "MTR",
                            routeId = item.id.uppercase(),
                            lineId = item.id.uppercase()
                        )
                    )
                )
            }
        }
    }
}
