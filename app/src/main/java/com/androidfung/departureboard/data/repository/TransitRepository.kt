package com.androidfung.departureboard.data.repository

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.androidfung.departureboard.data.datastore.StationPreferencesDataSource
import com.androidfung.departureboard.data.datastore.StationPreferencesDataStore
import com.androidfung.departureboard.data.db.AppDatabase
import com.androidfung.departureboard.data.db.CachedDepartureEntity
import com.androidfung.departureboard.data.db.DepartureDao
import com.androidfung.departureboard.data.model.DefaultStations
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TflLineStatusItem
import com.androidfung.departureboard.data.network.NationalRailApiService
import com.androidfung.departureboard.data.network.NationalRailNetworkClient
import com.androidfung.departureboard.data.network.TflApiService
import com.androidfung.departureboard.data.network.TflNetworkClient
import com.androidfung.departureboard.ui.theme.AppThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Repository interface for TfL transit data operations.
 */
interface TransitRepository {
    val savedStationsFlow: Flow<List<Station>>
    val recentStationIdFlow: Flow<String?>
    val departureRowsCountFlow: Flow<Int>
    val themeModeFlow: Flow<AppThemeMode>
    val dynamicColorFlow: Flow<Boolean>
    val selectedRegionFlow: Flow<com.androidfung.departureboard.data.model.TransitRegion>
    val isHkRegionEnabledFlow: Flow<Boolean>
    val autoSortByLocationFlow: Flow<Boolean>
    val recentSearchedStationsFlow: Flow<List<Station>>

