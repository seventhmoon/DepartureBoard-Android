package com.androidfung.departureboard.wear.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.ambient.AmbientLifecycleObserver
import com.androidfung.departureboard.wear.haptic.WearConcourseHapticAnnouncer
import com.androidfung.departureboard.wear.haptic.WearNotificationPublisher
import com.androidfung.departureboard.wear.model.WearAiPromptType
import com.androidfung.departureboard.wear.model.WearSnapshotFormatter
import com.androidfung.departureboard.wear.model.WearStationBoard
import com.androidfung.departureboard.wear.sync.WearCompanionStateRepository
import kotlinx.coroutines.launch

/**
 * Wear OS Wrist Departure Board Activity with Phone <-> Watch Companion Sync.
 *
 * Features when paired with the Phone app (`:app`):
 * 1. Mirrors the Phone's active `TrainTrackingService` journey at the top of the watch screen
 *    (live countdown, `TBD -> Plat X` haptic alert, and Target Alighting Stop countdown).
 * 2. Syncs the user's Pinned / Morning Commute / Evening Commute station boards from the phone.
 * 3. Provides 1-tap "Open on Phone" handoff (`ACTION_OPEN_ON_PHONE`) to inspect 8-coach formations
 *    and full calling-point timelines on the phone.
 * 4. Remote-controls `TrainTrackingService` on the phone directly from the wrist.
 *
 * Sub-screens and card components are decomposed into dedicated files:
 * - [WearAskAiScreen] (`WearAskAiScreen.kt`)
 * - [WearStationManagerScreen] (`WearStationManagerScreen.kt`)
 * - [WearSprintHudScreen] (`WearSprintHudScreen.kt`)
 * - [WearNotificationSettingsScreen] (`WearNotificationSettingsScreen.kt`)
 * - [WearTrackedJourneyCard], [WearBezelCountdownRing], [WearDepartureCard] (`WearBoardCards.kt`)
 */
class WearDepartureBoardActivity : ComponentActivity() {

    private var forceOpenSprintHudTrigger by mutableIntStateOf(0)
    private var isSystemAmbient by mutableStateOf(false)

    private val ambientCallback = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            isSystemAmbient = true
        }

        override fun onExitAmbient() {
            isSystemAmbient = false
        }
    }

    private val ambientObserver by lazy { AmbientLifecycleObserver(this, ambientCallback) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycle.addObserver(ambientObserver)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val openSprintHudInitially = intent?.getBooleanExtra(
            WearNotificationPublisher.EXTRA_OPEN_SPRINT_HUD,
            false
        ) == true
        if (openSprintHudInitially) {
            forceOpenSprintHudTrigger++
        }
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color.Black,
                    surface = Color(0xFF14161A),
                    primary = Color(0xFF4FC3F7),
                    secondary = Color(0xFFFFB74D)
                )
            ) {
                WearConcourseBoardScreen(
                    openSprintHudInitially = openSprintHudInitially,
                    openSprintHudTrigger = forceOpenSprintHudTrigger,
                    isSystemAmbient = isSystemAmbient
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(WearNotificationPublisher.EXTRA_OPEN_SPRINT_HUD, false)) {
            forceOpenSprintHudTrigger++
        }
    }
}

