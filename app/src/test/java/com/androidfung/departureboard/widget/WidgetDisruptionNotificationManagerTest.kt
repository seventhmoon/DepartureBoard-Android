package com.androidfung.departureboard.widget

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.model.AlertEffect
import com.androidfung.departureboard.data.model.AlertSeverity
import com.androidfung.departureboard.data.model.InformedEntity
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.TransitAlert
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.model.gb.TflLineStatusDetail
import com.androidfung.departureboard.data.model.gb.TflLineStatusItem
import com.androidfung.departureboard.test.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetDisruptionNotificationManagerTest {

    @Test
    fun filteredLine_ignoresDisruptionOnOtherLineAtSameStation() {
        val target = WidgetDisruptionNotificationManager.MonitoredWidgetTarget(
            appWidgetId = 1,
            stationId = "940GZZLUOXC",
            stationName = "Oxford Circus",
            filterLineId = "central",
            filterLineName = "Central"
        )

        val centralDep = TestFixtures.createTestDeparture(
            lineId = "central",
            lineName = "Central",
            lineBadge = LineBadgeInfo("central", "Central", Color.Red, mode = TransitMode.METRO)
        )
        val victoriaDep = TestFixtures.createTestDeparture(
            lineId = "victoria",
            lineName = "Victoria",
            lineBadge = LineBadgeInfo("victoria", "Victoria", Color.Cyan, mode = TransitMode.METRO)
        )

        val statuses = mapOf(
            "victoria" to TflLineStatusItem(
                id = "victoria",
                name = "Victoria",
                lineStatuses = listOf(
                    TflLineStatusDetail(statusSeverity = 3, statusSeverityDescription = "Part Suspended")
                )
            ),
            "central" to TflLineStatusItem(
                id = "central",
                name = "Central",
                lineStatuses = listOf(
                    TflLineStatusDetail(statusSeverity = 10, statusSeverityDescription = "Good Service")
                )
            )
        )

        val evaluation = WidgetDisruptionNotificationManager.evaluateTargetDisruption(
            target = target,
            allDepartures = listOf(centralDep, victoriaDep),
            lineStatuses = statuses,
            transitAlerts = emptyList(),
            context = null
        )

        // Since target is filtered to Central, Victoria's suspension should be ignored
        assertFalse(evaluation.isDisrupted)
        assertEquals("NORMAL", evaluation.fingerprint)
    }

    @Test
    fun filteredLine_notifiesWhenFilteredLineDisrupted() {
        val target = WidgetDisruptionNotificationManager.MonitoredWidgetTarget(
            appWidgetId = 2,
            stationId = "940GZZLUOXC",
            stationName = "Oxford Circus",
            filterLineId = "victoria",
            filterLineName = "Victoria"
        )

        val centralDep = TestFixtures.createTestDeparture(
            lineId = "central",
            lineName = "Central",
            lineBadge = LineBadgeInfo("central", "Central", Color.Red, mode = TransitMode.METRO)
        )
        val victoriaDep = TestFixtures.createTestDeparture(
            lineId = "victoria",
            lineName = "Victoria",
            lineBadge = LineBadgeInfo("victoria", "Victoria", Color.Cyan, mode = TransitMode.METRO)
        )

        val statuses = mapOf(
            "victoria" to TflLineStatusItem(
                id = "victoria",
                name = "Victoria",
                lineStatuses = listOf(
                    TflLineStatusDetail(statusSeverity = 3, statusSeverityDescription = "Part Suspended")
                )
            ),
            "central" to TflLineStatusItem(
                id = "central",
                name = "Central",
                lineStatuses = listOf(
                    TflLineStatusDetail(statusSeverity = 10, statusSeverityDescription = "Good Service")
                )
            )
        )

        val evaluation = WidgetDisruptionNotificationManager.evaluateTargetDisruption(
            target = target,
            allDepartures = listOf(centralDep, victoriaDep),
            lineStatuses = statuses,
            transitAlerts = emptyList(),
            context = null
        )

        assertTrue(evaluation.isDisrupted)
        assertTrue(evaluation.isSevere)
        assertEquals("Oxford Circus • Victoria", evaluation.title)
        assertEquals("Part Suspended", evaluation.message)
        assertTrue(evaluation.fingerprint.startsWith("CLOSED_3_") || evaluation.fingerprint.startsWith("SEVERE_"))
    }

    @Test
    fun filteredLine_notifiesWhenStationClosedDueToLackOfStaff() {
        val target = WidgetDisruptionNotificationManager.MonitoredWidgetTarget(
            appWidgetId = 3,
            stationId = "940GZZLUCGN",
            stationName = "Covent Garden",
            filterLineId = "piccadilly",
            filterLineName = "Piccadilly"
        )

        val piccadillyDep = TestFixtures.createTestDeparture(
            lineId = "piccadilly",
            lineName = "Piccadilly",
            lineBadge = LineBadgeInfo(
                lineId = "piccadilly",
                displayName = "Piccadilly",
                backgroundColor = Color.Blue,
                mode = TransitMode.METRO,
                statusSeverity = 1,
                statusDescription = "Closed",
                disruptionReason = "Covent Garden Station: Closed due to a lack of station staff."
            )
        )

        val statuses = mapOf(
            "piccadilly" to TflLineStatusItem(
                id = "piccadilly",
                name = "Piccadilly",
                lineStatuses = listOf(
                    TflLineStatusDetail(
                        statusSeverity = 1,
                        statusSeverityDescription = "Closed",
                        reason = "Covent Garden Station: Closed due to a lack of station staff."
                    )
                )
            )
        )

        val evaluation = WidgetDisruptionNotificationManager.evaluateTargetDisruption(
            target = target,
            allDepartures = listOf(piccadillyDep),
            lineStatuses = statuses,
            transitAlerts = emptyList(),
            context = null
        )

        assertTrue(evaluation.isDisrupted)
        assertTrue(evaluation.isSevere)
        assertEquals("Covent Garden • Piccadilly", evaluation.title)
        assertTrue(evaluation.message.contains("Closed due to a lack of station staff"))
        assertTrue(evaluation.fingerprint.startsWith("CLOSED_1_"))
    }

    @Test
    fun filteredLine_ignoresClosureOnDifferentLineAtSameStation() {
        val target = WidgetDisruptionNotificationManager.MonitoredWidgetTarget(
            appWidgetId = 4,
            stationId = "940GZZLUKSX",
            stationName = "King's Cross St. Pancras",
            filterLineId = "piccadilly",
            filterLineName = "Piccadilly"
        )

        val piccadillyDep = TestFixtures.createTestDeparture(
            lineId = "piccadilly",
            lineName = "Piccadilly",
            lineBadge = LineBadgeInfo("piccadilly", "Piccadilly", Color.Blue, mode = TransitMode.METRO)
        )

        // Northern line closed due to lack of staff, but Piccadilly has Good Service
        val statuses = mapOf(
            "northern" to TflLineStatusItem(
                id = "northern",
                name = "Northern",
                lineStatuses = listOf(
                    TflLineStatusDetail(
                        statusSeverity = 1,
                        statusSeverityDescription = "Closed",
                        reason = "Northern line platforms closed due to lack of staff."
                    )
                )
            ),
            "piccadilly" to TflLineStatusItem(
                id = "piccadilly",
                name = "Piccadilly",
                lineStatuses = listOf(
                    TflLineStatusDetail(statusSeverity = 10, statusSeverityDescription = "Good Service")
                )
            )
        )

        val evaluation = WidgetDisruptionNotificationManager.evaluateTargetDisruption(
            target = target,
            allDepartures = listOf(piccadillyDep),
            lineStatuses = statuses,
            transitAlerts = emptyList(),
            context = null
        )

        // User is monitoring Piccadilly, so Northern line closure must be ignored
        assertFalse(evaluation.isDisrupted)
        assertEquals("NORMAL", evaluation.fingerprint)
    }

    @Test
    fun filteredLine_ignoresAlertsTargetingDifferentLine() {
        val target = WidgetDisruptionNotificationManager.MonitoredWidgetTarget(
            appWidgetId = 5,
            stationId = "940GZZLUOXC",
            stationName = "Oxford Circus",
            filterLineId = "central",
            filterLineName = "Central"
        )

        val alertForVictoriaOnly = TransitAlert(
            id = "alert-vic-1",
            headerText = "Victoria line platform closed due to lack of staff",
            severity = AlertSeverity.SEVERE,
            effect = AlertEffect.NO_SERVICE,
            informedEntities = listOf(
                InformedEntity(
                    stationId = "940GZZLUOXC",
                    lineId = "victoria"
                )
            )
        )

        val evaluation = WidgetDisruptionNotificationManager.evaluateTargetDisruption(
            target = target,
            allDepartures = emptyList(),
            lineStatuses = emptyMap(),
            transitAlerts = listOf(alertForVictoriaOnly),
            context = null
        )

        // The alert specifies Victoria line at Oxford Circus, so Central line widget should not trigger
        assertFalse(evaluation.isDisrupted)
    }

    @Test
    fun filteredLine_matchesAlertTargetingFilteredLine() {
        val target = WidgetDisruptionNotificationManager.MonitoredWidgetTarget(
            appWidgetId = 6,
            stationId = "940GZZLUOXC",
            stationName = "Oxford Circus",
            filterLineId = "central",
            filterLineName = "Central"
        )

        val alertForCentral = TransitAlert(
            id = "alert-cen-1",
            headerText = "Central line station entrance closed due to staff shortage",
            severity = AlertSeverity.SEVERE,
            effect = AlertEffect.NO_SERVICE,
            informedEntities = listOf(
                InformedEntity(
                    stationId = "940GZZLUOXC",
                    lineId = "central"
                )
            )
        )

        val evaluation = WidgetDisruptionNotificationManager.evaluateTargetDisruption(
            target = target,
            allDepartures = emptyList(),
            lineStatuses = emptyMap(),
            transitAlerts = listOf(alertForCentral),
            context = null
        )

        assertTrue(evaluation.isDisrupted)
        assertEquals("Central line station entrance closed due to staff shortage", evaluation.message)
    }

    @Test
    fun noLineFilter_notifiesWhenAnyLineIsDisrupted() {
        val target = WidgetDisruptionNotificationManager.MonitoredWidgetTarget(
            appWidgetId = 7,
            stationId = "940GZZLUOXC",
            stationName = "Oxford Circus",
            filterLineId = null,
            filterLineName = null
        )

        val dep = TestFixtures.createTestDeparture(
            lineId = "victoria",
            lineName = "Victoria",
            lineBadge = LineBadgeInfo("victoria", "Victoria", Color.Cyan, mode = TransitMode.METRO)
        )

        val statuses = mapOf(
            "victoria" to TflLineStatusItem(
                id = "victoria",
                name = "Victoria",
                lineStatuses = listOf(
                    TflLineStatusDetail(statusSeverity = 9, statusSeverityDescription = "Minor Delays")
                )
            )
        )

        val evaluation = WidgetDisruptionNotificationManager.evaluateTargetDisruption(
            target = target,
            allDepartures = listOf(dep),
            lineStatuses = statuses,
            transitAlerts = emptyList(),
            context = null
        )

        assertTrue(evaluation.isDisrupted)
        assertEquals("Oxford Circus", evaluation.title)
        assertEquals("Victoria: Minor Delays", evaluation.message)
    }

    @Test
    fun normalOperatingHoursWithoutDisruptions_doesNotNotify() {
        val target = WidgetDisruptionNotificationManager.MonitoredWidgetTarget(
            appWidgetId = 8,
            stationId = "940GZZLUOXC",
            stationName = "Oxford Circus",
            filterLineId = "victoria",
            filterLineName = "Victoria"
        )

        val evaluation = WidgetDisruptionNotificationManager.evaluateTargetDisruption(
            target = target,
            allDepartures = emptyList(),
            lineStatuses = emptyMap(),
            transitAlerts = emptyList(),
            context = null
        )

        assertFalse(evaluation.isDisrupted)
        assertEquals("NORMAL", evaluation.fingerprint)
    }

    @Test
    fun deduplicationFingerprint_isDeterministic() {
        val target = WidgetDisruptionNotificationManager.MonitoredWidgetTarget(
            appWidgetId = 9,
            stationId = "940GZZLUOXC",
            stationName = "Oxford Circus",
            filterLineId = "victoria",
            filterLineName = "Victoria"
        )

        val statuses = mapOf(
            "victoria" to TflLineStatusItem(
                id = "victoria",
                name = "Victoria",
                lineStatuses = listOf(
                    TflLineStatusDetail(statusSeverity = 6, statusSeverityDescription = "Severe Delays")
                )
            )
        )

        val eval1 = WidgetDisruptionNotificationManager.evaluateTargetDisruption(
            target = target,
            allDepartures = emptyList(),
            lineStatuses = statuses,
            transitAlerts = emptyList(),
            context = null
        )

        val eval2 = WidgetDisruptionNotificationManager.evaluateTargetDisruption(
            target = target,
            allDepartures = emptyList(),
            lineStatuses = statuses,
            transitAlerts = emptyList(),
            context = null
        )

        assertEquals(eval1.fingerprint, eval2.fingerprint)
    }
}
