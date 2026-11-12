package com.androidfung.departureboard.data.repository.region.gb

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.TransitLineMatcher
import kotlin.math.abs

/**
 * GB/London specific collaborator responsible for merging and deduplicating departure feeds
 * from multiple sources (such as TfL predictions and National Rail services), reconciling
 * cross-feed overlaps (e.g., Overground named lines vs generic Overground, Elizabeth line, Euston).
 */
object GbFeedReconciliationEngine {


    /**
     * Merges TfL predictions and National Rail departures, avoiding duplicate entries for lines
     * reported by both sources (such as Elizabeth line and Overground/Lioness), using line-specific
     * minimum headway thresholds.
     */
    fun mergeAndDeduplicate(
        tflDepartures: List<Departure>,
        nrDepartures: List<Departure>
    ): List<Departure> {
        if (tflDepartures.isEmpty()) return nrDepartures
        if (nrDepartures.isEmpty()) return tflDepartures

        val result = tflDepartures.toMutableList()

        for (nr in nrDepartures) {
            val cleanNrDest = StationNameFormatter.clean(nr.destinationName)
            val gbLine = com.androidfung.departureboard.data.model.gb.GbTransitLine.fromCode(nr.lineId)
            val toleranceSeconds = gbLine?.minHeadwaySeconds ?: nr.transitMode.defaultMinHeadwaySeconds

            // Check if TfL already has an equivalent prediction for the same line and destination within the line headway window
            val isDuplicate = result.any { tfl ->
                val linesMatch = TransitLineMatcher.isSameLine(tfl.lineId, nr.lineId) ||
                        tfl.lineName.equals(nr.lineName, ignoreCase = true)

                val cleanTflDest = StationNameFormatter.clean(tfl.destinationName)
                val destsMatch = cleanTflDest.equals(cleanNrDest, ignoreCase = true) ||
                        cleanTflDest.contains(cleanNrDest, ignoreCase = true) ||
                        cleanNrDest.contains(cleanTflDest, ignoreCase = true)

                linesMatch && destsMatch && abs(tfl.timeToStationSeconds - nr.timeToStationSeconds) < toleranceSeconds
            }

            if (!isDuplicate) {
                result.add(nr)
            }
        }

        return result
    }
}
