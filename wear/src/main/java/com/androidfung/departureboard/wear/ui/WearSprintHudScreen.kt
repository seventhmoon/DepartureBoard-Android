package com.androidfung.departureboard.wear.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androidfung.departureboard.wear.model.WearSnapshotFormatter
import com.androidfung.departureboard.wear.model.WearTrackedJourneyState
import com.androidfung.departureboard.wear.sync.WearCompanionStateRepository
import kotlinx.coroutines.launch

@Composable
internal fun WearSprintHudScreen(
    journey: WearTrackedJourneyState,
    isAmbientMode: Boolean = false,
    onToggleManualAmbient: () -> Unit = {},
    onSelectAlightStop: (String?) -> Unit,
    onOpenOnPhone: () -> Unit,
    onStopTracking: () -> Unit,
    onBackToBoard: () -> Unit
) {
    val hudListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusReq = remember { FocusRequester() }

    BackHandler(onBack = onBackToBoard)

    LaunchedEffect(Unit) {
        runCatching { focusReq.requestFocus() }
    }

    val ringColor = when {
        isAmbientMode -> Color(0xFF78909C)
        journey.isCancelled -> Color(0xFFE53935)
        journey.remainingSeconds <= 120 -> Color(0xFFFFB74D)
        !journey.isPlatformTbd -> Color(0xFF00E676)
        else -> Color(0xFF4FC3F7)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        WearBezelCountdownRing(
            progressPercent = journey.progressPercent,
            ringColor = ringColor,
            isAmbient = isAmbientMode
        )

        LazyColumn(
            state = hudListState,
            modifier = Modifier
                .fillMaxSize()
                .onRotaryScrollEvent { event ->
                    scope.launch { hudListState.scrollBy(event.verticalScrollPixels) }
                    true
                }
                .focusRequester(focusReq)
                .focusable(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 26.dp, bottom = 46.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item(key = "hud_back") {
                if (isAmbientMode) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.Black,
                        border = BorderStroke(1.dp, Color(0xFF37474F)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onToggleManualAmbient)
                    ) {
                        Text(
                            text = "🌙 Ambient • Tap to Wake",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            color = Color(0xFF78909C),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF1E2229),
                            border = BorderStroke(1.dp, Color(0xFF37474F)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable(onClick = onBackToBoard)
                        ) {
                            Text(
                                text = "← Board",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                ),
                                color = Color(0xFF81D4FA),
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF14171C),
                            border = BorderStroke(1.dp, Color(0xFF37474F)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable(onClick = onToggleManualAmbient)
                        ) {
                            Text(
                                text = "🌙",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            // Giant Concourse Platform Hero Badge + Live MM:SS Timer (minutes-only in Ambient Mode)
            item(key = "hud_hero") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val platBg = when {
                        isAmbientMode -> Color.Black
                        journey.isPlatformTbd -> Color(0xFF37474F)
                        else -> Color(0xFF1B5E20)
                    }
                    val platBorder = when {
                        isAmbientMode -> Color(0xFF90A4AE)
                        journey.isPlatformTbd -> Color(0xFF78909C)
                        else -> Color(0xFF00E676)
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = platBg,
                        border = BorderStroke(1.dp, platBorder)
                    ) {
                        val expectedPlat = journey.expectedPlatformCode?.takeIf { it.isNotBlank() && !it.equals("TBD", ignoreCase = true) }
                        val heroPlatLabel = when {
                            !journey.isPlatformTbd -> "PLAT ${journey.cleanPlatformCode}"
                            expectedPlat != null -> "TBD (~P$expectedPlat)"
                            else -> "PLAT TBD"
                        }
                        Text(
                            text = heroPlatLabel,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            ),
                            color = if (isAmbientMode) Color(0xFFECEFF1) else Color.White,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }

                    val timerText = if (isAmbientMode) {
                        val mins = (journey.remainingSeconds / 60).coerceAtLeast(0)
                        if (mins <= 0) "Due" else "${mins}m"
                    } else {
                        journey.formattedMmSsCountdown
                    }
                    Text(
                        text = timerText,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        ),
                        color = if (isAmbientMode) Color(0xFFCFD8DC) else ringColor,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // Destination & Line
            item(key = "hud_dest") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = WearSnapshotFormatter.compactStationName(journey.destinationName),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val locText = if (!journey.targetAlightStopName.isNullOrBlank()) {
                        val stopsStr = journey.stopsAway?.let { " • $it stop(s)" }.orEmpty()
                        "🎯 Alight: ${journey.targetAlightStopName}$stopsStr"
                    } else {
                        journey.currentLocation?.takeIf { it.isNotBlank() } ?: journey.lineName
                    }
                    Text(
                        text = locText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = Color(0xFF81D4FA),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Bottom HUD Actions: Full-width comfortable scrolling action chips (hidden in Ambient Mode)
            if (!isAmbientMode) {
                item(key = "hud_actions") {
                    val context = LocalContext.current
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .padding(top = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Share ETA
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF003D33),
                            border = BorderStroke(1.dp, Color(0xFF26A69A)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    WearCompanionStateRepository.shareLiveEtaFromWatch(context, journey)
                                    Toast.makeText(context, "Sharing ETA on Phone…", Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📤 Share Live ETA",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    color = Color(0xFF80CBC4),
                                    maxLines = 1
                                )
                            }
                        }

                        // Open on Phone
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF0D2538),
                            border = BorderStroke(1.dp, Color(0xFF4FC3F7)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClick = onOpenOnPhone)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📱 Open on Phone",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    color = Color(0xFF81D4FA),
                                    maxLines = 1
                                )
                            }
                        }

                        // Stop Tracking
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF3E1A1A),
                            border = BorderStroke(1.dp, Color(0xFFEF5350)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClick = onStopTracking)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "⏹ Stop Tracking",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    color = Color(0xFFFFCDD2),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // On-Watch Target Alighting Stop Selector (when calling points are available & not in Ambient Mode)
            if (!isAmbientMode && journey.callingPoints.isNotEmpty()) {
                item(key = "hud_alight_header", contentType = "alight_header") {
                    Text(
                        text = "🎯 Tap Stop for Alight Alert",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        ),
                        color = Color(0xFFB0BEC5),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                itemsIndexed(
                    items = journey.callingPoints,
                    key = { index, stopName -> "stop_${index}_$stopName" },
                    contentType = { _, _ -> "calling_point" }
                ) { _, stopName ->
                    val isSelected = journey.targetAlightStopName.equals(stopName, ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) Color(0xFF004D40) else Color(0xFF14171C),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) Color(0xFF26A69A) else Color(0xFF262B33)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                onSelectAlightStop(if (isSelected) null else stopName)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isSelected) "🔔 $stopName" else stopName,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                    fontSize = 10.sp
                                ),
                                color = if (isSelected) Color(0xFF80CBC4) else Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isSelected) {
                                Text(
                                    text = "1-stop alert",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    color = Color(0xFFA7FFEB)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
