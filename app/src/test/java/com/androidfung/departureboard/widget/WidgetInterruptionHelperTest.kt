package com.androidfung.departureboard.widget

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TflLineStatusDetail
import com.androidfung.departureboard.data.model.TflLineStatusItem
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.test.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetInterruptionHelperTest {

    private val sampleStation = Station(
        id = "940GZZLUOXC",
        name = "Oxford Circus",
        modes = listOf("tube")
    )

    @Test
    fun testResolveInterruption_goodService() {
        val dep = TestFixtures.createTestDeparture(
            lineId = "victoria",
            lineName = "Victoria",
            lineBadge = LineBadgeInfo("victoria", "Victoria", Color.Cyan, mode = TransitMode.TUBE)
        )
        val statuses = mapOf(
            "victoria" to TflLineStatusItem(
                id = "victoria",
                name = "Victoria",
                lineStatuses = listOf(
                    TflLineStatusDetail(statusSeverity = 10, statusSeverityDescription = "Good Service")
                )
            )
        )

        val result = WidgetInterruptionHelper.resolveInterruption(
            station = sampleStation,
            departures = listOf(dep),
            filteredDepartures = listOf(dep),
            lineStatuses = statuses,
            filterLineId = null,
            filterLineName = null
        )

        assertEquals(WidgetInterruptionHelper.InterruptionLevel.NONE, result.level)
        assertEquals(null, result.statusText)
    }

    @Test
    fun testResolveInterruption_minorDelays() {
        val dep = TestFixtures.createTestDeparture(
            lineId = "victoria",
            lineName = "Victoria",
            lineBadge = LineBadgeInfo("victoria", "Victoria", Color.Cyan, mode = TransitMode.TUBE)
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

        val result = WidgetInterruptionHelper.resolveInterruption(
            station = sampleStation,
            departures = listOf(dep),
            filteredDepartures = listOf(dep),
            lineStatuses = statuses,
            filterLineId = null,
            filterLineName = null
        )

        assertEquals(WidgetInterruptionHelper.InterruptionLevel.WARNING, result.level)
        assertEquals("Victoria: Minor Delays", result.statusText)
    }

    @Test
    fun testResolveInterruption_filteredLineSevereDelays() {
        val vic = TestFixtures.createTestDeparture(
            lineId = "victoria",
            lineName = "Victoria",
            lineBadge = LineBadgeInfo("victoria", "Victoria", Color.Cyan, mode = TransitMode.TUBE)
        )
        val cen = TestFixtures.createTestDeparture(
            lineId = "central",
            lineName = "Central",
            lineBadge = LineBadgeInfo("central", "Central", Color.Red, mode = TransitMode.TUBE)
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

        // When filtered to Victoria, part suspended gives SEVERE
        val vicResult = WidgetInterruptionHelper.resolveInterruption(
            station = sampleStation,
            departures = listOf(vic, cen),
            filteredDepartures = listOf(vic),
            lineStatuses = statuses,
            filterLineId = "victoria",
            filterLineName = "Victoria"
        )
        assertEquals(WidgetInterruptionHelper.InterruptionLevel.SEVERE, vicResult.level)
        assertEquals("Part Suspended", vicResult.statusText)

        // When filtered to Central, it is Good Service (NONE)
        val cenResult = WidgetInterruptionHelper.resolveInterruption(
            station = sampleStation,
            departures = listOf(vic, cen),
            filteredDepartures = listOf(cen),
            lineStatuses = statuses,
            filterLineId = "central",
            filterLineName = "Central"
        )
        assertEquals(WidgetInterruptionHelper.InterruptionLevel.NONE, cenResult.level)
    }

    @Test
    fun testResolveInterruption_individualTrainCancellation() {
        val dep = TestFixtures.createTestDeparture(
            lineId = "nr-sw",
            lineName = "South Western Railway",
            status = DepartureStatus.CANCELLED
        )

        val result = WidgetInterruptionHelper.resolveInterruption(
            station = sampleStation,
            departures = listOf(dep),
            filteredDepartures = listOf(dep),
            lineStatuses = emptyMap(),
            filterLineId = null,
            filterLineName = null
        )

        assertEquals(WidgetInterruptionHelper.InterruptionLevel.SEVERE, result.level)
        assertEquals("Cancellations", result.statusText)
    }
}
