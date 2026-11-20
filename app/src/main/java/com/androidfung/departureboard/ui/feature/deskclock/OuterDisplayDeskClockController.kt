package com.androidfung.departureboard.ui.feature.deskclock

import android.content.Context
import android.os.Binder
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.window.area.WindowAreaCapability
import androidx.window.area.WindowAreaController
import androidx.window.area.WindowAreaInfo
import androidx.window.area.WindowAreaPresentationSessionCallback
import androidx.window.area.WindowAreaSession
import androidx.window.area.WindowAreaSessionCallback
import androidx.window.area.WindowAreaSessionPresenter
import androidx.window.core.ExperimentalWindowApi
import com.androidfung.departureboard.R
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.StationServiceHoursInfo
import com.androidfung.departureboard.data.model.TransitRegion
import com.androidfung.departureboard.ui.theme.DepartureBoardTheme
import com.androidfung.departureboard.ui.theme.LocalIsBilingualEnabled
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

enum class OuterDisplayMode {
    UNAVAILABLE,
    PRESENTATION,
    TRANSFER
}

internal enum class RearAreaCapabilityStatus {
    UNSUPPORTED,
    UNAVAILABLE,
    AVAILABLE,
    ACTIVE
}

internal data class RearAreaAvailability(
    val presentStatus: RearAreaCapabilityStatus = RearAreaCapabilityStatus.UNSUPPORTED,
    val transferStatus: RearAreaCapabilityStatus = RearAreaCapabilityStatus.UNSUPPORTED
)

@Immutable
data class OuterDisplayUiState(
    val mode: OuterDisplayMode = OuterDisplayMode.UNAVAILABLE,
    val isActive: Boolean = false,
    val isVisibleOnOuterDisplay: Boolean = false,
    val lastErrorMessage: String? = null
)

@Immutable
data class OuterDisplayBoardSnapshot(
    val station: Station,
    val departures: List<Departure>,
    val serviceHoursInfo: StationServiceHoursInfo?,
    val currentTime: ZonedDateTime,
    val boardStyle: DeskClockBoardStyle,
    val brightnessMultiplier: Float,
    val isBilingualEnabled: Boolean = true
)

internal fun resolveOuterDisplayMode(
    presentStatus: RearAreaCapabilityStatus,
    transferStatus: RearAreaCapabilityStatus
): OuterDisplayMode = when {
    presentStatus == RearAreaCapabilityStatus.AVAILABLE ||
        presentStatus == RearAreaCapabilityStatus.ACTIVE -> OuterDisplayMode.PRESENTATION
    transferStatus == RearAreaCapabilityStatus.AVAILABLE ||
        transferStatus == RearAreaCapabilityStatus.ACTIVE -> OuterDisplayMode.TRANSFER
    else -> OuterDisplayMode.UNAVAILABLE
}

internal interface RearSessionHandle {
    fun close()
}

internal interface RearPresentationHandle : RearSessionHandle {
    val context: Context
    val window: Window?
    fun setContentView(view: View)
}

internal interface RearDisplayBridge {
    fun observeAvailability(): Flow<RearAreaAvailability>

    fun startPresentation(
        activity: ComponentActivity,
        onStarted: (RearPresentationHandle) -> Unit,
        onVisibilityChanged: (Boolean) -> Unit,
        onEnded: (Throwable?) -> Unit
    )

    fun startTransfer(
        activity: ComponentActivity,
        onStarted: (RearSessionHandle) -> Unit,
        onEnded: (Throwable?) -> Unit
    )
}

@OptIn(ExperimentalWindowApi::class)
internal class WindowAreaRearDisplayBridge : RearDisplayBridge {

    @Volatile
    private var latestRearToken: Binder? = null

    private val controller: WindowAreaController? by lazy {
        runCatching { WindowAreaController.getOrCreate() }.getOrNull()
    }

    override fun observeAvailability(): Flow<RearAreaAvailability> {
        val activeController = controller ?: return flowOf(RearAreaAvailability())
        return activeController.windowAreaInfos
            .map { infos ->
                val rearInfo = infos.firstOrNull { it.type == WindowAreaInfo.Type.TYPE_REAR_FACING }
                latestRearToken = rearInfo?.token
                if (rearInfo == null) {
                    RearAreaAvailability()
                } else {
                    RearAreaAvailability(
                        presentStatus = rearInfo
                            .getCapability(WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA)
                            .status
                            .toBridgeStatus(),
                        transferStatus = rearInfo
                            .getCapability(WindowAreaCapability.Operation.OPERATION_TRANSFER_ACTIVITY_TO_AREA)
                            .status
                            .toBridgeStatus()
                    )
                }
            }
            .catch { emit(RearAreaAvailability()) }
    }

