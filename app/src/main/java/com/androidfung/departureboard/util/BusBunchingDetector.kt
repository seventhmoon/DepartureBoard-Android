package com.androidfung.departureboard.util

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus

/**
 * Severity level of bus headway spacing.
 */
enum class BusSpacingSeverity {
    NORMAL,
    BUNCHED,   // Buses running bumper-to-bumper (<= 90 seconds apart on the same route)
    LONG_GAP   // Abnormal gap between buses (>= 15 minutes or significant headway hole)
}

/**
 * Spacing telemetry for an individual bus departure.
 */
data class BusSpacingInfo(
    val departureId: String,
    val routeName: String,
    val isBunchedWithAhead: Boolean = false,
    val isBunchedWithBehind: Boolean = false,
    val gapAheadSeconds: Int? = null,
    val gapBehindSeconds: Int? = null,
    val severity: BusSpacingSeverity = BusSpacingSeverity.NORMAL
) {
    val isBunched: Boolean get() = isBunchedWithAhead || isBunchedWithBehind

    val displaySummary: String?
        get() = when {
            isBunchedWithAhead -> "Bumper-to-bumper with bus ahead"
            isBunchedWithBehind -> "Followed closely by 2nd bus"
            severity == BusSpacingSeverity.LONG_GAP && gapAheadSeconds != null ->
                "${gapAheadSeconds / 60}m gap since previous bus"
            else -> null
        }
}

/**
 * Utility to analyze real-time bus arrivals and detect bunching (convoys) and service gaps.
 */
object BusBunchingDetector {

    /** Maximum seconds between two buses on the same route to be considered "bunched" (90s) */
    const val BUNCHING_THRESHOLD_SECONDS = 90

    /** Gap in seconds to qualify as an abnormal service hole (15 minutes) */
    const val LONG_GAP_THRESHOLD_SECONDS = 15 * 60

    /**
     * Analyzes a list of departures for a bus stop or line and returns a map of [departureId] -> [BusSpacingInfo].
     */
    fun analyzeSpacing(departures: List<Departure>): Map<String, BusSpacingInfo> {
        val busDepartures = departures.filter { it.isBus && it.status != DepartureStatus.CANCELLED }
        if (busDepartures.isEmpty()) return emptyMap()

        val results = mutableMapOf<String, BusSpacingInfo>()

        // Group by line / route and towards destination
        val groupedByRoute = busDepartures.groupBy { dep ->
            "${dep.lineId.trim().lowercase()}_${dep.towards?.trim()?.lowercase() ?: dep.destinationName.trim().lowercase()}"
        }

        for ((_, routeBuses) in groupedByRoute) {
            val sortedBuses = routeBuses.sortedBy { it.timeToStationSeconds }

            for (i in sortedBuses.indices) {
                val current = sortedBuses[i]
                val prev = sortedBuses.getOrNull(i - 1)
                val next = sortedBuses.getOrNull(i + 1)

                val gapAheadSec = prev?.let { current.timeToStationSeconds - it.timeToStationSeconds }
                val gapBehindSec = next?.let { next.timeToStationSeconds - current.timeToStationSeconds }

                val isBunchedAhead = gapAheadSec != null && gapAheadSec in 0..BUNCHING_THRESHOLD_SECONDS
                val isBunchedBehind = gapBehindSec != null && gapBehindSec in 0..BUNCHING_THRESHOLD_SECONDS

                val isLongGap = (gapAheadSec != null && gapAheadSec >= LONG_GAP_THRESHOLD_SECONDS) ||
                    (gapBehindSec != null && gapBehindSec >= LONG_GAP_THRESHOLD_SECONDS)

                val severity = when {
                    isBunchedAhead || isBunchedBehind -> BusSpacingSeverity.BUNCHED
                    isLongGap -> BusSpacingSeverity.LONG_GAP
                    else -> BusSpacingSeverity.NORMAL
                }

                results[current.id] = BusSpacingInfo(
                    departureId = current.id,
                    routeName = current.lineName.ifBlank { current.lineId },
                    isBunchedWithAhead = isBunchedAhead,
                    isBunchedWithBehind = isBunchedBehind,
                    gapAheadSeconds = gapAheadSec,
                    gapBehindSeconds = gapBehindSec,
                    severity = severity
                )
            }
        }

        return results
    }
}