@Composable
fun WearConcourseBoardScreen(
    openSprintHudInitially: Boolean = false,
    openSprintHudTrigger: Int = 0,
    isSystemAmbient: Boolean = false
) {
    val context = LocalContext.current
    val syncedBoards by WearCompanionStateRepository.stationBoardsFlow.collectAsStateWithLifecycle()
    val trackedJourney by WearCompanionStateRepository.trackedJourneyFlow.collectAsStateWithLifecycle()
    val isPhoneSynced by WearCompanionStateRepository.isPhoneSyncedFlow.collectAsStateWithLifecycle()
    val repoWristAlert by WearCompanionStateRepository.latestWristAlertFlow.collectAsStateWithLifecycle()

    var localBoardsOverride by remember { mutableStateOf<List<WearStationBoard>?>(null) }
    val boards = localBoardsOverride ?: syncedBoards

    var selectedStationIndex by remember { mutableIntStateOf(0) }
    var watchedDepartureIds by remember { mutableStateOf(setOf("wat_1", "eus_1")) }
    var expandedDepartureId by remember { mutableStateOf<String?>(null) }
    var manualAmbientMode by remember { mutableStateOf(false) }
    val isAmbientMode = manualAmbientMode || isSystemAmbient
    var showSprintHud by remember { mutableStateOf(openSprintHudInitially) }
    var localWristAlert by remember { mutableStateOf<WearConcourseHapticAnnouncer.WristHapticEvent?>(null) }
    var showStationManager by remember { mutableStateOf(false) }
    var showAskAiScreen by remember { mutableStateOf(false) }
    var showNotificationSettings by remember { mutableStateOf(false) }
    val latestAiResponse by WearCompanionStateRepository.latestAiResponseFlow.collectAsStateWithLifecycle()
    val wearNotificationPrefs by WearNotificationPublisher.preferencesFlow.collectAsStateWithLifecycle()

    // Immediately switch to the Full-Screen Concourse Sprint HUD whenever a new train is tracked on the phone or watch
    LaunchedEffect(trackedJourney?.departureId, openSprintHudTrigger) {
        if (trackedJourney != null && wearNotificationPrefs.autoLaunchSprintHudEnabled) {
            showSprintHud = true
            showAskAiScreen = false
            showStationManager = false
            showNotificationSettings = false
        }
    }

    // Live 1-second wrist countdown ticker while a train is being tracked (paused in Ambient Mode to save battery)
    LaunchedEffect(trackedJourney != null, isAmbientMode) {
        while (trackedJourney != null && !isAmbientMode) {
            kotlinx.coroutines.delay(1000L)
            WearCompanionStateRepository.tickTrackedJourneySecond()
        }
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val rotaryFocusRequester = remember { FocusRequester() }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        WearNotificationPublisher.loadPreferences(context)
        WearNotificationPublisher.ensureAllChannels(context)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                runCatching {
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
        WearCompanionStateRepository.requestPhoneSync(context)
        runCatching { rotaryFocusRequester.requestFocus() }
    }

    // Automatically focus the Concourse-Geofenced station when the user walks into a station perimeter
    val concourseStationId = boards.firstOrNull { it.isAtConcourse }?.stationId
    LaunchedEffect(concourseStationId) {
        if (concourseStationId != null) {
            val idx = boards.indexOfFirst { it.stationId == concourseStationId }
            if (idx >= 0) {
                selectedStationIndex = idx
            }
        }
    }

    // Full-Screen Concourse Sprint & On-Board Alighting HUD
    if (showSprintHud && trackedJourney != null) {
        WearSprintHudScreen(
            journey = trackedJourney!!,
            isAmbientMode = isAmbientMode,
            onToggleManualAmbient = { manualAmbientMode = !manualAmbientMode },
            onSelectAlightStop = { stopName ->
                WearCompanionStateRepository.setTargetAlightStopFromWatch(context, stopName)
            },
            onOpenOnPhone = {
                val j = trackedJourney ?: return@WearSprintHudScreen
                WearCompanionStateRepository.openOnPhone(
                    context = context,
                    stationId = j.stationId,
                    stationName = j.stationName,
                    departureId = j.departureId
                )
                Toast.makeText(context, "Opening on Phone…", Toast.LENGTH_SHORT).show()
            },
            onStopTracking = {
                showSprintHud = false
                WearCompanionStateRepository.stopRemoteTrackingOnPhone(context)
            },
            onBackToBoard = { showSprintHud = false }
        )
        return
    }

    if (showStationManager) {
        WearStationManagerScreen(
            activeBoards = boards,
            onAddStation = { station ->
                localBoardsOverride = null
                WearCompanionStateRepository.addStationFromWatch(context, station)
                Toast.makeText(context, "Added ${station.stationCrs}", Toast.LENGTH_SHORT).show()
            },
            onRemoveStation = { stationId ->
                localBoardsOverride = null
                WearCompanionStateRepository.removeStationFromWatch(context, stationId)
                selectedStationIndex = 0
            },
            onSelectStation = { idx ->
                selectedStationIndex = idx
                showStationManager = false
            },
            onBack = { showStationManager = false }
        )
        return
    }

    if (showNotificationSettings) {
        WearNotificationSettingsScreen(
            prefs = wearNotificationPrefs,
            onUpdatePrefs = { liveTracking, autoHud, platform, alighting, disruption ->
                WearNotificationPublisher.updatePreferences(
                    context = context,
                    liveTrackingEnabled = liveTracking,
                    autoLaunchSprintHudEnabled = autoHud,
                    platformAlertsEnabled = platform,
                    alightingAlertsEnabled = alighting,
                    disruptionAlertsEnabled = disruption
                )
            },
            onOpenSystemChannelSettings = { channelId ->
                WearNotificationPublisher.openSystemNotificationSettings(context, channelId)
            },
            onBack = { showNotificationSettings = false }
        )
        return
    }

    if (boards.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "No Stations Synced",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF1E2229),
                    border = BorderStroke(1.dp, Color(0xFF4FC3F7)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { showStationManager = true }
                ) {
                    Text(
                        text = "+ Add Station",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF81D4FA),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
        return
    }

    val currentBoard: WearStationBoard = boards[selectedStationIndex.mod(boards.size)]

    if (showAskAiScreen) {
        WearAskAiScreen(
            station = currentBoard,
            aiResponse = latestAiResponse?.takeIf { it.stationId == currentBoard.stationId },
            onSelectPrompt = { promptType, queryText ->
                WearCompanionStateRepository.askAiConcourseTriage(
                    context = context,
                    station = currentBoard,
                    promptType = promptType,
                    queryText = queryText
                )
            },
            onOpenAiOnPhone = { queryText ->
                WearCompanionStateRepository.openAiOnPhone(
                    context = context,
                    stationId = currentBoard.stationId,
                    stationName = currentBoard.stationName,
                    queryText = queryText
                )
                Toast.makeText(context, "Opening AI on Phone…", Toast.LENGTH_SHORT).show()
            },
            onBack = { showAskAiScreen = false }
        )
        return
    }

    val complicationSnapshot = remember(currentBoard) {
        WearSnapshotFormatter.buildComplicationSnapshot(currentBoard)
    }
    val activeWristAlert = repoWristAlert ?: localWristAlert
    val topDeparture = currentBoard.departures.firstOrNull()
    val bezelProgressPercent = remember(trackedJourney, complicationSnapshot) {
        if (trackedJourney != null) {
            val clampedMins = (trackedJourney!!.remainingSeconds / 60).coerceIn(0, 15)
            (((15 - clampedMins) * 100) / 15).coerceIn(8, 100)
        } else {
            complicationSnapshot.rangedProgressPercent
        }
    }
    val bezelRingColor = remember(trackedJourney, topDeparture, isAmbientMode) {
        when {
            isAmbientMode -> Color(0xFF455A64)
            trackedJourney != null && !trackedJourney!!.isPlatformTbd -> Color(0xFF00E676)
            trackedJourney != null -> Color(0xFF4FC3F7)
            topDeparture != null && topDeparture.countdownMinutes <= 2 -> Color(0xFFFFB74D)
            topDeparture != null && !topDeparture.isPlatformTbd -> Color(0xFF00E676)
            else -> Color(0xFF4FC3F7)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Outer Bezel Countdown Progress Ring around the circular watch edge
        WearBezelCountdownRing(
            progressPercent = bezelProgressPercent,
            ringColor = bezelRingColor,
            isAmbient = isAmbientMode
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .onRotaryScrollEvent { event ->
                    coroutineScope.launch {
                        listState.scrollBy(event.verticalScrollPixels)
                    }
                    true
                }
                .focusRequester(rotaryFocusRequester)
                .focusable(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 24.dp, bottom = 46.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Top Curved-safe Complication Pill + Phone Sync Badge
            item(key = "complication_pill") {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isAmbientMode) Color.Black else Color(0xFF1E2229),
                    border = BorderStroke(1.dp, Color(0xFF37474F))
                ) {
                    Text(
                        text = complicationSnapshot.shortText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = if (isAmbientMode) Color(0xFFB0BEC5) else Color(0xFF81D4FA),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }

            // 2. Station Header & Commute Tag (tap to cycle pinned concourse stations)
            item(key = "station_header") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            selectedStationIndex = (selectedStationIndex + 1) % boards.size
                            localWristAlert = null
                            WearCompanionStateRepository.clearWristAlert()
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = currentBoard.compactStationName,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            ),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.SwapHoriz,
                            contentDescription = "Switch station",
                            tint = Color(0xFF90CAF9),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    val shortTag = currentBoard.commuteLabel
                        ?.replace(" on Phone", "", ignoreCase = true)
                        ?.replace(" Commute", "", ignoreCase = true)
                        ?.trim()
                        ?.takeIf { it.isNotBlank() && !it.equals("Phone Board", ignoreCase = true) }
                    val subtitle = buildList {
                        shortTag?.let { add(it) }
                        add(
                            when {
                                currentBoard.isStale() -> "⚠️ Sync needed"
                                isPhoneSynced -> "📱 Synced"
                                else -> "Live"
                            }
                        )
                    }.joinToString(" • ")
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = Color(0xFF90A4AE),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isAmbientMode) {
                item(key = "ambient_exit_pill") {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.Black,
                        border = BorderStroke(1.dp, Color(0xFF37474F)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { manualAmbientMode = false }
                    ) {
                        Text(
                            text = "🌙 Ambient OLED • Tap to Wake",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = Color(0xFF78909C),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // 3. Wrist Haptic Platform / Alight Alert Banner (when triggered)
            activeWristAlert?.let { alert ->
                item(key = "wrist_alert_banner") {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF004D40),
                        border = BorderStroke(1.dp, Color(0xFF26A69A)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                localWristAlert = null
                                WearCompanionStateRepository.clearWristAlert()
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🔔 ${alert.headline}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp
                                ),
                                color = Color(0xFF80CBC4),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = alert.detail,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // 4. Active Phone-Tracked Journey Card (Mirrored from Phone's TrainTrackingService)
            trackedJourney?.let { journey ->
                item(key = "phone_tracked_journey") {
                    WearTrackedJourneyCard(
                        journey = journey,
                        onOpenSprintHud = { showSprintHud = true },
                        onOpenOnPhone = {
                            WearCompanionStateRepository.openOnPhone(
                                context = context,
                                stationId = journey.stationId,
                                stationName = journey.stationName,
                                departureId = journey.departureId
                            )
                            Toast.makeText(context, "Opening on Phone…", Toast.LENGTH_SHORT).show()
                        },
                        onStopTracking = {
                            WearCompanionStateRepository.stopRemoteTrackingOnPhone(context)
                        }
                    )
                }
            }

            // 5. Departure Rows
            items(
                items = if (isAmbientMode) currentBoard.departures.take(2) else currentBoard.departures,
                key = { it.id },
                contentType = { "departure_row" }
            ) { dep ->
                val isWatched = watchedDepartureIds.contains(dep.id) || trackedJourney?.departureId == dep.id
                val isExpanded = expandedDepartureId == dep.id && !isAmbientMode
                WearDepartureCard(
                    departure = dep,
                    isWatched = isWatched,
                    isExpanded = isExpanded,
                    isAmbientMode = isAmbientMode,
                    onToggleExpand = {
                        if (isAmbientMode) {
                            manualAmbientMode = false
                        } else {
                            expandedDepartureId = if (expandedDepartureId == dep.id) null else dep.id
                        }
                    },
                    onToggleWatch = {
                        if (isWatched) {
                            watchedDepartureIds = watchedDepartureIds - dep.id
                            WearCompanionStateRepository.stopRemoteTrackingOnPhone(context)
                        } else {
                            watchedDepartureIds = watchedDepartureIds + dep.id
                            WearCompanionStateRepository.startRemoteTrackingOnPhone(context, currentBoard, dep)
                            showSprintHud = true
                        }
                        WearConcourseHapticAnnouncer.triggerWristPulse(
                            context,
                            WearConcourseHapticAnnouncer.WristAlertType.PLATFORM_ANNOUNCED
                        )
                    },
                    onOpenOnPhone = {
                        WearCompanionStateRepository.openOnPhone(
                            context = context,
                            stationId = currentBoard.stationId,
                            stationName = currentBoard.stationName,
                            departureId = dep.id
                        )
                        Toast.makeText(context, "Opening on Phone…", Toast.LENGTH_SHORT).show()
                    },
                    onSimulatePlatformAnnouncement = if (dep.isPlatformTbd) {
                        {
                            val announcedDep = dep.copy(
                                platformCode = "11",
                                isPlatformConfirmed = true,
                                statusLabel = "Boarding Now"
                            )
                            val event = WearConcourseHapticAnnouncer.evaluatePlatformTransition(
                                previous = dep,
                                current = announcedDep,
                                isWatched = true
                            )
                            localBoardsOverride = boards.map { board ->
                                if (board.stationId == currentBoard.stationId) {
                                    board.copy(
                                        departures = board.departures.map { item ->
                                            if (item.id == dep.id) announcedDep else item
                                        }
                                    )
                                } else board
                            }
                            if (event != null) {
                                localWristAlert = event
                                WearConcourseHapticAnnouncer.triggerWristPulse(context, event.type)
                                WearNotificationPublisher.postConcourseAlertNotification(context, event)
                            }
                        }
                    } else null
                )
            }

            // 6. Bottom Concourse Actions: [✨ Ask AI], [+ Stations], [🔔 Alerts], [🌙 AOD]
            item(key = "manage_stations_button") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF231934),
                            border = BorderStroke(1.dp, Color(0xFFB388FF).copy(alpha = 0.7f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    if (latestAiResponse?.stationId != currentBoard.stationId) {
                                        WearCompanionStateRepository.askAiConcourseTriage(
                                            context = context,
                                            station = currentBoard,
                                            promptType = WearAiPromptType.FASTEST_TRAIN
                                        )
                                    }
                                    showAskAiScreen = true
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "✨ Ask AI",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    color = Color(0xFFD1C4E9)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF1E2229),
                            border = BorderStroke(1.dp, Color(0xFF37474F)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { showStationManager = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    tint = Color(0xFF81D4FA),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "Stations",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    color = Color(0xFF81D4FA)
                                )
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF14171C),
                            border = BorderStroke(1.dp, Color(0xFF37474F)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { showNotificationSettings = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "🔔 Alerts",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = Color(0xFF80CBC4)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF14171C),
                            border = BorderStroke(1.dp, Color(0xFF37474F)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { manualAmbientMode = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "🌙 Ambient",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = Color(0xFF90A4AE)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
