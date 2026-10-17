package com.androidfung.departureboard.ui.dashboard

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.StationLineInferrer
import com.androidfung.departureboard.data.model.TflLineColors
import com.androidfung.departureboard.data.model.TflLineStatusItem
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.repository.TflStopPointUtils

/**
 * Pure helper object responsible for resolving, filtering, sorting, and hydrating line badges
 * for transit stations, integrating live departure badges with TfL line statuses.
 */
object StationBadgeSynthesizer {

    /**
     * Builds and enriches line badges for a station based on active departures,
     * station metadata fallback, and real-time line disruptions/statuses.
     */
    fun buildStationLineBadges(
        station: Station,
        departures: List<Departure>,
        lineStatuses: Map<String, TflLineStatusItem>
    ): List<LineBadgeInfo> {
        // Extract line badges strictly from active departures, falling back to verified station lines only if departures are empty
        val departureBadges = departures.map { it.lineBadge }.distinctBy { it.displayName }
        val rawBadges = if (departureBadges.isNotEmpty()) {
            val isWembleyPark = TflStopPointUtils.isWembleyPark(station.id, station.name)
            val isStPancrasOrKingsCrossNR = TflStopPointUtils.isStPancrasOrKingsCrossNR(station.id, station.name)

            departureBadges.filter { badge ->
                // Guard: Filter out spurious depot movements (e.g. H&C depot run at Wembley Park)
                if (isWembleyPark && badge.lineId.equals("hammersmith-city", ignoreCase = true)) return@filter false

                // Guard: Filter out Tube badges for St Pancras or King's Cross National Rail
                if (isStPancrasOrKingsCrossNR) {
                    if (badge.mode == TransitMode.TUBE ||
                        badge.lineId.lowercase() in TflLineColors.TUBE_LINE_IDS
                    ) {
                        return@filter false
                    }
                }

                true
            }
        } else {
            StationLineInferrer.infer(station)
        }

        // Sort line badges logically: Tube/Rail lines alphabetically, then numeric Bus routes in ascending order
        val sortedBadges = rawBadges.sortedWith(
            compareBy<LineBadgeInfo> { badge ->
                if (badge.displayName.all { it.isDigit() }) 1 else 0
            }.thenBy { badge ->
                val num = badge.displayName.filter { it.isDigit() }.toIntOrNull()
                num ?: Int.MAX_VALUE
            }.thenBy { it.displayName }
        )

        return sortedBadges.map { badge ->
            val statusItem = lineStatuses[badge.lineId.lowercase()]
            val firstDetail = statusItem?.lineStatuses?.firstOrNull()
            if (firstDetail != null) {
                badge.copy(
                    statusSeverity = firstDetail.statusSeverity,
                    statusDescription = firstDetail.statusSeverityDescription,
                    disruptionReason = firstDetail.reason
                )
            } else {
                badge
            }
        }
    }
}
