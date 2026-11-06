package com.androidfung.departureboard.data.repository

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.androidfung.departureboard.data.datastore.StationPreferencesDataSource
import com.androidfung.departureboard.data.datastore.StationPreferencesDataStore
import com.androidfung.departureboard.data.db.cache.CachedDepartureEntity
import com.androidfung.departureboard.data.db.cache.DepartureCacheDatabase
import com.androidfung.departureboard.data.db.cache.DepartureDao
import com.androidfung.departureboard.data.model.CallingPoint
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TransitRegion
import com.androidfung.departureboard.data.model.gb.BodsDisruptionXmlParser
import com.androidfung.departureboard.data.model.gb.GbDefaultStations
import com.androidfung.departureboard.data.model.gb.StationServiceHoursInfo
import com.androidfung.departureboard.data.model.gb.TflLineStatusDetail
import com.androidfung.departureboard.data.model.gb.TflLineStatusItem
import com.androidfung.departureboard.data.network.gb.BodsApiService
import com.androidfung.departureboard.data.network.gb.BodsNetworkClient
import com.androidfung.departureboard.data.network.gb.NationalRailApiService
import com.androidfung.departureboard.data.network.gb.NationalRailNetworkClient
import com.androidfung.departureboard.data.network.gb.TflApiService
import com.androidfung.departureboard.data.network.gb.TflNetworkClient
import com.androidfung.departureboard.data.repository.region.RegionalTransitProvider
import com.androidfung.departureboard.data.repository.region.au.AuCallingPointsResolver
import com.androidfung.departureboard.data.repository.region.au.AuDepartureSource
import com.androidfung.departureboard.data.repository.region.au.AuRegionalTransitProvider
import com.androidfung.departureboard.data.repository.region.gb.BodsCallingPointsResolver
import com.androidfung.departureboard.data.repository.region.gb.BodsDepartureSource
import com.androidfung.departureboard.data.repository.region.gb.GbRegionalTransitProvider
import com.androidfung.departureboard.data.repository.region.gb.NationalRailDepartureSource
import com.androidfung.departureboard.data.repository.region.gb.TflCallingPointsResolver
import com.androidfung.departureboard.data.repository.region.gb.TflDepartureSource
import com.androidfung.departureboard.data.repository.region.gb.TimetableScheduleResolver
import com.androidfung.departureboard.data.repository.region.gb.TyneAndWearMetroDepartureSource
import com.androidfung.departureboard.data.repository.region.gb.mapper.TflStationMapper
import com.androidfung.departureboard.data.repository.region.gb.search.GbStationSearchEngine
import com.androidfung.departureboard.data.repository.region.hk.HkBusCallingPointsResolver
import com.androidfung.departureboard.data.repository.region.hk.HkBusDepartureSource
import com.androidfung.departureboard.data.repository.region.hk.HkMtrCallingPointsResolver
import com.androidfung.departureboard.data.repository.region.hk.HkMtrDepartureSource
import com.androidfung.departureboard.data.repository.region.hk.HkRegionalTransitProvider
import com.androidfung.departureboard.data.repository.region.jp.JpCallingPointsResolver
import com.androidfung.departureboard.data.repository.region.jp.JpDepartureSource
import com.androidfung.departureboard.data.repository.region.jp.JpRegionalTransitProvider
import com.androidfung.departureboard.data.repository.region.sg.SgCallingPointsResolver
import com.androidfung.departureboard.data.repository.region.sg.SgDepartureSource
import com.androidfung.departureboard.data.repository.region.sg.SgRegionalTransitProvider
import com.androidfung.departureboard.data.topology.CallingPointsEngine
import com.androidfung.departureboard.ui.theme.AppThemeMode
import com.androidfung.departureboard.util.CoalescingTimedCache
import com.androidfung.departureboard.widget.DepartureBoardWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Repository interface for multi-regional transit data operations.
 */
interface TransitRepository {
    val savedStationsFlow: Flow<List<Station>>
    val recentStationIdFlow: Flow<String?>
    val departureRowsCountFlow: Flow<Int>
    val themeModeFlow: Flow<AppThemeMode>
    val dynamicColorFlow: Flow<Boolean>
    val selectedRegionFlow: Flow<TransitRegion>
    val isHkRegionEnabledFlow: Flow<Boolean>
    val isBilingualEnabledFlow: Flow<Boolean>
    val autoSortByLocationFlow: Flow<Boolean>
    val recentSearchedStationsFlow: Flow<List<Station>>

