package com.androidfung.departureboard.util

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.LineBadgeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BusBunchingDetectorTest {

    private fun createBusDeparture(
        id: String,
        lineId: String = "24",
        lineName: String = "24",
        destination: String = "Pimlico",
        timeToStationSeconds: Int
    ): Departure {
        return Departure(
            id = id,
            stationId = "490000077E",
            stationName = "Euston Station",
            lineId = lineId,
            lineName = lineName,
            platformName = "Stop D",
            destinationName = destination,
            towards = destination,
            timeToStationSeconds = timeToStationSeconds,
            expectedArrivalIso = null,
            currentLocation = null,
            modeName = "bus",
            lineBadge = LineBadgeInfo(lineId = lineId, displayName = lineName, backgroundColor = Color.Red, textColor = Color.White)
        )
    }

    @Test
    fun `detects bunched buses running bumper to bumper within 90 seconds`() {
        val bus1 = createBusDeparture("b1", timeToStationSeconds = 120) // 2 mins
        val bus2 = createBusDeparture("b2", timeToStationSeconds = 180) // 3 mins (60s gap)

        val analysis = BusBunchingDetector.analyzeSpacing(listOf(bus1, bus2))

        val info1 = analysis["b1"]
        val info2 = analysis["b2"]

        assertNotNull(info1)
        assertTrue(info1!!.isBunchedWithBehind)
        assertFalse(info1.isBunchedWithAhead)
        assertEquals(BusSpacingSeverity.BUNCHED, info1.severity)

        assertNotNull(info2)
        assertTrue(info2!!.isBunchedWithAhead)
        assertEquals(BusSpacingSeverity.BUNCHED, info2.severity)
        assertEquals(60, info2.gapAheadSeconds)
    }

    @Test
    fun `does not bunch different routes`() {
        val bus24 = createBusDeparture("b1", lineId = "24", timeToStationSeconds = 120)
        val bus73 = createBusDeparture("b2", lineId = "73", timeToStationSeconds = 130)

        val analysis = BusBunchingDetector.analyzeSpacing(listOf(bus24, bus73))

        assertFalse(analysis["b1"]?.isBunched == true)
        assertFalse(analysis["b2"]?.isBunched == true)
    }

    @Test
    fun `detects long service gap`() {
        val bus1 = createBusDeparture("b1", timeToStationSeconds = 60)
        val bus2 = createBusDeparture("b2", timeToStationSeconds = 1200) // 20 mins gap

        val analysis = BusBunchingDetector.analyzeSpacing(listOf(bus1, bus2))

        assertEquals(BusSpacingSeverity.LONG_GAP, analysis["b2"]?.severity)
        assertEquals(1140, analysis["b2"]?.gapAheadSeconds)
    }
}
