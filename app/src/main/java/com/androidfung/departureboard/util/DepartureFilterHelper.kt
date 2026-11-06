package com.androidfung.departureboard.util

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.TransitDirection
import com.androidfung.departureboard.data.model.TransitLineMatcher

/**
 * Shared utility for extracting available transit directions and filtering
 * departures consistently across Dashboard, Station Details, and App Widgets.
 */
object DepartureFilterHelper {

    /**
     * Determines whether a given [Departure] matches the specified optional line and direction filters.
     */
    fun matches(
        departure: Departure,
        lineId: String? = null,
        direction: String? = null,
        stopId: String? = null
    ): Boolean {
        if (!stopId.isNullOrBlank() && !departure.platformName.equals(stopId, ignoreCase = true)) {
            return false
        }

        if (!lineId.isNullOrBlank()) {
            val matchesLine = TransitLineMatcher.matchesLine(
                departureLineId = departure.lineId,
                departureLineName = departure.lineName,
                departureBadgeLineId = departure.lineBadge.lineId,
                targetLineId = lineId
            )
            if (!matchesLine) return false
        }

        if (!direction.isNullOrBlank()) {
            val targetDir = TransitDirection.fromString(direction)
            val depDir = TransitDirection.fromString(departure.direction)
            val matchesDirection = (targetDir != null && (targetDir == depDir || targetDir == TransitDirection.fromString(departure.towards))) ||
                    departure.direction.equals(direction, ignoreCase = true) ||
                    departure.towards.equals(direction, ignoreCase = true) ||
                    departure.destinationName.contains(direction, ignoreCase = true) ||
                    departure.platformName.contains(direction, ignoreCase = true)
            if (!matchesDirection) return false
        }

        return true
    }

    /**
     * Filters a list of departures matching optional lineId, direction, and stopId.
     */
    fun filterDepartures(
        departures: List<Departure>,
        lineId: String? = null,
        direction: String? = null,
        stopId: String? = null
    ): List<Departure> {
        if (lineId == null && direction == null && stopId == null) {
            return departures
        }
        return departures.filter { matches(it, lineId, direction, stopId) }
    }

    /**
     * Extracts available directions for a specific transit line (or all lines if null)
     * serving a station from live departures.
     */
    fun getAvailableDirections(
        departures: List<Departure>,
        lineId: String? = null
    ): List<String> {
        val targetDepartures = if (lineId.isNullOrBlank()) {
            departures
        } else {
            departures.filter { matches(it, lineId = lineId) }
        }

        return targetDepartures
            .asSequence()
            .mapNotNull { dep ->
                val resolvedFromDirection = TransitDirection.fromString(dep.direction)
                val resolvedFromPlatform = TransitDirection.entries.firstOrNull { dir ->
                    dep.platformName.contains(dir.displayName, ignoreCase = true)
                }
                (resolvedFromDirection ?: resolvedFromPlatform)?.displayName ?: dep.direction ?: dep.towards
            }
            .distinct()
            .toList()
    }
}
