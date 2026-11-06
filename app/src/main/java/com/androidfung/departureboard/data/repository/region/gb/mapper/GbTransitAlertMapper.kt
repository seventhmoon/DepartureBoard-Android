package com.androidfung.departureboard.data.repository.region.gb.mapper

import com.androidfung.departureboard.data.model.AlertCause
import com.androidfung.departureboard.data.model.AlertEffect
import com.androidfung.departureboard.data.model.AlertSeverity
import com.androidfung.departureboard.data.model.InformedEntity
import com.androidfung.departureboard.data.model.TransitAlert
import com.androidfung.departureboard.data.model.gb.BodsDisruption
import com.androidfung.departureboard.data.model.gb.NrMessage
import com.androidfung.departureboard.data.model.gb.TflLineStatusItem

/**
 * Maps UK transport feeds (TfL Line Status, DfT BODS SIRI-SX Disruptions, National Rail NRCC messages)
 * into standardized GTFS-RT [TransitAlert] domain models.
 */
object GbTransitAlertMapper {

    /**
     * Converts a disrupted [TflLineStatusItem] into a list of [TransitAlert]s (one per non-good status).
     */
    fun fromTflLineStatus(item: TflLineStatusItem): List<TransitAlert> {
        return item.lineStatuses
            .filter { it.statusSeverity != 10 }
            .mapIndexed { index, status ->
                val text = "${status.statusSeverityDescription} ${status.reason.orEmpty()}".lowercase()
                TransitAlert(
                    id = "tfl-alert-${item.id.lowercase()}-$index",
                    cause = inferCause(text),
                    effect = inferEffectFromTflSeverity(status.statusSeverity, text),
                    severity = inferSeverityFromTflCode(status.statusSeverity),
                    headerText = "${item.name}: ${status.statusSeverityDescription}",
                    descriptionText = status.reason,
                    informedEntities = listOf(
                        InformedEntity(
                            agencyId = "TfL",
                            routeId = item.id.lowercase(),
                            lineId = item.id.lowercase()
                        )
                    )
                )
            }
    }

    /**
     * Converts a DfT BODS SIRI-SX [BodsDisruption] into a [TransitAlert].
     */
    fun fromBodsDisruption(disruption: BodsDisruption): TransitAlert {
        val combinedText = "${disruption.reason.orEmpty()} ${disruption.summary} ${disruption.description.orEmpty()}".lowercase()
        val lineRef = disruption.lineRef?.lowercase()
        val lineId = if (!lineRef.isNullOrBlank()) {
            if (lineRef.startsWith("bods-")) lineRef else "bods-$lineRef"
        } else null

        val severity = when (disruption.severity?.lowercase()) {
            "severe", "verysevere", "pti26_4", "pti26_5" -> AlertSeverity.SEVERE
            "slight", "verySlight", "pti26_1", "pti26_2", "normal" -> AlertSeverity.INFO
            else -> AlertSeverity.WARNING
        }

        return TransitAlert(
            id = disruption.situationNumber.ifBlank { "bods-alert-${lineId ?: "general"}" },
            cause = inferCause(combinedText),
            effect = inferEffectFromText(combinedText),
            severity = severity,
            headerText = disruption.summary,
            descriptionText = disruption.description ?: disruption.reason,
            informedEntities = listOf(
                InformedEntity(
                    agencyId = disruption.participantRef ?: "BODS",
                    routeId = lineId,
                    lineId = lineId
                )
            )
        )
    }

