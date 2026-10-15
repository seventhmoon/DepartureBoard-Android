package com.androidfung.departureboard.util

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.ui.components.TransitIconHelper

/**
 * Shared utility for extracting available transit directions and filtering
 * departures consistently across Dashboard, Station Details, and App Widgets.
 */
object DepartureFilterHelper {

    private val CARDINAL_DIRECTIONS = listOf(
        "Eastbound", "Westbound", "Northbound", "Southbound", "Inbound", "Outbound"
    )

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

        if (!lineId.isNullOrBlank() && !TransitIconHelper.isSameLine(departure.lineId, lineId)) {
            return false
        }

        if (!direction.isNullOrBlank()) {
            val matchesDirection = departure.direction.equals(direction, ignoreCase = true) ||
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
            departures.filter { TransitIconHelper.isSameLine(it.lineId, lineId) }
        }

        return targetDepartures
            .asSequence()
            .mapNotNull { dep ->
                dep.direction ?: CARDINAL_DIRECTIONS.firstOrNull { d ->
                    dep.platformName.contains(d, ignoreCase = true)
                }
            }
            .distinct()
            .map { dir ->
                if (dir.equals("Inbound", ignoreCase = true) && lineId?.contains("northern", ignoreCase = true) == true) {
                    "Northbound"
                } else if (dir.equals("Outbound", ignoreCase = true) && lineId?.contains("northern", ignoreCase = true) == true) {
                    "Southbound"
                } else {
                    dir
                }
            }
            .distinct()
            .toList()
    }
}
