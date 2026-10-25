package com.androidfung.departureboard.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.ui.components.EmptyDashboardState
import com.androidfung.departureboard.ui.components.StationDepartureCard
import com.androidfung.departureboard.ui.dashboard.StationCardUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardScreenUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testEmptyDashboardStateOnDevice() {
        var addStationClicked = false

        composeTestRule.setContent {
            EmptyDashboardState(
                onAddStationClick = { addStationClicked = true }
            )
        }

        composeTestRule.onNodeWithText("No Saved Stations").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add Station").assertIsDisplayed().performClick()

        assertTrue(addStationClicked)
    }

    @Test
    fun testStationDepartureCardOnDevice() {
        val testStation = AndroidTestFixtures.createTestStation(
            id = "940GZZLUOXC",
            name = "Oxford Circus"
        )
        val dep = AndroidTestFixtures.createTestDeparture(
            id = "dep_test_device",
            stationId = testStation.id,
            destinationName = "Brixton",
            lineName = "Victoria",
            status = DepartureStatus.DUE
        )

        val cardModel = StationCardUiModel(
            station = testStation,
            departures = listOf(dep),
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

        composeTestRule.onNodeWithText("Oxford Circus").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithText("Brixton").assertIsDisplayed()
        composeTestRule.onNodeWithText("DUE").assertIsDisplayed()

        assertNotNull(clickedStation)
        assertEquals("Oxford Circus", clickedStation?.name)
    }
}
