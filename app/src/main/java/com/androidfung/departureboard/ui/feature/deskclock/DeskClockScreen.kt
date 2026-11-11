package com.androidfung.departureboard.ui.feature.deskclock

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DevicesFold
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.androidfung.departureboard.R
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.StationServiceHoursInfo
import com.androidfung.departureboard.data.model.TransitRegion
import com.androidfung.departureboard.ui.dashboard.StationCardUiModel
import com.androidfung.departureboard.ui.theme.LocalWindowPosture
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Display aesthetic styles for the Full-Screen Desk Clock / Split-Flap board.
 */
enum class DeskClockBoardStyle {
    SPLIT_FLAP,
    AMBER_LED,
    OLED_MATRIX
}

private val CLOCK_HH_MM_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
private val CLOCK_SS_FORMATTER = DateTimeFormatter.ofPattern("ss")

/**
 * Full-screen "Desk Clock / Split-Flap" Kiosk Mode with automatic Foldable Tabletop Posture support.
 *
 * - Keeps the screen awake (`FLAG_KEEP_SCREEN_ON`) and hides system bars in immersive mode.
 * - Supports authentic Solari Split-Flap mechanical tiles, Amber LED dot-matrix, and OLED Matrix styles.
 * - Automatically adapts to foldable Tabletop posture (`windowPosture.isTabletop`), placing the
 *   unobstructed departure board on the angled top half of the screen and an interactive touch
 *   control deck on the flat bottom half below the hinge crease.
 */
