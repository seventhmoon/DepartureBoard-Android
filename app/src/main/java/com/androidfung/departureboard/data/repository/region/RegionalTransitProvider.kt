package com.androidfung.departureboard.data.repository.region

import android.content.Context
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.model.TransitRegion
import com.androidfung.departureboard.data.model.au.AuStationDirectory
import com.androidfung.departureboard.data.model.extractRawHkBusStopId
import com.androidfung.departureboard.data.model.gb.DefaultStations
import com.androidfung.departureboard.data.model.gb.StationServiceHoursInfo
import com.androidfung.departureboard.data.model.hk.HkBusStopDirectory
import com.androidfung.departureboard.data.model.hk.HkDefaultStations
import com.androidfung.departureboard.data.model.hk.HkMtrStationRegistry
import com.androidfung.departureboard.data.model.hk.HkStationDirectory
import com.androidfung.departureboard.data.model.isAuTransitId
import com.androidfung.departureboard.data.model.isHkBusStationId
import com.androidfung.departureboard.data.model.isHkTransitId
import com.androidfung.departureboard.data.model.isJpTransitId
import com.androidfung.departureboard.data.model.isSgTransitId
import com.androidfung.departureboard.data.model.jp.JpStationDirectory
import com.androidfung.departureboard.data.model.parseHkMtrStationId
import com.androidfung.departureboard.data.model.sg.SgStationDirectory
import com.androidfung.departureboard.data.network.gb.TflApiService
import com.androidfung.departureboard.data.repository.StationNameFormatter
import com.androidfung.departureboard.data.repository.TimetableScheduleResolver
import com.androidfung.departureboard.data.repository.mapper.TflStationMapper
import com.androidfung.departureboard.data.repository.search.HkBusSearchEngine
import com.androidfung.departureboard.data.repository.search.StationSearchEngine
import com.androidfung.departureboard.data.repository.source.hk.HkBusServiceHoursResolver
import com.androidfung.departureboard.data.repository.source.hk.HkMtrServiceHoursResolver

/**
 * Polymorphic strategy encapsulating region-specific station search, station ID lookup,
 * and service-hours resolution for a [TransitRegion].
 */
interface RegionalTransitProvider {
    val region: TransitRegion

    /**
     * Returns true if [stationId] belongs to this region's namespace or station registry.
     */
    fun ownsStationId(stationId: String): Boolean

    /**
     * Searches stations within this region matching [query].
     */
    suspend fun searchStations(query: String): Result<List<Station>>

    /**
     * Resolves a [Station] by its [stationId] within this region, or null if not found.
     */
    suspend fun getStationById(stationId: String): Station?

    /**
     * Checks whether the station is currently closed overnight and returns first/last service times.
     */
    suspend fun checkStationServiceHours(
        stationId: String,
        lineId: String? = null
    ): StationServiceHoursInfo? = null
}

/**
 * Hong Kong (MTR, KMB, Citybus) regional transit provider.
 */
class HkRegionalTransitProvider(
    private val context: Context? = null,
    private val hkBusSearchEngine: HkBusSearchEngine = HkBusSearchEngine()
) : RegionalTransitProvider {

    override val region: TransitRegion = TransitRegion.HK

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
                val nameTc = detail.nameTc.orEmpty().trim()
                val nameEn = detail.nameEn.orEmpty().trim()
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

/**
 * United Kingdom (TfL, National Rail, DfT BODS, Tyne & Wear Metro) regional transit provider.
 */
class GbRegionalTransitProvider(
    private val apiService: TflApiService,
    private val stationSearchEngine: StationSearchEngine,
    private val timetableScheduleResolver: TimetableScheduleResolver
) : RegionalTransitProvider {

    override val region: TransitRegion = TransitRegion.GB

    override fun ownsStationId(stationId: String): Boolean =
        !stationId.isHkTransitId &&
            !stationId.isSgTransitId &&
            !stationId.isAuTransitId &&
            !stationId.isJpTransitId

    override suspend fun searchStations(query: String): Result<List<Station>> =
        stationSearchEngine.searchStations(query)

    override suspend fun getStationById(stationId: String): Station? {
        DefaultStations.POPULAR_STATIONS.firstOrNull {
            it.id.equals(stationId, ignoreCase = true)
        }?.let { return it }

        return try {
            val detail = apiService.getStopPointDetail(stationId)
            val lines = TflStationMapper.extractLines(detail)
            val cleaned = StationNameFormatter.clean(detail.commonName ?: stationId)
            val extractedModes = lines.mapNotNull { it.mode }.distinct()
            val modes = extractedModes.ifEmpty { listOf("tube") }
            val firstChild = detail.children.firstOrNull()

            Station(
                id = detail.id,
                name = cleaned,
                modes = modes,
                zone = null,
                lat = firstChild?.lat,
                lon = firstChild?.lon,
                lines = lines,
                isFavorite = false
            )
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun checkStationServiceHours(
        stationId: String,
        lineId: String?
    ): StationServiceHoursInfo? =
        timetableScheduleResolver.checkStationServiceHours(stationId, lineId)
}
