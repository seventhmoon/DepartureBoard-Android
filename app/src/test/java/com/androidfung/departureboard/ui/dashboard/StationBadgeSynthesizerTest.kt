package com.androidfung.departureboard.ui.dashboard

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TflLineStatusDetail
import com.androidfung.departureboard.data.model.TflLineStatusItem
import com.androidfung.departureboard.data.model.TransitMode
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
            lineBadge = LineBadgeInfo("victoria", "Victoria", Color.Cyan, mode = TransitMode.TUBE)
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
            lineBadge = LineBadgeInfo("hammersmith-city", "Hammersmith & City", Color.Magenta, mode = TransitMode.TUBE)
        )

        val badges = StationBadgeSynthesizer.buildStationLineBadges(
            wembleyStation,
            listOf(hcDepotDeparture),
            emptyMap()
        )

        // H&C should be filtered out from Wembley Park
        assertEquals(0, badges.size)
    }
}