@Composable
fun DeskClockScreen(
    station: Station,
    stationCards: List<StationCardUiModel>,
    departures: List<Departure>,
    serviceHoursInfo: StationServiceHoursInfo?,
    onSelectStation: (Station) -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val postureInfo = LocalWindowPosture.current

    // Keep screen on and enter immersive system-bar mode while Desk Clock is active
    DisposableEffect(context, view) {
        val window = context.findDeskClockActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }
        insetsController?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    var boardStyle by rememberSaveable { mutableStateOf(DeskClockBoardStyle.SPLIT_FLAP) }
    var isAutoCycleEnabled by rememberSaveable { mutableStateOf(false) }
    var manualTabletopOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var isDimmed by rememberSaveable { mutableStateOf(false) }
    var showControlsOverlay by rememberSaveable { mutableStateOf(true) }
    var selectedLineId by rememberSaveable(station.id) { mutableStateOf<String?>(null) }

    val isEffectiveTabletop = manualTabletopOverride ?: postureInfo.isTabletop
    val brightnessMultiplier by remember(isDimmed) {
        mutableFloatStateOf(if (isDimmed) 0.48f else 1.0f)
    }

    // Live 1-second clock tick in the station's regional timezone
    val stationRegion = remember(station.id) { TransitRegion.fromStationId(station.id) }
    var currentTime by remember(stationRegion) { mutableStateOf(ZonedDateTime.now(stationRegion.timeZone)) }
    LaunchedEffect(stationRegion) {
        while (isActive) {
            currentTime = ZonedDateTime.now(stationRegion.timeZone)
            delay(1000L.milliseconds)
        }
    }

    // Auto-cycle through saved stations every 15 seconds when enabled
    val availableStations = remember(stationCards, station) {
        val list = stationCards.map { it.station }
        if (list.none { it.id == station.id }) listOf(station) + list else list
    }

    LaunchedEffect(isAutoCycleEnabled, availableStations, station.id) {
        if (isAutoCycleEnabled && availableStations.size > 1) {
            delay(15_000L.milliseconds)
            val currentIndex = availableStations.indexOfFirst { it.id == station.id }.coerceAtLeast(0)
            val nextStation = availableStations[(currentIndex + 1) % availableStations.size]
            onSelectStation(nextStation)
        }
    }

    val distinctLines = remember(departures) {
        departures.map { it.lineBadge }.distinctBy { it.lineId }
    }

    val filteredDepartures = remember(departures, selectedLineId) {
        if (selectedLineId == null) {
            departures
        } else {
            departures.filter { it.lineId.equals(selectedLineId, ignoreCase = true) }
        }
    }

    val backgroundColor = when (boardStyle) {
        DeskClockBoardStyle.SPLIT_FLAP -> Color(0xFF101216)
        DeskClockBoardStyle.AMBER_LED -> Color(0xFF08090C)
        DeskClockBoardStyle.OLED_MATRIX -> Color.Black
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (!isEffectiveTabletop) {
                    showControlsOverlay = !showControlsOverlay
                }
            }
            .drawWithContent {
                drawContent()
                if (brightnessMultiplier < 1f) {
                    drawRect(Color.Black.copy(alpha = 1f - brightnessMultiplier))
                }
            }
    ) {
        if (isEffectiveTabletop) {
            // FOLDABLE TABLETOP LAYOUT:
            // Top half (above physical hinge) = Unobstructed Split-Flap Board & Flip Clock
            // Bottom half (flat on table below hinge) = Interactive Commuter Control Deck
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
            ) {
                // TOP PANE: Viewing Screen angled towards the user
                DeskClockBoardPane(
                    station = station,
                    departures = filteredDepartures,
                    serviceHoursInfo = serviceHoursInfo,
                    currentTime = currentTime,
                    boardStyle = boardStyle,
                    isCompactTabletop = true,
                    onClose = onClose,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // HINGE SAFE-ZONE GUTTER
                val hingeGutter = if (postureInfo.isTabletop && postureInfo.hingeHeightDp > 0.dp) {
                    postureInfo.hingeHeightDp.coerceAtLeast(10.dp)
                } else {
                    8.dp
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(hingeGutter)
                        .background(Color(0xFF1E222B))
                )

                // BOTTOM PANE: Touch Control Deck resting flat on the desk
                TabletopControlDeck(
                    currentStation = station,
                    availableStations = availableStations,
                    distinctLines = distinctLines,
                    selectedLineId = selectedLineId,
                    boardStyle = boardStyle,
                    isAutoCycleEnabled = isAutoCycleEnabled,
                    isTabletopActive = true,
                    isDimmed = isDimmed,
                    serviceHoursInfo = serviceHoursInfo,
                    onSelectStation = onSelectStation,
                    onSelectLine = { lineId ->
                        selectedLineId = if (selectedLineId.equals(lineId, ignoreCase = true)) null else lineId
                    },
                    onSelectStyle = { boardStyle = it },
                    onToggleAutoCycle = { isAutoCycleEnabled = !isAutoCycleEnabled },
                    onToggleTabletop = {
                        manualTabletopOverride = !isEffectiveTabletop
                    },
                    onToggleDim = { isDimmed = !isDimmed },
                    onRefresh = onRefresh,
                    onClose = onClose,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.46f)
                        .background(Color(0xFF14171E))
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                )
            }
        } else {
            // STANDARD FULL-SCREEN SPLIT-FLAP / LED MODE
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                DeskClockBoardPane(
                    station = station,
                    departures = filteredDepartures,
                    serviceHoursInfo = serviceHoursInfo,
                    currentTime = currentTime,
                    boardStyle = boardStyle,
                    isCompactTabletop = false,
                    onClose = onClose,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                AnimatedVisibility(
                    visible = showControlsOverlay,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    FullScreenControlStrip(
                        currentStation = station,
                        availableStations = availableStations,
                        distinctLines = distinctLines,
                        selectedLineId = selectedLineId,
                        boardStyle = boardStyle,
                        isAutoCycleEnabled = isAutoCycleEnabled,
                        isTabletopActive = false,
                        isDimmed = isDimmed,
                        onSelectStation = onSelectStation,
                        onSelectLine = { lineId ->
                            selectedLineId = if (selectedLineId.equals(lineId, ignoreCase = true)) null else lineId
                        },
                        onSelectStyle = { boardStyle = it },
                        onToggleAutoCycle = { isAutoCycleEnabled = !isAutoCycleEnabled },
                        onToggleTabletop = { manualTabletopOverride = true },
                        onToggleDim = { isDimmed = !isDimmed },
                        onRefresh = onRefresh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    )
                }
            }
        }
    }
}

