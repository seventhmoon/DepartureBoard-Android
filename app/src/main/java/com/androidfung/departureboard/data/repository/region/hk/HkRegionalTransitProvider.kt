package com.androidfung.departureboard.data.repository.region.hk

import android.content.Context
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.model.TransitRegion
import com.androidfung.departureboard.data.model.hk.extractRawHkBusStopId
import com.androidfung.departureboard.data.model.gb.StationServiceHoursInfo
import com.androidfung.departureboard.data.model.hk.HkBusStopDirectory
import com.androidfung.departureboard.data.model.hk.HkDefaultStations
import com.androidfung.departureboard.data.model.hk.HkMtrStationRegistry
import com.androidfung.departureboard.data.model.hk.HkStationDirectory
import com.androidfung.departureboard.data.model.isHkBusStationId
import com.androidfung.departureboard.data.model.isHkTransitId
import com.androidfung.departureboard.data.model.hk.parseHkMtrStationId
import com.androidfung.departureboard.data.repository.DepartureSource
import com.androidfung.departureboard.data.repository.region.RegionalTransitProvider
import com.androidfung.departureboard.data.repository.region.hk.search.HkBusSearchEngine
import com.androidfung.departureboard.data.topology.CallingPointsResolver
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Hong Kong (MTR, KMB, Citybus) regional transit provider.
 * Wraps both MTR rail and franchised bus departure sources behind a single regional interface.
 */
class HkRegionalTransitProvider(
    private val context: Context? = null,
    private val hkBusSearchEngine: HkBusSearchEngine = HkBusSearchEngine()
) : RegionalTransitProvider {

    override val region: TransitRegion = TransitRegion.HK

    private val hkMtrDepartureSource = HkMtrDepartureSource()
    private val hkBusDepartureSource = HkBusDepartureSource()
    private val hkMtrCallingPointsResolver = HkMtrCallingPointsResolver()
    private val hkBusCallingPointsResolver = HkBusCallingPointsResolver()

    override val departureSources: List<DepartureSource> = listOf(
        hkMtrDepartureSource,
        hkBusDepartureSource
    )

    override val callingPointsResolvers: List<CallingPointsResolver> = listOf(
        hkMtrCallingPointsResolver,
        hkBusCallingPointsResolver
    )

    override fun ownsStationId(stationId: String): Boolean =
        stationId.isHkTransitId || HkMtrStationRegistry.getStation(stationId) != null

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
}
