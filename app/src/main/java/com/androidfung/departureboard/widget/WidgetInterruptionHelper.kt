package com.androidfung.departureboard.widget

import android.content.Context
import com.androidfung.departureboard.R as AppR
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.gb.TflLineStatusItem
import com.androidfung.departureboard.ui.dashboard.StationBadgeSynthesizer
import com.androidfung.departureboard.ui.designsystem.TransitIconHelper

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
        filterLineName: String?,
        context: Context? = null
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

            val delaysLabel = context?.getString(AppR.string.widget_interruption_delays) ?: "Delays"
            val statusText = if (!filterLineId.isNullOrBlank() || disruptedBadges.size == 1) {
                val badge = disruptedBadges.first()
                val description = badge.statusDescription ?: delaysLabel
                if (!filterLineId.isNullOrBlank()) {
                    description
                } else {
                    context?.getString(AppR.string.widget_interruption_line_with_desc, badge.displayName, description)
                        ?: "${badge.displayName}: $description"
                }
            } else {
                context?.getString(AppR.string.widget_interruption_lines_disrupted, disruptedBadges.size)
                    ?: "${disruptedBadges.size} lines disrupted"
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
                statusText = context?.getString(AppR.string.widget_interruption_cancellations) ?: "Cancellations"
            )
        }

        if (hasDelayed) {
            return InterruptionInfo(
                level = InterruptionLevel.WARNING,
                statusText = context?.getString(AppR.string.widget_interruption_delays) ?: "Delays"
            )
        }

        return InterruptionInfo(level = InterruptionLevel.NONE, statusText = null)
    }
}