    override fun startPresentation(
        activity: ComponentActivity,
        onStarted: (RearPresentationHandle) -> Unit,
        onVisibilityChanged: (Boolean) -> Unit,
        onEnded: (Throwable?) -> Unit
    ) {
        val activeController = controller
        val token = latestRearToken
        if (activeController == null || token == null) {
            onEnded(IllegalStateException("Rear display is not currently available"))
            return
        }

        try {
            activeController.presentContentOnWindowArea(
                token = token,
                activity = activity,
                executor = ContextCompat.getMainExecutor(activity),
                windowAreaPresentationSessionCallback = object : WindowAreaPresentationSessionCallback {
                    override fun onSessionStarted(session: WindowAreaSessionPresenter) {
                        onStarted(
                            object : RearPresentationHandle {
                                override val context: Context get() = session.context
                                override val window: Window? get() = session.window
                                override fun setContentView(view: View) {
                                    session.setContentView(view)
                                }

                                override fun close() {
                                    session.close()
                                }
                            }
                        )
                    }

                    override fun onSessionEnded(t: Throwable?) {
                        onEnded(t)
                    }

                    override fun onContainerVisibilityChanged(isVisible: Boolean) {
                        onVisibilityChanged(isVisible)
                    }
                }
            )
        } catch (t: Throwable) {
            onEnded(t)
        }
    }

    override fun startTransfer(
        activity: ComponentActivity,
        onStarted: (RearSessionHandle) -> Unit,
        onEnded: (Throwable?) -> Unit
    ) {
        val activeController = controller
        val token = latestRearToken
        if (activeController == null || token == null) {
            onEnded(IllegalStateException("Rear display is not currently available"))
            return
        }

        try {
            activeController.transferActivityToWindowArea(
                token = token,
                activity = activity,
                executor = ContextCompat.getMainExecutor(activity),
                windowAreaSessionCallback = object : WindowAreaSessionCallback {
                    override fun onSessionStarted(session: WindowAreaSession) {
                        onStarted(
                            object : RearSessionHandle {
                                override fun close() {
                                    session.close()
                                }
                            }
                        )
                    }

                    override fun onSessionEnded(t: Throwable?) {
                        onEnded(t)
                    }
                }
            )
        } catch (t: Throwable) {
            onEnded(t)
        }
    }

    private fun WindowAreaCapability.Status.toBridgeStatus(): RearAreaCapabilityStatus = when (this) {
        WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE -> RearAreaCapabilityStatus.AVAILABLE
        WindowAreaCapability.Status.WINDOW_AREA_STATUS_ACTIVE -> RearAreaCapabilityStatus.ACTIVE
        WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNAVAILABLE -> RearAreaCapabilityStatus.UNAVAILABLE
        else -> RearAreaCapabilityStatus.UNSUPPORTED
    }
}

/**
 * Manages flip-foldable / dual-screen rear display projection for [DeskClockScreen] using
 * AndroidX Window's [WindowAreaController], falling back safely across OEM restrictions.
 */
