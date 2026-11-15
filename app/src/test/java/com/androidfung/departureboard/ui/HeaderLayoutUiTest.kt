package com.androidfung.departureboard.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.androidfung.departureboard.R
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TransitRegion
import com.androidfung.departureboard.test.TestFixtures
import com.androidfung.departureboard.ui.dashboard.DashboardContent
import com.androidfung.departureboard.ui.dashboard.DashboardUiState
import com.androidfung.departureboard.ui.dashboard.StationCardUiModel
import com.androidfung.departureboard.ui.feature.detail.LocalBackButtonVisibility
import com.androidfung.departureboard.ui.feature.detail.LocalIsThreePaneMode
import com.androidfung.departureboard.ui.feature.detail.LocalIsTwoPaneMode
import com.androidfung.departureboard.ui.feature.detail.StationDetailScreen
import com.androidfung.departureboard.ui.feature.detail.TrainJourneyScreen
import com.androidfung.departureboard.ui.theme.DepartureBoardTheme
import com.androidfung.departureboard.ui.theme.LocalWindowWidthClass
import com.androidfung.departureboard.ui.theme.WindowWidthClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp")
class HeaderLayoutUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun visibleInPane(paneWidthDp: Dp): SemanticsMatcher {
        val paneWidthPx = with(composeTestRule.density) { paneWidthDp.toPx() }
        return SemanticsMatcher("visibleWithinPane($paneWidthDp)") { node ->
            val bounds = node.boundsInRoot
            bounds.width > 0f && bounds.left >= -1f && bounds.right <= paneWidthPx + 1f
        }
    }

    private fun onVisibleNodeWithText(
        text: String,
        paneWidthDp: Dp,
        substring: Boolean = false
    ): SemanticsNodeInteraction =
        composeTestRule.onAllNodes(hasText(text, substring = substring))
            .filterToOne(visibleInPane(paneWidthDp))

    private fun onVisibleNodeWithContentDescription(
        desc: String,
        paneWidthDp: Dp
    ): SemanticsNodeInteraction =
        composeTestRule.onAllNodes(hasContentDescription(desc))
            .filterToOne(visibleInPane(paneWidthDp))

    @Test
    fun stationDetailThreePaneMovesPagerToTopRowAndKeepsTitleSingleLine() {
        val liverpoolStreet = TestFixtures.createTestStation(
            id = "940GZZLULVT",
            name = "Liverpool Street",
            modes = listOf("tube", "elizabeth-line", "overground", "national-rail"),
            zone = "1"
        )
        val stations = List(14) { index ->
            if (index == 11) {
                liverpoolStreet
            } else {
                TestFixtures.createTestStation(
                    id = "940GZZLU$index",
                    name = "Station $index",
                    zone = "2"
                )
            }
        }
        val departures = listOf(
            TestFixtures.createTestDeparture(
                id = "dep_1",
                stationId = liverpoolStreet.id,
                stationName = liverpoolStreet.name
            )
        )

        composeTestRule.setContent {
            DepartureBoardTheme {
                CompositionLocalProvider(
                    LocalBackButtonVisibility provides false,
                    LocalIsThreePaneMode provides true,
                    LocalIsTwoPaneMode provides false,
                    LocalWindowWidthClass provides WindowWidthClass.EXTRA_LARGE
                ) {
                    Box(modifier = Modifier.width(420.dp).height(700.dp)) {
                        StationDetailScreen(
                            station = liverpoolStreet,
                            departures = departures,
                            isLoading = false,
                            onRefresh = {},
                            onBack = {},
                            stations = stations,
                            onStationChange = {},
                            onOpenDeskClock = {}
                        )
                    }
                }
            }
        }

        val pageText = context.getString(R.string.pager_page_of_total, 12, 14)
        val mapsDesc = context.getString(R.string.cd_open_in_maps)
        val clockDesc = context.getString(R.string.cd_open_desk_clock)
        val refreshDesc = context.getString(R.string.cd_refresh_departures)
        val pinDesc = context.getString(R.string.cd_pin_to_home)
        val zonePill = context.getString(R.string.fare_zone_pill, "1")
        val paneWidth = 420.dp

        onVisibleNodeWithText(pageText, paneWidth).assertIsDisplayed()
        onVisibleNodeWithText("Liverpool Street", paneWidth).assertIsDisplayed()
        onVisibleNodeWithContentDescription(mapsDesc, paneWidth).assertIsDisplayed()
        onVisibleNodeWithContentDescription(clockDesc, paneWidth).assertIsDisplayed()
        onVisibleNodeWithContentDescription(refreshDesc, paneWidth).assertIsDisplayed()
        onVisibleNodeWithContentDescription(pinDesc, paneWidth).assertIsDisplayed()

        // Station title stays on a single line with full Row 2 width
        val titleNode = onVisibleNodeWithText("Liverpool Street", paneWidth).fetchSemanticsNode()
        val textLayoutResults = mutableListOf<TextLayoutResult>()
        titleNode.config[SemanticsActions.GetTextLayoutResult].action?.invoke(textLayoutResults)
        assertEquals(1, textLayoutResults.first().lineCount)

        // Row 1 holds pager metadata on left and actions on right, above the station title on Row 2
        val pagerBounds = onVisibleNodeWithText(pageText, paneWidth).fetchSemanticsNode().boundsInRoot
        val mapsBounds = onVisibleNodeWithContentDescription(mapsDesc, paneWidth).fetchSemanticsNode().boundsInRoot
        val titleBounds = titleNode.boundsInRoot

        assertTrue(
            "Pager metadata ($pagerBounds) should vertically share Row 1 with actions ($mapsBounds)",
            pagerBounds.top < mapsBounds.bottom && mapsBounds.top < pagerBounds.bottom
        )
        assertTrue(
            "Top bar actions ($mapsBounds) should sit above station title ($titleBounds)",
            mapsBounds.bottom <= titleBounds.top
        )

        // Zone 1 appears only once on the active page (no duplicate subtitle "Zone 1 •" + chip "Zone 1")
        composeTestRule.onAllNodes(hasText(zonePill, substring = true))
            .filter(visibleInPane(paneWidth))
            .assertCountEquals(1)
        composeTestRule.onNodeWithText("$zonePill •", substring = true).assertDoesNotExist()
    }

    @Test
    fun stationDetailHidesNaZoneInSubtitleAndMetaChips() {
        val naZoneStation: Station = TestFixtures.createTestStation(
            id = "940GZZLUOXC",
            name = "Oxford Circus",
            modes = listOf("tube"),
            zone = "NA"
        )

        composeTestRule.setContent {
            DepartureBoardTheme {
                CompositionLocalProvider(
                    LocalBackButtonVisibility provides false,
                    LocalIsThreePaneMode provides true
                ) {
                    Box(modifier = Modifier.width(420.dp).height(700.dp)) {
                        StationDetailScreen(
                            station = naZoneStation,
                            departures = emptyList(),
                            isLoading = false,
                            onRefresh = {},
                            onBack = {}
                        )
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Oxford Circus").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Zone", substring = true).assertCountEquals(0)
    }

    @Test
    fun stationDetailWideTwoPaneWithoutPagerUsesSingleRowHeader() {
        val liverpoolStreet = TestFixtures.createTestStation(
            id = "940GZZLULVT",
            name = "Liverpool Street",
            modes = listOf("tube"),
            zone = "1"
        )

        composeTestRule.setContent {
            DepartureBoardTheme {
                CompositionLocalProvider(
                    LocalBackButtonVisibility provides false,
                    LocalIsTwoPaneMode provides true,
                    LocalIsThreePaneMode provides false,
                    LocalWindowWidthClass provides WindowWidthClass.LARGE
                ) {
                    Box(modifier = Modifier.width(600.dp).height(700.dp)) {
                        StationDetailScreen(
                            station = liverpoolStreet,
                            departures = emptyList(),
                            isLoading = false,
                            onRefresh = {},
                            onBack = {},
                            stations = listOf(liverpoolStreet)
                        )
                    }
                }
            }
        }

        val mapsDesc = context.getString(R.string.cd_open_in_maps)
        val mapsBounds = composeTestRule.onNodeWithContentDescription(mapsDesc).fetchSemanticsNode().boundsInRoot
        val titleBounds = composeTestRule.onNodeWithText("Liverpool Street").fetchSemanticsNode().boundsInRoot

        assertTrue(
            "Single-row header in wide 2-pane without pager should align title ($titleBounds) and actions ($mapsBounds) on the same row",
            mapsBounds.top < titleBounds.bottom && titleBounds.top < mapsBounds.bottom
        )
    }

    @Test
    fun trainJourneyThreePaneDisplaysPagerPillAndActionsAboveTitle() {
        val departures = (1..3).map { index ->
            TestFixtures.createTestDeparture(
                id = "dep_$index",
                stationId = "940GZZLULVT",
                stationName = "Liverpool Street",
                lineName = "Elizabeth line",
                platformName = "Platform B",
                destinationName = "Maidenhead"
            )
        }

        composeTestRule.setContent {
            DepartureBoardTheme {
                CompositionLocalProvider(
                    LocalBackButtonVisibility provides false,
                    LocalIsThreePaneMode provides true,
                    LocalWindowWidthClass provides WindowWidthClass.EXTRA_LARGE
                ) {
                    Box(modifier = Modifier.width(420.dp).height(700.dp)) {
                        TrainJourneyScreen(
                            departure = departures.first(),
                            onBack = {},
                            onLoadCallingPoints = { emptyList() },
                            departures = departures,
                            onDepartureChange = {}
                        )
                    }
                }
            }
        }

        val pageText = context.getString(R.string.pager_page_of_total, 1, 3)
        val shareDesc = context.getString(R.string.cd_share_live_eta)
        val notifyDesc = context.getString(R.string.cd_start_live_notification)
        val paneWidth = 420.dp

        onVisibleNodeWithText(pageText, paneWidth).assertIsDisplayed()
        onVisibleNodeWithText("Platform B", paneWidth).assertIsDisplayed()
        onVisibleNodeWithContentDescription(shareDesc, paneWidth).assertIsDisplayed()
        onVisibleNodeWithContentDescription(notifyDesc, paneWidth).assertIsDisplayed()

        val shareBounds = onVisibleNodeWithContentDescription(shareDesc, paneWidth).fetchSemanticsNode().boundsInRoot
        val destBounds = onVisibleNodeWithText("Maidenhead", paneWidth, substring = true).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Top bar actions ($shareBounds) should sit above destination title ($destBounds)",
            shareBounds.bottom <= destBounds.top
        )
    }

    @Test
    fun dashboardThreePaneNarrowMovesUpgradeActionIntoOverflowMenu() {
        val station = TestFixtures.createTestStation(id = "940GZZLUOXC", name = "Oxford Circus")
        val uiState = DashboardUiState(
            stationCards = listOf(StationCardUiModel(station = station)),
            isInitialLoading = false,
            isPro = false,
            selectedRegion = TransitRegion.GB
        )

        composeTestRule.setContent {
            DepartureBoardTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                CompositionLocalProvider(
                    LocalIsThreePaneMode provides true,
                    LocalIsTwoPaneMode provides false,
                    LocalWindowWidthClass provides WindowWidthClass.EXTRA_LARGE
                ) {
                    Box(modifier = Modifier.width(380.dp).height(600.dp)) {
                        DashboardContent(
                            uiState = uiState,
                            snackbarHostState = snackbarHostState,
                            onRefresh = {},
                            onDismissStation = {},
                            onMoveStation = { _, _ -> },
                            onAddStationClick = {},
                            onStationClick = {}
                        )
                    }
                }
            }
        }

        val aiDesc = context.getString(R.string.dashboard_fab_ask_ai_desc)
        val statusDesc = context.getString(R.string.dashboard_live_network_status)
        val upgradeLabel = context.getString(R.string.dashboard_upgrade_to_pro)
        val moreDesc = context.getString(R.string.dashboard_more_options)

        composeTestRule.onNodeWithContentDescription(aiDesc).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(statusDesc).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(upgradeLabel).assertDoesNotExist()

        composeTestRule.onNodeWithContentDescription(moreDesc).performClick()
        composeTestRule.onNodeWithText(upgradeLabel).assertIsDisplayed()
    }

    @Test
    fun dashboardWideTwoPaneKeepsUpgradeActionInTopBar() {
        val station = TestFixtures.createTestStation(id = "940GZZLUOXC", name = "Oxford Circus")
        val uiState = DashboardUiState(
            stationCards = listOf(StationCardUiModel(station = station)),
            isInitialLoading = false,
            isPro = false,
            selectedRegion = TransitRegion.GB
        )

        composeTestRule.setContent {
            DepartureBoardTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                CompositionLocalProvider(
                    LocalIsThreePaneMode provides false,
                    LocalIsTwoPaneMode provides true,
                    LocalWindowWidthClass provides WindowWidthClass.LARGE
                ) {
                    Box(modifier = Modifier.width(480.dp).height(600.dp)) {
                        DashboardContent(
                            uiState = uiState,
                            snackbarHostState = snackbarHostState,
                            onRefresh = {},
                            onDismissStation = {},
                            onMoveStation = { _, _ -> },
                            onAddStationClick = {},
                            onStationClick = {}
                        )
                    }
                }
            }
        }

        val upgradeLabel = context.getString(R.string.dashboard_upgrade_to_pro)
        composeTestRule.onNodeWithContentDescription(upgradeLabel).assertIsDisplayed()
    }
}
