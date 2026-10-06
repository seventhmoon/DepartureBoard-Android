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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsRailway
import androidx.compose.material.icons.rounded.DirectionsSubway
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tram
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androidfung.departureboard.data.model.DefaultStations
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TflLineColors
import com.androidfung.departureboard.ui.components.LineDisruptionBanner
import com.androidfung.departureboard.ui.theme.DepartureBoardTheme

/**
 * Expressive ModalBottomSheet displaying detailed transit information and full departure predictions
 * for a selected station.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StationDetailBottomSheet(
    station: Station,
    departures: List<Departure>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    isSaved: Boolean = false,
    onToggleSaveStation: (Station) -> Unit = {},
    onRemoveStation: ((Station) -> Unit)? = null,
    initialSelectedLineId: String? = null,
    onDepartureClick: ((Departure) -> Unit)? = null,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val primaryMode = listOf("tube", "elizabeth-line", "national-rail", "overground", "dlr", "tram")
        .firstOrNull { it in station.modes }
        ?: station.modes.firstOrNull() ?: "tube"
    val modeIcon = getTransitIconForMode(primaryMode)

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
            .filter { it.platformName.isNotBlank() && it.platformName != "Platform" && it.platformName != "Bus Stand" }
            .distinctBy { it.platformName }
            .sortedBy { it.platformName }
    }

    val filteredDepartures = remember(departures, selectedLineId, selectedDirection, selectedStopId) {
        departures.filter { departure ->
            // Filter by sub-stop if selected
            if (selectedStopId != null && !departure.platformName.equals(selectedStopId, ignoreCase = true)) {
                return@filter false
            }
            if (selectedLineId != null && !TransitIconHelper.isSameLine(departure.lineId, selectedLineId)) {
                return@filter false
            }
            if (selectedDirection != null) {
                val matchesDirection = departure.direction.equals(selectedDirection, ignoreCase = true) ||
                        departure.platformName.contains(selectedDirection!!, ignoreCase = true)
                if (!matchesDirection) return@filter false
            }
            true
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier.fillMaxHeight(0.9f),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
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
                        val distinctTowards = departures.mapNotNull { it.towards?.takeIf { t -> t.isNotBlank() && t.lowercase() != "null" } }.distinct()
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
                            if (station.zone != null) {
                                Text(
                                    text = "Zone ${station.zone}",
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
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Bookmark / Pin to Home icon button (Box avoids IconButton 48dp touch-target expansion overlap)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSaved) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                            .clickable { onToggleSaveStation(station) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            contentDescription = if (isSaved) "Remove from Home" else "Pin to Home",
                            tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Close details button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable(onClick = onDismissRequest),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close details",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Lines badges serving this station - now interactive filter chips
            val distinctLines = departures.map { it.lineBadge }
                .distinctBy { it.lineId }
                .sortedWith(com.androidfung.departureboard.data.model.LineBadgeInfo.NATURAL_COMPARATOR)
            if (distinctLines.size > 1) {
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
                        val directionIndicator = when (if (isSelected) selectedDirection?.lowercase() else null) {
                            "eastbound" -> "→ EB"
                            "westbound" -> "← WB"
                            "northbound" -> "↑ NB"
                            "southbound" -> "↓ SB"
                            "inbound" -> "IN"
                            "outbound" -> "OUT"
                            else -> null
                        }

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
                                .clickable {
                                        val lineDepartures = departures.filter { TransitIconHelper.isSameLine(it.lineId, badge.lineId) }
                                    val availableDirections = lineDepartures
                                        .mapNotNull { it.direction ?: listOf("Eastbound", "Westbound", "Northbound", "Southbound", "Inbound", "Outbound").firstOrNull { d -> it.platformName.contains(d, ignoreCase = true) } }
                                        .distinct()
                                        .map {
                                            if (it.equals("Inbound", ignoreCase = true) && badge.lineId.contains("northern", ignoreCase = true)) "Northbound"
                                            else if (it.equals("Outbound", ignoreCase = true) && badge.lineId.contains("northern", ignoreCase = true)) "Southbound"
                                            else it
                                        }
                                        .distinct()

                                    if (!TransitIconHelper.isSameLine(selectedLineId, badge.lineId)) {
                                        selectedLineId = badge.lineId
                                        selectedDirection = null
                                    } else if (availableDirections.size > 1) {
                                        // Multi-direction line: cycle through available directions
                                        val currentIndex = if (selectedDirection == null) -1 else availableDirections.indexOf(selectedDirection)
                                        if (currentIndex == -1) {
                                            selectedDirection = availableDirections[0]
                                        } else if (currentIndex in 0 until availableDirections.lastIndex) {
                                            selectedDirection = availableDirections[currentIndex + 1]
                                        } else {
                                            selectedLineId = null
                                            selectedDirection = null
                                        }
                                    } else {
                                        // Terminus or single-direction line: deselect directly
                                        selectedLineId = null
                                        selectedDirection = null
                                    }
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
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
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
                                    .clickable {
                                        selectedStopId = if (selectedStopId == subStop.platformName) null else subStop.platformName
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
                    Text(
                        text = if (selectedLineId != null) "No departures for this line" else "No departures currently available.\nTap refresh below to retry.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
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
                        key = { index, item -> "${item.id}_${item.timeToStationSeconds}_${item.platformName}_$index" }
                    ) { _, departure ->
                        DepartureDetailRow(
                            departure = departure,
                            onClick = { onDepartureClick?.invoke(departure) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons Footer: Clean two-button utility row (Maps and Refresh)
            // Pinning / Bookmarking to Home Dashboard is controlled via the header Bookmark toggle
            val context = androidx.compose.ui.platform.LocalContext.current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Open in Google Maps
                OutlinedButton(
                    onClick = {
                        val geoUri = if (station.lat != null && station.lon != null) {
                            android.net.Uri.parse("geo:${station.lat},${station.lon}?q=${station.lat},${station.lon}(${java.net.URLEncoder.encode(station.name, "UTF-8")})")
                        } else {
                            android.net.Uri.parse("geo:0,0?q=${java.net.URLEncoder.encode("${station.name} London", "UTF-8")}")
                        }
                        val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)
                        try {
                            context.startActivity(mapIntent)
                        } catch (_: Exception) {
                            val browserUri = if (station.lat != null && station.lon != null) {
                                android.net.Uri.parse("https://www.google.com/maps/search/?api=1&query=${station.lat},${station.lon}")
                            } else {
                                android.net.Uri.parse("https://www.google.com/maps/search/?api=1&query=${java.net.URLEncoder.encode("${station.name} London", "UTF-8")}")
                            }
                            context.startActivity(Intent(Intent.ACTION_VIEW, browserUri))
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Map,
                        contentDescription = "Open in Maps",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Maps", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                }

                // Refresh Button
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Refresh", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                }
            }
        }
    }
}

/**
 * Detailed departure row item showing line, destination, direction, current location, and countdown.
 */
