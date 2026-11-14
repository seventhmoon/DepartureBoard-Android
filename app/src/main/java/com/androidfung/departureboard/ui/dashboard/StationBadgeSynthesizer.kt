package com.androidfung.departureboard.ui.dashboard

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.StationLineInferrer
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.model.gb.TflLineColors
import com.androidfung.departureboard.data.model.gb.TflLineStatusItem
import com.androidfung.departureboard.data.repository.region.gb.TflStopPointUtils

/**
 * Pure helper object responsible for resolving, filtering, sorting, and hydrating line badges
 * for transit stations, integrating live departure badges with TfL line statuses.
 */
object StationBadgeSynthesizer {

    private val NUMERIC_BUS_ROUTE_REGEX = Regex("^[A-Za-z]?[0-9]+[A-Za-z]?$")

    /**
     * Builds and enriches line badges for a station based on active departures,
     * station metadata fallback, and real-time line disruptions/statuses.
     */
    fun buildStationLineBadges(
        station: Station,
        departures: List<Departure>,
        lineStatuses: Map<String, TflLineStatusItem>
    ): List<LineBadgeInfo> {
        // Extract line badges from active departures, falling back to verified station lines when departures are empty,
        // and always merging any disrupted serving lines so full/partial closures with zero predictions still surface alerts.
        val isWembleyPark = TflStopPointUtils.isWembleyPark(station.id, station.name)
        val isStPancrasOrKingsCrossNR = TflStopPointUtils.isStPancrasOrKingsCrossNR(station.id, station.name)

        fun isAllowedBadge(badge: LineBadgeInfo): Boolean {
            if (isWembleyPark && badge.lineId.equals(com.androidfung.departureboard.data.model.gb.GbTransitLine.HAMMERSMITH_CITY.code, ignoreCase = true)) {
                return false
            }
            if (isStPancrasOrKingsCrossNR &&
                (badge.mode == TransitMode.METRO || badge.lineId.lowercase() in TflLineColors.TUBE_LINE_IDS)
            ) {
                return false
            }
            return true
        }

        val rawDepartureBadges = departures.map { it.lineBadge }.distinctBy { it.displayName }
        val inferredBadges = StationLineInferrer.infer(station).filter(::isAllowedBadge)
        val rawBadges = if (rawDepartureBadges.isNotEmpty()) {
            val filteredDepartureBadges = rawDepartureBadges.filter(::isAllowedBadge)
            val existingIds = filteredDepartureBadges.map { it.lineId.lowercase() }.toSet()
            val disruptedInferred = inferredBadges.filter { inferred ->
                val lowerId = inferred.lineId.lowercase()
                lowerId !in existingIds &&
                    (lineStatuses[lowerId]?.lineStatuses?.firstOrNull()?.statusSeverity ?: 10) != 10
            }
            filteredDepartureBadges + disruptedInferred
        } else {
            inferredBadges
        }

        // Sort line badges logically: Tube/Rail lines alphabetically, then numeric Bus routes in ascending order
        val sortedBadges = rawBadges.sortedWith(
            compareBy<LineBadgeInfo> { badge ->
                if (badge.mode == TransitMode.BUS || badge.displayName.matches(NUMERIC_BUS_ROUTE_REGEX)) 1 else 0
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
