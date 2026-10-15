package com.androidfung.departureboard.data.repository

import com.androidfung.departureboard.data.model.Departure
import kotlin.math.abs

/**
 * Collaborator responsible for merging and deduplicating departure feeds from multiple sources
 * (such as TfL predictions and National Rail services), reconciling cross-feed overlaps.
 */
object FeedMergeEngine {

    private const val TIMESTAMP_TOLERANCE_SECONDS = 180
    private val OVERGROUND_ALIASES = setOf(
        "lioness", "mildmay", "weaver", "suffragette", "liberty", "windrush"
    )

    /**
     * Merges TfL predictions and National Rail departures, avoiding duplicate entries for lines
     * reported by both sources (such as Elizabeth line and Overground/Lioness).
     */
    fun mergeAndDeduplicate(
        tflDepartures: List<Departure>,
        nrDepartures: List<Departure>
    ): List<Departure> {
        if (tflDepartures.isEmpty()) return nrDepartures
        if (nrDepartures.isEmpty()) return tflDepartures

        val result = tflDepartures.toMutableList()

        for (nr in nrDepartures) {
            // Check if TfL already has an equivalent prediction for the same line and destination within a 180s window
            val isDuplicate = result.any { tfl ->
                val linesMatch = tfl.lineId == nr.lineId ||
                        tfl.lineName.equals(nr.lineName, ignoreCase = true) ||
                        // Check Overground alias match (Lioness vs London Overground)
                        (tfl.lineId in OVERGROUND_ALIASES && nr.lineId == "overground") ||
                        (nr.lineId in OVERGROUND_ALIASES && tfl.lineId == "overground")

                val destsMatch = tfl.destinationName.contains(nr.destinationName, ignoreCase = true) ||
                        nr.destinationName.contains(tfl.destinationName, ignoreCase = true) ||
                        // Matches Euston / London Euston
                        (tfl.destinationName.contains("Euston", ignoreCase = true) && nr.destinationName.contains("Euston", ignoreCase = true))

                linesMatch && destsMatch && abs(tfl.timeToStationSeconds - nr.timeToStationSeconds) < TIMESTAMP_TOLERANCE_SECONDS
            }
            if (!isDuplicate) {
                result.add(nr)
            }
        }
        return result
    }
}
