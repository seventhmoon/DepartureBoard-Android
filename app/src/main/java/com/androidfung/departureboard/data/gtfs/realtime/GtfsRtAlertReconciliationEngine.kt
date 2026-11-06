package com.androidfung.departureboard.data.gtfs.realtime

import com.androidfung.departureboard.data.model.GtfsAlertEffect
import com.androidfung.departureboard.data.model.GtfsAlertSeverity

/**
 * Result of reconciling GTFS-RT alerts for a specific line or station.
 */
data class GtfsDisruptionStatus(
    val hasDisruption: Boolean,
    val severity: GtfsAlertSeverity,
    val effect: GtfsAlertEffect?,
    val header: String?,
    val description: String?,
    /**
     * Normalized integer severity scale matching [com.androidfung.departureboard.data.model.LineBadgeInfo.statusSeverity]:
     * 10 = Normal / Good Service
     * < 10 = Disrupted (5 = Minor delays, 1 = Suspended / Closed)
     */
    val normalizedSeverity: Int
)

object GtfsRtAlertReconciliationEngine {

    /**
     * Resolves disruption status for a given line routeId or station stopId from active GTFS-RT alerts.
     */
    fun reconcile(
        alerts: List<GtfsRtAlert>,
        routeId: String? = null,
        stopId: String? = null
    ): GtfsDisruptionStatus {
        val matchingAlerts = alerts.filter { alert ->
            alert.informedEntities.any { entity ->
                (routeId != null && entity.routeId.equals(routeId, ignoreCase = true)) ||
                        (stopId != null && entity.stopId.equals(stopId, ignoreCase = true))
            }
        }

        if (matchingAlerts.isEmpty()) {
            return GtfsDisruptionStatus(
                hasDisruption = false,
                severity = GtfsAlertSeverity.UNKNOWN,
                effect = null,
                header = null,
                description = null,
                normalizedSeverity = 10
            )
        }

        // Pick highest severity alert
        val primaryAlert = matchingAlerts.maxByOrNull { it.severityLevel.ordinal } ?: matchingAlerts.first()

        val normalized = when (primaryAlert.severityLevel) {
            GtfsAlertSeverity.SEVERE -> 1
            GtfsAlertSeverity.WARNING -> 5
            GtfsAlertSeverity.INFO -> 8
            GtfsAlertSeverity.UNKNOWN -> 7
        }

        return GtfsDisruptionStatus(
            hasDisruption = true,
            severity = primaryAlert.severityLevel,
            effect = primaryAlert.effect,
            header = primaryAlert.headerText,
            description = primaryAlert.descriptionText,
            normalizedSeverity = normalized
        )
    }
}
