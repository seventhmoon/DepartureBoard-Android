package com.androidfung.departureboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsRailway
import androidx.compose.material.icons.rounded.DirectionsSubway
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tram
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androidfung.departureboard.data.model.DefaultStations
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TflLineColors
import com.androidfung.departureboard.ui.theme.DepartureBoardTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Expressive ModalBottomSheet for searching and adding transit stations to the Departure Board.
 * Features instant filter chips (Tube, Elizabeth line, Overground, DLR, Bus), search debounce,
 * and clear indicators for saved stations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchStationBottomSheet(
    onDismissRequest: () -> Unit,
    onStationSelected: (Station) -> Unit,
    savedStationIds: Set<String>,
    onSearchQuery: suspend (String) -> List<Station>,
    modifier: Modifier = Modifier,
    nearbyStations: List<Pair<Station, Double>> = emptyList(),
    hasLocationPermission: Boolean = false,
    onRequestLocation: () -> Unit = {},
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedModeFilter by remember { mutableStateOf("All") }
    var searchResults by remember { mutableStateOf(DefaultStations.POPULAR_STATIONS) }
    var isSearching by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    // Trigger search when query changes with debouncing
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            searchResults = DefaultStations.POPULAR_STATIONS
            isSearching = false
        } else {
            isSearching = true
            delay(300L.milliseconds) // Debounce user typing
            val results = onSearchQuery(searchQuery)
            searchResults = results
            isSearching = false
        }
    }

    // Apply mode filtering on current search results
    val filteredResults = remember(searchResults, selectedModeFilter) {
        if (selectedModeFilter == "All") {
            searchResults
        } else {
            val filterNormalized = selectedModeFilter.lowercase().replace(" ", "-")
            searchResults.filter { station ->
                station.modes.any { mode ->
                    mode.equals(filterNormalized, ignoreCase = true) ||
                            (filterNormalized == "tube" && (mode.equals("underground", ignoreCase = true) || mode.equals("tube", ignoreCase = true))) ||
                            (filterNormalized == "elizabeth-line" && mode.contains("elizabeth", ignoreCase = true)) ||
                            (filterNormalized == "tram" && (mode.contains("tram", ignoreCase = true) || mode.equals("tram", ignoreCase = true)))
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier.fillMaxHeight(0.92f),
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Search Stations",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "View live departures & pin to your dashboard",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Search Bar Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                placeholder = {
                    Text(
                        text = "Search station or bus route (e.g. 221, SL1)...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Rounded.Clear,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            // Mode Filter Chips Row
            val modeFilters = listOf("All", "Tube", "Elizabeth line", "Overground", "DLR", "National Rail", "Tram", "Bus")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                modeFilters.forEach { mode ->
                    val isSelected = selectedModeFilter == mode
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedModeFilter = mode },
                        label = {
                            Text(
                                text = mode,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sub-header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (searchQuery.isBlank()) "Popular Stations" else "Search Results (${filteredResults.size})",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Results List
            if (filteredResults.isEmpty() && !isSearching) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No stations found",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Try searching for Victoria, Waterloo, or King's Cross",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Filter nearby stations by selected mode if mode filter is applied
                val filteredNearby = remember(nearbyStations, selectedModeFilter) {
                    if (selectedModeFilter == "All") {
                        nearbyStations
                    } else {
                        val filterNormalized = selectedModeFilter.lowercase().replace(" ", "-")
                        nearbyStations.filter { (station, _) ->
                            station.modes.any { mode ->
                                mode.equals(filterNormalized, ignoreCase = true) ||
                                        (filterNormalized == "tube" && mode.equals("underground", ignoreCase = true)) ||
                                        (filterNormalized == "elizabeth-line" && mode.contains("elizabeth", ignoreCase = true))
                            }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // When search query is blank:
                    // 1. If location is granted & nearby stations found, show "Nearby Stations" section
                    // 2. If location is not granted, show "Find stations near me" affordance
                    if (searchQuery.isBlank()) {
                        if (hasLocationPermission && filteredNearby.isNotEmpty()) {
                            item(key = "header_nearby") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.LocationOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Nearby Stations",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            items(
                                items = filteredNearby,
                                key = { "nearby_" + it.first.id + it.first.name }
                            ) { (station, distMeters) ->
                                val isAlreadySaved = savedStationIds.contains(station.id)
                                val distFormatted = if (distMeters < 1000) {
                                    "${distMeters.toInt()}m away"
                                } else {
                                    String.format(java.util.Locale.UK, "%.1f km away", distMeters / 1000.0)
                                }
                                StationSearchResultRow(
                                    station = station,
                                    isSaved = isAlreadySaved,
                                    distanceText = distFormatted,
                                    onAddClick = {
                                        coroutineScope.launch {
                                            onStationSelected(station)
                                        }
                                    }
                                )
                            }

                            item(key = "header_popular") {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Popular Stations",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                        } else if (!hasLocationPermission) {
                            item(key = "prompt_location") {
                                Surface(
                                    onClick = onRequestLocation,
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.LocationOn,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Find stations near me",
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Show stops and stations within walking distance",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }

                    items(
                        items = filteredResults,
                        key = { it.id + it.name } // Combined id and name for uniqueness
                    ) { station ->
                        val isAlreadySaved = savedStationIds.contains(station.id)
                        StationSearchResultRow(
                            station = station,
                            isSaved = isAlreadySaved,
                            onAddClick = {
                                coroutineScope.launch {
                                    onStationSelected(station)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual station row item in the search sheet.
 */
@Composable
fun StationSearchResultRow(
    station: Station,
    isSaved: Boolean,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
    distanceText: String? = null
) {
    val primaryMode = listOf("tube", "elizabeth-line", "national-rail", "overground", "dlr", "tram")
        .firstOrNull { it in station.modes }
        ?: station.modes.firstOrNull() ?: "tube"
    val modeIcon = getTransitIconForMode(primaryMode)

    Surface(
        onClick = onAddClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Mode Icon & Name info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = modeIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = station.name,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (distanceText != null) {
                            Text(
                                text = distanceText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (station.displayZone != null) {
                            Text(
                                text = "Zone ${station.displayZone}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Mode badges with standardized official terminology and branding
                        station.modes.take(3).forEach { modeStr ->
                            val transitMode = com.androidfung.departureboard.data.model.TransitMode.fromModeString(modeStr)
                            val badge = TflLineColors.getLineBadge(
                                lineId = transitMode.id,
                                lineName = transitMode.displayName,
                                modeName = transitMode.id
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(badge.backgroundColor.copy(alpha = 0.85f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = transitMode.displayName,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = badge.textColor
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action Indicator: "Saved" badge or Chevron to view departures
            if (isSaved) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Bookmark,
                            contentDescription = "Pinned to home",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Pinned",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = "View departures",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Helper to get appropriate icon vector for a transit mode.
 */
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

@Preview(name = "Search Station Item - Not Saved", showBackground = true)
@Composable
fun StationSearchResultRowPreview() {
    DepartureBoardTheme {
        StationSearchResultRow(
            station = DefaultStations.POPULAR_STATIONS[0],
            isSaved = false,
            onAddClick = {}
        )
    }
}

@Preview(name = "Search Station Item - Saved", showBackground = true)
@Composable
fun StationSearchResultRowSavedPreview() {
    DepartureBoardTheme {
        StationSearchResultRow(
            station = DefaultStations.POPULAR_STATIONS[1],
            isSaved = true,
            onAddClick = {}
        )
    }
}
