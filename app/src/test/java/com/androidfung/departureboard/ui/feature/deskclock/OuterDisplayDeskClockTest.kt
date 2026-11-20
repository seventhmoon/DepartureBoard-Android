package com.androidfung.departureboard.ui.feature.deskclock

import android.content.Context
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.test.TestFixtures
import com.androidfung.departureboard.ui.theme.DepartureBoardTheme
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp")
class OuterDisplayDeskClockTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun resolveOuterDisplayMode_prefersPresentationAndFallsBackToTransfer() {
        assertEquals(
            OuterDisplayMode.PRESENTATION,
            resolveOuterDisplayMode(RearAreaCapabilityStatus.AVAILABLE, RearAreaCapabilityStatus.AVAILABLE)
        )
        assertEquals(
            OuterDisplayMode.PRESENTATION,
            resolveOuterDisplayMode(RearAreaCapabilityStatus.ACTIVE, RearAreaCapabilityStatus.UNAVAILABLE)
        )
        assertEquals(
            OuterDisplayMode.TRANSFER,
            resolveOuterDisplayMode(RearAreaCapabilityStatus.UNAVAILABLE, RearAreaCapabilityStatus.AVAILABLE)
        )
        assertEquals(
            OuterDisplayMode.TRANSFER,
            resolveOuterDisplayMode(RearAreaCapabilityStatus.UNSUPPORTED, RearAreaCapabilityStatus.ACTIVE)
        )
        assertEquals(
            OuterDisplayMode.UNAVAILABLE,
            resolveOuterDisplayMode(RearAreaCapabilityStatus.UNAVAILABLE, RearAreaCapabilityStatus.UNSUPPORTED)
        )
    }

    @Test
    fun controller_observeAndTogglePresentation_activatesAndClosesCleanly() = runTest {
        val bridge = FakeRearDisplayBridge()
        bridge.availabilityFlow.value = RearAreaAvailability(
            presentStatus = RearAreaCapabilityStatus.AVAILABLE,
            transferStatus = RearAreaCapabilityStatus.UNAVAILABLE
        )
        val controller = OuterDisplayDeskClockController(bridge)
        val job = launch { controller.observeRearDisplay() }

        val detected = controller.uiState.first { it.mode == OuterDisplayMode.PRESENTATION }
        assertEquals(OuterDisplayMode.PRESENTATION, detected.mode)
        assertFalse(detected.isActive)

        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        controller.toggleOuterDisplay(activity) { {} }

        assertTrue(controller.uiState.value.isActive)
        assertTrue(controller.uiState.value.isVisibleOnOuterDisplay)
        assertNotNull(bridge.attachedView)

        // Toggle off closes active session cleanly
        controller.toggleOuterDisplay(activity) { {} }
        assertFalse(controller.uiState.value.isActive)
        assertTrue(bridge.closedCount == 1)

        job.cancel()
    }

    @Test
    fun controller_catchesSecurityExceptionAndBadTokenExceptionDefensively() = runTest {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

        val secBridge = FakeRearDisplayBridge(
            throwOnStart = SecurityException("OEM rear display denied")
        ).apply {
            availabilityFlow.value = RearAreaAvailability(presentStatus = RearAreaCapabilityStatus.AVAILABLE)
        }
        val secController = OuterDisplayDeskClockController(secBridge)
        val secJob = launch { secController.observeRearDisplay() }
        secController.uiState.first { it.mode == OuterDisplayMode.PRESENTATION }

        secController.toggleOuterDisplay(activity) { {} }
        assertFalse(secController.uiState.value.isActive)
        assertTrue(secController.uiState.value.lastErrorMessage?.contains("denied") == true)
        secController.dispose()
        secJob.cancel()

        val badTokenBridge = FakeRearDisplayBridge(
            throwOnStart = WindowManager.BadTokenException("Rear display token invalid")
        ).apply {
            availabilityFlow.value = RearAreaAvailability(presentStatus = RearAreaCapabilityStatus.AVAILABLE)
        }
        val badTokenController = OuterDisplayDeskClockController(badTokenBridge)
        val badJob = launch { badTokenController.observeRearDisplay() }
        badTokenController.uiState.first { it.mode == OuterDisplayMode.PRESENTATION }

        badTokenController.toggleOuterDisplay(activity) { {} }
        assertFalse(badTokenController.uiState.value.isActive)
        assertTrue(badTokenController.uiState.value.lastErrorMessage?.contains("invalid") == true)
        badTokenController.dispose()
        badJob.cancel()
    }

    @Test
    fun controller_disposeClosesActiveTransferSession() = runTest {
        val bridge = FakeRearDisplayBridge().apply {
            availabilityFlow.value = RearAreaAvailability(
                presentStatus = RearAreaCapabilityStatus.UNSUPPORTED,
                transferStatus = RearAreaCapabilityStatus.AVAILABLE
            )
        }
        val controller = OuterDisplayDeskClockController(bridge)
        val job = launch { controller.observeRearDisplay() }
        controller.uiState.first { it.mode == OuterDisplayMode.TRANSFER }

        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        controller.toggleOuterDisplay(activity) { {} }
        assertTrue(controller.uiState.value.isActive)

        controller.dispose()
        assertFalse(controller.uiState.value.isActive)
        assertEquals(1, bridge.closedCount)
        job.cancel()
    }

    @Test
    fun outerDisplayConcourseBoard_rendersOnCompactCoverScreen() {
        val station = TestFixtures.createTestStation(
            id = "940GZZLUKSX",
            name = "King's Cross St. Pancras"
        )
        val departures = listOf(
            TestFixtures.createTestDeparture(
                id = "d1",
                stationId = station.id,
                destinationName = "Brixton",
                status = DepartureStatus.DUE,
                timeToStationSeconds = 20,
                lineBadge = LineBadgeInfo(
                    lineId = "victoria",
                    displayName = "Victoria",
                    lineCode = "VIC",
                    backgroundColor = Color(0xFF0098D4),
                    mode = TransitMode.METRO
                )
            ),
            TestFixtures.createTestDeparture(
                id = "d2",
                stationId = station.id,
                destinationName = "Cockfosters",
                status = DepartureStatus.ON_TIME,
                timeToStationSeconds = 180,
                lineBadge = LineBadgeInfo(
                    lineId = "piccadilly",
                    displayName = "Piccadilly",
                    lineCode = "PIC",
                    backgroundColor = Color(0xFF003688),
                    mode = TransitMode.METRO
                )
            )
        )
        val snapshot = OuterDisplayBoardSnapshot(
            station = station,
            departures = departures,
            serviceHoursInfo = null,
            currentTime = ZonedDateTime.of(2026, 11, 20, 8, 42, 10, 0, ZoneId.of("Europe/London")),
            boardStyle = DeskClockBoardStyle.SPLIT_FLAP,
            brightnessMultiplier = 1f
        )

        composeTestRule.setContent {
            DepartureBoardTheme(darkTheme = true, dynamicColor = false) {
                Box(modifier = Modifier.width(240.dp).height(240.dp)) {
                    OuterDisplayConcourseBoard(snapshot = snapshot)
                }
            }
        }

        composeTestRule.onNodeWithText("KING'S CROSS ST. PANCRAS").assertIsDisplayed()
        composeTestRule.onNodeWithText("08:42").assertIsDisplayed()
        composeTestRule.onNodeWithText("VIC").assertIsDisplayed()
        composeTestRule.onNodeWithText("BRIXTON").assertIsDisplayed()
        composeTestRule.onNodeWithText("DUE").assertIsDisplayed()
        composeTestRule.onNodeWithText("PIC").assertIsDisplayed()
        composeTestRule.onNodeWithText("COCKFOSTERS").assertIsDisplayed()
    }

    private class FakeRearDisplayBridge(
        private val throwOnStart: Throwable? = null
    ) : RearDisplayBridge {
        val availabilityFlow = MutableStateFlow(RearAreaAvailability())
        var attachedView: View? = null
        var closedCount: Int = 0

        override fun observeAvailability(): Flow<RearAreaAvailability> = availabilityFlow

        override fun startPresentation(
            activity: ComponentActivity,
            onStarted: (RearPresentationHandle) -> Unit,
            onVisibilityChanged: (Boolean) -> Unit,
            onEnded: (Throwable?) -> Unit
        ) {
            throwOnStart?.let { throw it }
            val handle = object : RearPresentationHandle {
                override val context: Context = activity
                override val window: Window? = null
                override fun setContentView(view: View) {
                    attachedView = view
                }

                override fun close() {
                    closedCount++
                }
            }
            onStarted(handle)
        }

        override fun startTransfer(
            activity: ComponentActivity,
            onStarted: (RearSessionHandle) -> Unit,
            onEnded: (Throwable?) -> Unit
        ) {
            throwOnStart?.let { throw it }
            val handle = object : RearSessionHandle {
                override fun close() {
                    closedCount++
                }
            }
            onStarted(handle)
        }
    }
}
