package com.androidfung.departureboard.data

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.model.gb.GbDefaultStations
import com.androidfung.departureboard.data.model.gb.TflLineColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitModelsTest {

    @Test
    fun testTflLineColorsMapping() {
        val victoriaBadge = TflLineColors.getLineBadge("victoria", "Victoria", "tube")
        assertEquals("Victoria", victoriaBadge.displayName)
        assertEquals(TflLineColors.Victoria, victoriaBadge.backgroundColor)
        assertEquals(TransitMode.METRO, victoriaBadge.mode)

        val centralBadge = TflLineColors.getLineBadge("central", "Central", "tube")
        assertEquals("Central", centralBadge.displayName)
        assertEquals(TflLineColors.Central, centralBadge.backgroundColor)

        val elizabethBadge = TflLineColors.getLineBadge("elizabeth-line", "Elizabeth line", "elizabeth-line")
        assertEquals(TflLineColors.ElizabethLine, elizabethBadge.backgroundColor)
        assertEquals(TransitMode.RAIL, elizabethBadge.mode)

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

        val oneMinDeparture = dueDeparture.copy(timeToStationSeconds = 50, status = com.androidfung.departureboard.data.model.DepartureStatus.ON_TIME)
        assertEquals("1 min", oneMinDeparture.formattedTimeToArrival)

        val fiveMinsDeparture = dueDeparture.copy(timeToStationSeconds = 300, status = com.androidfung.departureboard.data.model.DepartureStatus.ON_TIME)
        assertEquals("5 mins", fiveMinsDeparture.formattedTimeToArrival)
    }

    @Test
    fun testDefaultStationsList() {
        assertTrue(GbDefaultStations.POPULAR_STATIONS.isNotEmpty())
        val oxfordCircus = GbDefaultStations.POPULAR_STATIONS.firstOrNull { it.name == "Oxford Circus" }
        assertNotNull(oxfordCircus)
        assertTrue(oxfordCircus!!.lines.isNotEmpty())
        assertEquals(listOf("bakerloo", "central", "victoria"), oxfordCircus.lines.map { it.id })

        val fallbacks = GbDefaultStations.getFallbackDepartures("940GZZLUOXC", "Oxford Circus")
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
        assertEquals(TransitMode.RAIL, badges[1].mode)
    }

    @Test
    fun testGtfsRouteTypesAndParsing() {
        assertEquals(TransitMode.METRO, TransitMode.fromModeString("metro"))
        assertEquals(TransitMode.METRO, TransitMode.fromModeString("subway"))
        assertEquals(TransitMode.RAIL, TransitMode.fromModeString("rail"))
        assertEquals(TransitMode.BUS, TransitMode.fromModeString("bus"))
        assertEquals(TransitMode.TRAM, TransitMode.fromModeString("tram"))
        assertEquals(TransitMode.FERRY, TransitMode.fromModeString("ferry"))
        assertEquals(TransitMode.CABLE_CAR, TransitMode.fromModeString("cable-car"))
        assertEquals(TransitMode.FUNICULAR, TransitMode.fromModeString("funicular"))
        assertEquals(TransitMode.MONORAIL, TransitMode.fromModeString("monorail"))

        assertEquals(1, TransitMode.METRO.gtfsRouteType)
        assertEquals(2, TransitMode.RAIL.gtfsRouteType)
        assertEquals(3, TransitMode.BUS.gtfsRouteType)
        assertEquals(0, TransitMode.TRAM.gtfsRouteType)
        assertEquals(4, TransitMode.FERRY.gtfsRouteType)
        assertEquals(6, TransitMode.CABLE_CAR.gtfsRouteType)
        assertEquals(7, TransitMode.FUNICULAR.gtfsRouteType)
        assertEquals(12, TransitMode.MONORAIL.gtfsRouteType)

        assertTrue(TransitMode.MONORAIL.isRail)
        assertTrue(TransitMode.RAIL.isRail)
        assertTrue(TransitMode.METRO.isRail)
    }

    @Test
    fun testTransitDirectionEnumAndResIds() {
        for (dir in com.androidfung.departureboard.data.model.TransitDirection.entries) {
            assertTrue("Expected valid string resource ID for direction: ${dir.name}", dir.labelResId != 0)
        }
        assertEquals(com.androidfung.departureboard.data.model.TransitDirection.NORTHBOUND, com.androidfung.departureboard.data.model.TransitDirection.fromString("northbound"))
        assertEquals(com.androidfung.departureboard.data.model.TransitDirection.ANTI_CLOCKWISE, com.androidfung.departureboard.data.model.TransitDirection.fromString("Outer"))
        assertEquals(com.androidfung.departureboard.data.model.TransitDirection.CLOCKWISE, com.androidfung.departureboard.data.model.TransitDirection.fromString("Inner"))
    }

    @Test
    fun testFarePeriod_computesPeakAndOffPeakCorrectly() {
        // Monday 08:00 (Morning Peak)
        val mondayMorningPeak = java.time.ZonedDateTime.of(2026, 11, 9, 8, 0, 0, 0, java.time.ZoneId.of("Europe/London"))
        assertEquals(com.androidfung.departureboard.data.model.FarePeriod.PEAK, com.androidfung.departureboard.data.model.FarePeriod.currentGbFarePeriod(mondayMorningPeak))

        // Monday 12:00 (Off-Peak)
        val mondayMiddayOffPeak = java.time.ZonedDateTime.of(2026, 11, 9, 12, 0, 0, 0, java.time.ZoneId.of("Europe/London"))
        assertEquals(com.androidfung.departureboard.data.model.FarePeriod.OFF_PEAK, com.androidfung.departureboard.data.model.FarePeriod.currentGbFarePeriod(mondayMiddayOffPeak))

        // Monday 17:30 (Evening Peak)
        val mondayEveningPeak = java.time.ZonedDateTime.of(2026, 11, 9, 17, 30, 0, 0, java.time.ZoneId.of("Europe/London"))
        assertEquals(com.androidfung.departureboard.data.model.FarePeriod.PEAK, com.androidfung.departureboard.data.model.FarePeriod.currentGbFarePeriod(mondayEveningPeak))

        // Saturday 17:30 (Weekend -> Off-Peak)
        val saturdayEvening = java.time.ZonedDateTime.of(2026, 11, 7, 17, 30, 0, 0, java.time.ZoneId.of("Europe/London"))
        assertEquals(com.androidfung.departureboard.data.model.FarePeriod.OFF_PEAK, com.androidfung.departureboard.data.model.FarePeriod.currentGbFarePeriod(saturdayEvening))
    }

    @Test
    fun testPlatformInfo_platformChangeAndConfirmation() {
        val changedPlat = com.androidfung.departureboard.data.model.PlatformInfo(
            code = "1b",
            isConfirmed = true,
            isChanged = true,
            previousPlatform = "4"
        )
        assertTrue(changedPlat.isChanged)
        assertEquals("4", changedPlat.previousPlatform)
        assertEquals("Platform 1b", changedPlat.formatDisplay())

        val expectedPlat = com.androidfung.departureboard.data.model.PlatformInfo(
            code = "9",
            isHistoricalExpected = true,
            expectedConfidencePercent = 90
        )
        assertTrue(expectedPlat.isHistoricalExpected)
        assertEquals("Platform 9 (Expected)", expectedPlat.formatDisplay())
    }

    @Test
    fun testStationAccessibility_osmAttributes() {
        val a11y = com.androidfung.departureboard.data.model.StationAccessibility(
            hasTactilePaving = true,
            hasHearingLoop = true,
            hasAccessibleToilet = true,
            requiresBoardingRamp = false
        )
        assertTrue(a11y.hasTactilePaving == true)
        assertTrue(a11y.hasHearingLoop == true)
        assertTrue(a11y.hasAccessibleToilet == true)
        assertFalse(a11y.requiresBoardingRamp == true)
    }
}
