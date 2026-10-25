package com.androidfung.departureboard.data.repository.engine

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.TflLineColors
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.repository.TflStopPointUtils
import com.androidfung.departureboard.data.repository.source.DepartureSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Orchestrator engine for querying matching [DepartureSource] instances in parallel,
 * merging results with [FeedMergeEngine], applying station-specific filters, and sorting.
 */
class DepartureQueryEngine(
    private val sources: List<DepartureSource>
) {

    /**
     * Queries all matching departure sources in parallel for the given station,
     * reconciles and merges feeds, filters St Pancras/Kings Cross Underground lines if applicable,
     * and returns sorted departures.
     */
    suspend fun queryDepartures(
        stationId: String,
        stationName: String
    ): Result<List<Departure>> = coroutineScope {
        val isBusStop = TflStopPointUtils.isBusStop(stationId, stationName)

        val matchingSources = sources.filter { it.canHandle(stationId, stationName, isBusStop) }
        if (matchingSources.isEmpty()) {
            return@coroutineScope Result.success(emptyList())
        }

        // Query all matching sources concurrently
        val deferredList = matchingSources.map { source ->
            async {
                source.fetchDepartures(stationId, stationName, isBusStop)
            }
        }

        val results = deferredList.awaitAll()

        var lastError: Throwable? = null
        val successfulFeeds = mutableListOf<List<Departure>>()

        for (res in results) {
            res.fold(
                onSuccess = { departures ->
                    successfulFeeds.add(departures)
                },
                onFailure = { error ->
                    lastError = error
                }
            )
        }

        // Deduplicate and merge all feeds
        var merged = emptyList<Departure>()
        for (feed in successfulFeeds) {
            merged = FeedMergeEngine.mergeAndDeduplicate(merged, feed)
        }

        val isStPancrasOrKingsCrossNR = TflStopPointUtils.isStPancrasOrKingsCrossNR(stationId, stationName)
        val filteredDepartures = if (isStPancrasOrKingsCrossNR) {
            merged.filter { dep ->
                val badge = dep.lineBadge
                badge.mode != TransitMode.TUBE &&
                        badge.lineId.lowercase() !in TflLineColors.TUBE_LINE_IDS
            }
        } else {
            merged
        }

        val sortedDepartures = filteredDepartures.sortedBy { it.timeToStationSeconds }

        if (sortedDepartures.isEmpty() && lastError != null) {
            Result.failure(lastError)
        } else {
            Result.success(sortedDepartures)
        }
    }
}
