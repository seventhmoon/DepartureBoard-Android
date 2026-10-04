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
import com.androidfung.departureboard.data.model.TflLineColors
import com.androidfung.departureboard.data.model.TflLineStatusItem
import com.androidfung.departureboard.data.model.TflStopPointChild
import com.androidfung.departureboard.data.model.TflStopPointMatch
import com.androidfung.departureboard.data.network.TflApiService
import com.androidfung.departureboard.data.network.TflNetworkClient
import kotlinx.coroutines.Dispatchers
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
    fun getDeparturesFlow(stationId: String, stationName: String): Flow<Result<List<Departure>>>
    suspend fun getLineStatuses(): Map<String, TflLineStatusItem>
    suspend fun saveStation(station: Station)
    suspend fun reorderStations(stations: List<Station>)
    suspend fun removeStation(stationId: String)
    suspend fun setRecentStationId(stationId: String)
}

/**
 * Implementation of TransitRepository using TfL API, DataStore, and Room offline cache.
 */
class TransitRepositoryImpl(
    private val apiService: TflApiService = TflNetworkClient.apiService,
    private val dataStore: StationPreferencesDataSource,
    private val departureDao: DepartureDao? = null
) : TransitRepository {

    constructor(context: Context) : this(
        apiService = TflNetworkClient.apiService,
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
            val matchedStations = response.matches.map { match ->
                createStationFromMatch(match)
            }

            val routeStopStations = findRouteStopsIfApplicable(trimmed)

            // Combine results, prioritizing exact bus route stops if route matched
            val combinedResults = if (routeStopStations.isNotEmpty()) {
                (routeStopStations + matchedStations).distinctBy { it.id }
            } else {
                matchedStations
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
                val rawArrivals = fetchRawArrivalsWithHubExpansion(stationId)

                if (rawArrivals.isEmpty()) {
                    Result.success(emptyList())
                } else {
                    val distinctArrivals = deduplicateArrivals(rawArrivals)
                    val departures = distinctArrivals
                        .sortedBy { it.timeToStation }
                        .map { mapPredictionToDeparture(it, stationId, stationName) }

                    cacheDeparturesSafely(stationId, departures)
                    Result.success(departures)
                }
            } catch (e: Exception) {
                fallbackDepartures(stationId, stationName, e)
            }
        }

    override fun getDeparturesFlow(stationId: String, stationName: String): Flow<Result<List<Departure>>> = flow {
        val cached = departureDao?.getDeparturesForStation(stationId)?.map { it.toDeparture() }
        if (!cached.isNullOrEmpty()) {
            emit(Result.success(cached))
        }
        emit(getDepartures(stationId, stationName))
    }.flowOn(Dispatchers.IO)

    override suspend fun getLineStatuses(): Map<String, TflLineStatusItem> = withContext(Dispatchers.IO) {
        try {
            apiService.getLineStatuses().associateBy { it.id.lowercase() }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    override suspend fun saveStation(station: Station) = dataStore.saveStation(station)
    override suspend fun reorderStations(stations: List<Station>) = dataStore.saveStations(stations)
    override suspend fun removeStation(stationId: String) = dataStore.removeStation(stationId)
    override suspend fun setRecentStationId(stationId: String) = dataStore.setRecentStationId(stationId)

    // =========================================================================
    // Private Helpers: Search & Station Building
    // =========================================================================

    private fun createStationFromMatch(match: TflStopPointMatch): Station {
        val cleaned = StationNameFormatter.clean(match.name)
        val isBus = match.modes.any { it.equals("bus", ignoreCase = true) }
        val isBusOnly = isBus && match.modes.none { it in listOf("tube", "overground", "elizabeth-line", "national-rail", "dlr") }

        val stopLetter = if (isBusOnly && !cleaned.contains("Stop ", ignoreCase = true)) {
            Regex("^490\\d+([A-Za-z0-9]+)$").find(match.id)?.groupValues?.getOrNull(1)?.uppercase()
        } else null

        val towards = match.towards?.takeIf { it.isNotBlank() && it.trim().lowercase() != "null" }
        val displayName = buildDisambiguatedStopName(cleaned, stopLetter, towards)

        return Station(
            id = match.id,
            name = displayName,
            modes = match.modes,
            zone = match.zone,
            lat = match.lat,
            lon = match.lon,
            isFavorite = false
        )
    }

    private suspend fun findRouteStopsIfApplicable(query: String): List<Station> {
        val busRouteRegex = Regex("""^(?:[0-9]{1,3}|[A-Za-z]{1,2}[0-9]{1,3})$""", RegexOption.IGNORE_CASE)
        if (!busRouteRegex.matches(query)) return emptyList()

        return try {
            val stopPoints = apiService.getLineStopPoints(query.lowercase())
            stopPoints
                .filter { it.modes.contains("bus") || it.id.startsWith("490") }
                .map { sp -> createStationFromStopPoint(sp) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun createStationFromStopPoint(sp: TflStopPointChild): Station {
        val common = sp.commonName ?: "Bus Stop"
        val cleaned = StationNameFormatter.clean(common)
        val letter = sp.stopLetter?.takeIf { it.isNotBlank() }
            ?: sp.indicator?.takeIf { it.startsWith("Stop ", ignoreCase = true) }?.removePrefix("Stop ")?.trim()
            ?: Regex("^490\\d+([A-Za-z0-9]+)$").find(sp.id)?.groupValues?.getOrNull(1)?.uppercase()

        val towards = sp.towards?.takeIf { it.isNotBlank() && it.trim().lowercase() != "null" }
            ?: sp.additionalProperties.firstOrNull { it.key.equals("Towards", ignoreCase = true) }?.value?.takeIf { it.isNotBlank() }

        val displayName = buildDisambiguatedStopName(cleaned, letter, towards)

        return Station(
            id = sp.id,
            name = displayName,
            modes = listOf("bus"),
            zone = sp.additionalProperties.firstOrNull { it.key.equals("Zone", ignoreCase = true) }?.value,
            lat = sp.lat,
            lon = sp.lon,
            isFavorite = false
        )
    }

    private fun buildDisambiguatedStopName(cleaned: String, stopLetter: String?, towards: String?): String {
        val details = mutableListOf<String>()
        if (!stopLetter.isNullOrBlank()) {
            details.add("Stop $stopLetter")
        }
        if (towards != null && !cleaned.contains("towards", ignoreCase = true)) {
            details.add("towards $towards")
        }
        return if (details.isNotEmpty()) "$cleaned (${details.joinToString(", ")})" else cleaned
    }

    // =========================================================================
    // Private Helpers: Departure Fetching, Deduplication & Mapping
    // =========================================================================

    private suspend fun fetchRawArrivalsWithHubExpansion(stationId: String): List<TflArrivalPrediction> {
        var arrivals = apiService.getArrivals(stationId)
        if (arrivals.isNotEmpty()) return arrivals

        try {
            val detail = apiService.getStopPointDetail(stationId)
            val sortedChildren = detail.children.sortedByDescending { child ->
                when {
                    child.id.startsWith("940G") -> 3 // Tube / Tram / Rail
                    child.id.startsWith("910G") -> 2 // National Rail
                    else -> 1 // Bus stops
                }
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

    private fun deduplicateArrivals(rawArrivals: List<TflArrivalPrediction>): List<TflArrivalPrediction> {
        return rawArrivals.distinctBy { item ->
            if (!item.vehicleId.isNullOrBlank()) {
                item.vehicleId
            } else {
                "${item.lineId}_${item.destinationName}_${item.timeToStation / 30}"
            }
        }
    }

    private fun mapPredictionToDeparture(
        item: TflArrivalPrediction,
        stationId: String,
        stationName: String
    ): Departure {
        val lineName = item.lineName ?: item.lineId ?: "Transit"
        val badge = TflLineColors.getLineBadge(
            lineId = item.lineId,
            lineName = item.lineName,
            modeName = item.modeName
        )
        val resolvedDest = DestinationResolver.resolve(
            stationName = stationName,
            itemDestination = item.destinationName,
            itemTowards = item.towards,
            lineId = item.lineId,
            platformName = item.platformName
        )
        val isBus = item.modeName.equals("bus", ignoreCase = true) || item.lineId?.toIntOrNull() != null
        val platformDisplay = formatPlatformDisplay(item.platformName, item.towards, isBus)
        val cleanTowards = item.towards?.takeIf { it.trim().lowercase() != "null" }?.let { StationNameFormatter.clean(it) }
        val resolvedDirection = resolveCardinalDirection(item, resolvedDest)

        return Departure(
            id = item.id,
            stationId = item.naptanId ?: stationId,
            stationName = StationNameFormatter.clean(item.stationName ?: stationName),
            lineId = item.lineId ?: "transit",
            lineName = lineName,
            platformName = platformDisplay,
            destinationName = resolvedDest.ifBlank { "Destination" },
            towards = cleanTowards,
            direction = resolvedDirection,
            timeToStationSeconds = item.timeToStation,
            expectedArrivalIso = item.expectedArrival,
            currentLocation = item.currentLocation?.takeIf { it.trim().lowercase() != "null" },
            modeName = item.modeName ?: if (isBus) "bus" else "tube",
            lineBadge = badge
        )
    }

    private fun formatPlatformDisplay(rawPlatform: String?, towards: String?, isBus: Boolean): String {
        val trimmed = rawPlatform?.trim() ?: ""
        return when {
            trimmed.isNotBlank() && trimmed.lowercase() != "null" -> when {
                isBus && !trimmed.startsWith("Stop ", ignoreCase = true) -> "Stop $trimmed"
                trimmed.matches(Regex("^[A-Z0-9]$")) -> "Platform $trimmed"
                !trimmed.startsWith("Platform", ignoreCase = true) &&
                !trimmed.contains("bound", ignoreCase = true) &&
                !trimmed.startsWith("Stop", ignoreCase = true) -> "Platform $trimmed"
                else -> trimmed
            }
            isBus && !towards.isNullOrBlank() && towards.trim().lowercase() != "null" ->
                "towards ${StationNameFormatter.clean(towards)}"
            else -> if (isBus) "Bus Stand" else "Platform"
        }
    }

    private fun resolveCardinalDirection(item: TflArrivalPrediction, resolvedDest: String): String? {
        val rawPlatform = item.platformName?.trim() ?: ""
        val isElizabeth = item.lineId?.contains("elizabeth", ignoreCase = true) == true ||
                item.lineName?.contains("elizabeth", ignoreCase = true) == true

        return when {
            rawPlatform.contains("Eastbound", ignoreCase = true) -> "Eastbound"
            rawPlatform.contains("Westbound", ignoreCase = true) -> "Westbound"
            rawPlatform.contains("Northbound", ignoreCase = true) -> "Northbound"
            rawPlatform.contains("Southbound", ignoreCase = true) -> "Southbound"
            isElizabeth -> {
                val destLower = resolvedDest.lowercase()
                when {
                    destLower.contains("abbey wood") || destLower.contains("shenfield") ||
                    destLower.contains("liverpool street") || destLower.contains("paddington") ||
                    destLower.contains("stratford") || item.direction.equals("inbound", ignoreCase = true) -> "Eastbound"

                    destLower.contains("reading") || destLower.contains("heathrow") ||
                    destLower.contains("maidenhead") || item.direction.equals("outbound", ignoreCase = true) -> "Westbound"

                    else -> null
                }
            }
            item.direction?.equals("inbound", ignoreCase = true) == true -> "Inbound"
            item.direction?.equals("outbound", ignoreCase = true) == true -> "Outbound"
            else -> null
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
        stationName: String,
        originalException: Exception
    ): Result<List<Departure>> {
        val cached = departureDao?.getDeparturesForStation(stationId)?.map { it.toDeparture() }
        if (!cached.isNullOrEmpty()) {
            return Result.success(cached)
        }
        val fallbacks = DefaultStations.getFallbackDepartures(stationId, stationName)
        return if (fallbacks.isNotEmpty()) {
            Result.success(fallbacks)
        } else {
            Result.failure(originalException)
        }
    }
}
