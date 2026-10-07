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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    suspend fun getCachedDepartures(stationId: String): List<Departure>
    suspend fun getLineStatuses(): Map<String, TflLineStatusItem>
    suspend fun getCallingPoints(departure: Departure): List<com.androidfung.departureboard.data.model.CallingPoint>
    suspend fun saveStation(station: Station)
    suspend fun reorderStations(stations: List<Station>)
    suspend fun removeStation(stationId: String)
    suspend fun setRecentStationId(stationId: String)
    suspend fun getNearbyStationsFromApi(lat: Double, lon: Double, radiusMeters: Int = 1500): List<Station>
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
                                val board = try {
                                    nrApiService.getDepBoardWithDetails(crs)
                                } catch (_: Exception) {
                                    nrApiService.getDepartureBoard(crs)
                                }
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

                val isStPancrasOrKingsCrossNR = stationId == "910GSTPX" || stationId == "910GKNGX" ||
                        stationName.lowercase().let { it == "st pancras international" || it == "st pancras" || it == "king's cross" }

                val filteredRawArrivals = if (isStPancrasOrKingsCrossNR) {
                    rawArrivals.filter { arrival ->
                        val mode = arrival.modeName?.lowercase() ?: ""
                        val line = arrival.lineId?.lowercase() ?: ""
                        mode != "tube" && line !in listOf("circle", "hammersmith-city", "metropolitan", "northern", "piccadilly", "victoria", "tube")
                    }
                } else {
                    rawArrivals
                }

                val tflDepartures = if (filteredRawArrivals.isNotEmpty()) {
                    val distinct = TflDepartureMapper.deduplicate(filteredRawArrivals)
                    distinct.map { TflDepartureMapper.mapToDeparture(it, stationId, stationName) }
                } else emptyList()

                val nrDepartures = nrServices.map { service ->
                    NationalRailDepartureMapper.mapToDeparture(service, stationId, stationName)
                }

                // Deduplicate cross-feed overlaps (e.g. Elizabeth line or Lioness line reported by both TfL and National Rail)
                val merged = mergeAndDeduplicateFeeds(tflDepartures, nrDepartures)
                val combinedDepartures = (if (isStPancrasOrKingsCrossNR) {
                    merged.filter { dep ->
                        val badge = dep.lineBadge
                        badge.mode != TransitMode.TUBE &&
                                badge.lineId.lowercase() !in com.androidfung.departureboard.data.model.TflLineColors.TUBE_LINE_IDS
                    }
                } else {
                    merged
                }).sortedBy { it.timeToStationSeconds }

                when {
                    combinedDepartures.isNotEmpty() -> {
                        cacheDeparturesSafely(stationId, combinedDepartures)
                        Result.success(combinedDepartures)
                    }
                    tflError != null && (crsCodes.isEmpty() || nrError != null) -> {
                        fallbackDepartures(stationId, tflError)
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

    override suspend fun getCachedDepartures(stationId: String): List<Departure> = withContext(Dispatchers.IO) {
        departureDao?.getDeparturesForStation(stationId)?.map { it.toDeparture() } ?: emptyList()
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
                val fresh = apiService.getLineStatuses().associateBy { it.id.lowercase() }
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

    // In-memory cache for line route sequences to prevent redundant network calls on tap
    private val routeSequenceCache = java.util.concurrent.ConcurrentHashMap<String, com.androidfung.departureboard.data.model.TflRouteSequenceResponse>()

    override suspend fun getCallingPoints(departure: Departure): List<com.androidfung.departureboard.data.model.CallingPoint> = withContext(Dispatchers.IO) {
        // If departure already has calling points (e.g. from National Rail Darwin details)
        if (departure.callingPoints.isNotEmpty()) {
            return@withContext departure.callingPoints
        }

        // For TfL rail, tube, elizabeth line, and buses, query route sequence
        val lineId = departure.lineId.lowercase().trim()
        if (lineId.isBlank()) return@withContext emptyList()

        try {
            val sequenceResponse = routeSequenceCache.getOrPut(lineId) {
                apiService.getLineRouteSequence(lineId, "all")
            }

            val currentStationClean = StationNameFormatter.clean(departure.stationName).lowercase()
            val destClean = StationNameFormatter.clean(departure.destinationName).lowercase()
            val branchMap = sequenceResponse.stopPointSequences.associateBy { it.branchId }

            // 1. Try finding a branch sequence that directly contains both current station and destination
            var resolvedStops: List<com.androidfung.departureboard.data.model.TflMatchedStop>? = null

            for ((_, _, _, _, stopPoint) in sequenceResponse.stopPointSequences) {
                val names = stopPoint.map { StationNameFormatter.clean(it.name).lowercase() }
                val curIndex = names.indexOfFirst { it.contains(currentStationClean) || currentStationClean.contains(it) }
                val destIndex = names.indexOfFirst { it.contains(destClean) || destClean.contains(it) }
                if (curIndex != -1 && destIndex != -1 && destIndex >= curIndex) {
                    resolvedStops = stopPoint.subList(curIndex, destIndex + 1)
                    break
                }
            }

            // 2. Multi-branch traversal (e.g. Northern Line: Finchley Central -> Camden Town -> Charing Cross -> Kennington -> Battersea)
            if (resolvedStops == null && branchMap.isNotEmpty()) {
                val startBranches = sequenceResponse.stopPointSequences.filter { seq ->
                    seq.stopPoint.any {
                        val n = StationNameFormatter.clean(it.name).lowercase()
                        n.contains(currentStationClean) || currentStationClean.contains(n)
                    }
                }

                fun findBranchPath(
                    currBranchId: Int,
                    targetStr: String,
                    visited: Set<Int>
                ): List<Int>? {
                    if (currBranchId in visited) return null
                    val branch = branchMap[currBranchId] ?: return null
                    val containsTarget = branch.stopPoint.any {
                        val n = StationNameFormatter.clean(it.name).lowercase()
                        n.contains(targetStr) || targetStr.contains(n)
                    }
                    if (containsTarget) return listOf(currBranchId)

                    val nextVisited = visited + currBranchId
                    for (nxt in branch.nextBranchIds) {
                        val subPath = findBranchPath(nxt, targetStr, nextVisited)
                        if (subPath != null) return listOf(currBranchId) + subPath
                    }
                    return null
                }

                for ((branchId) in startBranches) {
                    val branchPath = findBranchPath(branchId, destClean, emptySet())
                    if (branchPath != null) {
                        val combined = mutableListOf<com.androidfung.departureboard.data.model.TflMatchedStop>()
                        for (bId in branchPath) {
                            val branch = branchMap[bId] ?: continue
                            for (sp in branch.stopPoint) {
                                if (combined.isEmpty() || combined.last().id != sp.id) {
                                    combined.add(sp)
                                }
                            }
                        }
                        val combinedNames = combined.map { StationNameFormatter.clean(it.name).lowercase() }
                        val curIdx = combinedNames.indexOfFirst { it.contains(currentStationClean) || currentStationClean.contains(it) }
                        val destIdx = combinedNames.indexOfFirst { it.contains(destClean) || destClean.contains(it) }

                        val sIdx = if (curIdx != -1) curIdx else 0
                        val eIdx = if (destIdx != -1 && destIdx >= sIdx) destIdx else (combined.size - 1)
                        resolvedStops = combined.subList(sIdx, eIdx + 1)
                        break
                    }
                }
            }

            if (!resolvedStops.isNullOrEmpty()) {
                return@withContext resolvedStops.mapIndexed { idx, stop ->
                    val cleanName = StationNameFormatter.clean(stop.name)
                    com.androidfung.departureboard.data.model.CallingPoint(
                        stationName = cleanName,
                        scheduledTime = if (idx == 0) departure.formattedActualClockTime else null,
                        estimatedTime = if (idx == 0) departure.formattedTimeToArrival else null,
                        isCurrentStation = idx == 0,
                        isDestination = idx == resolvedStops.lastIndex
                    )
                }
            }
        } catch (_: Exception) {
            // Ignore error and fall through to fallback
        }

        // Minimal fallback: [Current Station] -> [Destination Station]
        listOf(
            com.androidfung.departureboard.data.model.CallingPoint(
                stationName = departure.stationName,
                scheduledTime = departure.formattedActualClockTime,
                estimatedTime = departure.formattedTimeToArrival,
                isCurrentStation = true,
                isDestination = false
            ),
            com.androidfung.departureboard.data.model.CallingPoint(
                stationName = departure.destinationName,
                isCurrentStation = false,
                isDestination = true
            )
        )
    }

    override suspend fun saveStation(station: Station) = dataStore.saveStation(station)
    override suspend fun reorderStations(stations: List<Station>) = dataStore.saveStations(stations)
    override suspend fun removeStation(stationId: String) = dataStore.removeStation(stationId)
    override suspend fun setRecentStationId(stationId: String) = dataStore.setRecentStationId(stationId)

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

            // Gather candidate stop point IDs from both children and lineModeGroup/lineGroup
            val candidateIds = mutableListOf<String>()

            // 1. Children sorted by priority
            val sortedChildren = detail.children.sortedByDescending { child ->
                TflStopPointUtils.getHubChildPriority(child.id)
            }
            candidateIds.addAll(sortedChildren.map { it.id })

            // Note: StopPointDetail can have additional child IDs in children or children of hub
            val childArrivals = mutableListOf<TflArrivalPrediction>()
            val visited = mutableSetOf(stationId)

            // Query candidate children concurrently instead of sequential loops
            val unvisitedChildren = candidateIds.filter { visited.add(it) }.take(6)
            if (unvisitedChildren.isNotEmpty()) {
                val results = coroutineScope {
                    unvisitedChildren.map { childId ->
                        async {
                            try {
                                apiService.getArrivals(childId)
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }
                    }.awaitAll()
                }
                results.forEach { childArrivals.addAll(it) }
            }

            // Check known inter-modal siblings for unified stations with split modal NaPTAN IDs:
            // - Watford Junction: National Rail + London Overground (Lioness line) DC lines
            // - Wimbledon: District line (Tube) + Croydon Tramlink + South Western Railway
            // - Stratford: Tube (Central, Jubilee) + DLR + National Rail / Overground / Elizabeth line
            // - West Ham: Tube (District, H&C, Jubilee) + DLR + c2c Rail
            // - Canary Wharf: Jubilee line (Tube) + DLR + Elizabeth line
            // - West Hampstead: Jubilee line (Tube) + Overground (Mildmay) + Thameslink
            val idUpper = stationId.uppercase()
            val interchangeSiblings = when {
                idUpper == "HUBWFJ" || idUpper == "910GWATFDJ" -> listOf("910GWATFJDC")
                idUpper == "HUBWMB" || idUpper == "940GZZLUWIM" || idUpper == "910GWIMBLDN" -> listOf("940GZZCRWMB", "940GZZLUWIM", "910GWIMBLDN")
                idUpper == "940GZZCRWMB" -> listOf("940GZZLUWIM", "910GWIMBLDN")

                idUpper == "HUBSRA" || idUpper == "940GZZLUSTD" || idUpper == "940GZZDLSTR" || idUpper == "910GSTFD" ->
                    listOf("940GZZLUSTD", "940GZZDLSTR", "910GSTFD")

                idUpper == "HUBWEH" || idUpper == "940GZZLUWHM" || idUpper == "940GZZDLWHM" || idUpper == "910GWHAMHL" ->
                    listOf("940GZZLUWHM", "940GZZDLWHM", "910GWHAMHL")

                idUpper == "HUBCAW" || idUpper == "940GZZLUCYF" || idUpper == "940GZZDLCAN" || idUpper == "910GCANWHRF" || idUpper == "910GCW" ->
                    listOf("940GZZLUCYF", "940GZZDLCAN", "910GCANWHRF")

                idUpper == "HUBWHD" || idUpper == "940GZZLUWHP" || idUpper == "910GWHMDSTD" || idUpper == "910GWSTHMPD" || idUpper == "910GWSTHMPT" ->
                    listOf("940GZZLUWHP", "910GWHMDSTD", "910GWSTHMPT")

                else -> emptyList()
            }

            val unvisitedSiblings = interchangeSiblings.filter { visited.add(it) }
            if (unvisitedSiblings.isNotEmpty()) {
                val siblingResults = coroutineScope {
                    unvisitedSiblings.map { siblingId ->
                        async {
                            try {
                                apiService.getArrivals(siblingId)
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }
                    }.awaitAll()
                }
                siblingResults.forEach { childArrivals.addAll(it) }
            }

            if (childArrivals.isNotEmpty()) {
                arrivals = (arrivals + childArrivals).distinctBy { it.id }
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
            // Check if TfL already has an equivalent prediction for the same line and destination within a 180s window
            val isDuplicate = result.any { tfl ->
                val linesMatch = tfl.lineId == nr.lineId ||
                        tfl.lineName.equals(nr.lineName, ignoreCase = true) ||
                        // Check Overground alias match (Lioness vs London Overground)
                        (tfl.lineId in listOf("lioness", "mildmay", "weaver", "suffragette", "liberty", "windrush") && nr.lineId == "overground") ||
                        (nr.lineId in listOf("lioness", "mildmay", "weaver", "suffragette", "liberty", "windrush") && tfl.lineId == "overground")

                val destsMatch = tfl.destinationName.contains(nr.destinationName, ignoreCase = true) ||
                        nr.destinationName.contains(tfl.destinationName, ignoreCase = true) ||
                        // Matches Euston / London Euston
                        (tfl.destinationName.contains("Euston", ignoreCase = true) && nr.destinationName.contains("Euston", ignoreCase = true))

                linesMatch && destsMatch && kotlin.math.abs(tfl.timeToStationSeconds - nr.timeToStationSeconds) < 180
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
