package com.androidfung.departureboard.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsRailway
import androidx.compose.material.icons.rounded.DirectionsSubway
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Tram
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androidfung.departureboard.data.model.DefaultStations
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TflLineColors
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.ui.dashboard.StationCardUiModel
import com.androidfung.departureboard.ui.theme.DepartureBoardTheme

/**
 * Pixel Clock-inspired departure card displaying a station, TfL line badges,
 * and upcoming departures with live countdown badges.
 * Supports swipe-to-dismiss for removing stations.
 */
@Composable
fun StationDepartureCard(
    cardModel: StationCardUiModel,
    onDismissStation: (Station) -> Unit,
    modifier: Modifier = Modifier,
    isNearest: Boolean = false,
    distanceMeters: Double? = null,
    onStationClick: ((Station) -> Unit)? = null
) {
    var selectedLineId by rememberSaveable(cardModel.station.id) { mutableStateOf<String?>(null) }

    val filteredDepartures = remember(cardModel.departures, selectedLineId) {
        if (selectedLineId == null) {
            cardModel.departures
        } else {
            cardModel.departures.filter { it.lineId.equals(selectedLineId, ignoreCase = true) }
        }
    }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart || dismissValue == SwipeToDismissBoxValue.StartToEnd) {
                onDismissStation(cardModel.station)
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.fillMaxWidth(),
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color by animateColorAsState(
                targetValue = when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.Settled -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.errorContainer
                },
                animationSpec = tween(durationMillis = 200),
                label = "DismissBackgroundColor"
            )
            val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.Center
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(32.dp))
                    .background(color)
                    .padding(horizontal = 24.dp),
                contentAlignment = alignment
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Delete station",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .then(
                    if (onStationClick != null) Modifier.clickable { onStationClick(cardModel.station) }
                    else Modifier
                ),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            // Gradient background: subtle surface container gradient in both light and dark themes
            val topGradientColor = MaterialTheme.colorScheme.surfaceVariant
            val bottomGradientColor = MaterialTheme.colorScheme.surfaceContainer
            var isExpanded by rememberSaveable(cardModel.station.id) { mutableStateOf(true) }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(topGradientColor, bottomGradientColor)
                        )
                    )
                    .padding(top = 22.dp, bottom = 12.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header: Station Name and Line Badges
                    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                    StationHeaderSection(
                        station = cardModel.station,
                        lineBadges = cardModel.availableLineBadges,
                        selectedLineId = selectedLineId,
                        isExpanded = isExpanded,
                        isNearest = isNearest,
                        distanceMeters = distanceMeters,
                        onToggleExpand = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            isExpanded = !isExpanded
                        },
                        onLineBadgeClick = { badge ->
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            selectedLineId = if (selectedLineId == badge.lineId) null else badge.lineId
                        },
                        isLoading = cardModel.isLoading,
                        modifier = Modifier.padding(horizontal = 22.dp)
                    )

                    androidx.compose.animation.AnimatedVisibility(
                        visible = isExpanded,
                        enter = androidx.compose.animation.expandVertically() + fadeIn(),
                        exit = androidx.compose.animation.shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))

                            // Departure List or Placeholder
                            val showLineBadgeOnEntries = selectedLineId == null && cardModel.availableLineBadges.size > 1
                            StationDeparturesContainer(
                                departures = filteredDepartures,
                                showLineBadge = showLineBadgeOnEntries,
                                isLoading = cardModel.isLoading,
                                errorMessage = cardModel.errorMessage,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Top header inside the card showing Station Name and clickable pill badges for transit lines.
 */
@Composable
private fun StationHeaderSection(
    station: Station,
    lineBadges: List<LineBadgeInfo>,
    selectedLineId: String?,
    isExpanded: Boolean,
    isNearest: Boolean = false,
    distanceMeters: Double? = null,
    onToggleExpand: () -> Unit,
    onLineBadgeClick: (LineBadgeInfo) -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (isNearest) {
                    val distText = if (distanceMeters != null) {
                        val km = distanceMeters / 1000.0
                        if (km < 1.0) "${distanceMeters.toInt()}m away" else "%.1f km away".format(km)
                    } else "Nearby"
                    Text(
                        text = "📍 $distText",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 8.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Expand / Collapse Chevron button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onToggleExpand),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isExpanded) "▲" else "▼",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (lineBadges.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                lineBadges.forEach { badge ->
                    val isSelected = selectedLineId == badge.lineId
                    val isDimmed = selectedLineId != null && !isSelected
                    LinePillBadge(
                        badge = badge,
                        isSelected = isSelected,
                        isDimmed = isDimmed,
                        onClick = { onLineBadgeClick(badge) }
                    )
                }
            }

            // If selected line has disruptions, show a disruption banner right under the badges
            val activeBadge = lineBadges.firstOrNull { it.lineId == selectedLineId }
            if (activeBadge != null && activeBadge.isDisrupted) {
                Spacer(modifier = Modifier.height(8.dp))
                LineDisruptionBanner(badge = activeBadge)
            }
        }
    }
}