    suspend fun searchStations(query: String): Result<List<Station>>
    suspend fun getStationById(stationId: String): Station?
    suspend fun getDepartures(stationId: String, stationName: String): Result<List<Departure>>
    suspend fun getBatchDepartures(stations: List<Station>): Map<String, Result<List<Departure>>>
    fun getDeparturesFlow(stationId: String, stationName: String): Flow<Result<List<Departure>>>
    suspend fun getCachedDepartures(stationId: String): List<Departure>
    suspend fun getLineStatuses(): Map<String, TflLineStatusItem>
    suspend fun getCallingPoints(departure: Departure): List<CallingPoint>
    suspend fun checkStationServiceHours(stationId: String, lineId: String? = null): StationServiceHoursInfo?
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
    suspend fun setSelectedRegion(region: TransitRegion)
    suspend fun setHkRegionEnabled(enabled: Boolean)
    suspend fun setBilingualEnabled(enabled: Boolean)
    suspend fun setAutoSortByLocation(enabled: Boolean)
    suspend fun getNearbyStationsFromApi(lat: Double, lon: Double, radiusMeters: Int = 1500): List<Station>
}

/**
 * Implementation of [TransitRepository] coordinating polymorphic [RegionalTransitProvider]s,
 * [DepartureQueryEngine], [CallingPointsEngine], DataStore preferences, and Room offline cache.
 */
