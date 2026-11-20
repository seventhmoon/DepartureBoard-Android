package com.androidfung.departureboard.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.androidfung.departureboard.billing.SubscriptionTier
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.test.TestFixtures
import com.androidfung.departureboard.ui.dashboard.StationCardUiModel
import com.androidfung.departureboard.ui.feature.deskclock.DeskClockScreen
import com.androidfung.departureboard.ui.feature.paywall.PaywallPlanSelector
import com.androidfung.departureboard.ui.feature.settings.SettingsDisplayPreferencesCard
import com.androidfung.departureboard.ui.feature.twinconcourse.TwinConcourseContent
import com.androidfung.departureboard.ui.theme.AppThemeMode
import com.androidfung.departureboard.ui.theme.DepartureBoardTheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp")
class AdaptiveFlexGridUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun twinConcourseGridSwitchesBetweenStackedAndSideBySide() {
        val oxford = TestFixtures.createTestStation(
            id = "940GZZLUOXC",
            name = "Oxford Circus",
            lat = 51.5152,
            lon = -0.1419
        )
        val bondStreet = TestFixtures.createTestStation(
            id = "940GZZLUBND",
            name = "Bond Street",
            lat = 51.5142,
            lon = -0.1494
        )
        val cards = listOf(
            StationCardUiModel(
                station = oxford,
                departures = listOf(
                    TestFixtures.createTestDeparture(
                        id = "dep_oxc",
                        stationId = oxford.id,
                        stationName = oxford.name,
                        destinationName = "Brixton"
                    )
                ),
                isLoading = false
            ),
            StationCardUiModel(
                station = bondStreet,
                departures = listOf(
                    TestFixtures.createTestDeparture(
                        id = "dep_bnd",
                        stationId = bondStreet.id,
                        stationName = bondStreet.name,
                        destinationName = "Stratford"
                    )
                ),
                isLoading = false
            )
        )

        var widthDp by mutableStateOf(360.dp)

        composeTestRule.setContent {
            DepartureBoardTheme {
                Box(modifier = Modifier.width(widthDp).height(760.dp)) {
                    TwinConcourseContent(
                        onDismissRequest = {},
                        stationCards = cards
                    )
                }
            }
        }

        // Narrow (360dp): Hub B stacks cleanly below Hub A
        val hubANarrow = composeTestRule.onNodeWithText("Hub A •", substring = true).getUnclippedBoundsInRoot()
        val hubBNarrow = composeTestRule.onNodeWithText("Hub B •", substring = true).getUnclippedBoundsInRoot()
        assertTrue(
            "Expected Hub B (${hubBNarrow.top}) below Hub A (${hubANarrow.bottom}) at 360dp",
            hubBNarrow.top >= hubANarrow.bottom
        )

        // Expanded (640dp): Hub A and Hub B sit side by side in a 2-column Grid
        composeTestRule.runOnUiThread { widthDp = 640.dp }
        composeTestRule.waitForIdle()