/**
 * Primary concourse Split-Flap / LED departure board display pane.
 */
@Composable
private fun DeskClockBoardPane(
    station: Station,
    departures: List<Departure>,
    serviceHoursInfo: StationServiceHoursInfo?,
    currentTime: ZonedDateTime,
    boardStyle: DeskClockBoardStyle,
    isCompactTabletop: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryTextColor = when (boardStyle) {
        DeskClockBoardStyle.SPLIT_FLAP -> Color(0xFFF5F5F0)
        DeskClockBoardStyle.AMBER_LED -> Color(0xFFFFB300)
        DeskClockBoardStyle.OLED_MATRIX -> Color(0xFFE2E8F0)
    }
    val secondaryTextColor = when (boardStyle) {
        DeskClockBoardStyle.SPLIT_FLAP -> Color(0xFF94A3B8)
        DeskClockBoardStyle.AMBER_LED -> Color(0xFFFF8F00).copy(alpha = 0.78f)
        DeskClockBoardStyle.OLED_MATRIX -> Color(0xFF64748B)
    }

    Column(modifier = modifier) {
        // 1. Concourse Header Bar: Station Title + Live Flip Clock + Close Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                val region = TransitRegion.fromStationId(station.id)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF222631))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = region.flagEmoji,
                        fontSize = if (isCompactTabletop) 16.sp else 20.sp
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = station.displayName.uppercase(),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = if (isCompactTabletop) 18.sp else 24.sp,
                            letterSpacing = 1.2.sp
                        ),
                        color = primaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Last Train Home / Night Service subtitle in concourse header
                    if (serviceHoursInfo != null && (serviceHoursInfo.isApproachingLastTrain || serviceHoursInfo.isNightServiceActive || serviceHoursInfo.isClosedForNight)) {
                        val statusLine = when {
                            serviceHoursInfo.isNightServiceActive ->
                                stringResource(R.string.night_service_banner_title)
                            serviceHoursInfo.isApproachingLastTrain -> {
                                val mins = serviceHoursInfo.minutesUntilLastTrain
                                val countdownSuffix = if (mins != null) " (${mins}m)" else ""
                                "🌙 LAST TRAIN ${serviceHoursInfo.lastTrainTime.orEmpty()}$countdownSuffix"
                            }
                            else ->
                                "🌙 SERVICE ENDED • FIRST TRAIN ~${serviceHoursInfo.firstTrainTime ?: "05:30"}"
                        }
                        Text(
                            text = statusLine,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                fontSize = 11.sp
                            ),
                            color = if (serviceHoursInfo.isNightServiceActive) Color(0xFFB388FF) else Color(0xFFFFB300),
                            maxLines = 1
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.station_detail_live_departures).uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.5.sp,
                                fontSize = 10.sp
                            ),
                            color = secondaryTextColor
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Split-Flap Digital Clock with Seconds
                SplitFlapClockDisplay(
                    currentTime = currentTime,
                    boardStyle = boardStyle,
                    isCompact = isCompactTabletop
                )

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF222631))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_close),
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(if (isCompactTabletop) 8.dp else 14.dp))

        // 2. Column Headers: LINE | DESTINATION | PLAT | TIME
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF191C24), RoundedCornerShape(6.dp))
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.desk_clock_col_line),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    fontSize = 10.sp
                ),
                color = secondaryTextColor,
                modifier = Modifier.width(86.dp)
            )
            Text(
                text = stringResource(R.string.desk_clock_col_destination),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    fontSize = 10.sp
                ),
                color = secondaryTextColor,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.desk_clock_col_platform),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    fontSize = 10.sp
                ),
                color = secondaryTextColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(64.dp)
            )
            Text(
                text = stringResource(R.string.desk_clock_col_time),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    fontSize = 10.sp
                ),
                color = secondaryTextColor,
                textAlign = TextAlign.End,
                modifier = Modifier.width(96.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 3. Split-Flap / LED Departure Rows
        if (departures.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF161920))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NightsStay,
                        contentDescription = null,
                        tint = primaryTextColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = if (serviceHoursInfo?.isClosedForNight == true) {
                            stringResource(R.string.station_detail_service_ended).uppercase()
                        } else {
                            stringResource(R.string.departures_none_upcoming).uppercase()
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = primaryTextColor
                    )
                    if (serviceHoursInfo?.firstTrainTime != null) {
                        Text(
                            text = stringResource(R.string.station_detail_first_train, serviceHoursInfo.firstTrainTime).uppercase(),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = secondaryTextColor
                        )
                    }
                }
            }
        } else {
            val cappedDepartures = remember(departures, isCompactTabletop) {
                val maxRows = if (isCompactTabletop) 6 else 10
                val seen = HashMap<String, Int>()
                departures.take(maxRows).map { dep ->
                    val base = dep.id.ifBlank { "${dep.stationId}_${dep.lineId}_${dep.destinationName}" }
                    val c = seen.getOrDefault(base, 0)
                    seen[base] = c + 1
                    val key = if (c == 0) base else "${base}#$c"
                    key to dep
                }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(if (isCompactTabletop) 5.dp else 7.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                itemsIndexed(
                    items = cappedDepartures,
                    key = { _, pair -> pair.first },
                    contentType = { _, _ -> "split_flap_row" }
                ) { index, (_, departure) ->
                    SplitFlapDepartureRow(
                        departure = departure,
                        index = index,
                        boardStyle = boardStyle,
                        isCompact = isCompactTabletop
                    )
                }
            }
        }
    }
}

