package com.androidfung.departureboard.widget

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TflLineStatusItem
import com.androidfung.departureboard.ui.components.TransitIconHelper
import com.androidfung.departureboard.ui.dashboard.StationBadgeSynthesizer

/**
 * Resolves transit service interruptions and delays for glanceable widget displays,
 * mapping disruptions to appropriate warning/severe alert levels and concise status labels.
 */
object WidgetInterruptionHelper {

    enum class InterruptionLevel {
        NONE,
        WARNING, // Minor delays, reduced service, or departure delays (Amber)
        SEVERE   // Suspended, part closed, severe delays, cancellations (Red)
    }

    data class InterruptionInfo(
        val level: InterruptionLevel = InterruptionLevel.NONE,
        val statusText: String? = null
    )

    fun resolveInterruption(
        station: Station,
        departures: List<Departure>,
        filteredDepartures: List<Departure>,
        lineStatuses: Map<String, TflLineStatusItem>,
        filterLineId: String?,
        filterLineName: String?
    ): InterruptionInfo {
        val badges = StationBadgeSynthesizer.buildStationLineBadges(station, departures, lineStatuses)

        val relevantBadges = if (!filterLineId.isNullOrBlank()) {
            badges.filter { badge ->
                TransitIconHelper.isSameLine(badge.lineId, filterLineId) ||
                    badge.displayName.equals(filterLineName, ignoreCase = true) ||
                    badge.lineId.equals(filterLineId, ignoreCase = true)
            }
        } else {
            badges
        }

        val disruptedBadges = relevantBadges.filter { it.isDisrupted }

        // Check departures for individual cancelled or delayed trips
        val departuresToCheck = if (!filterLineId.isNullOrBlank()) filteredDepartures else departures
        val hasCancelled = departuresToCheck.any { it.status == DepartureStatus.CANCELLED }
        val hasDelayed = departuresToCheck.any { it.status == DepartureStatus.DELAYED }

        if (disruptedBadges.isNotEmpty()) {
            val minSeverity = disruptedBadges.minOf { it.statusSeverity }
            val isSevere = minSeverity in 1..5 || hasCancelled ||
                disruptedBadges.any { badge ->
                    val desc = badge.statusDescription?.lowercase().orEmpty()
                    desc.contains("severe") || desc.contains("suspended") || desc.contains("closure") || desc.contains("closed")
                }

            val statusText = if (!filterLineId.isNullOrBlank() || disruptedBadges.size == 1) {
                val badge = disruptedBadges.first()
                if (!filterLineId.isNullOrBlank()) {
                    badge.statusDescription ?: "Delays"
                } else {
                    "${badge.displayName}: ${badge.statusDescription ?: "Delays"}"
                }
            } else {
                "${disruptedBadges.size} lines disrupted"
            }

            return InterruptionInfo(
                level = if (isSevere) InterruptionLevel.SEVERE else InterruptionLevel.WARNING,
                statusText = statusText
            )
        }

        // If no line-wide disruption is flagged in lineStatuses, check trip-level delays/cancellations
        if (hasCancelled) {
            return InterruptionInfo(
                level = InterruptionLevel.SEVERE,
                statusText = "Cancellations"
            )
        }

        if (hasDelayed) {
            return InterruptionInfo(
                level = InterruptionLevel.WARNING,
                statusText = "Delays"
            )
        }

        return InterruptionInfo(level = InterruptionLevel.NONE, statusText = null)
    }
}
