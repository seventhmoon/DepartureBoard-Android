package com.androidfung.departureboard.ui.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.test.TestFixtures
import com.androidfung.departureboard.ui.dashboard.StationCardUiModel
import com.androidfung.departureboard.ui.dashboard.StationDepartureCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StationDepartureCardUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testStationDepartureCardDisplaysStationAndDepartures() {
        val testStation = TestFixtures.createTestStation(
            id = "940GZZLUOXC",
            name = "Oxford Circus"
        )
        val dep1 = TestFixtures.createTestDeparture(
            id = "dep_1",
            destinationName = "Brixton",
            lineName = "Victoria",
            lineId = "victoria",
            status = DepartureStatus.ON_TIME,
            timeToStationSeconds = 120
        )
        val dep2 = TestFixtures.createTestDeparture(
            id = "dep_2",
            destinationName = "Epping",
            lineName = "Central",
            lineId = "central",
            status = DepartureStatus.DUE,
            timeToStationSeconds = 30
        )

        val cardModel = StationCardUiModel(
            station = testStation,
            departures = listOf(dep1, dep2),
            isLoading = false
        )

        var clickedStation: Station? = null

        composeTestRule.setContent {
            StationDepartureCard(
                cardModel = cardModel,
                onDismissStation = {},
                onStationClick = { clickedStation = it }
            )
        }

        // Verify station name is rendered
        composeTestRule.onNodeWithText("Oxford Circus").assertIsDisplayed()

        // Verify departure destinations
        composeTestRule.onNodeWithText("Brixton").assertIsDisplayed()
        composeTestRule.onNodeWithText("Epping").assertIsDisplayed()

        // Verify status / countdown badge texts
        composeTestRule.onNodeWithText("DUE").assertIsDisplayed()
        composeTestRule.onNodeWithText("2").assertIsDisplayed()

        // Click station card
        composeTestRule.onNodeWithText("Oxford Circus").performClick()
        assertNotNull(clickedStation)
        assertEquals("Oxford Circus", clickedStation?.name)
    }

    @Test
    fun testHkBusDepartureCardRendering() {
        val hkStation = TestFixtures.createTestStation(
            id = "HK_BUS_KMB_001554",
            name = "Cross Harbour Tunnel Toll Plaza"
        )
        val hkDeparture = TestFixtures.createTestDeparture(
            id = "dep_hk_1",
            stationId = "HK_BUS_KMB_001554",
            destinationName = "Central (Hong Kong Station)",
            lineName = "104",
            lineId = "104",
            status = DepartureStatus.ON_TIME,
            timeToStationSeconds = 300
        )

        val cardModel = StationCardUiModel(
            station = hkStation,
            departures = listOf(hkDeparture),
            isLoading = false
        )

        composeTestRule.setContent {
            StationDepartureCard(
                cardModel = cardModel,
                onDismissStation = {}
            )
        }

        composeTestRule.onNodeWithText("Cross Harbour Tunnel Toll Plaza").assertIsDisplayed()
        composeTestRule.onNodeWithText("Central (Hong Kong Station)").assertIsDisplayed()
        composeTestRule.onNodeWithText("5").assertIsDisplayed()
        composeTestRule.onNodeWithText("min").assertIsDisplayed()
    }

    @Test
    fun testMultipleDisruptionsRenderCollapsibleSummaryAndExpands() {
        val testStation = TestFixtures.createTestStation(
            id = "940GZZLUKSX",
            name = "King's Cross St. Pancras"
        )
        val badge1 = com.androidfung.departureboard.data.model.LineBadgeInfo(
            lineId = "victoria",
            displayName = "Victoria",
            backgroundColor = androidx.compose.ui.graphics.Color(0xFF0098D4),
            statusSeverity = 9,
            statusDescription = "Minor Delays",
            disruptionReason = "Signal failure at Finsbury Park"
        )
        val badge2 = com.androidfung.departureboard.data.model.LineBadgeInfo(
            lineId = "northern",
            displayName = "Northern",
            backgroundColor = androidx.compose.ui.graphics.Color(0xFF000000),
            statusSeverity = 6,
            statusDescription = "Severe Delays",
            disruptionReason = "Points failure at Camden Town"
        )

        val cardModel = StationCardUiModel(
            station = testStation,
            departures = emptyList(),
            availableLineBadges = listOf(badge1, badge2),
            isLoading = false
        )

        composeTestRule.setContent {
            StationDepartureCard(
                cardModel = cardModel,
                onDismissStation = {}
            )
        }

        // Summary row displayed with count (%1$d Line Disruptions)
        composeTestRule.onNodeWithText("2 Line Disruptions", substring = true).assertIsDisplayed()

        // Click summary row to expand
        composeTestRule.onNodeWithText("2 Line Disruptions", substring = true).performClick()

        // Both lines' details are now displayed
        composeTestRule.onNodeWithText("Victoria: Minor Delays", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Northern: Severe Delays", substring = true).assertIsDisplayed()
    }
}