/**
 * Mechanical Solari Split-Flap Clock component with HH:mm and ticking seconds flap.
 */
@Composable
private fun SplitFlapClockDisplay(
    currentTime: ZonedDateTime,
    boardStyle: DeskClockBoardStyle,
    isCompact: Boolean
) {
    val hhMm = remember(currentTime.hour, currentTime.minute) {
        currentTime.format(CLOCK_HH_MM_FORMATTER)
    }
    val ss = remember(currentTime.second) {
        currentTime.format(CLOCK_SS_FORMATTER)
    }

    val digitColor = when (boardStyle) {
        DeskClockBoardStyle.SPLIT_FLAP -> Color(0xFFF8FAFC)
        DeskClockBoardStyle.AMBER_LED -> Color(0xFFFFB300)
        DeskClockBoardStyle.OLED_MATRIX -> Color(0xFF38BDF8)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        FlapTileBox(
            text = hhMm,
            textColor = digitColor,
            fontSize = if (isCompact) 18.sp else 22.sp,
            boardStyle = boardStyle,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
        AnimatedContent(
            targetState = ss,
            transitionSpec = {
                (slideInVertically { -it } + fadeIn()) togetherWith
                    (slideOutVertically { it } + fadeOut())
            },
            label = "SecondsFlap"
        ) { secText ->
            FlapTileBox(
                text = secText,
                textColor = digitColor.copy(alpha = 0.85f),
                fontSize = if (isCompact) 13.sp else 15.sp,
                boardStyle = boardStyle
            )
        }
    }
}

/**
 * Individual mechanical Split-Flap or LED Matrix departure row.
 */
@Composable
private fun SplitFlapDepartureRow(
    departure: Departure,
    index: Int,
    boardStyle: DeskClockBoardStyle,
    isCompact: Boolean
) {
    val flipAnim = remember { Animatable(0f) }
    LaunchedEffect(departure.destinationName, departure.formattedTimeToArrival) {
        flipAnim.snapTo(-28f)
        flipAnim.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
        )
    }

    val rowBgBrush = when (boardStyle) {
        DeskClockBoardStyle.SPLIT_FLAP -> Brush.verticalGradient(
            colors = listOf(Color(0xFF232733), Color(0xFF181B24), Color(0xFF1F232E))
        )
        DeskClockBoardStyle.AMBER_LED -> Brush.verticalGradient(
            colors = listOf(Color(0xFF121318), Color(0xFF0D0E12))
        )
        DeskClockBoardStyle.OLED_MATRIX -> Brush.verticalGradient(
            colors = listOf(Color(0xFF0A0A0E), Color(0xFF050507))
        )
    }

    val destinationColor = when (boardStyle) {
        DeskClockBoardStyle.SPLIT_FLAP -> Color(0xFFF8FAFC)
        DeskClockBoardStyle.AMBER_LED -> Color(0xFFFFB300)
        DeskClockBoardStyle.OLED_MATRIX -> Color.White
    }

    val platformShort = remember(departure.platformName) {
        departure.platformName
            .replace("Platform ", "", ignoreCase = true)
            .replace("Stop ", "", ignoreCase = true)
            .take(5)
            .ifBlank { "-" }
    }

    val isDue = departure.status == DepartureStatus.DUE || departure.timeToStationSeconds < 45
    val isCancelled = departure.status == DepartureStatus.CANCELLED

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isCompact) 42.dp else 50.dp)
            .graphicsLayer {
                rotationX = flipAnim.value
                cameraDistance = 14f * density
            }
            .clip(RoundedCornerShape(8.dp))
            .background(rowBgBrush)
            .border(
                width = 1.dp,
                color = if (index == 0 && !isCancelled) {
                    departure.lineBadge.backgroundColor.copy(alpha = 0.55f)
                } else {
                    Color.White.copy(alpha = 0.08f)
                },
                shape = RoundedCornerShape(8.dp)
            )
            .drawWithContent {
                drawContent()
                // Mechanical split-flap horizontal center crease across the row
                if (boardStyle == DeskClockBoardStyle.SPLIT_FLAP) {
                    val midY = size.height / 2f
                    drawLine(
                        color = Color.Black.copy(alpha = 0.55f),
                        start = Offset(0f, midY),
                        end = Offset(size.width, midY),
                        strokeWidth = 1.5f
                    )
                }
            }
            .padding(horizontal = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Line Pill / Code
            Box(
                modifier = Modifier
                    .width(78.dp)
                    .padding(end = 8.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        if (boardStyle == DeskClockBoardStyle.AMBER_LED) {
                            Color(0xFF261C00)
                        } else {
                            departure.lineBadge.backgroundColor
                        }
                    )
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                val lineLabel = departure.lineBadge.lineCode.ifBlank { departure.lineBadge.displayName }
                Text(
                    text = lineLabel.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = if (isCompact) 11.sp else 12.sp
                    ),
                    color = if (boardStyle == DeskClockBoardStyle.AMBER_LED) {
                        Color(0xFFFFB300)
                    } else {
                        departure.lineBadge.textColor
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 2. Destination & Last Train / Night Tag
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = departure.destinationName.ifBlank { departure.lineName }.uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (isCompact) 14.sp else 16.sp,
                        letterSpacing = 0.6.sp
                    ),
                    color = if (isCancelled) Color(0xFFEF4444) else destinationColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (departure.isLastTrain) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFFFB300).copy(alpha = 0.2f))
                            .border(1.dp, Color(0xFFFFB300).copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "🌙 LAST",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                fontSize = 9.sp
                            ),
                            color = Color(0xFFFFB300)
                        )
                    }
                } else if (departure.isNightService) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF7C4DFF).copy(alpha = 0.22f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "🦉 24H",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            ),
                            color = Color(0xFFB388FF)
                        )
                    }
                }
            }

            // 3. Platform Flap
            Box(
                modifier = Modifier.width(64.dp),
                contentAlignment = Alignment.Center
            ) {
                FlapTileBox(
                    text = platformShort.uppercase(),
                    textColor = destinationColor.copy(alpha = 0.9f),
                    fontSize = if (isCompact) 12.sp else 13.sp,
                    boardStyle = boardStyle
                )
            }

            // 4. Countdown & Scheduled Clock Time Flap
            Box(
                modifier = Modifier.width(96.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                val timeLabel = when {
                    isCancelled -> "CANCEL"
                    isDue -> "DUE"
                    else -> {
                        val mins = (departure.timeToStationSeconds / 60).coerceAtLeast(1)
                        "${mins}m ${departure.formattedActualClockTime.orEmpty()}".trim()
                    }
                }
                val timeColor = when {
                    isCancelled -> Color(0xFFEF4444)
                    isDue -> Color(0xFF22C55E)
                    boardStyle == DeskClockBoardStyle.AMBER_LED -> Color(0xFFFFD54F)
                    else -> Color(0xFFFBBF24)
                }

                FlapTileBox(
                    text = timeLabel,
                    textColor = timeColor,
                    fontSize = if (isCompact) 12.sp else 13.sp,
                    boardStyle = boardStyle
                )
            }
        }
    }
}