/**
 * Line Disruption Alert Banner with warning icon and reason description.
 */
@Composable
fun LineDisruptionBanner(
    badge: LineBadgeInfo,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFFF3CD).copy(alpha = 0.15f))
            .border(1.dp, Color(0xFFFFC107).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = "⚠",
                color = Color(0xFFFFC107),
                fontSize = 13.sp,
                modifier = Modifier.padding(end = 6.dp)
            )
            Column {
                Text(
                    text = "${badge.displayName}: ${badge.statusDescription ?: "Delays"}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFFFC107)
                )
                if (!badge.disruptionReason.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = badge.disruptionReason,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * TfL-branded pill chip badge displaying official line colors and typography with selection/dimmed states.
 */
@Composable
fun LinePillBadge(
    badge: LineBadgeInfo,
    isSelected: Boolean = false,
    isDimmed: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isBusOrNumeric = badge.mode == TransitMode.BUS || badge.displayName.all { it.isDigit() }
    val shape = if (isBusOrNumeric && badge.displayName.length <= 3) CircleShape else RoundedCornerShape(16.dp)

    val alpha = if (isDimmed) 0.35f else 1.0f
    val borderWidth = if (isSelected) 2.dp else if (badge.isDisrupted) 1.5.dp else 0.dp
    val borderColor = if (isSelected) MaterialTheme.colorScheme.onSurface else if (badge.isDisrupted) Color(0xFFFFC107) else Color.Transparent

    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (borderWidth > 0.dp) Modifier.border(borderWidth, borderColor, shape)
                else Modifier
            )
            .background(badge.backgroundColor.copy(alpha = alpha))
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            )
            .padding(horizontal = if (isBusOrNumeric && badge.displayName.length <= 3) 10.dp else 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = badge.displayName,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    fontSize = 13.sp
                ),
                color = badge.textColor.copy(alpha = if (isDimmed) 0.6f else 1.0f),
                maxLines = 1
            )
            if (badge.isDisrupted) {
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFC107))
                )
            }
        }
    }
}

/**
 * Inner rounded sheet for departure rows, matching the Pixel Clock card design.
 */
@Composable
private fun StationDeparturesContainer(
    departures: List<Departure>,
    showLineBadge: Boolean = false,
    isLoading: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .padding(vertical = 8.dp, horizontal = 14.dp)
    ) {
        when {
            departures.isNotEmpty() -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val displayList = departures.take(5)
                    displayList.forEachIndexed { index, departure ->
                        DepartureRowItem(
                            departure = departure,
                            isFirst = index == 0,
                            showLineBadge = showLineBadge
                        )
                        if (index < displayList.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 38.dp, end = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                thickness = 0.8.dp
                            )
                        }
                    }
                }
            }

            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Loading live departures…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No upcoming departures",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Individual departure row styled with transit mode icon, destination, platform/direction,
 * line badge (when multiple lines exist), and high-contrast Pixel Clock countdown display.
 */
