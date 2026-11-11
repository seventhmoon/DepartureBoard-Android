package com.androidfung.departureboard.wear.ui

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androidfung.departureboard.wear.haptic.WearNotificationPublisher
import kotlinx.coroutines.launch

@Composable
internal fun WearNotificationSettingsScreen(
    prefs: WearNotificationPublisher.WearNotificationPreferences,
    onUpdatePrefs: (
        liveTracking: Boolean,
        autoHud: Boolean,
        platform: Boolean,
        alighting: Boolean,
        disruption: Boolean
    ) -> Unit,
    onOpenSystemChannelSettings: (String?) -> Unit,
    onBack: () -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    BackHandler(onBack = onBack)

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
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
                .focusRequester(focusRequester)
                .focusable(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item(key = "notif_header") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "🔔 Notification Channels",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Toggle alert types or customize sound/vibration",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                        color = Color(0xFF90A4AE),
                        textAlign = TextAlign.Center
                    )
                }
            }

            item(key = "toggle_live_tracking") {
                WearNotificationToggleRow(
                    title = "Live Train Tracker",
                    subtitle = "Ongoing countdown & progress bar",
                    enabled = prefs.liveTrackingEnabled,
                    onToggle = {
                        onUpdatePrefs(
                            !prefs.liveTrackingEnabled,
                            prefs.autoLaunchSprintHudEnabled,
                            prefs.platformAlertsEnabled,
                            prefs.alightingAlertsEnabled,
                            prefs.disruptionAlertsEnabled
                        )
                    },
                    onChannelSettings = {
                        onOpenSystemChannelSettings(WearNotificationPublisher.CHANNEL_TRACKING_ID)
                    }
                )
            }

            item(key = "toggle_auto_hud") {
                WearNotificationToggleRow(
                    title = "Instant Sprint HUD",
                    subtitle = "Wake watch when Track tapped on phone",
                    enabled = prefs.autoLaunchSprintHudEnabled,
                    onToggle = {
                        onUpdatePrefs(
                            prefs.liveTrackingEnabled,
                            !prefs.autoLaunchSprintHudEnabled,
                            prefs.platformAlertsEnabled,
                            prefs.alightingAlertsEnabled,
                            prefs.disruptionAlertsEnabled
                        )
                    },
                    onChannelSettings = {
                        onOpenSystemChannelSettings(WearNotificationPublisher.CHANNEL_SPRINT_HUD_ID)
                    }
                )
            }

            item(key = "toggle_platform_alerts") {
                WearNotificationToggleRow(
                    title = "Platform Alerts",
                    subtitle = "TBD → Plat X & platform changes",
                    enabled = prefs.platformAlertsEnabled,
                    onToggle = {
                        onUpdatePrefs(
                            prefs.liveTrackingEnabled,
                            prefs.autoLaunchSprintHudEnabled,
                            !prefs.platformAlertsEnabled,
                            prefs.alightingAlertsEnabled,
                            prefs.disruptionAlertsEnabled
                        )
                    },
                    onChannelSettings = {
                        onOpenSystemChannelSettings(WearNotificationPublisher.CHANNEL_PLATFORM_ALERTS_ID)
                    }
                )
            }

            item(key = "toggle_alighting_alerts") {
                WearNotificationToggleRow(
                    title = "Alighting Stop Alerts",
                    subtitle = "1 stop before target destination",
                    enabled = prefs.alightingAlertsEnabled,
                    onToggle = {
                        onUpdatePrefs(
                            prefs.liveTrackingEnabled,
                            prefs.autoLaunchSprintHudEnabled,
                            prefs.platformAlertsEnabled,
                            !prefs.alightingAlertsEnabled,
                            prefs.disruptionAlertsEnabled
                        )
                    },
                    onChannelSettings = {
                        onOpenSystemChannelSettings(WearNotificationPublisher.CHANNEL_ALIGHTING_ALERTS_ID)
                    }
                )
            }

            item(key = "toggle_disruption_alerts") {
                WearNotificationToggleRow(
                    title = "Delay & Cancellation",
                    subtitle = "Delay Repay & disruption alerts",
                    enabled = prefs.disruptionAlertsEnabled,
                    onToggle = {
                        onUpdatePrefs(
                            prefs.liveTrackingEnabled,
                            prefs.autoLaunchSprintHudEnabled,
                            prefs.platformAlertsEnabled,
                            prefs.alightingAlertsEnabled,
                            !prefs.disruptionAlertsEnabled
                        )
                    },
                    onChannelSettings = {
                        onOpenSystemChannelSettings(WearNotificationPublisher.CHANNEL_DISRUPTION_ALERTS_ID)
                    }
                )
            }

            item(key = "system_all_channels") {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF1E2229),
                    border = BorderStroke(1.dp, Color(0xFF4FC3F7).copy(alpha = 0.6f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { onOpenSystemChannelSettings(null) }
                ) {
                    Text(
                        text = "⚙ All System Channels",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = Color(0xFF81D4FA),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            item(key = "back_btn") {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF263238),
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
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WearNotificationToggleRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onToggle: () -> Unit,
    onChannelSettings: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (enabled) Color(0xFF142229) else Color(0xFF14161A),
        border = BorderStroke(
            1.dp,
            if (enabled) Color(0xFF00E676).copy(alpha = 0.55f) else Color(0xFF37474F)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .toggleable(
                value = enabled,
                role = Role.Switch,
                onValueChange = { onToggle() }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                    color = Color(0xFF90A4AE),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (enabled) Color(0xFF00C853) else Color(0xFF37474F)
                ) {
                    Text(
                        text = if (enabled) "ON" else "OFF",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 9.sp
                        ),
                        color = if (enabled) Color.Black else Color(0xFFB0BEC5),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF263238),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .semantics {
                            role = Role.Button
                            contentDescription = "$title channel settings"
                        }
                        .clickable(onClick = onChannelSettings)
                ) {
                    Text(
                        text = "⚙",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = Color(0xFF81D4FA),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}
