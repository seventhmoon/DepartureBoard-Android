package com.androidfung.departureboard.ui.feature.ai

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsTransit
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidfung.departureboard.R
import com.androidfung.departureboard.ai.A2UiAction
import com.androidfung.departureboard.ai.A2UiBlock
import com.androidfung.departureboard.ai.A2UiResponse
import com.androidfung.departureboard.ai.A2UiSeverity
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.service.TrainTrackingService

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun A2UiRenderer(
    response: A2UiResponse,
    modifier: Modifier = Modifier,
    onActionClick: (A2UiAction) -> Unit = {}
) {
    val context = LocalContext.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        response.blocks.forEach { block ->
            when (block) {
                is A2UiBlock.TextSummary -> {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = block.message,
                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                is A2UiBlock.LineStatusBanner -> {
                    LineStatusCard(block)
                }

                is A2UiBlock.LiveDeparturesCard -> {
                    val trackedDepartureId by TrainTrackingService.currentlyTrackedDepartureIdFlow.collectAsStateWithLifecycle()
                    var pendingTrackDeparture by remember { mutableStateOf<Departure?>(null) }
                    val notificationPermissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { isGranted ->
                        if (isGranted) {
                            pendingTrackDeparture?.let { dep ->
                                TrainTrackingService.startTracking(context, dep)
                            }
                        }
                        pendingTrackDeparture = null
                    }

                    DeparturesActionCard(
                        station = block.station,
                        departures = block.departures,
                        trackedDepartureId = trackedDepartureId,
                        onTrackDeparture = { dep ->
                            if (trackedDepartureId == dep.id) {
                                TrainTrackingService.stopTracking(context)
                            } else {
                                com.androidfung.departureboard.util.NotificationHelper.startTrackingWithPermissionCheck(context, dep) { pendingDep ->
                                    pendingTrackDeparture = pendingDep
                                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                            onActionClick(A2UiAction.TrackTrain(dep))
                        }
                    )
                }

                is A2UiBlock.ActionButtons -> {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        block.actions.forEach { action ->
                            ActionButtonChip(action, context, onActionClick)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LineStatusCard(block: A2UiBlock.LineStatusBanner) {
    val (bgColor, icon, tint) = when (block.severity) {
        A2UiSeverity.GOOD_SERVICE -> Triple(
            Color(0xFFE8F5E9),
            Icons.Rounded.CheckCircle,
            Color(0xFF2E7D32)
        )
        A2UiSeverity.WARNING -> Triple(
            Color(0xFFFFF8E1),
            Icons.Rounded.Warning,
            Color(0xFFF57F17)
        )
        A2UiSeverity.DISRUPTION -> Triple(
            Color(0xFFFFEBEE),
            Icons.Rounded.Error,
            Color(0xFFC62828)
        )
        A2UiSeverity.INFO -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            Icons.Rounded.DirectionsTransit,
            MaterialTheme.colorScheme.primary
        )
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = block.lineName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.Black
                )
                Text(
                    text = block.statusDescription,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = tint
                )
                if (!block.reason.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = block.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.DarkGray
                    )
                }
            }
        }
    }
}

@Composable
private fun DeparturesActionCard(
    station: Station,
    departures: List<Departure>,
    trackedDepartureId: String?,
    onTrackDeparture: (Departure) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = station.displayName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.ai_live_services),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            departures.forEachIndexed { idx, dep ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = dep.destinationName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val platText = if (dep.platformName.isNotBlank()) " • Plat ${dep.platformName}" else ""
                        Text(
                            text = "${dep.lineName}$platText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = dep.formattedTimeToArrival,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        // Track button
                        val isTracked = trackedDepartureId == dep.id
                        AssistChip(
                            onClick = { onTrackDeparture(dep) },
                            label = {
                                Text(
                                    if (isTracked) stringResource(R.string.tracking_action_untrack) else stringResource(R.string.tracking_action_track),
                                    fontSize = 11.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.NotificationsActive,
                                    contentDescription = null,
                                    tint = if (isTracked) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (isTracked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                }

                if (idx < departures.lastIndex) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun ActionButtonChip(
    action: A2UiAction,
    context: Context,
    onActionClick: (A2UiAction) -> Unit
) {
    when (action) {
        is A2UiAction.OpenGoogleMaps -> {
            AssistChip(
                onClick = {
                    val uri = "google.navigation:q=${android.net.Uri.encode(action.destination)}&mode=r".toUri()
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    onActionClick(action)
                },
                label = { Text(stringResource(R.string.ai_action_maps)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Map,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }

        is A2UiAction.OpenTflJourneyPlanner -> {
            AssistChip(
                onClick = {
                    val url = "https://tfl.gov.uk/plan-a-journey/results?From=${android.net.Uri.encode(action.origin)}&To=${android.net.Uri.encode(action.destination)}"
                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                    onActionClick(action)
                },
                label = { Text(stringResource(R.string.ai_action_tfl)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.DirectionsTransit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }

        is A2UiAction.PinStation -> {
            AssistChip(
                onClick = { onActionClick(action) },
                label = { Text(stringResource(R.string.ai_action_pin, action.station.displayName.substringBefore("(").trim())) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.PushPin,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }

        is A2UiAction.QueryPrompt -> {
            AssistChip(
                onClick = { onActionClick(action) },
                label = { Text(action.text) }
            )
        }

        is A2UiAction.TrackTrain -> {}
    }
}
