package com.androidfung.departureboard.data.resolver

import com.androidfung.departureboard.data.model.CallingPoint
import com.androidfung.departureboard.data.model.Departure

/**
 * Orchestrator engine for resolving journey calling points using registered [CallingPointsResolver]s.
 * Parallels [com.androidfung.departureboard.data.repository.DepartureQueryEngine].
 */
class CallingPointsEngine(
    private val resolvers: List<CallingPointsResolver>
) {

    /**
     * Resolves calling points for the given [Departure].
     * 1. Returns existing calling points if already populated on the departure (e.g. National Rail Darwin feed).
     * 2. Finds the first registered resolver that can handle the departure and delegates resolution.
     */
    suspend fun resolveCallingPoints(departure: Departure): List<CallingPoint> {
        if (departure.callingPoints.isNotEmpty()) {
            return departure.callingPoints
        }

        val matchingResolver = resolvers.firstOrNull { it.canHandle(departure) }
        return matchingResolver?.resolveCallingPoints(departure) ?: emptyList()
    }
}