/**
 * Individual mechanical Split-Flap tile box with horizontal split line.
 */
@Composable
private fun FlapTileBox(
    text: String,
    textColor: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    boardStyle: DeskClockBoardStyle,
    modifier: Modifier = Modifier
) {
    val bgColor = when (boardStyle) {
        DeskClockBoardStyle.SPLIT_FLAP -> Color(0xFF14171F)
        DeskClockBoardStyle.AMBER_LED -> Color(0xFF16130A)
        DeskClockBoardStyle.OLED_MATRIX -> Color(0xFF0F172A)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(bgColor)
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(5.dp))
            .drawWithContent {
                drawContent()
                if (boardStyle == DeskClockBoardStyle.SPLIT_FLAP) {
                    val midY = size.height / 2f
                    drawLine(
                        color = Color.Black.copy(alpha = 0.65f),
                        start = Offset(0f, midY),
                        end = Offset(size.width, midY),
                        strokeWidth = 1.2f
                    )
                }
            }
            .padding(horizontal = 7.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = fontSize,
                letterSpacing = 0.5.sp
            ),
            color = textColor,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

/**
 * Bottom half touch control deck for Foldable Tabletop posture (resting flat on the desk).
 */
@Composable
private fun TabletopControlDeck(
    currentStation: Station,
    availableStations: List<Station>,
    distinctLines: List<LineBadgeInfo>,
    selectedLineId: String?,
    boardStyle: DeskClockBoardStyle,
    isAutoCycleEnabled: Boolean,
    isTabletopActive: Boolean,
    isDimmed: Boolean,
    serviceHoursInfo: StationServiceHoursInfo?,
    onSelectStation: (Station) -> Unit,
    onSelectLine: (String) -> Unit,
    onSelectStyle: (DeskClockBoardStyle) -> Unit,
    onToggleAutoCycle: () -> Unit,
    onToggleTabletop: () -> Unit,
    onToggleDim: () -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Deck Header: Tabletop Status + Quick Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DevicesFold,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.desk_clock_tabletop_toggle).uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = Color(0xFF38BDF8)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeckControlChip(
                    label = stringResource(R.string.desk_clock_auto_cycle),
                    icon = Icons.Rounded.Sync,
                    isSelected = isAutoCycleEnabled,
                    onClick = onToggleAutoCycle
                )
                DeckControlChip(
                    label = stringResource(R.string.desk_clock_dim_mode),
                    icon = Icons.Rounded.BrightnessLow,
                    isSelected = isDimmed,
                    onClick = onToggleDim
                )
                DeckControlChip(
                    label = "Full",
                    icon = Icons.Rounded.DevicesFold,
                    isSelected = !isTabletopActive,
                    onClick = onToggleTabletop
                )
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF222634))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.cd_refresh_departures),
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        // Station Switcher Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            availableStations.forEach { st ->
                val isSelected = st.id == currentStation.id
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) Color(0xFF38BDF8).copy(alpha = 0.22f) else Color(0xFF1F2430),
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.clickable { onSelectStation(st) }
                ) {
                    Text(
                        text = st.displayName,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Line Filter + Board Style Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Line filters
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                distinctLines.forEach { badge ->
                    val isSelected = selectedLineId.equals(badge.lineId, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected || selectedLineId == null) badge.backgroundColor
                                else badge.backgroundColor.copy(alpha = 0.35f)
                            )
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = if (isSelected) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectLine(badge.lineId) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = badge.lineCode.ifBlank { badge.displayName },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = badge.textColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Board Style Switcher
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DeskClockBoardStyle.entries.forEach { style ->
                    val label = when (style) {
                        DeskClockBoardStyle.SPLIT_FLAP -> stringResource(R.string.desk_clock_style_split_flap)
                        DeskClockBoardStyle.AMBER_LED -> stringResource(R.string.desk_clock_style_amber_led)
                        DeskClockBoardStyle.OLED_MATRIX -> stringResource(R.string.desk_clock_style_oled_dark)
                    }
                    DeckControlChip(
                        label = label,
                        icon = Icons.Rounded.Tune,
                        isSelected = boardStyle == style,
                        onClick = { onSelectStyle(style) }
                    )
                }
            }
        }
    }
}