    suspend fun searchStations(query: String): Result<List<Station>>
    suspend fun getStationById(stationId: String): Station?
    suspend fun getDepartures(stationId: String, stationName: String): Result<List<Departure>>
    suspend fun getBatchDepartures(stations: List<Station>): Map<String, Result<List<Departure>>>
    fun getDeparturesFlow(stationId: String, stationName: String): Flow<Result<List<Departure>>>
    suspend fun getCachedDepartures(stationId: String): List<Departure>
    suspend fun getLineStatuses(): Map<String, TflLineStatusItem>
    suspend fun getCallingPoints(departure: Departure): List<com.androidfung.departureboard.data.model.CallingPoint>
    suspend fun checkStationServiceHours(stationId: String, lineId: String? = null): com.androidfung.departureboard.data.model.StationServiceHoursInfo?
    suspend fun saveStation(station: Station)
    suspend fun reorderStations(stations: List<Station>)
    suspend fun removeStation(stationId: String)
    suspend fun setRecentStationId(stationId: String)
    suspend fun addRecentSearchedStation(station: Station)
    suspend fun removeRecentSearchedStation(stationId: String)
    suspend fun clearRecentSearchedStations()
    suspend fun setDepartureRowsCount(count: Int)
    suspend fun setThemeMode(mode: AppThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setSelectedRegion(region: com.androidfung.departureboard.data.model.TransitRegion)
    suspend fun setHkRegionEnabled(enabled: Boolean)
    suspend fun setAutoSortByLocation(enabled: Boolean)
    suspend fun getNearbyStationsFromApi(lat: Double, lon: Double, radiusMeters: Int = 1500): List<Station>
}

/**
 * Implementation of TransitRepository using TfL API, National Rail (NRE Darwin) API, DataStore, and Room offline cache.
 */
class TransitRepositoryImpl(
    private val apiService: TflApiService = TflNetworkClient.apiService,
    private val nrApiService: NationalRailApiService = NationalRailNetworkClient.apiService,
    private val bodsApiService: com.androidfung.departureboard.data.network.BodsApiService = com.androidfung.departureboard.data.network.BodsNetworkClient.apiService,
    private val dataStore: StationPreferencesDataSource,
    private val departureDao: DepartureDao? = null,
    private val timetableScheduleResolver: TimetableScheduleResolver = TimetableScheduleResolver(apiService),
    private val context: Context? = null
) : TransitRepository {

    private val tflDepartureSource = com.androidfung.departureboard.data.repository.source.TflDepartureSource(apiService)
    private val nationalRailDepartureSource = com.androidfung.departureboard.data.repository.source.NationalRailDepartureSource(nrApiService)
    private val bodsDepartureSource = com.androidfung.departureboard.data.repository.source.BodsDepartureSource(bodsApiService) { id -> getStationById(id) }
    private val tyneAndWearMetroSource = com.androidfung.departureboard.data.repository.source.TyneAndWearMetroSource()
    private val hkBusDepartureSource = com.androidfung.departureboard.data.repository.source.HkBusDepartureSource()
    private val hkMtrDepartureSource = com.androidfung.departureboard.data.repository.source.HkMtrDepartureSource()
    private val hkBusCallingPointsResolver = com.androidfung.departureboard.data.resolver.hk.HkBusCallingPointsResolver()
    private val hkMtrCallingPointsResolver = com.androidfung.departureboard.data.resolver.hk.HkMtrCallingPointsResolver()
    private val bodsCallingPointsResolver = com.androidfung.departureboard.data.resolver.gb.BodsCallingPointsResolver(bodsApiService)
    private val tflCallingPointsResolver = com.androidfung.departureboard.data.resolver.gb.TflCallingPointsResolver(apiService)

    private val callingPointsEngine = com.androidfung.departureboard.data.resolver.CallingPointsEngine(
        listOf(
            bodsCallingPointsResolver,
            hkMtrCallingPointsResolver,
            hkBusCallingPointsResolver,
            tflCallingPointsResolver
        )
    )

    private val departureQueryEngine = DepartureQueryEngine(
        listOf(
            tflDepartureSource,
            nationalRailDepartureSource,
            bodsDepartureSource,
            tyneAndWearMetroSource,
            hkBusDepartureSource,
            hkMtrDepartureSource
        )
    )
    private val stationSearchEngine = com.androidfung.departureboard.data.repository.search.StationSearchEngine(
        apiService = apiService,
        bodsApiService = bodsApiService,
        context = context
    )

    constructor(context: Context) : this(
        apiService = TflNetworkClient.apiService,
        nrApiService = NationalRailNetworkClient.apiService,
        bodsApiService = com.androidfung.departureboard.data.network.BodsNetworkClient.apiService,
        dataStore = StationPreferencesDataStore(context.applicationContext),
        departureDao = AppDatabase.getInstance(context.applicationContext).departureDao(),
        context = context.applicationContext
    )

    override val savedStationsFlow: Flow<List<Station>> = dataStore.savedStationsFlow
    override val recentStationIdFlow: Flow<String?> = dataStore.recentStationIdFlow
    override val departureRowsCountFlow: Flow<Int> = dataStore.departureRowsCountFlow
    override val themeModeFlow: Flow<AppThemeMode> = dataStore.themeModeFlow
    override val dynamicColorFlow: Flow<Boolean> = dataStore.dynamicColorFlow
    override val selectedRegionFlow: Flow<com.androidfung.departureboard.data.model.TransitRegion> = dataStore.selectedRegionFlow
    override val isHkRegionEnabledFlow: Flow<Boolean> = dataStore.isHkRegionEnabledFlow
    override val autoSortByLocationFlow: Flow<Boolean> = dataStore.autoSortByLocationFlow
    override val recentSearchedStationsFlow: Flow<List<Station>> = dataStore.recentSearchedStationsFlow

    override suspend fun setThemeMode(mode: AppThemeMode) = dataStore.setThemeMode(mode)
    override suspend fun setDynamicColor(enabled: Boolean) = dataStore.setDynamicColor(enabled)
    override suspend fun setSelectedRegion(region: com.androidfung.departureboard.data.model.TransitRegion) = dataStore.setSelectedRegion(region)
    override suspend fun setHkRegionEnabled(enabled: Boolean) = dataStore.setHkRegionEnabled(enabled)
    override suspend fun setAutoSortByLocation(enabled: Boolean) = dataStore.setAutoSortByLocation(enabled)

    override suspend fun searchStations(query: String): Result<List<Station>> = withContext(Dispatchers.IO) {
        val currentRegion = try {
            dataStore.selectedRegionFlow.first()
        } catch (_: Exception) {
            com.androidfung.departureboard.data.model.TransitRegion.GB
        }
        if (currentRegion == com.androidfung.departureboard.data.model.TransitRegion.HK) {
            Result.success(com.androidfung.departureboard.data.model.HkStationDirectory.searchHkStations(query))
        } else {
            stationSearchEngine.searchStations(query)
        }
    }

    override suspend fun getStationById(stationId: String): Station? = withContext(Dispatchers.IO) {
        // 1. Check HK stations if HK station ID
        if (stationId.startsWith("HK_")) {
            com.androidfung.departureboard.data.model.HkDefaultStations.POPULAR_STATIONS.firstOrNull {
                it.id.equals(stationId, ignoreCase = true)
            }?.let { return@withContext it }

            if (stationId.startsWith("HK_MTR_")) {
                val parts = stationId.removePrefix("HK_MTR_").split("_")
                val lineCode = parts.getOrNull(0)
                val staCode = parts.getOrNull(1) ?: (if (parts.size == 1) parts[0] else "")
                val mtrStation = com.androidfung.departureboard.data.model.HkMtrStationRegistry.getStation(staCode)
                if (mtrStation != null) {
                    return@withContext com.androidfung.departureboard.data.model.HkStationDirectory.mtrToStation(mtrStation, lineCode)
                }
            }
        }

        // 2. Check local popular stations first
        DefaultStations.POPULAR_STATIONS.firstOrNull { it.id.equals(stationId, ignoreCase = true) }?.let {
            return@withContext it
        }

        // 3. Fetch live StopPointDetail from TfL API
        try {
            val detail = apiService.getStopPointDetail(stationId)
            val lines = TflStationMapper.extractLines(detail)
            val cleaned = StationNameFormatter.clean(detail.commonName ?: stationId)
            val extractedModes = lines.mapNotNull { it.mode }.distinct()
            val modes = extractedModes.ifEmpty { listOf("tube") }
            val firstChild = detail.children.firstOrNull()
            val lat = firstChild?.lat
            val lon = firstChild?.lon

            Station(
                id = detail.id,
                name = cleaned,
                modes = modes,
                zone = null,
                lat = lat,
                lon = lon,
                lines = lines,
                isFavorite = false
            )
        } catch (_: Exception) {
            null
        }
    }

    private val repositoryScope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    private val inFlightDepartures = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Deferred<Result<List<Departure>>>>()
    private val inFlightLock = Mutex()

    override suspend fun getDepartures(stationId: String, stationName: String): Result<List<Departure>> =
        withContext(Dispatchers.IO) {
            val deferred = inFlightLock.withLock {
                inFlightDepartures[stationId] ?: run {
                    val d = repositoryScope.async {
                        try {
                            val queryResult = departureQueryEngine.queryDepartures(stationId, stationName)
                            queryResult.fold(
                                onSuccess = { departures ->
                                    if (departures.isNotEmpty()) {
                                        cacheDeparturesSafely(stationId, departures)
                                    }
                                    Result.success(departures)
                                },
                                onFailure = { error ->
                                    val exception = if (error is Exception) error else Exception(error)
                                    fallbackDepartures(stationId, exception)
                                }
                            )
                        } finally {
                            inFlightLock.withLock {
                                inFlightDepartures.remove(stationId)
                            }
                        }
                    }
                    inFlightDepartures[stationId] = d
                    d
                }
            }
            deferred.await()
        }

    override suspend fun getBatchDepartures(stations: List<Station>): Map<String, Result<List<Departure>>> =
        withContext(Dispatchers.IO) {
            if (stations.isEmpty()) return@withContext emptyMap()
            
            // TfL /StopPoint/{id}/Arrivals only accepts a single ID.
            // Execute concurrent requests in parallel across all stations.
            coroutineScope {
                stations.map { station ->
                    async {
                        station.id to getDepartures(station.id, station.name)
                    }
                }.awaitAll().toMap()
            }
        }

    override suspend fun getCachedDepartures(stationId: String): List<Departure> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        departureDao?.getDeparturesForStation(stationId)
            ?.filter { !it.isExpired(now) }
            ?.map { it.toDeparture(now) }
            ?.sortedBy { it.timeToStationSeconds }
            ?: emptyList()
    }

