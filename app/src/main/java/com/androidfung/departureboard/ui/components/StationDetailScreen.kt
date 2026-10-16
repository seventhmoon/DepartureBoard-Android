package com.androidfung.departureboard.ui.components

import android.content.Intent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.Station

@Composable
fun StationDetailScreen(
    station: Station,
    departures: List<Departure>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isSaved: Boolean = false,
    onToggleSaveStation: (Station) -> Unit = {},
    initialSelectedLineId: String? = null,
    onDepartureClick: ((Departure) -> Unit)? = null,
    serviceHoursInfo: com.androidfung.departureboard.data.model.StationServiceHoursInfo? = null
) {
    val primaryMode = listOf("tube", "elizabeth-line", "national-rail", "overground", "dlr", "tram")
        .firstOrNull { it in station.modes }
        ?: station.modes.firstOrNull() ?: "tube"
    val modeIcon = TransitIconHelper.getTransitIconForMode(primaryMode)

    // Pulsing animation for live updates badge
    val transition = rememberInfiniteTransition(label = "LivePulseTransition")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LivePulseAlpha"
    )

    var selectedLineId by remember(initialSelectedLineId) { mutableStateOf(initialSelectedLineId) }
    var selectedDirection by remember { mutableStateOf<String?>(null) }
    var selectedStopId by remember { mutableStateOf<String?>(null) }

    // Distinct sub-stops (bays/platforms) present in departures (e.g. Stop A, Stop P, Stop Z4, Platform 1, etc.)
    val distinctStops = remember(departures) {
        departures
            .asSequence()
            .filter { it.platformName.isNotBlank() && (it.platformName != "Platform") && (it.platformName != "Bus Stand") }
            .distinctBy { it.platformName }
            .sortedBy { it.platformName }
            .toList()
    }

    val filteredDepartures = remember(departures, selectedLineId, selectedDirection, selectedStopId) {
        com.androidfung.departureboard.util.DepartureFilterHelper.filterDepartures(
            departures = departures,
            lineId = selectedLineId,
            direction = selectedDirection,
            stopId = selectedStopId
        )
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val haptic = LocalHapticFeedback.current
    val trackedDepartureId by com.androidfung.departureboard.service.TrainTrackingService.currentlyTrackedDepartureIdFlow
        .collectAsStateWithLifecycle()
    var pendingTrackDeparture by remember { mutableStateOf<Departure?>(null) }

    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingTrackDeparture?.let { dep ->
                com.androidfung.departureboard.service.TrainTrackingService.startTracking(context, dep)
            }
        }
        pendingTrackDeparture = null
    }

    val onToggleTrackDeparture: (Departure) -> Unit = remember(trackedDepartureId, context, haptic) {
        { dep ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (trackedDepartureId == dep.id) {
                com.androidfung.departureboard.service.TrainTrackingService.stopTracking(context)
            } else {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                    if (hasPermission) {
                        com.androidfung.departureboard.service.TrainTrackingService.startTracking(context, dep)
                    } else {
                        pendingTrackDeparture = dep
                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    com.androidfung.departureboard.service.TrainTrackingService.startTracking(context, dep)
                }
            }
        }
    }

    DetailPaneScaffold(
        onBack = onBack,
        modifier = modifier,
        headerLeading = {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = modeIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        headerTitle = {
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

            // Subtitle: Bus stop "towards ..." indicator for single-direction bus stops
            val distinctTowards = departures
                .asSequence()
                .mapNotNull { it.towards?.takeIf { t -> t.isNotBlank() && t.lowercase() != "null" } }
                .distinct()
                .toList()
            val isSingleDirectionBusStop = station.isBusOnly && distinctTowards.size == 1

            if (isSingleDirectionBusStop) {
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (station.displayZone != null) {
                    Text(
                        text = "Zone ${station.displayZone}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = station.modes.joinToString(", ") { mode ->
                        mode.replaceFirstChar { it.uppercase() }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        headerActions = {
            val context = androidx.compose.ui.platform.LocalContext.current

            // Open in Maps Button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable {
                        val querySuffix = if (station.isBusOnly) "Bus Stop" else "Station"
                        val queryText = "${station.displayName} $querySuffix London"
                        val encodedQuery = java.net.URLEncoder.encode(queryText, "UTF-8")

                        val geoUri = if (station.lat != null && station.lon != null) {
                            android.net.Uri.parse("geo:${station.lat},${station.lon}?q=$encodedQuery")
                        } else {
                            android.net.Uri.parse("geo:0,0?q=$encodedQuery")
                        }
                        val mapIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                            setPackage("com.google.android.apps.maps")
                        }
                        try {
                            context.startActivity(mapIntent)
                        } catch (_: Exception) {
                            val fallbackIntent = Intent(Intent.ACTION_VIEW, geoUri)
                            try {
                                context.startActivity(fallbackIntent)
                            } catch (_: Exception) {
                                val browserUri = if (station.lat != null && station.lon != null) {
                                    android.net.Uri.parse("https://www.google.com/maps/search/?api=1&query=$encodedQuery")
                                } else {
                                    android.net.Uri.parse("https://www.google.com/maps/search/?api=1&query=$encodedQuery")
                                }
                                context.startActivity(Intent(Intent.ACTION_VIEW, browserUri))
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Map,
                    contentDescription = "Open in Maps",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp)
                )
            }

            // Refresh Departures Button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable(onClick = onRefresh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Refresh Departures",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp)
                )
            }

            // Bookmark / Pin to Home icon button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSaved) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleSaveStation(station)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    contentDescription = if (isSaved) "Remove from Home" else "Pin to Home",
                    tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    ) { _ ->

        // Lines badges serving this station - now interactive filter chips
        val distinctLines = departures.map { it.lineBadge }
            .distinctBy { it.lineId }
            .sortedWith(com.androidfung.departureboard.data.model.LineBadgeInfo.NATURAL_COMPARATOR)
        // Show line badges if multiple lines exist, OR if single line has multiple directions to filter
        val singleLineHasDirections = distinctLines.size == 1 && departures.mapNotNull { it.direction }.distinct().size > 1
        if (distinctLines.size > 1 || singleLineHasDirections) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val hasActiveSelection = selectedLineId != null
                val windowWidthClass = com.androidfung.departureboard.ui.theme.LocalWindowWidthClass.current
                val isNarrow = windowWidthClass.isNarrow

                distinctLines.forEach { badge ->
                    val isSelected = selectedLineId.equals(badge.lineId, ignoreCase = true)
                    val directionIndicator = com.androidfung.departureboard.data.model.TransitDirection.fromString(
                        if (isSelected) selectedDirection else null
                    )?.indicatorArrow

                    val baseName = if (isNarrow && badge.lineCode.isNotBlank()) badge.lineCode else badge.displayName
                    val labelText = if (!directionIndicator.isNullOrBlank()) {
                        "$baseName $directionIndicator"
                    } else {
                        baseName
                    }

                    // Standardized chip dimensions: 32dp height, consistent min-width, perfectly centered text
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .defaultMinSize(minWidth = if (isNarrow) 48.dp else 56.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected || !hasActiveSelection) badge.backgroundColor
                                else badge.backgroundColor.copy(alpha = 0.35f)
                            )
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = if (isSelected) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable(
                                role = Role.Checkbox
                            ) {
                                val availableDirections = com.androidfung.departureboard.util.DepartureFilterHelper.getAvailableDirections(
                                    departures = departures,
                                    lineId = badge.lineId
                                )

                                if (!TransitIconHelper.isSameLine(selectedLineId, badge.lineId)) {
                                    selectedLineId = badge.lineId
                                    selectedDirection = null
                                } else if (availableDirections.size > 1) {
                                    // Multi-direction line: cycle through available directions
                                    val currentIndex = if (selectedDirection == null) -1 else availableDirections.indexOf(selectedDirection)
                                    when (currentIndex) {
                                        -1 -> {
                                            selectedDirection = availableDirections[0]
                                        }
                                        in 0 until availableDirections.lastIndex -> {
                                            selectedDirection = availableDirections[currentIndex + 1]
                                        }
                                        else -> {
                                            selectedLineId = null
                                            selectedDirection = null
                                        }
                                    }
                                } else {
                                    // Terminus or single-direction line: deselect directly
                                    selectedLineId = null
                                    selectedDirection = null
                                }
                            }
                            .semantics {
                                this.selected = isSelected
                                this.role = Role.Checkbox
                                this.stateDescription = if (isSelected) "Selected" else "Not selected"
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = labelText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = if (isSelected || !hasActiveSelection) badge.textColor else badge.textColor.copy(alpha = 0.6f),
                            maxLines = 1
                        )
                    }
                }
            }

            // If selected line has disruptions, show a disruption banner right under the badges in detail sheet
            val activeBadge = distinctLines.firstOrNull { it.lineId.equals(selectedLineId, ignoreCase = true) }
            if (activeBadge != null && activeBadge.isDisrupted) {
                Spacer(modifier = Modifier.height(8.dp))
                LineDisruptionBanner(badge = activeBadge)
            }
        }

        // Only show sub-stops / stand filter chips for bus stops/stations (e.g. Stop P, Stop S, Stop Z4).
        // Tube and rail stations already have clear Line and Cardinal Direction filters.
        val isBusStop = station.isBusOnly
        if (isBusStop && distinctStops.size > 1) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Stops / Stands:",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp)
                )
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    distinctStops.forEach { subStop ->
                        val isSelected = selectedStopId.equals(subStop.platformName, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable(
                                    role = Role.Checkbox
                                ) {
                                    selectedStopId = if (selectedStopId == subStop.platformName) null else subStop.platformName
                                }
                                .semantics {
                                    this.selected = isSelected
                                    this.role = Role.Checkbox
                                    this.stateDescription = if (isSelected) "Selected" else "Not selected"
                                }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = subStop.platformName,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(12.dp))

        // Live Departures Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Pulsing live indicator
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF22C55E).copy(alpha = pulseAlpha))
                )
                Text(
                    text = "Live Departures",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    text = "${filteredDepartures.size} predictions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Departures List
        if (filteredDepartures.isEmpty() && !isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (serviceHoursInfo?.isClosedForNight == true) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Text(
                            text = "🌙 Service Ended for Today",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        val lastText = serviceHoursInfo.lastTrainTime?.let { "Last train departed at $it." } ?: ""
                        val firstText = serviceHoursInfo.firstTrainTime?.let { "Morning service starts at ~$it." } ?: ""
                        Text(
                            text = listOf(lastText, firstText).filter { it.isNotBlank() }.joinToString("\n"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 22.sp
                        )
                    }
                } else {
                    Text(
                        text = if (selectedLineId != null) "No departures for this line" else "No departures currently available.\nTap the refresh icon above to check for updates.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(
                    items = filteredDepartures,
                    key = { index, item ->
                        val baseId = item.id.ifBlank { "${item.stationId}_${item.lineId}" }
                        "${baseId}_$index"
                    }
                ) { _, departure ->
                    DepartureDetailItemRow(
                        departure = departure,
                        isTracked = trackedDepartureId == departure.id,
                        onToggleTrackDeparture = onToggleTrackDeparture,
                        onClick = onDepartureClick?.let { { it(departure) } }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}

/**
 * Detailed departure row item utilizing the unified DepartureRowItem component.
 */
@Composable
fun DepartureDetailItemRow(
    departure: Departure,
    isTracked: Boolean = false,
    onToggleTrackDeparture: (Departure) -> Unit = {},
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        DepartureRowItem(
            departure = departure,
            isFirst = false,
            variant = DepartureRowVariant.DETAILED,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            trailingAction = if (departure.status == com.androidfung.departureboard.data.model.DepartureStatus.CANCELLED) {
                null
            } else {
                {
                    // Inline Notification Bell Action with 48dp minimum interactive touch target
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if (isTracked) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            )
                            .clickable {
                                onToggleTrackDeparture(departure)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isTracked) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsNone,
                            contentDescription = if (isTracked) "Stop Live Notification" else "Notify with Live Update",
                            modifier = Modifier.size(16.dp),
                            tint = if (isTracked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        )
    }
}
