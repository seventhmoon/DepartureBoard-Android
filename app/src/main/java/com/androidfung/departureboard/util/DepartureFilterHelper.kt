package com.androidfung.departureboard.util

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.TransitDirection
import com.androidfung.departureboard.ui.components.TransitIconHelper

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
            val matchesLine = TransitIconHelper.isSameLine(departure.lineId, lineId) ||
                    TransitIconHelper.isSameLine(departure.lineBadge.lineId, lineId) ||
                    departure.lineName.equals(lineId.removePrefix("bods-"), ignoreCase = true)
            if (!matchesLine) return false
        }

        if (!direction.isNullOrBlank()) {
            val matchesDirection = departure.direction.equals(direction, ignoreCase = true) ||
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

        val isNorthernLine = lineId?.contains("northern", ignoreCase = true) == true

        return targetDepartures
            .asSequence()
            .mapNotNull { dep ->
                val resolvedFromDirection = TransitDirection.fromString(dep.direction)
                val resolvedFromPlatform = TransitDirection.entries.firstOrNull { dir ->
                    dep.platformName.contains(dir.displayName, ignoreCase = true)
                }
                val resolvedDirection = resolvedFromDirection ?: resolvedFromPlatform

                val normalizedDirection = when {
                    isNorthernLine && resolvedDirection == TransitDirection.INBOUND -> TransitDirection.NORTHBOUND
                    isNorthernLine && resolvedDirection == TransitDirection.OUTBOUND -> TransitDirection.SOUTHBOUND
                    else -> resolvedDirection
                }

                normalizedDirection?.displayName ?: dep.direction ?: dep.towards
            }
            .distinct()
            .toList()
    }
}