/**
 * Collapsible bottom control strip for standard Full-Screen mode.
 */
@Composable
private fun FullScreenControlStrip(
    currentStation: Station,
    availableStations: List<Station>,
    distinctLines: List<LineBadgeInfo>,
    selectedLineId: String?,
    boardStyle: DeskClockBoardStyle,
    isAutoCycleEnabled: Boolean,
    isTabletopActive: Boolean,
    isDimmed: Boolean,
    onSelectStation: (Station) -> Unit,
    onSelectLine: (String) -> Unit,
    onSelectStyle: (DeskClockBoardStyle) -> Unit,
    onToggleAutoCycle: () -> Unit,
    onToggleTabletop: () -> Unit,
    onToggleDim: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1A1E29).copy(alpha = 0.94f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top strip: Saved stations switcher + Mode toggles
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableStations.forEach { st ->
                        val isSelected = st.id == currentStation.id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) Color(0xFF38BDF8).copy(alpha = 0.25f)
                                    else Color(0xFF252A38)
                                )
                                .border(
                                    width = if (isSelected) 1.dp else 0.dp,
                                    color = if (isSelected) Color(0xFF38BDF8) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onSelectStation(st) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = st.displayName,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DeckControlChip(
                        label = stringResource(R.string.desk_clock_tabletop_toggle),
                        icon = Icons.Rounded.DevicesFold,
                        isSelected = isTabletopActive,
                        onClick = onToggleTabletop
                    )
                    DeckControlChip(
                        label = stringResource(R.string.desk_clock_auto_cycle),
                        icon = Icons.Rounded.Sync,
                        isSelected = isAutoCycleEnabled,
                        onClick = onToggleAutoCycle
                    )
                    DeckControlChip(
                        label = stringResource(R.string.desk_clock_dim_mode),
                        icon = Icons.Rounded.BrightnessLow,
                        isSelected = isDimmed,
                        onClick = onToggleDim
                    )
                    DeskClockBoardStyle.entries.forEach { style ->
                        val label = when (style) {
                            DeskClockBoardStyle.SPLIT_FLAP -> stringResource(R.string.desk_clock_style_split_flap)
                            DeskClockBoardStyle.AMBER_LED -> stringResource(R.string.desk_clock_style_amber_led)
                            DeskClockBoardStyle.OLED_MATRIX -> stringResource(R.string.desk_clock_style_oled_dark)
                        }
                        DeckControlChip(
                            label = label,
                            icon = Icons.Rounded.Tune,
                            isSelected = boardStyle == style,
                            onClick = { onSelectStyle(style) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeckControlChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) Color(0xFFFFB300).copy(alpha = 0.22f) else Color(0xFF232836),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isSelected) Color(0xFFFFB300) else Color.White.copy(alpha = 0.1f)
        ),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color(0xFFFFB300) else Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.sp
                ),
                color = if (isSelected) Color(0xFFFFB300) else Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

private tailrec fun Context.findDeskClockActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findDeskClockActivity()
    else -> null
}
