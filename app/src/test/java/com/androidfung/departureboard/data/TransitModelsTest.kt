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
    fun testLineBadgeInfoNaturalComparator() {
        val bus102 = com.androidfung.departureboard.data.model.LineBadgeInfo("102", "102", androidx.compose.ui.graphics.Color.Red)
        val bus13 = com.androidfung.departureboard.data.model.LineBadgeInfo("13", "13", androidx.compose.ui.graphics.Color.Red)
        val bus460 = com.androidfung.departureboard.data.model.LineBadgeInfo("460", "460", androidx.compose.ui.graphics.Color.Red)
        val victoria = com.androidfung.departureboard.data.model.LineBadgeInfo("victoria", "Victoria", androidx.compose.ui.graphics.Color.Blue)
        val bakerloo = com.androidfung.departureboard.data.model.LineBadgeInfo("bakerloo", "Bakerloo", androidx.compose.ui.graphics.Color.Gray)

        val unsorted = listOf(victoria, bus102, bakerloo, bus460, bus13)
        val sorted = unsorted.sortedWith(com.androidfung.departureboard.data.model.LineBadgeInfo.NATURAL_COMPARATOR)

        val names = sorted.map { it.displayName }
        assertEquals(listOf("13", "102", "460", "Bakerloo", "Victoria"), names)
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
        assertTrue(oxfordCircus!!.lines.isNotEmpty())
        assertEquals(listOf("bakerloo", "central", "victoria"), oxfordCircus.lines.map { it.id })

        val fallbacks = DefaultStations.getFallbackDepartures("940GZZLUOXC", "Oxford Circus")
        assertTrue(fallbacks.isNotEmpty())
        assertEquals("Oxford Circus", fallbacks.first().stationName)
    }

    @Test
    fun testStationLineInferrerWithDynamicLines() {
        val station = com.androidfung.departureboard.data.model.Station(
            id = "custom_1",
            name = "Custom Station",
            modes = listOf("tube", "overground"),
            lines = listOf(
                com.androidfung.departureboard.data.model.StationLineInfo("weaver", "Weaver", "overground"),
                com.androidfung.departureboard.data.model.StationLineInfo("central", "Central", "tube")
            )
        )
        val badges = com.androidfung.departureboard.data.model.StationLineInferrer.infer(station)
        assertEquals(2, badges.size)
        assertEquals("Central", badges[0].displayName)
        assertEquals("Weaver", badges[1].displayName)
        assertEquals(TransitMode.OVERGROUND, badges[1].mode)
    }
}
