package com.androidfung.departureboard.ui.dashboard

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.model.gb.TflLineStatusDetail
import com.androidfung.departureboard.data.model.gb.TflLineStatusItem
import com.androidfung.departureboard.test.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Test

class StationBadgeSynthesizerTest {

    private val sampleStation = Station(
        id = "940GZZLUOXC",
        name = "Oxford Circus",
        modes = listOf("tube")
    )

    @Test
    fun testBuildStationLineBadges_extractsAndHydratesStatuses() {
        val departure = TestFixtures.createTestDeparture(
            lineId = "victoria",
            lineName = "Victoria",
            lineBadge = LineBadgeInfo("victoria", "Victoria", Color.Cyan, mode = TransitMode.METRO)
        )

        val statuses = mapOf(
            "victoria" to TflLineStatusItem(
                id = "victoria",
                name = "Victoria",
                modeName = "tube",
                lineStatuses = listOf(
                    TflLineStatusDetail(
                        id = 1,
                        statusSeverity = 2,
                        statusSeverityDescription = "Minor Delays",
                        reason = "Signal failure"
                    )
                )
            )
        )

        val badges = StationBadgeSynthesizer.buildStationLineBadges(
            sampleStation,
            listOf(departure),
            statuses
        )

        assertEquals(1, badges.size)
        assertEquals("Victoria", badges[0].displayName)
        assertEquals(2, badges[0].statusSeverity)
        assertEquals("Minor Delays", badges[0].statusDescription)
        assertEquals("Signal failure", badges[0].disruptionReason)
    }

    @Test
    fun testBuildStationLineBadges_filtersSpuriousDepotMovementAtWembleyPark() {
        val wembleyStation = Station(
            id = "940GZZLUWYP",
            name = "Wembley Park",
            modes = listOf("tube")
        )
        val hcDepotDeparture = TestFixtures.createTestDeparture(
            lineId = "hammersmith-city",
            lineName = "Hammersmith & City",
            lineBadge = LineBadgeInfo("hammersmith-city", "Hammersmith & City", Color.Magenta, mode = TransitMode.METRO)
        )

        val badges = StationBadgeSynthesizer.buildStationLineBadges(
            wembleyStation,
            listOf(hcDepotDeparture),
            emptyMap()
        )

        // H&C should be filtered out from Wembley Park
        assertEquals(0, badges.size)
    }

    @Test
    fun testBuildStationLineBadges_includesDisruptedInferredLineWhenNoDeparturesForIt() {
        val kingsCrossStPancras = Station(
            id = "940GZZLUKSX",
            name = "King's Cross St. Pancras",
            modes = listOf("tube")
        )
        val victoriaDeparture = TestFixtures.createTestDeparture(
            lineId = "victoria",
            lineName = "Victoria",
            lineBadge = LineBadgeInfo("victoria", "Victoria", Color.Cyan, mode = TransitMode.METRO)
        )
        val statuses = mapOf(
            "victoria" to TflLineStatusItem(
                id = "victoria",
                name = "Victoria",
                modeName = "tube",
                lineStatuses = listOf(
                    TflLineStatusDetail(
                        id = 10,
                        statusSeverity = 10,
                        statusSeverityDescription = "Good Service"
                    )
                )
            ),
            "northern" to TflLineStatusItem(
                id = "northern",
                name = "Northern",
                modeName = "tube",
                lineStatuses = listOf(
                    TflLineStatusDetail(
                        id = 3,
                        statusSeverity = 3,
                        statusSeverityDescription = "Part Closure",
                        reason = "NORTHERN LINE: No service between Euston and Morden."
                    )
                )
            )
        )

        val badges = StationBadgeSynthesizer.buildStationLineBadges(
            kingsCrossStPancras,
            listOf(victoriaDeparture),
            statuses
        )

        val northernBadge = badges.firstOrNull { it.lineId == "northern" }
        org.junit.Assert.assertNotNull("Expected disrupted Northern badge at King's Cross St. Pancras", northernBadge)
        assertEquals("Part Closure", northernBadge?.statusDescription)
        assertEquals(3, northernBadge?.statusSeverity)
    }
}
