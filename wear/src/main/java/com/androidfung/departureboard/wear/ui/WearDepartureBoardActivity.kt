package com.androidfung.departureboard.wear.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidfung.departureboard.wear.haptic.WearConcourseHapticAnnouncer
import com.androidfung.departureboard.wear.model.WearAiConcourseResponse
import com.androidfung.departureboard.wear.model.WearAiPromptType
import com.androidfung.departureboard.wear.model.WearDepartureItem
import com.androidfung.departureboard.wear.model.WearSnapshotFormatter
import com.androidfung.departureboard.wear.model.WearStationBoard
import com.androidfung.departureboard.wear.model.WearTrackedJourneyState
import com.androidfung.departureboard.wear.sync.WearCompanionStateRepository

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
 */
class WearDepartureBoardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color.Black,
                    surface = Color(0xFF14161A),
                    primary = Color(0xFF4FC3F7),
                    secondary = Color(0xFFFFB74D)
                )
            ) {
                WearConcourseBoardScreen()
            }
        }
    }
}

@Composable
fun WearConcourseBoardScreen() {
    val context = LocalContext.current
    val syncedBoards by WearCompanionStateRepository.stationBoardsFlow.collectAsStateWithLifecycle()
    val trackedJourney by WearCompanionStateRepository.trackedJourneyFlow.collectAsStateWithLifecycle()
    val isPhoneSynced by WearCompanionStateRepository.isPhoneSyncedFlow.collectAsStateWithLifecycle()
    val repoWristAlert by WearCompanionStateRepository.latestWristAlertFlow.collectAsStateWithLifecycle()

    var localBoardsOverride by remember { mutableStateOf<List<WearStationBoard>?>(null) }
    val boards = localBoardsOverride ?: syncedBoards

    var selectedStationIndex by remember { mutableIntStateOf(0) }
    var watchedDepartureIds by remember { mutableStateOf(setOf("wat_1", "eus_1")) }
    var localWristAlert by remember { mutableStateOf<WearConcourseHapticAnnouncer.WristHapticEvent?>(null) }
    var showStationManager by remember { mutableStateOf(false) }
    var showAskAiScreen by remember { mutableStateOf(false) }
    val latestAiResponse by WearCompanionStateRepository.latestAiResponseFlow.collectAsStateWithLifecycle()

    val currentBoard: WearStationBoard = boards[selectedStationIndex % boards.size]

    LaunchedEffect(Unit) {
        WearCompanionStateRepository.requestPhoneSync(context)
    }

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
    val complicationSnapshot = remember(currentBoard) {
        WearSnapshotFormatter.buildComplicationSnapshot(currentBoard)
    }
    val activeWristAlert = repoWristAlert ?: localWristAlert

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Top Curved-safe Complication Pill + Phone Sync Badge
            item(key = "complication_pill") {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF1E2229),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF37474F))
                ) {
                    Text(
                        text = complicationSnapshot.shortText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = Color(0xFF81D4FA),
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
                        add(if (isPhoneSynced) "📱 Synced" else "Live")
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

            // 3. Wrist Haptic Platform / Alight Alert Banner (when triggered)
            activeWristAlert?.let { alert ->
                item(key = "wrist_alert_banner") {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF004D40),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF26A69A)),
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
                items = currentBoard.departures,
                key = { it.id }
            ) { dep ->
                val isWatched = watchedDepartureIds.contains(dep.id) || trackedJourney?.departureId == dep.id
                WearDepartureCard(
                    departure = dep,
                    isWatched = isWatched,
                    onToggleWatch = {
                        if (isWatched) {
                            watchedDepartureIds = watchedDepartureIds - dep.id
                            WearCompanionStateRepository.stopRemoteTrackingOnPhone(context)
                        } else {
                            watchedDepartureIds = watchedDepartureIds + dep.id
                            WearCompanionStateRepository.startRemoteTrackingOnPhone(context, currentBoard, dep)
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
                            }
                        }
                    } else null
                )
            }

            // 6. Bottom Concourse Actions: [✨ Ask AI] and [+ Stations]
            item(key = "manage_stations_button") {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFF231934),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFB388FF).copy(alpha = 0.7f)),
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
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF37474F)),
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
            }
        }
    }
}

