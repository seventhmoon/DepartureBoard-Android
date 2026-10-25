package com.androidfung.departureboard.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EmptyDashboardStateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testEmptyDashboardStateDisplaysMessageAndButton() {
        var clicked = false

        composeTestRule.setContent {
            EmptyDashboardState(
                onAddStationClick = { clicked = true }
            )
        }

        composeTestRule.onNodeWithText("No Saved Stations").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add Station").assertIsDisplayed().performClick()

        assertTrue(clicked)
    }
}