@Composable
fun DepartureRowItem(
    departure: Departure,
    isFirst: Boolean,
    showLineBadge: Boolean = false,
    modifier: Modifier = Modifier
) {
    val transitIcon = getTransitIcon(departure.modeName, departure.destinationName)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 4.dp)
            .semantics {
                contentDescription = "${departure.destinationName}, arriving in ${departure.formattedTimeToArrival}"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Transit Mode Icon
        Icon(
            imageVector = transitIcon,
            contentDescription = departure.modeName,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(24.dp)
                .padding(end = 4.dp)
        )

        Spacer(modifier = Modifier.width(10.dp))

        // Destination & Direction details
        Column(modifier = Modifier.weight(1f)) {
            val destinationText = departure.destinationName.ifBlank { departure.lineName }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Line badge / route chip if filtering is not active and station has multiple lines
                if (showLineBadge) {
                    val badge = departure.lineBadge
                    val isBusOrNumeric = badge.mode == TransitMode.BUS || badge.displayName.all { it.isDigit() }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badge.backgroundColor)
                            .padding(horizontal = if (isBusOrNumeric) 6.dp else 8.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = badge.displayName,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            color = badge.textColor,
                            maxLines = 1
                        )
                    }
                }

                Text(
                    text = destinationText,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (isFirst) FontWeight.Bold else FontWeight.Medium,
                        fontSize = if (isFirst) 16.sp else 15.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Platform & line details (avoiding redundant destination repeats)
            val subText = when {
                !departure.platformName.isBlank() -> departure.platformName
                !departure.currentLocation.isNullOrBlank() -> departure.currentLocation
                else -> departure.lineName
            }

            if (!subText.isBlank()) {
                Text(
                    text = subText,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Live Train Progress Track for upcoming trains (due or arriving within 3 minutes)
            if (departure.timeToStationSeconds <= 180) {
                Spacer(modifier = Modifier.height(4.dp))
                LiveTrainTrackIndicator(timeToStationSeconds = departure.timeToStationSeconds)
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Countdown & Actual Clock Time
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Center
        ) {
            CountdownBadge(
                timeToStationSeconds = departure.timeToStationSeconds,
                isFirst = isFirst,
                lineColor = departure.lineBadge.backgroundColor
            )

            val clockTime = departure.formattedActualClockTime
            if (!clockTime.isNullOrBlank()) {
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
    modifier: Modifier = Modifier
) {
    val stage = when {
        timeToStationSeconds <= 30 -> 2 // At platform
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
                2 -> "At platform"
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
 */
@Composable
fun CountdownBadge(
    timeToStationSeconds: Int,
    isFirst: Boolean,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    val minutes = timeToStationSeconds / 60
    val isDue = timeToStationSeconds <= 30

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        if (isDue) {
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
                    androidx.compose.animation.AnimatedContent(
                        targetState = minutes,
                        transitionSpec = {
                            if (targetState > initialState) {
                                androidx.compose.animation.slideInVertically { height -> height } + androidx.compose.animation.fadeIn() togetherWith
                                        androidx.compose.animation.slideOutVertically { height -> -height } + androidx.compose.animation.fadeOut()
                            } else {
                                androidx.compose.animation.slideInVertically { height -> -height } + androidx.compose.animation.fadeIn() togetherWith
                                        androidx.compose.animation.slideOutVertically { height -> height } + androidx.compose.animation.fadeOut()
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
                            color = Color(0xFFFFB800) // Classic London Amber Dot-Matrix LED
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

/**
 * Selects an appropriate Material icon for the transit mode or destination.
 */
private fun getTransitIcon(modeName: String?, destination: String): ImageVector {
    if (destination.contains("Airport", ignoreCase = true) || destination.contains("Heathrow", ignoreCase = true)) {
        return Icons.Rounded.Flight
    }
    return when (TransitMode.fromModeString(modeName)) {
        TransitMode.BUS -> Icons.Rounded.DirectionsBus
        TransitMode.TUBE -> Icons.Rounded.DirectionsSubway
        TransitMode.OVERGROUND, TransitMode.ELIZABETH_LINE, TransitMode.NATIONAL_RAIL -> Icons.Rounded.DirectionsRailway
        TransitMode.TRAM -> Icons.Rounded.Tram
        else -> Icons.Rounded.DirectionsSubway
    }
}

// =================== PREVIEWS ===================

@Preview(name = "Station Departure Card - Light", showBackground = true)
@Composable
fun StationDepartureCardLightPreview() {
    val sampleStation = DefaultStations.POPULAR_STATIONS[1] // King's Cross
    val sampleDepartures = DefaultStations.getFallbackDepartures(sampleStation.id, sampleStation.name)
    val cardModel = StationCardUiModel(
        station = sampleStation,
        departures = sampleDepartures,
        availableLineBadges = listOf(
            TflLineColors.getLineBadge("victoria", "Victoria", "tube"),
            TflLineColors.getLineBadge("northern", "Northern", "tube"),
            TflLineColors.getLineBadge("piccadilly", "Piccadilly", "tube")
        ),
        isLoading = false
    )

    DepartureBoardTheme(darkTheme = false) {
        Box(
            modifier = Modifier
                .background(Color(0xFFE7EDF7))
                .padding(16.dp)
        ) {
            StationDepartureCard(
                cardModel = cardModel,
                onDismissStation = {}
            )
        }
    }
}

@Preview(name = "Station Departure Card - Dark", showBackground = true)
@Composable
fun StationDepartureCardDarkPreview() {
    val sampleStation = DefaultStations.POPULAR_STATIONS[1] // King's Cross
    val sampleDepartures = DefaultStations.getFallbackDepartures(sampleStation.id, sampleStation.name)
    val cardModel = StationCardUiModel(
        station = sampleStation,
        departures = sampleDepartures,
        availableLineBadges = listOf(
            TflLineColors.getLineBadge("victoria", "Victoria", "tube"),
            TflLineColors.getLineBadge("northern", "Northern", "tube"),
            TflLineColors.getLineBadge("piccadilly", "Piccadilly", "tube")
        ),
        isLoading = false
    )

    DepartureBoardTheme(darkTheme = true) {
        Box(
            modifier = Modifier
                .background(Color(0xFF1B1F28))
                .padding(16.dp)
        ) {
            StationDepartureCard(
                cardModel = cardModel,
                onDismissStation = {}
            )
        }
    }
}