@Composable
private fun WearAskAiScreen(
    station: WearStationBoard,
    aiResponse: WearAiConcourseResponse?,
    onSelectPrompt: (WearAiPromptType, String) -> Unit,
    onOpenAiOnPhone: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            if (spoken.isNotBlank()) {
                onSelectPrompt(WearAiPromptType.VOICE_QUERY, spoken)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item(key = "back_button") {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF1E2229),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4FC3F7)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onBack)
                ) {
                    Text(
                        text = "← ${station.stationCrs} Board",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = Color(0xFF81D4FA),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }

            // Glanceable 2-Line AI Concourse Answer Card
            aiResponse?.let { resp ->
                item(key = "ai_response_card") {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF1C1429),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFB388FF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (resp.isFromPhone) "✨ PHONE AI TRIAGE" else "✨ CONCOURSE TRIAGE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 9.sp
                                ),
                                color = Color(0xFFB388FF)
                            )
                            Text(
                                text = resp.headline,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp
                                ),
                                color = Color.White
                            )
                            Text(
                                text = resp.summary,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                color = Color(0xFFE0E0E0)
                            )
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color(0xFF311B92),
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .clip(RoundedCornerShape(50))
                                    .clickable { onOpenAiOnPhone(resp.rawQuery) }
                            ) {
                                Text(
                                    text = "📱 Continue in Phone AI",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp
                                    ),
                                    color = Color(0xFFD1C4E9),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Voice Input Button
            item(key = "voice_query_button") {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0D2538),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4FC3F7)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            try {
                                val voiceIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(
                                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                    )
                                    putExtra(
                                        RecognizerIntent.EXTRA_PROMPT,
                                        "Ask about ${station.stationName}…"
                                    )
                                }
                                speechLauncher.launch(voiceIntent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "Voice input unavailable on this device", Toast.LENGTH_SHORT).show()
                            }
                        }
                ) {
                    Text(
                        text = "🎤 Speak Question",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = Color(0xFF81D4FA),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                }
            }

            // 1-Tap Concourse Triage Prompts
            val prompts = listOf(
                WearAiPromptType.FASTEST_TRAIN to "⚡ Fastest / Best Train",
                WearAiPromptType.DELAYS_AND_BACKLOG to "⚠️ Delays & Delay Repay",
                WearAiPromptType.LAST_TRAIN to "🌙 Last Train Tonight"
            )
            items(
                items = prompts,
                key = { it.first.name }
            ) { (promptType, label) ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF14171C),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF262B33)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectPrompt(promptType, "") }
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        ),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WearStationManagerScreen(
    activeBoards: List<WearStationBoard>,
    onAddStation: (WearStationBoard) -> Unit,
    onRemoveStation: (String) -> Unit,
    onSelectStation: (Int) -> Unit,
    onBack: () -> Unit
) {
    val catalog = remember { WearSnapshotFormatter.stationCatalog() }
    val availableToAdd = remember(activeBoards, catalog) {
        catalog.filter { candidate ->
            activeBoards.none { it.stationId.equals(candidate.stationId, ignoreCase = true) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item(key = "back_button") {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF1E2229),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4FC3F7)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onBack)
                ) {
                    Text(
                        text = "← Back to Board",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = Color(0xFF81D4FA),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }

            item(key = "active_header") {
                Text(
                    text = "Your Stations (${activeBoards.size})",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp
                    ),
                    color = Color.White,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            items(
                items = activeBoards.mapIndexed { idx, board -> idx to board },
                key = { (_, board) -> "active_${board.stationId}" }
            ) { (idx, board) ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF14171C),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF262B33)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectStation(idx) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${board.stationName} (${board.stationCrs})",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            board.commuteLabel?.let { tag ->
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = Color(0xFF90A4AE)
                                )
                            }
                        }
                        if (activeBoards.size > 1) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4A1C1C))
                                    .clickable { onRemoveStation(board.stationId) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Remove ${board.stationName}",
                                    tint = Color(0xFFFF8A80),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (availableToAdd.isNotEmpty()) {
                item(key = "add_header") {
                    Text(
                        text = "+ Add Concourse Hub",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp
                        ),
                        color = Color(0xFF81D4FA),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                items(
                    items = availableToAdd,
                    key = { "add_${it.stationId}" }
                ) { candidate ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF101D26),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0277BD).copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onAddStation(candidate) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${candidate.stationName} (${candidate.stationCrs})",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                ),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0277BD)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Add ${candidate.stationName}",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WearTrackedJourneyCard(
    journey: WearTrackedJourneyState,
    onOpenOnPhone: () -> Unit,
    onStopTracking: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF0D2538),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4FC3F7)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📱 LIVE TRACKING",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 9.sp
                    ),
                    color = Color(0xFF81D4FA)
                )
                Text(
                    text = journey.formattedCountdown,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    ),
                    color = Color(0xFFFFB74D)
                )
            }

            Text(
                text = "${journey.destinationName} (${journey.lineName})",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val alightInfo = if (!journey.targetAlightStopName.isNullOrBlank()) {
                val stopsStr = journey.stopsAway?.let { " • $it stops" } ?: ""
                "Alight: ${journey.targetAlightStopName}$stopsStr"
            } else {
                journey.currentLocation ?: "Plat ${journey.platformCode}"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = alightInfo,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = Color(0xFFB3E5FC),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0277BD))
                            .clickable(onClick = onOpenOnPhone),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = "Open on Phone",
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF37474F))
                            .clickable(onClick = onStopTracking),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsOff,
                            contentDescription = "Stop tracking",
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WearDepartureCard(
    departure: WearDepartureItem,
    isWatched: Boolean,
    onToggleWatch: () -> Unit,
    onOpenOnPhone: () -> Unit,
    onSimulatePlatformAnnouncement: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF14171C),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isWatched) Color(0xFF4FC3F7).copy(alpha = 0.7f) else Color(0xFF262B33)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                if (onSimulatePlatformAnnouncement != null && isWatched) {
                    onSimulatePlatformAnnouncement()
                } else {
                    onToggleWatch()
                }
            }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 4.dp, height = 16.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(departure.lineColorArgb))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = departure.compactDestination,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = departure.formattedCountdown,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    ),
                    color = if (departure.countdownMinutes <= 2) Color(0xFFFFB74D) else Color(0xFF4FC3F7)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Single-line compact Platform Pill
                    val platBg = when {
                        departure.isPlatformChanged -> Color(0xFFB71C1C)
                        departure.isPlatformTbd -> Color(0xFF37474F)
                        else -> Color(0xFF1B5E20)
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = platBg
                    ) {
                        Text(
                            text = if (departure.isPlatformChanged) {
                                "⚠️ P${departure.cleanPlatformCode}"
                            } else {
                                departure.formattedPlatformShort
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (departure.delayRepayBadge != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF00695C)
                        ) {
                            Text(
                                text = "💸 +15m+",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                ),
                                color = Color(0xFFA7FFEB),
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Text(
                            text = departure.compactStatusOrLine,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = Color(0xFFB0BEC5),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Open on Phone Handoff button
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF263238))
                            .clickable(onClick = onOpenOnPhone),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = "Open on Phone",
                            tint = Color(0xFF81D4FA),
                            modifier = Modifier.size(11.dp)
                        )
                    }

                    // Watch / Track on Phone & Wrist toggle
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(if (isWatched) Color(0xFF0277BD) else Color(0xFF263238))
                            .clickable(onClick = onToggleWatch),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isWatched) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                            contentDescription = if (isWatched) "Watching platform" else "Watch platform",
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }
        }
    }
}