class OuterDisplayDeskClockController internal constructor(
    private val bridge: RearDisplayBridge = WindowAreaRearDisplayBridge()
) {
    constructor() : this(WindowAreaRearDisplayBridge())

    private val _uiState = MutableStateFlow(OuterDisplayUiState())
    val uiState: StateFlow<OuterDisplayUiState> = _uiState.asStateFlow()

    private var activeSession: RearSessionHandle? = null

    suspend fun observeRearDisplay() {
        runCatching {
            bridge.observeAvailability().collect { availability ->
                val resolvedMode = resolveOuterDisplayMode(
                    presentStatus = availability.presentStatus,
                    transferStatus = availability.transferStatus
                )
                val isCapabilityActive =
                    availability.presentStatus == RearAreaCapabilityStatus.ACTIVE ||
                        availability.transferStatus == RearAreaCapabilityStatus.ACTIVE
                _uiState.update { current ->
                    current.copy(
                        mode = resolvedMode,
                        isActive = if (activeSession != null) true else isCapabilityActive
                    )
                }
            }
        }
    }

    fun toggleOuterDisplay(
        activity: ComponentActivity,
        contentProvider: () -> (@Composable () -> Unit)
    ) {
        if (activeSession != null || _uiState.value.isActive) {
            closeActiveSession()
            return
        }

        val currentMode = _uiState.value.mode
        if (currentMode == OuterDisplayMode.UNAVAILABLE) {
            return
        }

        _uiState.update { it.copy(lastErrorMessage = null) }

        when (currentMode) {
            OuterDisplayMode.PRESENTATION -> {
                try {
                    bridge.startPresentation(
                        activity = activity,
                        onStarted = { presenter ->
                            activeSession = presenter
                            val attached = attachComposePresentation(activity, presenter, contentProvider)
                            if (attached) {
                                _uiState.update {
                                    it.copy(
                                        isActive = true,
                                        isVisibleOnOuterDisplay = true,
                                        lastErrorMessage = null
                                    )
                                }
                            } else {
                                activeSession = null
                                runCatching { presenter.close() }
                            }
                        },
                        onVisibilityChanged = { isVisible ->
                            _uiState.update { it.copy(isVisibleOnOuterDisplay = isVisible) }
                        },
                        onEnded = { throwable ->
                            activeSession = null
                            _uiState.update {
                                it.copy(
                                    isActive = false,
                                    isVisibleOnOuterDisplay = false,
                                    lastErrorMessage = throwable?.toDisplayErrorMessage()
                                )
                            }
                        }
                    )
                } catch (e: SecurityException) {
                    handleStartFailure(e)
                } catch (e: WindowManager.BadTokenException) {
                    handleStartFailure(e)
                } catch (e: IllegalStateException) {
                    handleStartFailure(e)
                } catch (t: Throwable) {
                    handleStartFailure(t)
                }
            }

            OuterDisplayMode.TRANSFER -> {
                try {
                    bridge.startTransfer(
                        activity = activity,
                        onStarted = { session ->
                            activeSession = session
                            _uiState.update {
                                it.copy(
                                    isActive = true,
                                    isVisibleOnOuterDisplay = true,
                                    lastErrorMessage = null
                                )
                            }
                        },
                        onEnded = { throwable ->
                            activeSession = null
                            _uiState.update {
                                it.copy(
                                    isActive = false,
                                    isVisibleOnOuterDisplay = false,
                                    lastErrorMessage = throwable?.toDisplayErrorMessage()
                                )
                            }
                        }
                    )
                } catch (e: SecurityException) {
                    handleStartFailure(e)
                } catch (e: WindowManager.BadTokenException) {
                    handleStartFailure(e)
                } catch (e: IllegalStateException) {
                    handleStartFailure(e)
                } catch (t: Throwable) {
                    handleStartFailure(t)
                }
            }

            OuterDisplayMode.UNAVAILABLE -> Unit
        }
    }

    fun clearError() {
        _uiState.update { it.copy(lastErrorMessage = null) }
    }

    fun dispose() {
        closeActiveSession()
    }

    private fun closeActiveSession() {
        val session = activeSession
        activeSession = null
        runCatching { session?.close() }
        _uiState.update {
            it.copy(
                isActive = false,
                isVisibleOnOuterDisplay = false
            )
        }
    }

    private fun attachComposePresentation(
        activity: ComponentActivity,
        presenter: RearPresentationHandle,
        contentProvider: () -> (@Composable () -> Unit)
    ): Boolean {
        return try {
            presenter.window?.let { window ->
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                window.decorView.let { decor ->
                    decor.setViewTreeLifecycleOwner(activity)
                    decor.setViewTreeViewModelStoreOwner(activity)
                    decor.setViewTreeSavedStateRegistryOwner(activity)
                }
            }

            val composeView = ComposeView(presenter.context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setViewTreeLifecycleOwner(activity)
                setViewTreeViewModelStoreOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setContent {
                    DepartureBoardTheme(
                        darkTheme = true,
                        dynamicColor = false
                    ) {
                        contentProvider().invoke()
                    }
                }
            }
            presenter.setContentView(composeView)
            true
        } catch (e: SecurityException) {
            handleStartFailure(e)
            false
        } catch (e: WindowManager.BadTokenException) {
            handleStartFailure(e)
            false
        } catch (e: IllegalStateException) {
            handleStartFailure(e)
            false
        } catch (t: Throwable) {
            handleStartFailure(t)
            false
        }
    }

    private fun handleStartFailure(throwable: Throwable) {
        activeSession = null
        _uiState.update {
            it.copy(
                isActive = false,
                isVisibleOnOuterDisplay = false,
                lastErrorMessage = throwable.toDisplayErrorMessage()
            )
        }
    }

    private fun Throwable.toDisplayErrorMessage(): String =
        localizedMessage ?: message ?: this::class.java.simpleName
}

private val OUTER_CLOCK_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Compact high-contrast split-flap / LED board tailored for small flip cover screens
 * and secondary rear displays.
 */
