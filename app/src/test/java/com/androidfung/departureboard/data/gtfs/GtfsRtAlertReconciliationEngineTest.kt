package com.androidfung.departureboard.data.gtfs

import com.androidfung.departureboard.data.gtfs.realtime.GtfsRtAlert
import com.androidfung.departureboard.data.gtfs.realtime.GtfsRtAlertReconciliationEngine
import com.androidfung.departureboard.data.gtfs.realtime.GtfsRtEntitySelector
import com.androidfung.departureboard.data.model.GtfsAlertEffect
import com.androidfung.departureboard.data.model.GtfsAlertSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GtfsRtAlertReconciliationEngineTest {

    @Test
    fun reconcile_returnsNormalWhenNoMatchingAlerts() {
        val alerts = listOf(
            GtfsRtAlert(
                informedEntities = listOf(GtfsRtEntitySelector(routeId = "OTHER_LINE")),
                headerText = "Closure",
                severityLevel = GtfsAlertSeverity.SEVERE
            )
        )

        val status = GtfsRtAlertReconciliationEngine.reconcile(alerts, routeId = "T4")

        assertFalse(status.hasDisruption)
        assertEquals(10, status.normalizedSeverity)
    }

    @Test
    fun reconcile_matchesRouteIdAndReturnsSevereDisruption() {
        val alerts = listOf(
            GtfsRtAlert(
                informedEntities = listOf(GtfsRtEntitySelector(routeId = "T4")),
                headerText = "Trackwork between Bondi and Central",
                descriptionText = "Buses replace trains",
                effect = GtfsAlertEffect.MODIFIED_SERVICE,
                severityLevel = GtfsAlertSeverity.SEVERE
            )
        )

        val status = GtfsRtAlertReconciliationEngine.reconcile(alerts, routeId = "T4")

        assertTrue(status.hasDisruption)
        assertEquals(GtfsAlertSeverity.SEVERE, status.severity)
        assertEquals(GtfsAlertEffect.MODIFIED_SERVICE, status.effect)
        assertEquals(1, status.normalizedSeverity)
        assertEquals("Trackwork between Bondi and Central", status.header)
    }

    @Test
    fun reconcile_matchesStopIdAndReturnsWarningDisruption() {
        val alerts = listOf(
            GtfsRtAlert(
                informedEntities = listOf(GtfsRtEntitySelector(stopId = "AU_TRAIN_CENTRAL")),
                headerText = "Lift out of service",
                effect = GtfsAlertEffect.OTHER_EFFECT,
                severityLevel = GtfsAlertSeverity.WARNING
            )
        )

        val status = GtfsRtAlertReconciliationEngine.reconcile(alerts, stopId = "AU_TRAIN_CENTRAL")

        assertTrue(status.hasDisruption)
        assertEquals(GtfsAlertSeverity.WARNING, status.severity)
        assertEquals(5, status.normalizedSeverity)
    }
}
