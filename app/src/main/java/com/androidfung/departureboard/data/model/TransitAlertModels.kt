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
