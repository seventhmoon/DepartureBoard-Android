package com.androidfung.departureboard.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.rounded.NightsStay
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.androidfung.departureboard.data.model.TransitDirection
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
    onStationClick: ((Station) -> Unit)? = null,
    onDepartureClick: ((Departure) -> Unit)? = null
) {
    var selectedLineId by rememberSaveable(cardModel.station.id) { mutableStateOf<String?>(null) }
    var selectedDirection by rememberSaveable(cardModel.station.id) { mutableStateOf<String?>(null) }

    val filteredDepartures = remember(cardModel.departures, selectedLineId, selectedDirection) {
        cardModel.getFilteredDepartures(selectedLineId, selectedDirection)
    }

    // Require deliberate swipe gesture (EndToStart only, with 50% positional threshold)
    // to prevent accidental removal while panning vertically.
    // Key by station.id so restoring a deleted station produces a fresh, un-swiped state.
    @Suppress("DEPRECATION")
    val dismissState = androidx.compose.runtime.key(cardModel.station.id) {
        rememberSwipeToDismissBoxState(
            positionalThreshold = { totalDistance -> totalDistance * 0.5f },
            confirmValueChange = { dismissValue ->
                if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                    onDismissStation(cardModel.station)
                    true
                } else {
                    false
                }
            }
        )
    }

    // Reset dismiss state if station is restored/re-added to avoid staying swiped on the red delete box
    androidx.compose.runtime.LaunchedEffect(cardModel.station.id, cardModel.departures) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    val dismissProgress = dismissState.progress
    val isPastThreshold = dismissProgress >= 0.5f && dismissState.targetValue == SwipeToDismissBoxValue.EndToStart

    // Trigger haptic when crossing the threshold
    val hapticFeedback = androidx.compose.ui.platform.LocalHapticFeedback.current
    androidx.compose.runtime.LaunchedEffect(isPastThreshold) {
        if (isPastThreshold) {
            hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false, // Disable right-swipe dismissal to prevent conflicts
        enableDismissFromEndToStart = true,
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

            val iconScale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (isPastThreshold) 1.25f else 1.0f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy
                ),
                label = "DeleteIconScale"
            )

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
                    modifier = Modifier
                        .size(28.dp)
                        .scale(iconScale)
                )
            }
        }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp)),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            // Gradient background: subtle surface container gradient in both light and dark themes
            val topGradientColor = MaterialTheme.colorScheme.surfaceVariant
            val bottomGradientColor = MaterialTheme.colorScheme.surfaceContainer

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
                        selectedDirection = selectedDirection,
                        isNearest = isNearest,
                        distanceMeters = distanceMeters,
                        onLineBadgeClick = { badge ->
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            val availableDirections = cardModel.getAvailableDirectionsForLine(badge.lineId)

                            if (!TransitIconHelper.isSameLine(selectedLineId, badge.lineId)) {
                                // 1st click: Filter by line (all departures for this line)
                                selectedLineId = badge.lineId
                                selectedDirection = null
                            } else if (availableDirections.size > 1) {
                                // Multi-direction line (e.g. through stations): cycle through available directions
                                val currentIndex = if (selectedDirection == null) -1 else availableDirections.indexOf(selectedDirection)
                                when (currentIndex) {
                                    -1 -> {
                                        // 2nd click: First direction
                                        selectedDirection = availableDirections[0]
                                    }
                                    in 0 until availableDirections.lastIndex -> {
                                        // 3rd+ click: Next direction
                                        selectedDirection = availableDirections[currentIndex + 1]
                                    }
                                    else -> {
                                        // Loop completes: deselect line & direction
                                        selectedLineId = null
                                        selectedDirection = null
                                    }
                                }
                            } else {
                                // Terminus or single-direction line (e.g. Ealing Broadway for Central & District):
                                // 2nd click directly deselects without redundant direction cycling
                                selectedLineId = null
                                selectedDirection = null
                            }
                        },
                        onStationClick = onStationClick,
                        isLoading = cardModel.isLoading,
                        departures = cardModel.departures,
                        hasDepartures = cardModel.departures.isNotEmpty(),
                        modifier = Modifier.padding(horizontal = 22.dp)
                    )

                    val showLineBadgeOnEntries = selectedLineId == null && cardModel.availableLineBadges.size > 1

                    Column {
                        Spacer(modifier = Modifier.height(16.dp))

                        StationDeparturesContainer(
                            departures = filteredDepartures,
                            showLineBadge = showLineBadgeOnEntries,
                            isLoading = cardModel.isLoading,
                            errorMessage = cardModel.errorMessage,
                            onDepartureClick = onDepartureClick,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
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
    selectedDirection: String?,
    onLineBadgeClick: (LineBadgeInfo) -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    departures: List<Departure> = emptyList(),
    hasDepartures: Boolean = true,
    isNearest: Boolean = false,
    distanceMeters: Double? = null,
    onStationClick: ((Station) -> Unit)? = null
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (onStationClick != null) Modifier.clickable { onStationClick(station) }
                        else Modifier
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Header Transport Mode Icon indicator:
                // Prioritize Rail / Tube / Metro modes over Bus, or derive from active line badges if present
                val badgeModes = lineBadges.map { it.mode.id }
                val candidateModes = (badgeModes + station.modes).distinct()
                val primaryMode = listOf("tube", "elizabeth-line", "national-rail", "overground", "dlr", "tram")
                    .firstOrNull { it in candidateModes }
                    ?: station.modes.firstOrNull { it in listOf("tube", "overground", "elizabeth-line", "national-rail", "dlr", "tram", "bus") }
                    ?: station.modes.firstOrNull() ?: "tube"
                val modeIcon = TransitIconHelper.getTransitIconForMode(primaryMode)

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = modeIcon,
                        contentDescription = primaryMode,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

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
                        text = station.displayName,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Subtitle: Bus stop "towards ..." indicator (e.g. "towards Edgware or Millbrook Park")
                    // Only show for single-stand bus stops where all routes share the same direction.
                    // Multi-stand bus stations/hubs (like North Finchley Bus Station) have divergent directions and sub-stops.
                    val distinctTowards = departures.mapNotNull { it.towards?.takeIf { t -> t.isNotBlank() && t.lowercase() != "null" } }.distinct()
                    val isSingleDirectionBusStop = station.isBusOnly && distinctTowards.size == 1

                    if (isSingleDirectionBusStop) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "towards ${distinctTowards.first()}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Option C: When there are no departures, only display the line badges if any line has disruptions.
        // If service is normal / closed and there are no departures or disruptions, hide the chips.
        val shouldShowBadges = lineBadges.isNotEmpty() && (hasDepartures || isLoading || lineBadges.any { it.isDisrupted })

        if (shouldShowBadges) {
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                lineBadges.forEach { badge ->
                    val isSelected = TransitIconHelper.isSameLine(selectedLineId, badge.lineId)
                    val isDimmed = selectedLineId != null && !isSelected
                    LinePillBadge(
                        badge = badge,
                        isSelected = isSelected,
                        selectedDirection = if (isSelected) selectedDirection else null,
                        isDimmed = isDimmed,
                        onClick = onLineBadgeClick.let { { it(badge) } }
                    )
                }
            }

            // Disruption Banners:
            // If a specific line is selected and disrupted, show its banner.
            // If no line is selected, show banners for all disrupted lines on this station.
            val disruptedBadgesToShow = if (selectedLineId != null) {
                lineBadges.filter { TransitIconHelper.isSameLine(it.lineId, selectedLineId) && it.isDisrupted }
            } else {
                lineBadges.filter { it.isDisrupted }
            }

            if (disruptedBadgesToShow.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    disruptedBadgesToShow.forEach { disruptedBadge ->
                        LineDisruptionBanner(badge = disruptedBadge)
                    }
                }
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
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    selectedDirection: String? = null,
    isDimmed: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val isBusOrNumeric = badge.mode == TransitMode.BUS || badge.displayName.all { it.isDigit() }

    val alpha = if (isDimmed) 0.35f else 1.0f

    // Detect very dark background colors (e.g. Northern line black) to add a subtle contrasting border in dark mode
    val badgeLuminance = 0.299f * badge.backgroundColor.red + 0.587f * badge.backgroundColor.green + 0.114f * badge.backgroundColor.blue
    val isVeryDarkBadge = badgeLuminance < 0.2f

    val defaultBorderWidth = if (isVeryDarkBadge) 1.dp else 0.dp
    val defaultBorderColor = if (isVeryDarkBadge) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f) else Color.Transparent

    val borderWidth = if (isSelected) 2.dp else if (badge.isDisrupted) 1.5.dp else defaultBorderWidth
    val borderColor = if (isSelected) MaterialTheme.colorScheme.onSurface else if (badge.isDisrupted) Color(0xFFFFC107) else defaultBorderColor

    val directionIndicator = TransitDirection.fromString(selectedDirection)?.indicatorArrow

    val windowWidthClass = com.androidfung.departureboard.ui.theme.LocalWindowWidthClass.current
    val isNarrow = windowWidthClass.isNarrow
    val baseName = if (isNarrow && badge.lineCode.isNotBlank()) badge.lineCode else badge.displayName

    Box(
        modifier = modifier
            .height(32.dp)
            .defaultMinSize(
                minWidth = if (isNarrow) 48.dp else if (isBusOrNumeric && badge.displayName.length <= 3) 36.dp else 56.dp
            )
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (borderWidth > 0.dp) Modifier.border(borderWidth, borderColor, RoundedCornerShape(10.dp))
                else Modifier
            )
            .background(badge.backgroundColor.copy(alpha = alpha))
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            )
            .padding(
                horizontal = if (isNarrow) 8.dp else if (isBusOrNumeric && badge.displayName.length <= 3) 10.dp else 12.dp,
                vertical = 4.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            val labelText = if (!directionIndicator.isNullOrBlank()) {
                "$baseName $directionIndicator"
            } else {
                baseName
            }
            Text(
                text = labelText,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    fontSize = if (isNarrow) 12.sp else 13.sp
                ),
                textAlign = TextAlign.Center,
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
    isLoading: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier,
    showLineBadge: Boolean = false,
    onDepartureClick: ((Departure) -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(26.dp)
            )
            .padding(vertical = 8.dp, horizontal = 14.dp)
    ) {
        when {
            departures.isNotEmpty() -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val displayList = departures.take(5)
                    displayList.forEachIndexed { index, departure ->
                        androidx.compose.animation.AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            DepartureRowItem(
                                departure = departure,
                                isFirst = index == 0,
                                showLineBadge = showLineBadge,
                                onClick = { onDepartureClick?.invoke(departure) }
                            )
                        }
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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Pull down or tap to retry",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NightsStay,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "No upcoming departures",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Service may have ended for the day",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
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

@Preview(name = "Station Departure Card - Bus Stop Preview", showBackground = true)
@Composable
fun StationDepartureCardBusStopPreview() {
    val busStation = Station(
        id = "490008660N",
        name = "Tottenham Court Road",
        modes = listOf("bus")
    )
    val busDepartures = listOf(
        Departure(
            id = "bus-1",
            stationId = busStation.id,
            stationName = busStation.name,
            lineId = "73",
            lineName = "73",
            destinationName = "Oxford Circus",
            towards = "Oxford Circus",
            platformName = "Stop T",
            timeToStationSeconds = 45,
            expectedArrivalIso = "2025-01-01T12:00:45Z",
            currentLocation = "Approaching",
            modeName = "bus",
            lineBadge = TflLineColors.getLineBadge("73", "73", "bus")
        ),
        Departure(
            id = "bus-2",
            stationId = busStation.id,
            stationName = busStation.name,
            lineId = "390",
            lineName = "390",
            destinationName = "Victoria",
            towards = "Victoria",
            platformName = "Stop T",
            timeToStationSeconds = 180,
            expectedArrivalIso = "2025-01-01T12:03:00Z",
            currentLocation = "2 stops away",
            modeName = "bus",
            lineBadge = TflLineColors.getLineBadge("390", "390", "bus")
        ),
        Departure(
            id = "bus-3",
            stationId = busStation.id,
            stationName = busStation.name,
            lineId = "14",
            lineName = "14",
            destinationName = "Putney Heath",
            towards = "Putney Heath",
            platformName = "Stop T",
            timeToStationSeconds = 420,
            expectedArrivalIso = "2025-01-01T12:07:00Z",
            currentLocation = "On route",
            modeName = "bus",
            lineBadge = TflLineColors.getLineBadge("14", "14", "bus")
        )
    )
    val cardModel = StationCardUiModel(
        station = busStation,
        departures = busDepartures,
        availableLineBadges = listOf(
            TflLineColors.getLineBadge("73", "73", "bus"),
            TflLineColors.getLineBadge("390", "390", "bus"),
            TflLineColors.getLineBadge("14", "14", "bus")
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