@Composable
fun OuterDisplayConcourseBoard(
    snapshot: OuterDisplayBoardSnapshot,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when (snapshot.boardStyle) {
        DeskClockBoardStyle.SPLIT_FLAP -> Color(0xFF101216)
        DeskClockBoardStyle.AMBER_LED -> Color(0xFF08090C)
        DeskClockBoardStyle.OLED_MATRIX -> Color.Black
    }
    val primaryTextColor = when (snapshot.boardStyle) {
        DeskClockBoardStyle.SPLIT_FLAP -> Color(0xFFF5F5F0)
        DeskClockBoardStyle.AMBER_LED -> Color(0xFFFFB300)
        DeskClockBoardStyle.OLED_MATRIX -> Color(0xFFE2E8F0)
    }
    val accentClockColor = when (snapshot.boardStyle) {
        DeskClockBoardStyle.SPLIT_FLAP -> Color(0xFFF8FAFC)
        DeskClockBoardStyle.AMBER_LED -> Color(0xFFFFB300)
        DeskClockBoardStyle.OLED_MATRIX -> Color(0xFF38BDF8)
    }

    val stationTitle = remember(snapshot.station, snapshot.isBilingualEnabled) {
        snapshot.station.resolveDisplayName(snapshot.isBilingualEnabled).uppercase()
    }
    val clockText = remember(snapshot.currentTime.hour, snapshot.currentTime.minute) {
        snapshot.currentTime.format(OUTER_CLOCK_FORMATTER)
    }
    val regionFlag = remember(snapshot.station.id) {
        TransitRegion.fromStationId(snapshot.station.id).flagEmoji
    }
    val visibleDepartures = remember(snapshot.departures) {
        snapshot.departures.take(4)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .drawWithContent {
                drawContent()
                if (snapshot.brightnessMultiplier < 1f) {
                    drawRect(Color.Black.copy(alpha = 1f - snapshot.brightnessMultiplier))
                }
            }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Cover Header: Station Title + Live Clock
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = regionFlag,
                    fontSize = 14.sp
                )
                Text(
                    text = stationTitle,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        letterSpacing = 0.8.sp
                    ),
                    color = primaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.widthIn(min = 6.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1E222B))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = clockText,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp
                    ),
                    color = accentClockColor,
                    maxLines = 1
                )
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

        if (visibleDepartures.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF161920)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (snapshot.serviceHoursInfo?.isClosedForNight == true) {
                        stringResource(R.string.station_detail_service_ended).uppercase()
                    } else {
                        stringResource(R.string.departures_none_upcoming).uppercase()
                    },
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = primaryTextColor.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(8.dp)
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                visibleDepartures.forEach { dep ->
                    OuterDisplayDepartureRow(
                        departure = dep,
                        boardStyle = snapshot.boardStyle,
                        primaryTextColor = primaryTextColor
                    )
                }
            }
        }
    }
}

@Composable
private fun OuterDisplayDepartureRow(
    departure: Departure,
    boardStyle: DeskClockBoardStyle,
    primaryTextColor: Color
) {
    val isCancelled = departure.status == DepartureStatus.CANCELLED
    val isDue = departure.status == DepartureStatus.DUE || departure.timeToStationSeconds < 45
    val timeColor = when {
        isCancelled -> Color(0xFFEF4444)
        isDue -> Color(0xFF22C55E)
        boardStyle == DeskClockBoardStyle.AMBER_LED -> Color(0xFFFFB300)
        else -> Color(0xFF38BDF8)
    }
    val lineLabel = departure.lineBadge.lineCode.ifBlank { departure.lineBadge.displayName }
        .take(5)
        .uppercase()
    val destinationLabel = departure.destinationName.ifBlank { departure.lineName }.uppercase()
    val timeLabel = when {
        isCancelled -> "CANC"
        isDue -> "DUE"
        else -> departure.formattedTimeToArrival.uppercase()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF181C25))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 38.dp, max = 56.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (boardStyle == DeskClockBoardStyle.AMBER_LED) Color(0xFF261C00)
                    else departure.lineBadge.backgroundColor
                )
                .padding(horizontal = 5.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = lineLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 10.sp
                ),
                color = if (boardStyle == DeskClockBoardStyle.AMBER_LED) Color(0xFFFFB300)
                else departure.lineBadge.textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = destinationLabel,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            ),
            color = if (isCancelled) Color(0xFFEF4444) else primaryTextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = timeLabel,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 12.sp
            ),
            color = timeColor,
            maxLines = 1
        )
    }
}
