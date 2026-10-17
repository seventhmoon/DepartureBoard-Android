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
import com.androidfung.departureboard.data.model.TflArrivalPrediction
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
    suspend fun setDepartureRowsCount(count: Int)
    suspend fun setThemeMode(mode: AppThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
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
    private val departureQueryEngine = DepartureQueryEngine(
        listOf(tflDepartureSource, nationalRailDepartureSource, bodsDepartureSource, tyneAndWearMetroSource)
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

    override suspend fun setThemeMode(mode: AppThemeMode) = dataStore.setThemeMode(mode)
    override suspend fun setDynamicColor(enabled: Boolean) = dataStore.setDynamicColor(enabled)

    override suspend fun searchStations(query: String): Result<List<Station>> =
        stationSearchEngine.searchStations(query)

    override suspend fun getStationById(stationId: String): Station? = withContext(Dispatchers.IO) {
        // 1. Check local popular stations first
        DefaultStations.POPULAR_STATIONS.firstOrNull { it.id.equals(stationId, ignoreCase = true) }?.let {
            return@withContext it
        }

        // 2. Fetch live StopPointDetail from TfL API
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

    override suspend fun getDepartures(stationId: String, stationName: String): Result<List<Departure>> =
        withContext(Dispatchers.IO) {
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

    // In-memory cache for line route sequences to prevent redundant network calls on tap
    private val routeSequenceCache = java.util.concurrent.ConcurrentHashMap<String, com.androidfung.departureboard.data.model.TflRouteSequenceResponse>()

    override suspend fun getCallingPoints(departure: Departure): List<com.androidfung.departureboard.data.model.CallingPoint> = withContext(Dispatchers.IO) {
        // If departure already has calling points (e.g. from National Rail Darwin details)
        if (departure.callingPoints.isNotEmpty()) {
            return@withContext departure.callingPoints
        }

        // For BODS buses, resolve calling points from BODS vehicle journey data
        if (departure.lineId.startsWith("bods-", ignoreCase = true)) {
            val rawLineRef = departure.lineId.removePrefix("bods-").uppercase()
            return@withContext fetchBodsCallingPoints(rawLineRef, departure)
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
                // If we have a vehicleId, query live Line Arrivals across the route to get exact prediction times
                val vehicleArrivalsByStation = mutableMapOf<String, TflArrivalPrediction>()
                val vehicleId = departure.vehicleId
                if (!vehicleId.isNullOrBlank()) {
                    try {
                        val lineArrivals = apiService.getLineArrivals(lineId)
                        lineArrivals.filter { it.vehicleId == vehicleId }.forEach { pred ->
                            val naptan = pred.naptanId?.lowercase()
                            val stName = pred.stationName?.let { StationNameFormatter.clean(it).lowercase() }
                            if (!naptan.isNullOrBlank()) vehicleArrivalsByStation[naptan] = pred
                            if (!stName.isNullOrBlank()) vehicleArrivalsByStation[stName] = pred
                        }
                    } catch (_: Exception) {}
                }

                val baseSeconds = departure.timeToStationSeconds
                val nowMillis = System.currentTimeMillis()
                val isBus = departure.modeName.equals("bus", ignoreCase = true)
                val interStationSeconds = if (isBus) 90 else 120 // 1.5 min per bus stop, 2 min per tube stop
                val londonZone = java.time.ZoneId.of("Europe/London")
                val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm", java.util.Locale.UK)
                    .withZone(londonZone)

                return@withContext resolvedStops.mapIndexed { idx, stop ->
                    val cleanName = StationNameFormatter.clean(stop.name)
                    val stopKey = cleanName.lowercase()
                    val naptanKey = stop.id.lowercase()

                    // Check if there is a live prediction for this exact vehicle at this calling point
                    val livePrediction = vehicleArrivalsByStation[naptanKey] ?: vehicleArrivalsByStation[stopKey]

                    val formattedTime: String? = when {
                        idx == 0 -> departure.formattedActualClockTime
                        livePrediction != null && livePrediction.timeToStation > 0 -> {
                            val arrivalMillis = nowMillis + (livePrediction.timeToStation * 1000L)
                            timeFormatter.format(java.time.Instant.ofEpochMilli(arrivalMillis))
                        }
                        departure.expectedArrivalIso != null || baseSeconds > 0 -> {
                            // Extrapolate forward from current station arrival
                            val cumulativeSeconds = baseSeconds + (idx * interStationSeconds)
                            val arrivalMillis = nowMillis + (cumulativeSeconds * 1000L)
                            timeFormatter.format(java.time.Instant.ofEpochMilli(arrivalMillis))
                        }
                        else -> null
                    }

                    val estimatedBadge: String? = when {
                        idx == 0 -> departure.formattedTimeToArrival
                        livePrediction != null && livePrediction.timeToStation > 0 -> {
                            val mins = (livePrediction.timeToStation / 60).coerceAtLeast(1)
                            "$mins min${if (mins > 1) "s" else ""}"
                        }
                        else -> null
                    }

                    com.androidfung.departureboard.data.model.CallingPoint(
                        stationName = cleanName,
                        scheduledTime = formattedTime,
                        estimatedTime = estimatedBadge,
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

    private suspend fun fetchBodsCallingPoints(lineRef: String, departure: Departure): List<com.androidfung.departureboard.data.model.CallingPoint> {
        return try {
            val responseBody = bodsApiService.getVehicleDatafeedByLine(lineRef = lineRef)
            val xmlString = responseBody.string()
            val activities = com.androidfung.departureboard.data.model.BodsXmlParser.parse(xmlString)

            // Find matching vehicle activity by destination or line
            val match = activities.firstOrNull { act ->
                act.destinationName?.replace("_", " ")?.contains(departure.destinationName, ignoreCase = true) == true ||
                act.lineRef.equals(lineRef, ignoreCase = true)
            } ?: activities.firstOrNull()

            val origin = match?.originName?.replace("_", " ")?.trim() ?: departure.stationName
            val dest = match?.destinationName?.replace("_", " ")?.trim() ?: departure.destinationName

            listOf(
                com.androidfung.departureboard.data.model.CallingPoint(
                    stationName = origin,
                    scheduledTime = departure.formattedActualClockTime,
                    estimatedTime = departure.formattedTimeToArrival,
                    isCurrentStation = true,
                    isDestination = false
                ),
                com.androidfung.departureboard.data.model.CallingPoint(
                    stationName = dest,
                    isCurrentStation = false,
                    isDestination = true
                )
            )
        } catch (_: Exception) {
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
                departureDao.replaceDeparturesForStation(
                    stationId,
                    departures.map { CachedDepartureEntity.fromDeparture(it) }
                )
            } catch (_: Exception) {}
        }
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
