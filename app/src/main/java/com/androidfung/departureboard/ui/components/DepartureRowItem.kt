package com.androidfung.departureboard.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.TransitMode

/**
 * Line or bus route indicator badge shown on the left of each departure row.
 * Uniform 38dp x 28dp size so departure labels align uniformly:
 * - Bus: Route number badge (e.g. "73", "390", "N5").
 * - Tube / Overground / Elizabeth Line / DLR: Line badge pill.
 */
@Composable
fun DepartureLineRouteIndicator(
    departure: Departure,
    modifier: Modifier = Modifier
) {
    val badge = departure.lineBadge
    val isBus = badge.mode == TransitMode.BUS || departure.modeName.equals("bus", ignoreCase = true)

    val routeText = if (isBus) {
        badge.displayName
    } else {
        badge.lineCode.ifBlank { badge.displayName.take(3).uppercase() }
    }

    val badgeLuminance = 0.299f * badge.backgroundColor.red + 0.587f * badge.backgroundColor.green + 0.114f * badge.backgroundColor.blue
    val isVeryDark = badgeLuminance < 0.2f
    val shape = RoundedCornerShape(8.dp)
    val borderWidth = if (isVeryDark) 0.8.dp else 0.dp
    val borderColor = if (isVeryDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f) else Color.Transparent

    Box(
        modifier = modifier
            .size(width = 38.dp, height = 28.dp)
            .clip(shape)
            .then(
                if (borderWidth > 0.dp) Modifier.border(borderWidth, borderColor, shape)
                else Modifier
            )
            .background(badge.backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = routeText,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = if (routeText.length > 3) 11.sp else 12.sp
            ),
            color = badge.textColor,
            maxLines = 1
        )
    }
}

/**
 * Individual departure row styled with line/route indicator, destination, platform/direction,
 * and high-contrast Pixel Clock countdown display.
 */
@Composable
fun DepartureRowItem(
    departure: Departure,
    isFirst: Boolean,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") showLineBadge: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp, horizontal = 4.dp)
            .semantics {
                contentDescription = "${departure.destinationName}, arriving in ${departure.formattedTimeToArrival}"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        DepartureLineRouteIndicator(departure = departure)

        Spacer(modifier = Modifier.width(12.dp))

        // Destination & Direction details
        Column(modifier = Modifier.weight(1f)) {
            val destinationText = departure.destinationName.ifBlank { departure.lineName }

            Text(
                text = destinationText,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (isFirst) FontWeight.Bold else FontWeight.Medium,
                    fontSize = if (isFirst) 15.sp else 14.5.sp,
                    lineHeight = 18.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Subtitle row: [Platform Pill Tag / Towards] (with compact cardinal abbreviations like NB, SB, EB, WB)
            if (departure.platformName.isNotBlank()) {
                val windowWidthClass = com.androidfung.departureboard.ui.theme.LocalWindowWidthClass.current
                val isNarrow = windowWidthClass.isNarrow

                val platformLabel = if (isNarrow) {
                    departure.platformName
                        .replace("Northbound", "NB", ignoreCase = true)
                        .replace("Southbound", "SB", ignoreCase = true)
                        .replace("Eastbound", "EB", ignoreCase = true)
                        .replace("Westbound", "WB", ignoreCase = true)
                } else {
                    departure.platformName
                }

                Spacer(modifier = Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = platformLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Live Progress Track for upcoming departures (due or arriving within 3 minutes)
            if (departure.timeToStationSeconds <= 180) {
                Spacer(modifier = Modifier.height(4.dp))
                val isBus = departure.modeName.equals("bus", ignoreCase = true)
                LiveTrainTrackIndicator(
                    timeToStationSeconds = departure.timeToStationSeconds,
                    isBus = isBus
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Countdown & Actual Clock Time
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Center
        ) {
            val isCancelled = departure.currentLocation?.equals("Cancelled", ignoreCase = true) == true
            val isDelayed = departure.currentLocation?.contains("Delayed", ignoreCase = true) == true

            CountdownBadge(
                timeToStationSeconds = departure.timeToStationSeconds,
                isFirst = isFirst,
                lineColor = departure.lineBadge.backgroundColor,
                statusText = departure.currentLocation
            )

            val clockTime = departure.formattedActualClockTime
            if (isCancelled) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "No service",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFFE32017)
                )
            } else if (isDelayed) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = departure.currentLocation,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFFFF9800)
                )
            } else if (!clockTime.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = clockTime,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                )
            }
        }
    }
}

/**
 * Mini visual track progression indicator (e.g. ●───○───○ At Platform).
 */
@Composable
fun LiveTrainTrackIndicator(
    timeToStationSeconds: Int,
    modifier: Modifier = Modifier,
    isBus: Boolean = false
) {
    val stage = when {
        timeToStationSeconds <= 30 -> 2 // At platform / stop
        timeToStationSeconds <= 90 -> 1 // Arriving / Approaching
        else -> 0 // In transit
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(3) { step ->
            val isActive = step <= stage
            Box(
                modifier = Modifier
                    .size(if (step == stage) 6.dp else 4.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
            )
            if (step < 2) {
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .height(1.5.dp)
                        .background(
                            if (step < stage) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                )
            }
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = when (stage) {
                2 -> if (isBus) "At stop" else "At platform"
                1 -> "Approaching"
                else -> "In transit"
            },
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = if (stage == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * London platform LED / Flip-style countdown badge with animated number transitions.
 * Also prominently displays Cancelled and Delayed badges.
 */
@Composable
fun CountdownBadge(
    timeToStationSeconds: Int,
    isFirst: Boolean,
    lineColor: Color,
    modifier: Modifier = Modifier,
    statusText: String? = null
) {
    val isCancelled = statusText?.equals("Cancelled", ignoreCase = true) == true
    val minutes = timeToStationSeconds / 60
    val isDue = timeToStationSeconds <= 30

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        if (isCancelled) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE32017).copy(alpha = 0.15f))
                    .border(1.dp, Color(0xFFE32017).copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "CANCELLED",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp,
                        fontSize = 11.sp
                    ),
                    color = Color(0xFFE32017)
                )
            }
        } else if (isDue) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE32017).copy(alpha = 0.15f))
                    .border(1.dp, Color(0xFFE32017).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "DUE",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                        fontSize = 14.sp
                    ),
                    color = Color(0xFFE32017)
                )
            }
        } else {
            // London Platform Dot-Matrix LED / Flip Card Display
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF161A22))
                    .border(1.dp, Color(0xFF282E3E), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    AnimatedContent(
                        targetState = minutes,
                        transitionSpec = {
                            if (targetState > initialState) {
                                slideInVertically { height -> height } + fadeIn() togetherWith
                                        slideOutVertically { height -> -height } + fadeOut()
                            } else {
                                slideInVertically { height -> -height } + fadeIn() togetherWith
                                        slideOutVertically { height -> height } + fadeOut()
                            }
                        },
                        label = "MinutesFlipAnimation"
                    ) { targetMinutes ->
                        Text(
                            text = targetMinutes.toString(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = if (isFirst) 17.sp else 15.sp
                            ),
                            color = Color(0xFFFFB800)
                        )
                    }
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "m",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        ),
                        color = Color(0xFFFFB800).copy(alpha = 0.75f)
                    )
                }
            }
        }
    }
}