class TransitRepositoryImpl(
    private val apiService: TflApiService = TflNetworkClient.apiService,
    private val nrApiService: NationalRailApiService = NationalRailNetworkClient.apiService,
    private val bodsApiService: BodsApiService = BodsNetworkClient.apiService,
    private val dataStore: StationPreferencesDataSource,
    private val departureDao: DepartureDao? = null,
    private val timetableScheduleResolver: TimetableScheduleResolver = TimetableScheduleResolver(apiService),
    private val context: Context? = null,
    customRegionalProviders: List<RegionalTransitProvider>? = null
) : TransitRepository {

    private val stationSearchEngine = GbStationSearchEngine(
        apiService = apiService,
        bodsApiService = bodsApiService,
        context = context
    )

    private val gbRegionalProvider = GbRegionalTransitProvider(
        apiService = apiService,
        nrApiService = nrApiService,
        bodsApiService = bodsApiService,
        stationSearchEngine = stationSearchEngine,
        timetableScheduleResolver = timetableScheduleResolver,
        stationLookup = { id -> GbDefaultStations.POPULAR_STATIONS.firstOrNull { it.id.equals(id, ignoreCase = true) } }
    )

    private val regionalProviders: List<RegionalTransitProvider> = customRegionalProviders ?: listOf(
        HkRegionalTransitProvider(context = context),
        SgRegionalTransitProvider,
        AuRegionalTransitProvider,
        JpRegionalTransitProvider,
        gbRegionalProvider
    )

    private val providersByRegion: Map<TransitRegion, RegionalTransitProvider> =
        regionalProviders.associateBy { it.region }

    private val callingPointsEngine = CallingPointsEngine(
        regionalProviders.flatMap { it.callingPointsResolvers }
    )

    constructor(context: Context) : this(
        apiService = TflNetworkClient.apiService,
        nrApiService = NationalRailNetworkClient.apiService,
        bodsApiService = BodsNetworkClient.apiService,
        dataStore = StationPreferencesDataStore(context.applicationContext),
        departureDao = DepartureCacheDatabase.getInstance(context.applicationContext).departureDao(),
        context = context.applicationContext
    )

    override val savedStationsFlow: Flow<List<Station>> = dataStore.savedStationsFlow
    override val recentStationIdFlow: Flow<String?> = dataStore.recentStationIdFlow
    override val departureRowsCountFlow: Flow<Int> = dataStore.departureRowsCountFlow
    override val themeModeFlow: Flow<AppThemeMode> = dataStore.themeModeFlow
    override val dynamicColorFlow: Flow<Boolean> = dataStore.dynamicColorFlow
    override val selectedRegionFlow: Flow<TransitRegion> = dataStore.selectedRegionFlow
    override val isHkRegionEnabledFlow: Flow<Boolean> = dataStore.isHkRegionEnabledFlow
    override val isBilingualEnabledFlow: Flow<Boolean> = dataStore.isBilingualEnabledFlow
    override val autoSortByLocationFlow: Flow<Boolean> = dataStore.autoSortByLocationFlow
    override val recentSearchedStationsFlow: Flow<List<Station>> = dataStore.recentSearchedStationsFlow

    override suspend fun setThemeMode(mode: AppThemeMode) = dataStore.setThemeMode(mode)
    override suspend fun setDynamicColor(enabled: Boolean) = dataStore.setDynamicColor(enabled)
    override suspend fun setSelectedRegion(region: TransitRegion) = dataStore.setSelectedRegion(region)
    override suspend fun setHkRegionEnabled(enabled: Boolean) = dataStore.setHkRegionEnabled(enabled)
    override suspend fun setBilingualEnabled(enabled: Boolean) = dataStore.setBilingualEnabled(enabled)
    override suspend fun setAutoSortByLocation(enabled: Boolean) = dataStore.setAutoSortByLocation(enabled)

    override suspend fun searchStations(query: String): Result<List<Station>> = withContext(Dispatchers.IO) {
        val currentRegion = try {
            dataStore.selectedRegionFlow.first()
        } catch (_: Exception) {
            TransitRegion.GB
        }
        val provider = providersByRegion[currentRegion] ?: gbRegionalProvider
        provider.searchStations(query)
    }

    override suspend fun getStationById(stationId: String): Station? = withContext(Dispatchers.IO) {
        val provider = regionalProviders.firstOrNull { it.ownsStationId(stationId) } ?: gbRegionalProvider
        provider.getStationById(stationId)
    }

    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val inFlightDepartures = CoalescingTimedCache<String, Result<List<Departure>>>(ttlMillis = 0L)

    override suspend fun getDepartures(stationId: String, stationName: String): Result<List<Departure>> =
        withContext(Dispatchers.IO) {
            inFlightDepartures.getOrPutCoalesced(
                key = stationId,
                scope = repositoryScope
            ) {
                val provider = regionalProviders.firstOrNull { it.ownsStationId(stationId) } ?: gbRegionalProvider
                val queryResult = provider.getDepartures(stationId, stationName)
                queryResult.fold(
                    onSuccess = { departures ->
                        if (departures.isNotEmpty()) {
                            cacheDeparturesSafely(stationId, departures)
                        }
                        Result.success(departures)
                    },
                    onFailure = { error ->
                        val exception = error as? Exception ?: Exception(error)
                        fallbackDepartures(stationId, exception)
                    }
                )
            }
        }

    override suspend fun getBatchDepartures(stations: List<Station>): Map<String, Result<List<Departure>>> =
        withContext(Dispatchers.IO) {
            if (stations.isEmpty()) return@withContext emptyMap()

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

    override suspend fun getLineStatuses(): Map<String, TflLineStatusItem> = withContext(Dispatchers.IO) {
        val gb = providersByRegion[TransitRegion.GB] as? GbRegionalTransitProvider ?: gbRegionalProvider
        gb.getLineStatuses()
    }

    override suspend fun getCallingPoints(departure: Departure): List<CallingPoint> = withContext(Dispatchers.IO) {
        callingPointsEngine.resolveCallingPoints(departure)
    }

    private suspend fun syncGlanceWidgets() {
        context?.let { ctx ->
            try {
                DepartureBoardWidget().updateAll(ctx)
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
     * Checks scheduled timetables or regional service-hours rules for a station to detect if service
     * has concluded for the night and when the first morning service departs.
     */
    override suspend fun checkStationServiceHours(
        stationId: String,
        lineId: String?
    ): StationServiceHoursInfo? = withContext(Dispatchers.IO) {
        val provider = regionalProviders.firstOrNull { it.ownsStationId(stationId) } ?: gbRegionalProvider
        provider.checkStationServiceHours(stationId, lineId)
    }

    /**
     * Fetches nearby stations for the currently selected region by latitude and longitude.
     */
    override suspend fun getNearbyStationsFromApi(lat: Double, lon: Double, radiusMeters: Int): List<Station> = withContext(Dispatchers.IO) {
        val currentRegion = try {
            dataStore.selectedRegionFlow.first()
        } catch (_: Exception) {
            TransitRegion.GB
        }
        val provider = providersByRegion[currentRegion] ?: gbRegionalProvider
        provider.getNearbyStations(lat, lon, radiusMeters)
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

    companion object {
        private const val LINE_STATUS_CACHE_TTL_MS = 180_000L
        private const val LINE_STATUS_CACHE_KEY = "ALL"
    }
}
