package com.androidfung.departureboard.data.model

import androidx.compose.runtime.Immutable
import com.androidfung.departureboard.data.i18n.LocalizedNameProvider
import java.time.Instant

/**
 * Standardized transit disruption and service alert adhering to GTFS-RT Alert specifications.
 */
@Immutable
data class TransitAlert(
    val id: String,
    val cause: AlertCause = AlertCause.UNKNOWN_CAUSE,
    val effect: AlertEffect = AlertEffect.UNKNOWN_EFFECT,
    val severity: AlertSeverity = AlertSeverity.WARNING,
    val headerText: String,
    val descriptionText: String? = null,
    val url: String? = null,
    val activePeriodStart: Instant? = null,
    val activePeriodEnd: Instant? = null,
    val informedEntities: List<InformedEntity> = emptyList(),
    override val localizedNames: Map<String, String> = emptyMap()
) : LocalizedNameProvider {
    fun appliesToStation(stationId: String): Boolean =
        informedEntities.any { it.stationId.equals(stationId, ignoreCase = true) }

    fun appliesToLine(lineId: String): Boolean =
        informedEntities.any { it.lineId.equals(lineId, ignoreCase = true) || it.routeId.equals(lineId, ignoreCase = true) }

    fun appliesToOperator(agencyId: String): Boolean =
        informedEntities.any { it.agencyId.equals(agencyId, ignoreCase = true) }

    companion object {
        private val HTML_TAG_REGEX = Regex("<[^>]+>")
        private val MULTI_SPACE_REGEX = Regex("\\s+")

        /**
         * Strips HTML tags and collapses extra whitespace from alert descriptions.
         */
        fun stripHtml(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            return raw.replace(HTML_TAG_REGEX, " ")
                .replace(MULTI_SPACE_REGEX, " ")
                .trim()
                .ifBlank { null }
        }

        /**
         * Infers standard GTFS-RT [AlertCause] and [AlertEffect] from free-form disruption text.
         */
        fun inferCauseAndEffect(text: String): Pair<AlertCause, AlertEffect> {
            val lower = text.lowercase()
            val cause = when {
                "strike" in lower || "industrial action" in lower -> AlertCause.STRIKE
                "signal" in lower || "fault" in lower || "failure" in lower || "technical" in lower -> AlertCause.TECHNICAL_PROBLEM
                "trackwork" in lower || "maintenance" in lower || "planned" in lower || "engineering" in lower -> AlertCause.MAINTENANCE
                "weather" in lower || "flood" in lower || "wind" in lower || "storm" in lower -> AlertCause.WEATHER
                "police" in lower || "security" in lower -> AlertCause.POLICE_ACTIVITY
                "medical" in lower || "emergency" in lower || "ill" in lower -> AlertCause.MEDICAL_EMERGENCY
                else -> AlertCause.OTHER_CAUSE
            }
            val effect = when {
                "suspend" in lower || "no service" in lower || "closed" in lower || "cancel" in lower -> AlertEffect.NO_SERVICE
                "part suspend" in lower || "reduced" in lower || "trackwork" in lower || "bus" in lower -> AlertEffect.MODIFIED_SERVICE
                "delay" in lower || "minor" in lower || "severe" in lower -> AlertEffect.SIGNIFICANT_DELAYS
                else -> AlertEffect.OTHER_EFFECT
            }
            return cause to effect
        }

        /**
         * Shared factory for station accessibility / lift outage alerts across regions.
         */
        fun accessibilityOutage(
            id: String,
            stationLabel: String,
            facilityLabel: String = "Lift Outage",
            areaOrLocation: String? = null,
            description: String? = null,
            agencyId: String,
            stationId: String? = null
        ): TransitAlert {
            val header = if (!areaOrLocation.isNullOrBlank()) {
                "$stationLabel — $facilityLabel ($areaOrLocation)"
            } else {
                "$stationLabel — $facilityLabel"
            }
            return TransitAlert(
                id = id,
                cause = AlertCause.MAINTENANCE,
                effect = AlertEffect.ACCESSIBILITY_ISSUE,
                severity = AlertSeverity.INFO,
                headerText = header,
                descriptionText = description?.trim()?.takeIf(String::isNotEmpty),
                informedEntities = listOfNotNull(
                    stationId?.trim()?.takeIf(String::isNotEmpty)?.let {
                        InformedEntity(agencyId = agencyId, stationId = it)
                    }
                )
            )
        }
    }
}

/**
 * Identifies the specific transit entity (agency, route/line, station/stop, or trip) impacted by an alert.
 */
@Immutable
data class InformedEntity(
    val agencyId: String? = null,
    val routeId: String? = null,
    val lineId: String? = null,
    val stationId: String? = null,
    val tripId: String? = null
)

/**
 * Standard GTFS-RT Cause of alert.
 */
enum class AlertCause {
    UNKNOWN_CAUSE,
    OTHER_CAUSE,
    TECHNICAL_PROBLEM,
    STRIKE,
    DEMONSTRATION,
    ACCIDENT,
    HOLIDAY,
    WEATHER,
    MAINTENANCE,
    CONSTRUCTION,
    POLICE_ACTIVITY,
    MEDICAL_EMERGENCY
}

/**
 * Standard GTFS-RT Effect of alert.
 */
enum class AlertEffect {
    NO_SERVICE,
    REDUCED_SERVICE,
    SIGNIFICANT_DELAYS,
    DETOUR,
    ADDITIONAL_SERVICE,
    MODIFIED_SERVICE,
    OTHER_EFFECT,
    UNKNOWN_EFFECT,
    STOP_MOVED,
    NO_EFFECT,
    ACCESSIBILITY_ISSUE
}

/**
 * Relative severity level of the alert.
 */
enum class AlertSeverity {
    INFO,
    WARNING,
    SEVERE
}
