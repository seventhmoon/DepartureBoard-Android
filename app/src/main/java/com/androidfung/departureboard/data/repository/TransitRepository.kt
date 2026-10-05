package com.androidfung.departureboard.data.repository

import android.content.Context
import com.androidfung.departureboard.data.datastore.StationPreferencesDataSource
import com.androidfung.departureboard.data.datastore.StationPreferencesDataStore
import com.androidfung.departureboard.data.db.AppDatabase
import com.androidfung.departureboard.data.db.CachedDepartureEntity
import com.androidfung.departureboard.data.db.DepartureDao
import com.androidfung.departureboard.data.model.DefaultStations
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TflArrivalPrediction
import com.androidfung.departureboard.data.model.TflLineStatusItem
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.network.NationalRailApiService
import com.androidfung.departureboard.data.network.NationalRailNetworkClient
import com.androidfung.departureboard.data.network.TflApiService
import com.androidfung.departureboard.data.network.TflNetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Repository interface for TfL transit data operations.
 */
interface TransitRepository {
    val savedStationsFlow: Flow<List<Station>>
    val recentStationIdFlow: Flow<String?>

    suspend fun searchStations(query: String): Result<List<Station>>
    suspend fun getDepartures(stationId: String, stationName: String): Result<List<Departure>>
    suspend fun getBatchDepartures(stations: List<Station>): Map<String, Result<List<Departure>>>
    fun getDeparturesFlow(stationId: String, stationName: String): Flow<Result<List<Departure>>>
    suspend fun getLineStatuses(): Map<String, TflLineStatusItem>
    suspend fun saveStation(station: Station)
    suspend fun reorderStations(stations: List<Station>)
    suspend fun removeStation(stationId: String)
    suspend fun setRecentStationId(stationId: String)
}

/**
 * Implementation of TransitRepository using TfL API, National Rail (NRE Darwin) API, DataStore, and Room offline cache.
 */
