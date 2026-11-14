package com.androidfung.departureboard.wear.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androidfung.departureboard.wear.model.WearDepartureItem
import com.androidfung.departureboard.wear.model.WearTrackedJourneyState

@Composable
internal fun WearTrackedJourneyCard(
    journey: WearTrackedJourneyState,
    onOpenSprintHud: () -> Unit,
    onOpenOnPhone: () -> Unit,
    onStopTracking: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF0D2538),
        border = BorderStroke(1.dp, Color(0xFF4FC3F7)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onOpenSprintHud)
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
                    text = "🎯 SPRINT HUD • TAP",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 9.sp
                    ),
                    color = Color(0xFF81D4FA)
                )
                Text(
                    text = journey.formattedMmSsCountdown,
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
internal fun WearBezelCountdownRing(
    progressPercent: Int,
    ringColor: Color,
    isAmbient: Boolean
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokePx = if (isAmbient) 1.5.dp.toPx() else 3.dp.toPx()
        val inset = strokePx / 2f + 2.dp.toPx()
        val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
        val topLeft = Offset(inset, inset)

        // Track ring
        drawArc(
            color = if (isAmbient) Color(0xFF14171C) else Color(0xFF1E2229),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx)
        )

        // Countdown progress arc
        val sweep = (progressPercent.coerceIn(5, 100) / 100f) * 360f
        drawArc(
            color = ringColor,
            startAngle = -90f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )
    }
}

@Composable
internal fun WearDepartureCard(
    departure: WearDepartureItem,
    isWatched: Boolean,
    isExpanded: Boolean,
    isAmbientMode: Boolean,
    onToggleExpand: () -> Unit,
    onToggleWatch: () -> Unit,
    onOpenOnPhone: () -> Unit,
    onSimulatePlatformAnnouncement: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isAmbientMode) Color.Black else Color(0xFF14171C),
        border = BorderStroke(
            width = 1.dp,
            color = when {
                isAmbientMode -> Color(0xFF263238)
                isWatched -> Color(0xFF4FC3F7).copy(alpha = 0.7f)
                isExpanded -> Color(0xFF81D4FA).copy(alpha = 0.6f)
                else -> Color(0xFF262B33)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onToggleExpand)
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
                    if (!isAmbientMode) {
                        Box(
                            modifier = Modifier
                                .size(width = 4.dp, height = 16.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(departure.lineColorArgb))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = departure.compactDestination,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        color = if (isAmbientMode) Color(0xFFCFD8DC) else Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                val liveCountdownMins = departure.effectiveCountdownMinutes()
                Text(
                    text = departure.formattedLiveCountdown(),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    ),
                    color = when {
                        isAmbientMode -> Color.White
                        departure.isStale() -> Color(0xFF90A4AE)
                        liveCountdownMins <= 2 -> Color(0xFFFFB74D)
                        else -> Color(0xFF4FC3F7)
                    }
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
                        isAmbientMode -> Color.Black
                        departure.isPlatformChanged -> Color(0xFFB71C1C)
                        departure.isPlatformTbd -> Color(0xFF37474F)
                        else -> Color(0xFF1B5E20)
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = platBg,
                        border = if (isAmbientMode) BorderStroke(1.dp, Color(0xFF546E7A)) else null
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

                    if (!isAmbientMode && departure.delayRepayBadge != null) {
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

                if (!isAmbientMode) {
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

            // Inline Tap-to-Expand Calling Points & Coach Formation Drawer
            if (isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 4.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    val metaParts = buildList {
                        if (departure.coachCount > 1) add("🚃 ${departure.coachCount} coaches")
                        if (departure.scheduledTime.isNotBlank()) add("🕒 ${departure.scheduledTime}")
                        add(departure.lineName)
                    }.joinToString(" • ")

                    Text(
                        text = metaParts,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 9.sp
                        ),
                        color = Color(0xFF81D4FA),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val stopsText = departure.callingPointsSummary?.takeIf { it.isNotBlank() }
                        ?: "Direct / Check calling points on Phone"
                    Text(
                        text = "Calls: $stopsText",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                        color = Color(0xFFCFD8DC),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (onSimulatePlatformAnnouncement != null && departure.isPlatformTbd) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF004D40),
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .clip(RoundedCornerShape(50))
                                .clickable(onClick = onSimulatePlatformAnnouncement)
                        ) {
                            Text(
                                text = "⚡ Simulate Plat Announced",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                ),
                                color = Color(0xFF80CBC4),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
