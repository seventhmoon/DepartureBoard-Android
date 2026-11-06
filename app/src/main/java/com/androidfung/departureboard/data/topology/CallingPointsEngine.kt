package com.androidfung.departureboard.data.topology

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
     * 3. For rail services where stopping patterns vary dynamically, falls back safely to origin and destination.
     */
    suspend fun resolveCallingPoints(departure: Departure): List<CallingPoint> {
        if (departure.callingPoints.isNotEmpty()) {
            return departure.callingPoints
        }

        val matchingResolver = resolvers.firstOrNull { it.canHandle(departure) }
        val resolved = matchingResolver?.resolveCallingPoints(departure) ?: emptyList()
        if (resolved.isNotEmpty()) {
            return resolved
        }

        // For Inter-city / Regional Rail where stopping patterns vary per train run:
        // Do not interpolate unverified intermediate stops. Present origin and destination.
        if (departure.lineBadge.mode.isRail) {
            return listOf(
                CallingPoint(
                    stationName = departure.stationName,
                    isCurrentStation = true,
                    stationId = departure.stationId,
                    modes = listOf(departure.modeName)
                ),
                CallingPoint(
                    stationName = departure.destinationName,
                    isDestination = true,
                    stationId = departure.destinationStationId,
                    modes = listOf(departure.modeName)
                )
            )
        }

        return emptyList()
    }
}