    override fun getDeparturesFlow(stationId: String, stationName: String): Flow<Result<List<Departure>>> = flow {
        val cached = getCachedDepartures(stationId)
        if (cached.isNotEmpty()) {
            emit(Result.success(cached))
        }
        emit(getDepartures(stationId, stationName))
    }.flowOn(Dispatchers.IO)

    // In-memory cache for TfL line statuses with 3-minute TTL (line statuses change slowly)
    private var cachedLineStatuses: Map<String, TflLineStatusItem> = emptyMap()
    private var lineStatusesCacheTimestamp: Long = 0L
    private val LINE_STATUS_CACHE_TTL_MS = 180_000L // 3 minutes

    // Serializes cache-miss refreshes so concurrent callers (30s polling + AI sheet)
    // share a single in-flight network request instead of thundering the API.
    private val lineStatusesLock = Mutex()

    override suspend fun getLineStatuses(): Map<String, TflLineStatusItem> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (cachedLineStatuses.isNotEmpty() && (now - lineStatusesCacheTimestamp) < LINE_STATUS_CACHE_TTL_MS) {
            return@withContext cachedLineStatuses
        }

        lineStatusesLock.withLock {
            // Double-check TTL: another caller may have refreshed while we waited for the lock.
            val refreshedAt = System.currentTimeMillis()
            if (cachedLineStatuses.isNotEmpty() && (refreshedAt - lineStatusesCacheTimestamp) < LINE_STATUS_CACHE_TTL_MS) {
                return@withLock cachedLineStatuses
            }

            try {
                val fresh = apiService.getLineStatuses().associateBy { it.id.lowercase() }.toMutableMap()

                // Concurrently fetch DfT BODS SIRI-SX disruptions for regional bus lines
                try {
                    val bodsDisruptionsXml = bodsApiService.getDisruptions().string()
                    val bodsDisruptions = com.androidfung.departureboard.data.model.BodsDisruptionXmlParser.parse(bodsDisruptionsXml)
                    bodsDisruptions.forEach { bd ->
                        val line = bd.lineRef?.lowercase()
                        if (!line.isNullOrBlank()) {
                            val lineKey = if (line.startsWith("bods-")) line else "bods-$line"
                            fresh[lineKey] = com.androidfung.departureboard.data.model.TflLineStatusItem(
                                id = lineKey,
                                name = bd.lineRef.uppercase(),
                                modeName = "bus",
                                lineStatuses = listOf(
                                    com.androidfung.departureboard.data.model.TflLineStatusDetail(
                                        id = 0,
                                        statusSeverity = 9,
                                        statusSeverityDescription = bd.reason ?: "Disrupted",
                                        reason = "${bd.summary}${if (!bd.description.isNullOrBlank()) ": ${bd.description}" else ""}"
                                    )
                                )
                            )
                        }
                    }
                } catch (_: Exception) {
                    // Non-fatal if BODS disruptions call fails
                }

                if (fresh.isNotEmpty()) {
                    cachedLineStatuses = fresh
                    lineStatusesCacheTimestamp = refreshedAt
                }
            } catch (_: Exception) {
                // Keep serving stale (or empty) statuses on network failure.
            }
            cachedLineStatuses
        }
    }

    override suspend fun getCallingPoints(departure: Departure): List<com.androidfung.departureboard.data.model.CallingPoint> = withContext(Dispatchers.IO) {
        callingPointsEngine.resolveCallingPoints(departure)
    }


    private suspend fun syncGlanceWidgets() {
        context?.let { ctx ->
            try {
                com.androidfung.departureboard.widget.DepartureBoardWidget().updateAll(ctx)
            } catch (_: Exception) {}
        }
    }

    override suspend fun saveStation(station: Station) {
        dataStore.saveStation(station)
        syncGlanceWidgets()
    }

    override suspend fun reorderStations(stations: List<Station>) {
        dataStore.saveStations(stations)
        syncGlanceWidgets()
    }

    override suspend fun removeStation(stationId: String) {
        dataStore.removeStation(stationId)
        syncGlanceWidgets()
    }
    override suspend fun setRecentStationId(stationId: String) = dataStore.setRecentStationId(stationId)
    override suspend fun addRecentSearchedStation(station: Station) = dataStore.addRecentSearchedStation(station)
    override suspend fun removeRecentSearchedStation(stationId: String) = dataStore.removeRecentSearchedStation(stationId)
    override suspend fun clearRecentSearchedStations() = dataStore.clearRecentSearchedStations()
    override suspend fun setDepartureRowsCount(count: Int) = dataStore.setDepartureRowsCount(count)

    /**
     * Checks scheduled timetables for a station to detect if service has concluded for the night
     * and when the first morning train departs.
     */
    override suspend fun checkStationServiceHours(stationId: String, lineId: String?): com.androidfung.departureboard.data.model.StationServiceHoursInfo? =
        timetableScheduleResolver.checkStationServiceHours(stationId, lineId)

    /**
     * Fetches nearby stations directly from TfL API by latitude and longitude.
     */
    override suspend fun getNearbyStationsFromApi(lat: Double, lon: Double, radiusMeters: Int): List<Station> {
        return try {
            val response = apiService.getNearbyStopPoints(
                lat = lat,
                lon = lon,
                radiusMeters = radiusMeters
            )
            response.stopPoints
                .filter { it.modes.isNotEmpty() }
                .map { sp -> TflStationMapper.fromStopPointChild(sp) }
                .distinctBy { it.id }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun cacheDeparturesSafely(stationId: String, departures: List<Departure>) {
        if (departures.isNotEmpty() && departureDao != null) {
            try {
                val now = System.currentTimeMillis()
                departureDao.replaceDeparturesForStation(
                    stationId,
                    departures.map { CachedDepartureEntity.fromDeparture(it, now) }
                )
                departureDao.clearStaleDepartures(now - 3_600_000L)
            } catch (_: Exception) {}
        }
    }

    private suspend fun fallbackDepartures(
        stationId: String,
        originalException: Exception
    ): Result<List<Departure>> {
        val now = System.currentTimeMillis()
        val cached = departureDao?.getDeparturesForStation(stationId)
            ?.filter { !it.isExpired(now) }
            ?.map { it.toDeparture(now) }
            ?.sortedBy { it.timeToStationSeconds }
        return if (!cached.isNullOrEmpty()) {
            Result.success(cached)
        } else {
            Result.failure(originalException)
        }
    }
}
