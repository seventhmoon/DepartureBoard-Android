package com.androidfung.departureboard.data.repository.search

import android.content.Context
import com.androidfung.departureboard.data.db.AppDatabase
import com.androidfung.departureboard.data.model.DefaultStations
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.network.BodsApiService
import com.androidfung.departureboard.data.network.TflApiService
import com.androidfung.departureboard.data.repository.StationNameFormatter
import com.androidfung.departureboard.data.repository.TflStationMapper
import com.androidfung.departureboard.data.repository.TflStopPointUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Pluggable search coordinator that searches across transit networks concurrently:
 * - 3-letter National Rail CRS codes via Room StationCrsDao.
 * - TfL StopPoint search via TflApiService.searchStations with async line hydration.
 * - Bus route stops (TfL bus lineStopPoints + DfT BODS line search).
 * - Popular stations local fallback when query is blank or on error.
 */
class StationSearchEngine(
    private val apiService: TflApiService,
    private val bodsApiService: BodsApiService,
    private val context: Context? = null
) {

    /**
     * Executes parallel search across CRS, TfL StopPoints, and bus routes.
     */
    suspend fun searchStations(query: String): Result<List<Station>> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.success(DefaultStations.POPULAR_STATIONS)
        }

        try {
            coroutineScope {
                // 1. Search 3-letter National Rail CRS codes concurrently if applicable
                val crsDeferred = async { searchCrsCode(trimmed) }

                // 2. Search local National Rail station database by name keyword (e.g. Cambridge South, Birmingham, Edinburgh)
                val localRailDeferred = async { searchLocalRailStations(trimmed) }

                // 3. Search TfL StopPoints and hydrate lines concurrently
                val tflDeferred = async { searchTflStations(trimmed) }

                // 4. Search Bus route stops concurrently if applicable
                val routeStopsDeferred = async { findRouteStopsIfApplicable(trimmed) }

                // 5. Search regional UK bus stops / NaPTAN ATCO codes or stop names via BODS
                val naptanDeferred = async { searchNaptanStopsIfApplicable(trimmed) }

                val crsMatchStation = crsDeferred.await()
                val localRailStations = localRailDeferred.await()
                val hydratedStations = tflDeferred.await()
                val routeStopStations = routeStopsDeferred.await()
                val naptanStations = naptanDeferred.await()

                // Combine results, prioritizing exact bus route stops or CRS matches
                val baseCombined = if (crsMatchStation != null) {
                    listOf(crsMatchStation) + (routeStopStations + localRailStations + naptanStations + hydratedStations)
                } else if (routeStopStations.isNotEmpty()) {
                    (routeStopStations + localRailStations + naptanStations + hydratedStations)
                } else {
                    (localRailStations + naptanStations + hydratedStations)
                }
                val combinedResults = baseCombined.distinctBy { it.id }

                if (combinedResults.isNotEmpty()) {
                    Result.success(combinedResults)
                } else {
                    val localMatches = DefaultStations.POPULAR_STATIONS.filter {
                        it.name.contains(trimmed, ignoreCase = true)
                    }
                    Result.success(localMatches)
                }
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

    private suspend fun searchCrsCode(query: String): Station? {
        if (query.length != 3 || !query.all { it.isLetter() }) return null
        return try {
            val crsEntity = context?.let { ctx ->
                AppDatabase.getInstance(ctx).stationCrsDao().getStationByCrsCode(query.uppercase())
            } ?: return null

            // Query TfL for this station name so we get proper coordinates/Naptan if available
            val tflSearch = try {
                apiService.searchStations(query = crsEntity.stationName)
            } catch (_: Exception) {
                null
            }
            val matched = tflSearch?.matches?.firstOrNull()
            if (matched != null) {
                Station(
                    id = matched.id,
                    name = StationNameFormatter.clean(matched.name),
                    modes = matched.modes.ifEmpty { listOf("national-rail") },
                    zone = matched.zone,
                    lat = matched.lat,
                    lon = matched.lon,
                    isFavorite = false
                )
            } else {
                Station(
                    id = crsEntity.crsCode,
                    name = crsEntity.stationName,
                    modes = listOf("national-rail"),
                    isFavorite = false
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun searchLocalRailStations(query: String): List<Station> {
        if (query.length < 3) return emptyList()
        return try {
            val entities = context?.let { ctx ->
                val normalized = query.trim().lowercase()
                AppDatabase.getInstance(ctx).stationCrsDao().getStationsByNameKeyword(normalized)
            } ?: emptyList()

            entities.map { entity ->
                val modes = entity.modes.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
                Station(
                    id = entity.crsCode,
                    name = entity.stationName,
                    modes = if (modes.isNotEmpty()) modes else listOf("national-rail"),
                    isFavorite = false
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun searchTflStations(query: String): List<Station> {
        val response = try {
            apiService.searchStations(query = query)
        } catch (_: Exception) {
            return emptyList()
        }

        val matchedStations = response.matches.flatMap { match ->
            TflStationMapper.fromStopPointMatch(match)
        }

        // Hydrate station lines from StopPoint API for non-bus stations that don't have lines yet
        return coroutineScope {
            matchedStations.map { station ->
                async {
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
            }.awaitAll()
        }
    }

    private suspend fun findRouteStopsIfApplicable(query: String): List<Station> {
        val busRouteRegex = Regex("""^(?:[0-9]{1,3}|[A-Za-z]{1,3}[0-9]{1,3}|[A-Za-z]{1,2})$""", RegexOption.IGNORE_CASE)
        if (!busRouteRegex.matches(query)) return emptyList()

        // 1. Try TfL bus route stops first (for London buses)
        val tflStops = try {
            val stopPoints = apiService.getLineStopPoints(query.lowercase())
            stopPoints
                .filter { it.modes.contains(TransitMode.BUS.id) || it.id.startsWith(TflStopPointUtils.PREFIX_BUS_STOP) }
                .map { sp -> TflStationMapper.fromStopPointChild(sp) }
        } catch (_: Exception) {
            emptyList()
        }

        if (tflStops.isNotEmpty()) return tflStops

        // 2. If no TfL stops found, check BODS for regional / national bus routes (e.g. HL1, 310, 641)
        return try {
            val responseBody = bodsApiService.getVehicleDatafeedByLine(lineRef = query.uppercase())
            val xmlString = responseBody.string()
            val activities = com.androidfung.departureboard.data.model.BodsXmlParser.parse(xmlString)

            activities.flatMap { act ->
                listOfNotNull(
                    act.originRef?.let { ref ->
                        val name = act.originName?.replace("_", " ")?.trim() ?: "Stop ($ref)"
                        Station(
                            id = ref,
                            name = "$name (Route ${act.publishedLineName.ifBlank { act.lineRef }})",
                            modes = listOf("bus"),
                            lat = act.latitude,
                            lon = act.longitude,
                            isFavorite = false
                        )
                    },
                    act.destinationRef?.let { ref ->
                        val name = act.destinationName?.replace("_", " ")?.trim() ?: "Stop ($ref)"
                        Station(
                            id = ref,
                            name = "$name (Route ${act.publishedLineName.ifBlank { act.lineRef }})",
                            modes = listOf("bus"),
                            lat = act.latitude,
                            lon = act.longitude,
                            isFavorite = false
                        )
                    }
                )
            }.distinctBy { it.id }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Resolves NaPTAN ATCO bus stop codes (e.g. 0100BRP90312, 1800SB04611, 2100...) or stops from regional feeds.
     */
    private fun searchNaptanStopsIfApplicable(query: String): List<Station> {
        val naptanAtcoRegex = Regex("""^(?:[0-9]{3,4}[A-Za-z0-9]{5,12}|210[0-9A-Za-z]{4,8})$""")
        if (!naptanAtcoRegex.matches(query)) return emptyList()

        return listOf(
            Station(
                id = query.uppercase(),
                name = "Bus Stop ($query)",
                modes = listOf("bus"),
                lat = null,
                lon = null,
                isFavorite = false
            )
        )
    }
}