    /**
     * Converts a National Rail NRCC station message ([NrMessage]) into a [TransitAlert].
     */
    fun fromNrccMessage(message: NrMessage, stationCrs: String? = null, index: Int = 0): TransitAlert? {
        val rawValue = message.value?.trim() ?: return null
        val cleaned = rawValue.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
        if (cleaned.isBlank()) return null

        val lower = cleaned.lowercase()
        return TransitAlert(
            id = "nrcc-${stationCrs?.lowercase() ?: "uk"}-$index",
            cause = inferCause(lower),
            effect = inferEffectFromText(lower),
            severity = if (lower.contains("suspended") || lower.contains("no service") || lower.contains("major")) {
                AlertSeverity.SEVERE
            } else {
                AlertSeverity.WARNING
            },
            headerText = cleaned.take(120),
            descriptionText = cleaned,
            informedEntities = listOf(
                InformedEntity(
                    agencyId = "NR",
                    stationId = stationCrs?.uppercase()
                )
            )
        )
    }

    internal fun inferCause(text: String): AlertCause = when {
        text.contains("strike") || text.contains("industrial action") -> AlertCause.STRIKE
        text.contains("signal") || text.contains("fault") || text.contains("broken down") || text.contains("points failure") || text.contains("technical") -> AlertCause.TECHNICAL_PROBLEM
        text.contains("weather") || text.contains("flood") || text.contains("snow") || text.contains("wind") || text.contains("storm") -> AlertCause.WEATHER
        text.contains("engineering") || text.contains("planned") || text.contains("maintenance") || text.contains("track work") -> AlertCause.MAINTENANCE
        text.contains("construction") || text.contains("roadworks") -> AlertCause.CONSTRUCTION
        text.contains("police") || text.contains("security") || text.contains("trespass") -> AlertCause.POLICE_ACTIVITY
        text.contains("casualty") || text.contains("medical") || text.contains("ill passenger") || text.contains("ambulance") -> AlertCause.MEDICAL_EMERGENCY
        text.contains("accident") || text.contains("collision") || text.contains("derail") -> AlertCause.ACCIDENT
        text.contains("protest") || text.contains("demonstration") -> AlertCause.DEMONSTRATION
        text.contains("holiday") || text.contains("christmas") || text.contains("bank holiday") -> AlertCause.HOLIDAY
        else -> AlertCause.UNKNOWN_CAUSE
    }

    internal fun inferEffectFromTflSeverity(severityCode: Int, text: String): AlertEffect = when (severityCode) {
        1, 2, 16, 20 -> AlertEffect.NO_SERVICE       // Closed, Suspended, Not Running, Service Closed
        3, 4, 5, 11 -> AlertEffect.MODIFIED_SERVICE  // Part Suspended, Planned Closure, Part Closure
        6 -> AlertEffect.SIGNIFICANT_DELAYS          // Severe Delays
        7 -> AlertEffect.REDUCED_SERVICE             // Reduced Service
        8 -> AlertEffect.ADDITIONAL_SERVICE          // Bus Service replacement
        9 -> AlertEffect.SIGNIFICANT_DELAYS          // Minor Delays
        10, 18 -> AlertEffect.NO_EFFECT              // Good Service, No Issues
        else -> inferEffectFromText(text)
    }

    internal fun inferEffectFromText(text: String): AlertEffect = when {
        text.contains("divert") || text.contains("detour") || text.contains("reroute") -> AlertEffect.DETOUR
        text.contains("suspended") || text.contains("no service") || text.contains("cancelled") || text.contains("closed") -> AlertEffect.NO_SERVICE
        text.contains("reduced") || text.contains("fewer trains") -> AlertEffect.REDUCED_SERVICE
        text.contains("step-free") || text.contains("lift") || text.contains("escalator") -> AlertEffect.ACCESSIBILITY_ISSUE
        text.contains("stop moved") || text.contains("relocated") -> AlertEffect.STOP_MOVED
        text.contains("delay") || text.contains("disrupt") || text.contains("late") -> AlertEffect.SIGNIFICANT_DELAYS
        else -> AlertEffect.MODIFIED_SERVICE
    }

    internal fun inferSeverityFromTflCode(severityCode: Int): AlertSeverity = when (severityCode) {
        in 1..6, 16, 20 -> AlertSeverity.SEVERE
        10, 18 -> AlertSeverity.INFO
        else -> AlertSeverity.WARNING
    }
}