class TransitRepositoryImpl(
    private val apiService: TflApiService = TflNetworkClient.apiService,
    private val nrApiService: NationalRailApiService = NationalRailNetworkClient.apiService,
    private val dataStore: StationPreferencesDataSource,
    private val departureDao: DepartureDao? = null
) : TransitRepository {

    constructor(context: Context) : this(
        apiService = TflNetworkClient.apiService,
        nrApiService = NationalRailNetworkClient.apiService,
        dataStore = StationPreferencesDataStore(context.applicationContext),
        departureDao = AppDatabase.getInstance(context.applicationContext).departureDao()
    )

    override val savedStationsFlow: Flow<List<Station>> = dataStore.savedStationsFlow
    override val recentStationIdFlow: Flow<String?> = dataStore.recentStationIdFlow

    override suspend fun searchStations(query: String): Result<List<Station>> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.success(DefaultStations.POPULAR_STATIONS)
        }

        try {
            val response = apiService.searchStations(query = trimmed)
            val matchedStations = response.matches.flatMap { match ->
                TflStationMapper.fromStopPointMatch(match)
            }

            // Hydrate station lines from StopPoint API for non-bus stations that don't have lines yet
            val hydratedStations = matchedStations.map { station ->
                if (!station.isBusOnly && station.lines.isEmpty()) {
                    try {
                        val detail = apiService.getStopPointDetail(station.id)
                        val lines = TflStationMapper.extractLines(detail)
                        if (lines.isNotEmpty()) station.copy(lines = lines) else station
                    } catch (_: Exception) {
                        station
                    }
                } else {
                    station
                }
            }

            val routeStopStations = findRouteStopsIfApplicable(trimmed)

            // Combine results, prioritizing exact bus route stops if route matched
            val combinedResults = if (routeStopStations.isNotEmpty()) {
                (routeStopStations + hydratedStations).distinctBy { it.id }
            } else {
                hydratedStations
            }

            if (combinedResults.isNotEmpty()) {
                Result.success(combinedResults)
            } else {
                val localMatches = DefaultStations.POPULAR_STATIONS.filter {
                    it.name.contains(trimmed, ignoreCase = true)
                }
                Result.success(localMatches)
            }
        } catch (e: Exception) {
            val localMatches = DefaultStations.POPULAR_STATIONS.filter {
                it.name.contains(trimmed, ignoreCase = true)
            }
            if (localMatches.isNotEmpty()) {
                Result.success(localMatches)
            } else {
                Result.failure(e)
            }
        }
    }

    override suspend fun getDepartures(stationId: String, stationName: String): Result<List<Departure>> =
        withContext(Dispatchers.IO) {
            try {
                var tflError: Exception? = null
                var nrError: Exception? = null

                // 1. Fetch TfL arrivals (Underground, Elizabeth Line, Overground, DLR, Buses)
                val tflArrivalsDeferred = async {
                    try {
                        fetchRawArrivalsWithHubExpansion(stationId)
                    } catch (e: Exception) {
                        tflError = e
                        emptyList()
                    }
                }

                // 2. Fetch National Rail departures if station has known CRS code(s)
                val crsCodes = NationalRailStationCodes.findCrsCodes(stationId, stationName)
                val nrServicesDeferred = async {
                    if (crsCodes.isNotEmpty()) {
                        val collectedServices = mutableListOf<com.androidfung.departureboard.data.model.NrTrainService>()
                        for (crs in crsCodes) {
                            try {
                                val board = nrApiService.getDepartureBoard(crs)
                                board.trainServices?.let { collectedServices.addAll(it) }
                            } catch (e: Exception) {
                                nrError = e
                            }
                        }
                        collectedServices
                    } else {
                        emptyList()
                    }
                }

                val rawArrivals = tflArrivalsDeferred.await()
                val nrServices = nrServicesDeferred.await()

                val tflDepartures = if (rawArrivals.isNotEmpty()) {
                    val distinct = TflDepartureMapper.deduplicate(rawArrivals)
                    distinct.map { TflDepartureMapper.mapToDeparture(it, stationId, stationName) }
                } else emptyList()

                val nrDepartures = nrServices.map { service ->
                    NationalRailDepartureMapper.mapToDeparture(service, stationId, stationName)
                }

                // Deduplicate cross-feed overlaps (e.g. Elizabeth line or Lioness line reported by both TfL and National Rail)
                val combinedDepartures = mergeAndDeduplicateFeeds(tflDepartures, nrDepartures)
                    .sortedBy { it.timeToStationSeconds }

                when {
                    combinedDepartures.isNotEmpty() -> {
                        cacheDeparturesSafely(stationId, combinedDepartures)
                        Result.success(combinedDepartures)
                    }
                    tflError != null && (crsCodes.isEmpty() || nrError != null) -> {
                        fallbackDepartures(stationId, tflError ?: Exception("Network error"))
                    }
                    else -> {
                        Result.success(emptyList())
                    }
                }
            } catch (e: Exception) {
                fallbackDepartures(stationId, e)
            }
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

    override fun getDeparturesFlow(stationId: String, stationName: String): Flow<Result<List<Departure>>> = flow {
        val cached = departureDao?.getDeparturesForStation(stationId)?.map { it.toDeparture() }
        if (!cached.isNullOrEmpty()) {
            emit(Result.success(cached))
        }
        emit(getDepartures(stationId, stationName))
    }.flowOn(Dispatchers.IO)

    // In-memory cache for TfL line statuses with 3-minute TTL (line statuses change slowly)
    private var cachedLineStatuses: Map<String, TflLineStatusItem> = emptyMap()
    private var lineStatusesCacheTimestamp: Long = 0L
    private val LINE_STATUS_CACHE_TTL_MS = 180_000L // 3 minutes

    override suspend fun getLineStatuses(): Map<String, TflLineStatusItem> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (cachedLineStatuses.isNotEmpty() && (now - lineStatusesCacheTimestamp) < LINE_STATUS_CACHE_TTL_MS) {
            return@withContext cachedLineStatuses
        }

        try {
            val fresh = apiService.getLineStatuses().associateBy { it.id.lowercase() }
            if (fresh.isNotEmpty()) {
                cachedLineStatuses = fresh
                lineStatusesCacheTimestamp = now
            }
            cachedLineStatuses
        } catch (_: Exception) {
            cachedLineStatuses
        }
    }

    override suspend fun saveStation(station: Station) = dataStore.saveStation(station)
    override suspend fun reorderStations(stations: List<Station>) = dataStore.saveStations(stations)
    override suspend fun removeStation(stationId: String) = dataStore.removeStation(stationId)
    override suspend fun setRecentStationId(stationId: String) = dataStore.setRecentStationId(stationId)

    // =========================================================================
    // Private Helpers: Search & Route Stops
    // =========================================================================

    private suspend fun findRouteStopsIfApplicable(query: String): List<Station> {
        val busRouteRegex = Regex("""^(?:[0-9]{1,3}|[A-Za-z]{1,2}[0-9]{1,3})$""", RegexOption.IGNORE_CASE)
        if (!busRouteRegex.matches(query)) return emptyList()

        return try {
            val stopPoints = apiService.getLineStopPoints(query.lowercase())
            stopPoints
                .filter { it.modes.contains(TransitMode.BUS.id) || it.id.startsWith(TflStopPointUtils.PREFIX_BUS_STOP) }
                .map { sp -> TflStationMapper.fromStopPointChild(sp) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // =========================================================================
    // Private Helpers: Departure Fetching with Hub Expansion
    // =========================================================================

    private suspend fun fetchRawArrivalsWithHubExpansion(stationId: String): List<TflArrivalPrediction> {
        var arrivals = apiService.getArrivals(stationId)
        if (arrivals.isNotEmpty()) return arrivals

        try {
            val detail = apiService.getStopPointDetail(stationId)
            val sortedChildren = detail.children.sortedByDescending { child ->
                TflStopPointUtils.getHubChildPriority(child.id)
            }

            val childArrivals = mutableListOf<TflArrivalPrediction>()
            for (child in sortedChildren) {
                if (child.id != stationId) {
                    try {
                        val childResult = apiService.getArrivals(child.id)
                        if (childResult.isNotEmpty()) {
                            childArrivals.addAll(childResult)
                        }
                    } catch (_: Exception) {}
                }
            }
            if (childArrivals.isNotEmpty()) {
                arrivals = childArrivals
            }
        } catch (_: Exception) {}

        return arrivals
    }

    private suspend fun cacheDeparturesSafely(stationId: String, departures: List<Departure>) {
        if (departures.isNotEmpty() && departureDao != null) {
            try {
                departureDao.replaceDeparturesForStation(
                    stationId,
                    departures.map { CachedDepartureEntity.fromDeparture(it) }
                )
            } catch (_: Exception) {}
        }
    }

    /**
     * Merges TfL predictions and National Rail services, avoiding duplicate entries for lines
     * reported by both sources (such as Elizabeth line and Overground/Lioness).
     */
    private fun mergeAndDeduplicateFeeds(
        tflDepartures: List<Departure>,
        nrDepartures: List<Departure>
    ): List<Departure> {
        if (tflDepartures.isEmpty()) return nrDepartures
        if (nrDepartures.isEmpty()) return tflDepartures

        val result = tflDepartures.toMutableList()

        for (nr in nrDepartures) {
            // Check if TfL already has an equivalent prediction for the same line and destination within 90 seconds
            val isDuplicate = result.any { tfl ->
                (tfl.lineId == nr.lineId || tfl.lineName.equals(nr.lineName, ignoreCase = true)) &&
                (tfl.destinationName.contains(nr.destinationName, ignoreCase = true) || nr.destinationName.contains(tfl.destinationName, ignoreCase = true)) &&
                kotlin.math.abs(tfl.timeToStationSeconds - nr.timeToStationSeconds) < 120
            }
            if (!isDuplicate) {
                result.add(nr)
            }
        }
        return result
    }

    private suspend fun fallbackDepartures(
        stationId: String,
        originalException: Exception
    ): Result<List<Departure>> {
        val cached = departureDao?.getDeparturesForStation(stationId)?.map { it.toDeparture() }
        return if (!cached.isNullOrEmpty()) {
            Result.success(cached)
        } else {
            Result.failure(originalException)
        }
    }
}
