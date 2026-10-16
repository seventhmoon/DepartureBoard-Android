package com.androidfung.departureboard.ui.components

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsRailway
import androidx.compose.material.icons.rounded.DirectionsSubway
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Tram
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.ui.theme.dimensions

/**
 * Display size variant for DepartureRowItem.
 */
enum class DepartureRowVariant {
    COMPACT, // For station departure cards in dashboard
    DETAILED // For station detail bottom sheet
}

/**
 * Reusable departure row component styled with line/route indicator, destination, platform,
 * clean Material 3 countdown badge, and optional trailing notification action.
 */
@Composable
fun DepartureRowItem(
    departure: Departure,
    isFirst: Boolean,
    modifier: Modifier = Modifier,
    variant: DepartureRowVariant = DepartureRowVariant.COMPACT,
    showLineBadge: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailingAction: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MaterialTheme.dimensions.cornerRadiusLarge))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(
                vertical = if (variant == DepartureRowVariant.DETAILED) 10.dp else 9.dp,
                horizontal = MaterialTheme.dimensions.paddingSmall
            )
            .semantics {
                contentDescription = "${departure.lineName} to ${departure.destinationName}, ${if (departure.status == DepartureStatus.CANCELLED) "cancelled" else "departing in " + departure.formattedTimeToArrival}"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Line / Mode Indicator
        DepartureLineRouteIndicator(departure = departure)

        Spacer(modifier = Modifier.width(MaterialTheme.dimensions.paddingLarge))

        // Destination & Platform details
        Column(modifier = Modifier.weight(1f)) {
            val destinationText = departure.destinationName.ifBlank { departure.lineName }

            Text(
                text = destinationText,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (isFirst || variant == DepartureRowVariant.DETAILED) FontWeight.Bold else FontWeight.Medium,
                    fontSize = if (isFirst || variant == DepartureRowVariant.DETAILED) 15.sp else 14.5.sp,
                    lineHeight = 18.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Subtitle row: [Platform Pill Tag / Towards]
            if (departure.platformName.isNotBlank()) {
                val windowWidthClass = com.androidfung.departureboard.ui.theme.LocalWindowWidthClass.current
                val isNarrow = windowWidthClass.isNarrow

                val platformLabel = if (isNarrow && variant == DepartureRowVariant.COMPACT) {
                    departure.platformName
                        .replace("Northbound", "NB", ignoreCase = true)
                        .replace("Southbound", "SB", ignoreCase = true)
                        .replace("Eastbound", "EB", ignoreCase = true)
                        .replace("Westbound", "WB", ignoreCase = true)
                } else {
                    departure.platformName
                }

                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = platformLabel,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Current location subtitle if available (especially in detailed view)
            if (variant == DepartureRowVariant.DETAILED && !departure.currentLocation.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = departure.currentLocation,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Live Progress Track for upcoming departures on cards
            if (variant == DepartureRowVariant.COMPACT && departure.timeToStationSeconds <= 180) {
                Spacer(modifier = Modifier.height(4.dp))
                val isBus = departure.modeName.equals("bus", ignoreCase = true)
                LiveTrainTrackIndicator(
                    timeToStationSeconds = departure.timeToStationSeconds,
                    isBus = isBus
                )
            }
        }

        Spacer(modifier = Modifier.width(MaterialTheme.dimensions.paddingSmall))

        // Trailing Section: Notification bell on left + Unified Countdown & Clock Time Pill on right
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (trailingAction != null) {
                trailingAction()
            }

            UnifiedDepartureTimeBadge(
                departure = departure,
                isFirst = isFirst
            )
        }
    }
}

/**
 * Clean unified departure badge combining real-time countdown and scheduled clock departure time
 * into a single cohesive Material 3 pill container.
 */
@Composable
fun UnifiedDepartureTimeBadge(
    departure: Departure,
    isFirst: Boolean,
    modifier: Modifier = Modifier
) {
    val status = departure.status
    val isCancelled = status == DepartureStatus.CANCELLED
    val isDelayed = status == DepartureStatus.DELAYED
    val isDue = status == DepartureStatus.DUE
    val minutes = departure.timeToStationSeconds / 60
    val clockTime = departure.formattedActualClockTime

    when {
        isCancelled -> {
            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE32017).copy(alpha = 0.15f))
                    .border(1.dp, Color(0xFFE32017).copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 9.dp, vertical = 4.dp),
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
        }
        isDue -> {
            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "DUE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.8.sp,
                            fontSize = 12.sp
                        ),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    if (!clockTime.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = clockTime,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }
        else -> {
            // Standard countdown & scheduled clock time container
            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (minutes < 1) "<1" else minutes.toString(),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = if (isFirst) 14.sp else 13.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "min",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isDelayed) {
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = "Delayed",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color(0xFFFF9800)
                        )
                    } else if (!clockTime.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = clockTime,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                        )
                    }
                }
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
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
            )
            if (step < 2) {
                Box(
                    modifier = Modifier
                        .size(width = 10.dp, height = 2.dp)
                        .background(
                            if (step < stage) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
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
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Line / Route Indicator matching TfL identity (e.g. Bus red rounded badge vs vertical colored line bar).
 */
@Composable
fun DepartureLineRouteIndicator(
    departure: Departure,
    modifier: Modifier = Modifier
) {
    val isBus = departure.modeName.equals(TransitMode.BUS.id, ignoreCase = true)
    val badge = departure.lineBadge

    if (isBus) {
        Box(
            modifier = modifier
                .size(width = 40.dp, height = 26.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(badge.backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            val routeText = badge.lineCode.ifBlank { departure.lineName }
            Text(
                text = routeText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    letterSpacing = 0.sp
                ),
                color = badge.textColor,
                maxLines = 1
            )
        }
    } else {
        Box(
            modifier = modifier
                .size(width = 4.5.dp, height = 34.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(badge.backgroundColor)
        )
    }
}