@Composable
fun DepartureDetailRow(
    departure: Departure,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Standardized Line Badge Pill
                    val lineCodeText = departure.lineBadge.lineCode.ifBlank { departure.lineBadge.displayName }
                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .defaultMinSize(minWidth = 36.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(departure.lineBadge.backgroundColor)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = lineCodeText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            color = departure.lineBadge.textColor,
                            maxLines = 1
                        )
                    }

                    // Platform or direction
                    Text(
                        text = departure.platformName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Destination
                Text(
                    text = departure.destinationName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        lineHeight = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Current location if available
                if (!departure.currentLocation.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = departure.currentLocation,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Countdown Pill
            val isDue = departure.timeToStationSeconds <= 30
            val pillBg = if (isDue) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            }
            val pillText = if (isDue) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(pillBg)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = departure.formattedTimeToArrival,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    ),
                    color = pillText
                )
            }
        }
    }
}

private fun getTransitIconForMode(mode: String): ImageVector {
    return when (mode.lowercase()) {
        "tube", "underground" -> Icons.Rounded.DirectionsSubway
        "bus" -> Icons.Rounded.DirectionsBus
        "national-rail", "overground", "elizabeth-line" -> Icons.Rounded.DirectionsRailway
        "dlr", "tram" -> Icons.Rounded.Tram
        else -> Icons.Rounded.DirectionsSubway
    }
}

// =================== PREVIEWS ===================

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Station Detail - Light Mode", showBackground = true)
@Composable
fun StationDetailBottomSheetPreview() {
    val station = DefaultStations.POPULAR_STATIONS[0]
    val departures = DefaultStations.getFallbackDepartures(station.id, station.name)

    DepartureBoardTheme {
        StationDetailBottomSheet(
            station = station,
            departures = departures,
            isLoading = false,
            onRefresh = {},
            onRemoveStation = {},
            onDismissRequest = {}
        )
    }
}
