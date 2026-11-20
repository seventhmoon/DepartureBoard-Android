package com.androidfung.departureboard.ui

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.androidfung.departureboard.billing.SubscriptionTier
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.ui.dashboard.StationCardUiModel
import com.androidfung.departureboard.ui.dashboard.StationDepartureCard
import com.androidfung.departureboard.ui.designsystem.EmptyDashboardState
import com.androidfung.departureboard.ui.feature.deskclock.DeskClockScreen
import com.androidfung.departureboard.ui.feature.paywall.PaywallPlanSelector
import com.androidfung.departureboard.ui.feature.settings.SettingsDisplayPreferencesCard
import com.androidfung.departureboard.ui.feature.twinconcourse.TwinConcourseContent
import com.androidfung.departureboard.ui.theme.AppThemeMode
import com.androidfung.departureboard.ui.theme.DepartureBoardTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class AccessibilityChecksAndroidTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        composeTestRule.enableAccessibilityChecks()
    }

    @Test
    fun emptyDashboardState_passesAccessibilityChecks() {
        var clicked = false
        composeTestRule.setContent {
            DepartureBoardTheme {
                EmptyDashboardState(onAddStationClick = { clicked = true })
            }
        }
        composeTestRule.onNodeWithText("Add Station").assertIsDisplayed().performClick()
        assertTrue(clicked)
    }

    @Test
    fun stationDepartureCard_passesAccessibilityChecks() {
        val station = AndroidTestFixtures.createTestStation()
        val dep = AndroidTestFixtures.createTestDeparture()
        var clicked = false

        composeTestRule.setContent {
            DepartureBoardTheme {
                StationDepartureCard(
                    cardModel = StationCardUiModel(station = station, departures = listOf(dep), isLoading = false),
                    onDismissStation = {},
                    onStationClick = { clicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Oxford Circus").performClick()
        assertTrue(clicked)
    }

    @Test
    fun settingsDisplayPreferencesCard_passesAccessibilityChecks() {
        var rows by mutableIntStateOf(5)
        var theme by mutableStateOf(AppThemeMode.SYSTEM)
        var autoSort by mutableStateOf(false)

        composeTestRule.setContent {
            DepartureBoardTheme {
                SettingsDisplayPreferencesCard(
                    departureRowsCount = rows,
                    onDepartureRowsChange = { rows = it },
                    themeMode = theme,
                    onThemeModeChange = { theme = it },
                    isDynamicColor = true,
                    onDynamicColorChange = {},
                    isAutoSortByLocation = autoSort,
                    onAutoSortByLocationChange = { autoSort = it }
                )
            }
        }

        composeTestRule.onNodeWithText("Dark").performClick()
        composeTestRule.onNodeWithText("Smart Location Auto-Sort").performClick()
        assertEquals(AppThemeMode.DARK, theme)
        assertTrue(autoSort)
    }

    @Test
    fun paywallPlanSelector_passesAccessibilityChecks() {
        var selectedPlan by mutableStateOf(SubscriptionTier.BASE_PLAN_ANNUAL)

        composeTestRule.setContent {
            DepartureBoardTheme {
                PaywallPlanSelector(
                    annualTitle = "Annual",
                    annualSubtitle = "Best value",
                    annualPrice = "£19.99/yr",
                    annualTag = "SAVE 40%",
                    monthlyTitle = "Monthly",
                    monthlySubtitle = "Flexible",
                    monthlyPrice = "£2.99/mo",
                    selectedPlanType = selectedPlan,
                    onSelectPlan = { selectedPlan = it }
                )
            }
        }

        composeTestRule.onNodeWithText("Monthly").performClick()
        assertEquals(SubscriptionTier.BASE_PLAN_MONTHLY, selectedPlan)
    }

    @Test
    fun twinConcourseContent_passesAccessibilityChecks() {
        val oxford = AndroidTestFixtures.createTestStation(id = "940GZZLUOXC", name = "Oxford Circus")
        val bondStreet = AndroidTestFixtures.createTestStation(id = "940GZZLUBND", name = "Bond Street")
        val cards = listOf(
            StationCardUiModel(
                station = oxford,
                departures = listOf(
                    AndroidTestFixtures.createTestDeparture(id = "dep_oxc", stationId = oxford.id, stationName = oxford.name)
                ),
                isLoading = false
            ),
            StationCardUiModel(
                station = bondStreet,
                departures = listOf(
                    AndroidTestFixtures.createTestDeparture(id = "dep_bnd", stationId = bondStreet.id, stationName = bondStreet.name)
                ),
                isLoading = false
            )
        )
        var dismissed = false

        composeTestRule.setContent {
            DepartureBoardTheme {
                Box(modifier = Modifier.width(640.dp).height(760.dp)) {
                    TwinConcourseContent(
                        onDismissRequest = { dismissed = true },
                        stationCards = cards
                    )
                }
            }
        }

        composeTestRule.onNodeWithContentDescription("Close").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun deskClockScreen_passesAccessibilityChecks() {
        composeTestRule.mainClock.autoAdvance = false

        val station = AndroidTestFixtures.createTestStation()
        val dep = AndroidTestFixtures.createTestDeparture().copy(
            lineBadge = LineBadgeInfo(
                lineId = "victoria",
                displayName = "Victoria",
                lineCode = "VIC",
                backgroundColor = Color(0xFF0098D4),
                mode = TransitMode.METRO
            ),
            status = DepartureStatus.DUE
        )
        var closed = false

        composeTestRule.setContent {
            DepartureBoardTheme {
                DeskClockScreen(
                    station = station,
                    stationCards = listOf(StationCardUiModel(station = station, departures = listOf(dep), isLoading = false)),
                    departures = listOf(dep),
                    serviceHoursInfo = null,
                    onSelectStation = {},
                    onRefresh = {},
                    onClose = { closed = true }
                )
            }
        }

        composeTestRule.mainClock.advanceTimeBy(350L)
        composeTestRule.onNodeWithText("Night Dimmer").performClick()
        composeTestRule.onNodeWithContentDescription("Close").performClick()
        assertTrue(closed)
    }
}
