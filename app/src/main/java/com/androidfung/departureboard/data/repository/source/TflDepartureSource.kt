package com.androidfung.departureboard.data.repository.source

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.TflArrivalPrediction
import com.androidfung.departureboard.data.model.TflLineColors
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.network.TflApiService
import com.androidfung.departureboard.data.repository.TflDepartureMapper
import com.androidfung.departureboard.data.repository.TflStopPointUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Departure source for Transport for London (TfL) live arrival predictions.
 * Encapsulates TfL arrivals querying, hub child expansion, deduplication, and modal filtering.
 */
class TflDepartureSource(
    private val apiService: TflApiService
) : DepartureSource {

    override fun canHandle(stationId: String, stationName: String, isBusStop: Boolean): Boolean {
        return true
    }

    override suspend fun fetchDepartures(
        stationId: String,
        stationName: String,
        isBusStop: Boolean
    ): Result<List<Departure>> {
        return try {
            val rawArrivals = fetchRawArrivalsWithHubExpansion(stationId)

            val isStPancrasOrKingsCrossNR = TflStopPointUtils.isStPancrasOrKingsCrossNR(stationId, stationName)

            val filteredRawArrivals = if (isStPancrasOrKingsCrossNR) {
                rawArrivals.filter { arrival ->
                    val mode = arrival.modeName?.lowercase() ?: ""
                    val line = arrival.lineId?.lowercase() ?: ""
                    mode != "tube" && line !in TflLineColors.TUBE_LINE_IDS && line != "tube"
                }
            } else {
                rawArrivals
            }

            val tflDepartures = if (filteredRawArrivals.isNotEmpty()) {
                val distinct = TflDepartureMapper.deduplicate(filteredRawArrivals)
                distinct.map { TflDepartureMapper.mapToDeparture(it, stationId, stationName) }
            } else {
                emptyList()
            }

            // Modal filtering:
            // - Bus stop: strictly bus departures only
            // - Train/Tube station: strictly train, tube, overground, elizabeth line, dlr, tram only (excluding buses)
            val effectiveTflDepartures = when {
                isBusStop -> tflDepartures.filter { it.lineBadge.mode == TransitMode.BUS || it.modeName.equals("bus", ignoreCase = true) }
                else -> tflDepartures.filter { it.lineBadge.mode != TransitMode.BUS && !it.modeName.equals("bus", ignoreCase = true) }
            }

            Result.success(effectiveTflDepartures)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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

            val interchangeSiblings = TflStopPointUtils.getInterchangeSiblings(stationId)

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
}
