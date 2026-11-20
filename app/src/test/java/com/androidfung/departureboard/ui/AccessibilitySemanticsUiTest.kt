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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.androidfung.departureboard.billing.SubscriptionTier
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.test.TestFixtures
import com.androidfung.departureboard.ui.dashboard.StationCardUiModel
import com.androidfung.departureboard.ui.dashboard.StationDepartureCard
import com.androidfung.departureboard.ui.designsystem.EmptyDashboardState
import com.androidfung.departureboard.ui.feature.deskclock.DeskClockScreen
import com.androidfung.departureboard.ui.feature.paywall.PaywallPlanSelector
import com.androidfung.departureboard.ui.feature.settings.SettingsDisplayPreferencesCard
import com.androidfung.departureboard.ui.theme.AppThemeMode
import com.androidfung.departureboard.ui.theme.DepartureBoardTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp")
class AccessibilitySemanticsUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun settingsDisplayPreferencesCard_exposesLabeledSwitchesAndMeetsTouchTargets() {
        var rows by mutableIntStateOf(5)
        var theme by mutableStateOf(AppThemeMode.SYSTEM)
        var dynamicColor by mutableStateOf(true)
        var autoSort by mutableStateOf(false)
        var minHeightPx = 0f

        composeTestRule.setContent {
            minHeightPx = with(LocalDensity.current) { 48.dp.toPx() }
            DepartureBoardTheme {
                SettingsDisplayPreferencesCard(
                    departureRowsCount = rows,
                    onDepartureRowsChange = { rows = it },
                    themeMode = theme,
                    onThemeModeChange = { theme = it },
                    isDynamicColor = dynamicColor,
                    onDynamicColorChange = { dynamicColor = it },
                    isAutoSortByLocation = autoSort,
                    onAutoSortByLocationChange = { autoSort = it }
                )
            }
        }

        // Verify interactive nodes all have speakable labels
        assertEveryInteractiveNodeHasSpeakableLabel()

        // Verify Smart Location Auto-Sort exposes Role.Switch, speakable label, and >= 48dp height
        val autoSortNode = composeTestRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch) and
                SemanticsMatcher("has Smart Location Auto-Sort text") { node ->
                    node.speakableText().contains("Smart Location Auto-Sort")
                }
        )
        autoSortNode.assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off)
        )
        val autoSortBounds = autoSortNode.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Expected auto-sort switch height >= $minHeightPx px, got ${autoSortBounds.height}",
            autoSortBounds.height >= minHeightPx - 1f
        )

        autoSortNode.performClick()
        assertTrue(autoSort)
        autoSortNode.assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On)
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val dynamicNode = composeTestRule.onNode(
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch) and
                    SemanticsMatcher("has Dynamic Color text") { node ->
                        node.speakableText().contains("Dynamic Color")
                    }
            )
            dynamicNode.performClick()
            assertEquals(false, dynamicColor)
        }
    }

    @Test
    fun deskClockScreen_controlsHaveLabelsAndAccessibleTouchTargets() {
        composeTestRule.mainClock.autoAdvance = false

        val station = TestFixtures.createTestStation()
        val dep = TestFixtures.createTestDeparture(
            lineBadge = LineBadgeInfo(
                lineId = "victoria",
                displayName = "Victoria",
                lineCode = "VIC",
                backgroundColor = Color(0xFF0098D4),
                mode = TransitMode.METRO
            ),
            status = DepartureStatus.DUE
        )
        var minTouchPx = 0f

        composeTestRule.setContent {
            minTouchPx = with(LocalDensity.current) { 48.dp.toPx() }
            DepartureBoardTheme {
                Box(modifier = Modifier.width(412.dp).height(800.dp)) {
                    DeskClockScreen(
                        station = station,
                        stationCards = listOf(StationCardUiModel(station = station, departures = listOf(dep), isLoading = false)),
                        departures = listOf(dep),
                        serviceHoursInfo = null,
                        onSelectStation = {},
                        onRefresh = {},
                        onClose = {}
                    )
                }
            }
        }

        composeTestRule.mainClock.advanceTimeBy(350L)

        val closeNode = composeTestRule.onNodeWithContentDescription("Close").fetchSemanticsNode()
        assertTrue(
            "Expected Close button >= 48dp ($minTouchPx px), got ${closeNode.boundsInRoot.width}x${closeNode.boundsInRoot.height}",
            closeNode.boundsInRoot.width >= minTouchPx - 1f && closeNode.boundsInRoot.height >= minTouchPx - 1f
        )

        val dimmerNode = composeTestRule.onNodeWithText("Night Dimmer").fetchSemanticsNode()
        assertTrue(
            "Expected Night Dimmer chip height >= 48dp ($minTouchPx px), got ${dimmerNode.boundsInRoot.height}",
            dimmerNode.boundsInRoot.height >= minTouchPx - 1f
        )
    }

    @Test
    fun emptyDashboardState_hasSpeakableInteractiveLabels() {
        composeTestRule.setContent {
            DepartureBoardTheme {
                EmptyDashboardState(onAddStationClick = {})
            }
        }
        assertEveryInteractiveNodeHasSpeakableLabel()
    }

    @Test
    fun stationDepartureCard_hasSpeakableInteractiveLabels() {
        val station = TestFixtures.createTestStation()
        val dep = TestFixtures.createTestDeparture()

        composeTestRule.setContent {
            DepartureBoardTheme {
                StationDepartureCard(
                    cardModel = StationCardUiModel(station = station, departures = listOf(dep), isLoading = false),
                    onDismissStation = {},
                    onStationClick = {}
                )
            }
        }
        assertEveryInteractiveNodeHasSpeakableLabel()
    }

    @Test
    fun paywallPlanSelector_hasSpeakableInteractiveLabels() {
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
                    selectedPlanType = SubscriptionTier.BASE_PLAN_ANNUAL,
                    onSelectPlan = {}
                )
            }
        }
        assertEveryInteractiveNodeHasSpeakableLabel()
    }

    private fun assertEveryInteractiveNodeHasSpeakableLabel() {
        val interactiveNodes = composeTestRule.onAllNodes(hasClickAction(), useUnmergedTree = false)
            .fetchSemanticsNodes()
        assertTrue("Expected at least one interactive node", interactiveNodes.isNotEmpty())
        interactiveNodes.forEach { node ->
            val label = node.speakableText()
            assertTrue(
                "Interactive node at ${node.boundsInRoot} is missing speakable Text or ContentDescription",
                label.isNotBlank()
            )
        }
        // Also ensure root tree exists cleanly
        composeTestRule.onRoot().fetchSemanticsNode()
    }

    private fun SemanticsNode.speakableText(): String {
        val text = config.getOrNull(SemanticsProperties.Text)
            ?.joinToString(" ") { it.text }
            .orEmpty()
        val editable = config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
        val contentDesc = config.getOrNull(SemanticsProperties.ContentDescription)
            ?.joinToString(" ")
            .orEmpty()
        val descendantText = children.joinToString(" ") { it.speakableText() }
        return listOf(contentDesc, text, editable, descendantText)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
    }
}
