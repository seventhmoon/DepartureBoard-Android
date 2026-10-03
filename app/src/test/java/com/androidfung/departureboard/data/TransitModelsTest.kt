package com.androidfung.departureboard.data

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DefaultStations
import com.androidfung.departureboard.data.model.TflLineColors
import com.androidfung.departureboard.data.model.TransitMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitModelsTest {

    @Test
    fun testTflLineColorsMapping() {
        val victoriaBadge = TflLineColors.getLineBadge("victoria", "Victoria", "tube")
        assertEquals("Victoria", victoriaBadge.displayName)
        assertEquals(TflLineColors.Victoria, victoriaBadge.backgroundColor)
        assertEquals(TransitMode.TUBE, victoriaBadge.mode)

        val centralBadge = TflLineColors.getLineBadge("central", "Central", "tube")
        assertEquals("Central", centralBadge.displayName)
        assertEquals(TflLineColors.Central, centralBadge.backgroundColor)

        val elizabethBadge = TflLineColors.getLineBadge("elizabeth-line", "Elizabeth line", "elizabeth-line")
        assertEquals(TflLineColors.ElizabethLine, elizabethBadge.backgroundColor)
        assertEquals(TransitMode.ELIZABETH_LINE, elizabethBadge.mode)

        val dlrBadge = TflLineColors.getLineBadge("dlr", "DLR", "dlr")
        assertEquals(TflLineColors.Dlr, dlrBadge.backgroundColor)

        val overgroundBadge = TflLineColors.getLineBadge("overground", "London Overground", "overground")
        assertEquals(TflLineColors.LondonOverground, overgroundBadge.backgroundColor)

        val busBadge = TflLineColors.getLineBadge("73", "73", "bus")
        assertEquals(TflLineColors.Bus, busBadge.backgroundColor)
        assertEquals(TransitMode.BUS, busBadge.mode)
    }

    @Test
    fun testFormattedTimeToArrival() {
        val badge = TflLineColors.getLineBadge("victoria", "Victoria", "tube")
        val dueDeparture = Departure(
            id = "1",
            stationId = "OXC",
            stationName = "Oxford Circus",
            lineId = "victoria",
            lineName = "Victoria",
            platformName = "Platform 1",
            destinationName = "Brixton",
            towards = "Brixton",
            timeToStationSeconds = 25,
            expectedArrivalIso = null,
            currentLocation = null,
            modeName = "tube",
            lineBadge = badge
        )
        assertEquals("Due", dueDeparture.formattedTimeToArrival)

        val oneMinDeparture = dueDeparture.copy(timeToStationSeconds = 50)
        assertEquals("1 min", oneMinDeparture.formattedTimeToArrival)

        val fiveMinsDeparture = dueDeparture.copy(timeToStationSeconds = 300)
        assertEquals("5 mins", fiveMinsDeparture.formattedTimeToArrival)
    }

    @Test
    fun testDefaultStationsList() {
        assertTrue(DefaultStations.POPULAR_STATIONS.isNotEmpty())
        val oxfordCircus = DefaultStations.POPULAR_STATIONS.firstOrNull { it.name == "Oxford Circus" }
        assertNotNull(oxfordCircus)

        val fallbacks = DefaultStations.getFallbackDepartures("940GZZLUOXC", "Oxford Circus")
        assertTrue(fallbacks.isNotEmpty())
        assertEquals("Oxford Circus", fallbacks.first().stationName)
    }
}
