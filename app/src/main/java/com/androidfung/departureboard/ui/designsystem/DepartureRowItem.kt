package com.androidfung.departureboard.ui.designsystem

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
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androidfung.departureboard.R
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.OccupancyStatus
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
    trailingAction: (@Composable () -> Unit)? = null,
    busSpacing: com.androidfung.departureboard.util.BusSpacingInfo? = null
) {
    val departureContentDescription = if (departure.status == DepartureStatus.CANCELLED) {
        stringResource(R.string.cd_departure_cancelled, departure.lineName, departure.destinationName)
    } else {
        stringResource(R.string.cd_departure_departing, departure.lineName, departure.destinationName, departure.formattedTimeToArrival)
    }

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
                contentDescription = departureContentDescription
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Line / Mode Indicator
        DepartureLineRouteIndicator(departure = departure)

        Spacer(modifier = Modifier.width(MaterialTheme.dimensions.paddingLarge))

        // Destination & Platform details
        Column(modifier = Modifier.weight(1f)) {
            val destinationText = departure.destinationName.ifBlank { departure.lineName }
            val isCancelled = departure.status == DepartureStatus.CANCELLED

            Text(
                text = destinationText,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (isFirst || variant == DepartureRowVariant.DETAILED) FontWeight.Bold else FontWeight.Medium,
                    fontSize = if (isFirst || variant == DepartureRowVariant.DETAILED) 15.sp else 14.5.sp,
                    lineHeight = 18.sp,
                    textDecoration = if (isCancelled) androidx.compose.ui.text.style.TextDecoration.LineThrough else androidx.compose.ui.text.style.TextDecoration.None
                ),
                color = if (isCancelled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f) else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            val delayRepay = androidx.compose.runtime.remember(
                departure.id,
                departure.stationId,
                departure.status,
                departure.lineId,
                departure.lineName,
                departure.modeName,
                departure.operator?.name,
                departure.scheduledDepartureInstant,
                departure.expectedDepartureInstant,
                departure.callingPoints
            ) {
                com.androidfung.departureboard.util.DelayRepayCalculator.evaluate(departure)
            }

            // Subtitle row: [Platform Pill Tag / Towards / Formation / Operator / Boarding Semantics / Last Train / Delay Repay]
            if (departure.platformName.isNotBlank() || departure.isWheelchairAccessible == true || !departure.occupancyStatus.isNullOrBlank() || departure.formation != null || departure.isSetDownOnly || departure.isPickUpOnly || departure.isLastTrain || departure.isNightService || delayRepay != null) {
                val windowWidthClass = com.androidfung.departureboard.ui.theme.LocalWindowWidthClass.current
                val isNarrow = windowWidthClass.isNarrow

                val localizedNorth = stringResource(R.string.dir_northbound)
                val localizedSouth = stringResource(R.string.dir_southbound)
                val localizedEast = stringResource(R.string.dir_eastbound)
                val localizedWest = stringResource(R.string.dir_westbound)
                val useCompactEnglishDirs = isNarrow && variant == DepartureRowVariant.COMPACT

                val platformLabel = departure.platformName
                    .replace(
                        "Northbound",
                        if (useCompactEnglishDirs && localizedNorth.equals("Northbound", ignoreCase = true)) "NB" else localizedNorth,
                        ignoreCase = true
                    )
                    .replace(
                        "Southbound",
                        if (useCompactEnglishDirs && localizedSouth.equals("Southbound", ignoreCase = true)) "SB" else localizedSouth,
                        ignoreCase = true
                    )
                    .replace(
                        "Eastbound",
                        if (useCompactEnglishDirs && localizedEast.equals("Eastbound", ignoreCase = true)) "EB" else localizedEast,
                        ignoreCase = true
                    )
                    .replace(
                        "Westbound",
                        if (useCompactEnglishDirs && localizedWest.equals("Westbound", ignoreCase = true)) "WB" else localizedWest,
                        ignoreCase = true
                    )
                    .replace("Inbound", stringResource(R.string.dir_inbound), ignoreCase = true)
                    .replace("Outbound", stringResource(R.string.dir_outbound), ignoreCase = true)
                    .replace("Anti-Clockwise", stringResource(R.string.dir_anti_clockwise), ignoreCase = true)
                    .replace("Clockwise", stringResource(R.string.dir_clockwise), ignoreCase = true)

                val isPlatformTbd = platformLabel.contains("TBD", ignoreCase = true)

                Spacer(modifier = Modifier.height(3.dp))
                FlowRow(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (platformLabel.isNotBlank()) {
                        Text(
                            text = platformLabel,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (isPlatformTbd) FontWeight.Normal else FontWeight.Medium,
                                fontStyle = if (isPlatformTbd) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            ),
                            color = if (isPlatformTbd) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (departure.isWheelchairAccessible == true) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = stringResource(R.string.status_accessible),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    if (!departure.occupancyStatus.isNullOrBlank()) {
                        val parsed = departure.parsedOccupancyStatus
                        val occupancyLabel = when (parsed) {
                            OccupancyStatus.EMPTY,
                            OccupancyStatus.MANY_SEATS_AVAILABLE,
                            OccupancyStatus.FEW_SEATS_AVAILABLE -> stringResource(R.string.status_seats)
                            OccupancyStatus.STANDING_ROOM_ONLY,
                            OccupancyStatus.CRUSHED_STANDING_ROOM_ONLY -> stringResource(R.string.status_standing)
                            OccupancyStatus.FULL,
                            OccupancyStatus.NOT_ACCEPTING_PASSENGERS -> stringResource(R.string.status_full)
                            null -> departure.occupancyStatus
                        }
                        val occupancyContainerColor = when (parsed) {
                            OccupancyStatus.EMPTY,
                            OccupancyStatus.MANY_SEATS_AVAILABLE -> MaterialTheme.colorScheme.primaryContainer
                            OccupancyStatus.FEW_SEATS_AVAILABLE,
                            OccupancyStatus.STANDING_ROOM_ONLY -> MaterialTheme.colorScheme.tertiaryContainer
                            OccupancyStatus.CRUSHED_STANDING_ROOM_ONLY,
                            OccupancyStatus.FULL,
                            OccupancyStatus.NOT_ACCEPTING_PASSENGERS -> MaterialTheme.colorScheme.errorContainer
                            null -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        val occupancyTextColor = when (parsed) {
                            OccupancyStatus.EMPTY,
                            OccupancyStatus.MANY_SEATS_AVAILABLE -> MaterialTheme.colorScheme.onPrimaryContainer
                            OccupancyStatus.FEW_SEATS_AVAILABLE,
                            OccupancyStatus.STANDING_ROOM_ONLY -> MaterialTheme.colorScheme.onTertiaryContainer
                            OccupancyStatus.CRUSHED_STANDING_ROOM_ONLY,
                            OccupancyStatus.FULL,
                            OccupancyStatus.NOT_ACCEPTING_PASSENGERS -> MaterialTheme.colorScheme.onErrorContainer
                            null -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = occupancyContainerColor
                        ) {
                            Text(
                                text = occupancyLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                color = occupancyTextColor,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    if (departure.formation != null && departure.formation!!.coachCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = departure.formation!!.displayCoachCount,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    if (departure.isSetDownOnly) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = stringResource(R.string.status_set_down_only),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    } else if (departure.isPickUpOnly) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = stringResource(R.string.status_pick_up_only),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }



                    if (departure.platform?.isChanged == true && !departure.platform.previousPlatform.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = "⚠️ " + stringResource(R.string.platform_changed_alert, departure.platform.code.orEmpty(), departure.platform.previousPlatform),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    if (departure.platform?.isHistoricalExpected == true) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                        ) {
                            val confidenceStr = departure.platform.expectedConfidencePercent?.let { " ($it%)" } ?: ""
                            Text(
                                text = androidx.compose.ui.res.stringResource(
                                    com.androidfung.departureboard.R.string.typically_platform_code,
                                    departure.platform.code,
                                    confidenceStr
                                ),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    val isDarkTheme = com.androidfung.departureboard.ui.theme.LocalIsDarkTheme.current
                    if (departure.isLastTrain) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFFFB300).copy(alpha = 0.22f),
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFFFFB300).copy(alpha = 0.65f))
                        ) {
                            Text(
                                text = stringResource(R.string.badge_last_train),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold),
                                color = if (isDarkTheme) Color(0xFFFFB300) else Color(0xFF8C5000),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    } else if (departure.isNightService) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF7C4DFF).copy(alpha = 0.20f),
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFF7C4DFF).copy(alpha = 0.55f))
                        ) {
                            Text(
                                text = stringResource(R.string.badge_night_service),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = if (isDarkTheme) Color(0xFFB388FF) else Color(0xFF5E35B1),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }

                    if (busSpacing != null && busSpacing.isBunched) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = "⚠️ " + (busSpacing.displaySummary ?: "Bunched (2 buses together)"),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    } else if (busSpacing != null && busSpacing.severity == com.androidfung.departureboard.util.BusSpacingSeverity.LONG_GAP) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "⏱️ ${busSpacing.displaySummary}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    if (delayRepay != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF00897B).copy(alpha = 0.20f),
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFF00897B).copy(alpha = 0.65f))
                        ) {
                            Text(
                                text = "💸 ${delayRepay.badgeLabel}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = if (isDarkTheme) Color(0xFF4DB6AC) else Color(0xFF00695C),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }

                    if (departure.fare != null) {
                        val isPeak = departure.fare.period == com.androidfung.departureboard.data.model.FarePeriod.PEAK
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isPeak) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = if (isPeak) stringResource(R.string.fare_peak) else stringResource(R.string.fare_off_peak),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                                color = if (isPeak) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            // Reason for delay or cancellation in detailed view
            if (variant == DepartureRowVariant.DETAILED && !departure.cancelReason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = departure.cancelReason!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (variant == DepartureRowVariant.DETAILED && !departure.delayReason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = departure.delayReason!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (variant == DepartureRowVariant.DETAILED && !departure.currentLocation.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = departure.currentLocation,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Carriage Formation Crowding Bar
            if (departure.formation != null && departure.formation!!.coaches.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                CarriageFormationBar(
                    formation = departure.formation!!,
                    isCompact = variant == DepartureRowVariant.COMPACT
                )
            }

            // Live Progress Track for upcoming departures on cards
            if (variant == DepartureRowVariant.COMPACT && departure.timeToStationSeconds <= 180) {
                Spacer(modifier = Modifier.height(4.dp))
                val isBus = departure.isBus
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
                    text = stringResource(R.string.status_cancelled).uppercase(),
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
        departure.frequency != null && departure.timeToStationSeconds <= 0 -> {
            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = departure.frequency.displayHeadway,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
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
                        text = stringResource(R.string.status_due).uppercase(),
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
                        AnimatedContent(
                            targetState = if (minutes < 1) "<1" else minutes.toString(),
                            transitionSpec = {
                                if (targetState != initialState) {
                                    (slideInVertically { height -> -height } + fadeIn()) togetherWith
                                        (slideOutVertically { height -> height } + fadeOut())
                                } else {
                                    fadeIn() togetherWith fadeOut()
                                }
                            },
                            label = "CountdownDigitRoll"
                        ) { minText ->
                            Text(
                                text = minText,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = if (isFirst) 14.sp else 13.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = stringResource(R.string.unit_min),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isDelayed) {
                        Spacer(modifier = Modifier.height(1.dp))
                        val isDarkTheme = com.androidfung.departureboard.ui.theme.LocalIsDarkTheme.current
                        val delayedColor = if (isDarkTheme) Color(0xFFFFB74D) else Color(0xFFC05600)
                        Text(
                            text = stringResource(R.string.status_delayed),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = delayedColor
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
        val statusText = when (stage) {
            2 -> if (isBus) {
                androidx.compose.ui.res.stringResource(com.androidfung.departureboard.R.string.status_at_stop)
            } else {
                androidx.compose.ui.res.stringResource(com.androidfung.departureboard.R.string.status_at_platform)
            }
            1 -> androidx.compose.ui.res.stringResource(com.androidfung.departureboard.R.string.status_approaching)
            else -> androidx.compose.ui.res.stringResource(com.androidfung.departureboard.R.string.status_in_transit)
        }
        Text(
            text = statusText,
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
    val isBus = departure.isBus
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