        val hubAWide = composeTestRule.onNodeWithText("Hub A •", substring = true).getUnclippedBoundsInRoot()
        val hubBWide = composeTestRule.onNodeWithText("Hub B •", substring = true).getUnclippedBoundsInRoot()
        assertTrue(
            "Expected Hub B (${hubBWide.left}) to the right of Hub A (${hubAWide.right}) at 640dp",
            hubBWide.left > hubAWide.right
        )
        assertTrue(
            "Expected Hub A (${hubAWide.top}) and Hub B (${hubBWide.top}) headers aligned at 640dp",
            abs(hubAWide.top.value - hubBWide.top.value) < 4f
        )
    }

    @Test
    fun settingsBottomSheetFlexBoxChipsRenderAndSelect() {
        var containerWidthDp by mutableStateOf(340.dp)
        var selectedRows by mutableIntStateOf(5)
        var selectedTheme by mutableStateOf(AppThemeMode.SYSTEM)

        composeTestRule.setContent {
            DepartureBoardTheme {
                Box(modifier = Modifier.width(containerWidthDp)) {
                    SettingsDisplayPreferencesCard(
                        departureRowsCount = selectedRows,
                        onDepartureRowsChange = { selectedRows = it },
                        themeMode = selectedTheme,
                        onThemeModeChange = { selectedTheme = it },
                        isDynamicColor = true,
                        onDynamicColorChange = {},
                        isAutoSortByLocation = false,
                        onAutoSortByLocationChange = {}
                    )
                }
            }
        }

        listOf("1", "2", "3", "4", "5", "System", "Light", "Dark").forEach { label ->
            composeTestRule.onNodeWithText(label).assertIsDisplayed()
        }

        val narrowSystemWidth = composeTestRule.onNodeWithText("System").getUnclippedBoundsInRoot().let { it.right - it.left }

        composeTestRule.onNodeWithText("3").performClick()
        composeTestRule.onNodeWithText("Dark").performClick()
        assertEquals(3, selectedRows)
        assertEquals(AppThemeMode.DARK, selectedTheme)

        // Widening the sheet stretches FlexBox chips via flex { grow(1f) }
        composeTestRule.runOnUiThread { containerWidthDp = 600.dp }
        composeTestRule.waitForIdle()

        val systemBounds = composeTestRule.onNodeWithText("System").getUnclippedBoundsInRoot()
        val lightBounds = composeTestRule.onNodeWithText("Light").getUnclippedBoundsInRoot()
        val darkBounds = composeTestRule.onNodeWithText("Dark").getUnclippedBoundsInRoot()
        val wideSystemWidth = systemBounds.right - systemBounds.left

        assertTrue(
            "Expected FlexBox chip width to grow from $narrowSystemWidth to > $narrowSystemWidth at 600dp (got $wideSystemWidth)",
            wideSystemWidth > narrowSystemWidth
        )
        assertTrue(abs(systemBounds.top.value - lightBounds.top.value) < 4f)
        assertTrue(abs(systemBounds.top.value - darkBounds.top.value) < 4f)
    }

    @Test
    fun deskClockGridAlignsHeaderColumnsWithDepartureRows() {
        composeTestRule.mainClock.autoAdvance = false

        val station = TestFixtures.createTestStation(
            id = "940GZZLUOXC",
            name = "Oxford Circus"
        )
        val departure = TestFixtures.createTestDeparture(
            id = "dep_due_1",
            stationId = station.id,
            stationName = station.name,
            lineId = "victoria",
            lineName = "Victoria",
            platformName = "Northbound - Platform 3",
            destinationName = "Walthamstow Central",
            status = DepartureStatus.DUE,
            timeToStationSeconds = 20,
            lineBadge = LineBadgeInfo(
                lineId = "victoria",
                displayName = "Victoria",
                lineCode = "VIC",
                backgroundColor = androidx.compose.ui.graphics.Color(0xFF0098D4),
                mode = TransitMode.METRO
            )
        )
        val cards = listOf(
            StationCardUiModel(station = station, departures = listOf(departure), isLoading = false)
        )

        var widthDp by mutableStateOf(360.dp)

        composeTestRule.setContent {
            DepartureBoardTheme {
                Box(modifier = Modifier.width(widthDp).height(700.dp)) {
                    DeskClockScreen(
                        station = station,
                        stationCards = cards,
                        departures = listOf(departure),
                        serviceHoursInfo = null,
                        onSelectStation = {},
                        onRefresh = {},
                        onClose = {}
                    )
                }
            }
        }

        composeTestRule.mainClock.advanceTimeBy(350L)

        // Check both 360dp narrow track widths and 720dp desktop/foldable track widths
        listOf(360.dp, 720.dp).forEach { targetWidth ->
            composeTestRule.runOnUiThread { widthDp = targetWidth }
            composeTestRule.mainClock.advanceTimeBy(100L)

            listOf("LINE", "DESTINATION", "PLAT", "TIME").forEach { header ->
                composeTestRule.onNodeWithText(header).assertIsDisplayed()
            }
            composeTestRule.onNodeWithText("VIC").assertIsDisplayed()
            composeTestRule.onNodeWithText("WALTHAMSTOW CENTRAL").assertIsDisplayed()
            composeTestRule.onNodeWithText("3").assertIsDisplayed()
            composeTestRule.onNodeWithText("DUE").assertIsDisplayed()

            val platHeader = composeTestRule.onNodeWithText("PLAT").getUnclippedBoundsInRoot()
            val platCell = composeTestRule.onNodeWithText("3").getUnclippedBoundsInRoot()
            val timeHeader = composeTestRule.onNodeWithText("TIME").getUnclippedBoundsInRoot()
            val timeCell = composeTestRule.onNodeWithText("DUE").getUnclippedBoundsInRoot()

            val platHeaderCenter = (platHeader.left.value + platHeader.right.value) / 2f
            val platCellCenter = (platCell.left.value + platCell.right.value) / 2f
            assertTrue(
                "Expected PLAT header center ($platHeaderCenter) and cell center ($platCellCenter) aligned at $targetWidth",
                abs(platHeaderCenter - platCellCenter) < 12f
            )
            assertTrue(
                "Expected TIME cell right (${timeCell.right}) aligned near TIME header right (${timeHeader.right}) at $targetWidth",
                abs(timeCell.right.value - timeHeader.right.value) < 12f
            )
        }
    }

    @Test
    fun paywallPlansSwitchDirectionResponsively() {
        var containerWidthDp by mutableStateOf(360.dp)
        var selectedPlan by mutableStateOf(SubscriptionTier.BASE_PLAN_ANNUAL)

        composeTestRule.setContent {
            DepartureBoardTheme {
                Box(modifier = Modifier.width(containerWidthDp)) {
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
        }

        val annualNarrow = composeTestRule.onNodeWithText("Annual").getUnclippedBoundsInRoot()
        val monthlyNarrow = composeTestRule.onNodeWithText("Monthly").getUnclippedBoundsInRoot()
        assertTrue(
            "Expected Monthly (${monthlyNarrow.top}) stacked below Annual (${annualNarrow.bottom}) at 360dp",
            monthlyNarrow.top > annualNarrow.bottom
        )

        composeTestRule.onNodeWithText("Monthly").performClick()
        assertEquals(SubscriptionTier.BASE_PLAN_MONTHLY, selectedPlan)

        composeTestRule.runOnUiThread { containerWidthDp = 600.dp }
        composeTestRule.waitForIdle()

        val annualWide = composeTestRule.onNodeWithText("Annual").getUnclippedBoundsInRoot()
        val monthlyWide = composeTestRule.onNodeWithText("Monthly").getUnclippedBoundsInRoot()
        assertTrue(
            "Expected Monthly (${monthlyWide.left}) placed to the right of Annual (${annualWide.right}) at 600dp",
            monthlyWide.left > annualWide.right
        )
        assertTrue(
            "Expected Annual (${annualWide.top}) and Monthly (${monthlyWide.top}) cards top-aligned at 600dp",
            abs(annualWide.top.value - monthlyWide.top.value) < 4f
        )
    }
}
