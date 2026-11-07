package com.androidfung.departureboard.util

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus

/**
 * An alert raised when an expected high-frequency line experiences an abnormal gap,
 * leading to platform crowding and backlog.
 */
data class PlatformBacklogAlert(
    val lineId: String,
    val lineName: String,
    val direction: String?,
    val minutesUntilNext: Int,
    val typicalHeadwayMinutes: Int,
    val message: String
)

/**
 * Evaluates whether rapid transit platforms have an abnormal backlog / delay gap.
 */
object PlatformBacklogDetector {

    /**
     * Known typical peak/day headways in minutes for common metro lines when frequencies.txt is missing.
     */
    private val DEFAULT_LINE_HEADWAYS = mapOf(
        "victoria" to 2,
        "northern" to 3,
        "central" to 3,
        "jubilee" to 2,
        "piccadilly" to 3,
        "bakerloo" to 3,
        "district" to 4,
        "circle" to 5,
        "metropolitan" to 5,
        "hammersmith-city" to 5,
        "elizabeth" to 4,
        "dlr" to 3,
        "island" to 2,
        "twen-wan" to 2,
        "kwun-tong" to 2,
        "east-rail" to 3
    )

    /**
     * Checks a station's departures for platform backlog conditions.
     * Returns a list of active [PlatformBacklogAlert]s.
     */
    fun detectBacklogs(departures: List<Departure>): List<PlatformBacklogAlert> {
        val nonBusDepartures = departures.filter {
            !it.isBus && it.status != DepartureStatus.CANCELLED
        }
        if (nonBusDepartures.isEmpty()) return emptyMap<String, Any>().let { emptyList() }

        val alerts = mutableListOf<PlatformBacklogAlert>()

        // Group by line and direction / platform
        val grouped = nonBusDepartures.groupBy { dep ->
            val dir = dep.direction ?: dep.towards ?: dep.platformName
            "${dep.lineId.lowercase()}_$dir"
        }

        for ((_, group) in grouped) {
            val firstDep = group.minByOrNull { it.timeToStationSeconds } ?: continue
            val minutesUntilNext = firstDep.timeToStationSeconds / 60

            val lineKey = firstDep.lineId.lowercase()
            val typicalHeadway = firstDep.frequency?.headwaySeconds?.let { it / 60 }
                ?: DEFAULT_LINE_HEADWAYS[lineKey]
                ?: 4 // Default fallback for metro/rail lines

            // If the next departure is more than 3x the normal headway and at least 9 minutes away
            if (minutesUntilNext >= 9 && minutesUntilNext >= (typicalHeadway * 2.8)) {
                val dirLabel = firstDep.direction ?: firstDep.platformName
                alerts.add(
                    PlatformBacklogAlert(
                        lineId = firstDep.lineId,
                        lineName = firstDep.lineName,
                        direction = dirLabel,
                        minutesUntilNext = minutesUntilNext,
                        typicalHeadwayMinutes = typicalHeadway,
                        message = "Platform backlog alert: Next ${firstDep.lineName} in ${minutesUntilNext}m (Normal headway: ${typicalHeadway}m)"
                    )
                )
            }
        }

        return alerts
    }
}
