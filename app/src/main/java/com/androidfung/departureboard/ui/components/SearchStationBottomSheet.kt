package com.androidfung.departureboard.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Search
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androidfung.departureboard.data.model.DefaultStations
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
    recentSearchedStations: List<Station> = emptyList(),
    onRemoveRecentSearch: (String) -> Unit = {},
    onClearRecentSearches: () -> Unit = {},
    hasLocationPermission: Boolean = false,
    onRequestLocation: () -> Unit = {},
    selectedRegion: com.androidfung.departureboard.data.model.TransitRegion = com.androidfung.departureboard.data.model.TransitRegion.GB,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val defaultStationsForRegion = remember(selectedRegion) {
        when (selectedRegion) {
            com.androidfung.departureboard.data.model.TransitRegion.HK -> com.androidfung.departureboard.data.model.HkDefaultStations.POPULAR_STATIONS
            com.androidfung.departureboard.data.model.TransitRegion.GB -> DefaultStations.POPULAR_STATIONS
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedModeFilter by remember { mutableStateOf("All") }
    var searchResults by remember(defaultStationsForRegion) { mutableStateOf(defaultStationsForRegion) }
    var isSearching by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val coroutineScope = rememberCoroutineScope()

    // Auto-request focus on search field after sheet opens
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Trigger search when query changes with debouncing
    LaunchedEffect(searchQuery, defaultStationsForRegion) {
        if (searchQuery.isBlank()) {
            searchResults = defaultStationsForRegion
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
                    .padding(vertical = 8.dp)
                    .focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }),
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
                        IconButton(onClick = {
                            searchQuery = ""
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }) {
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
            SearchFilterChipsRow(
                selectedMode = selectedModeFilter,
                onModeSelected = { selectedModeFilter = it }
            )

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
                    if (searchQuery.isBlank()) {
                        // 1. Recent Searches Section
                        if (recentSearchedStations.isNotEmpty()) {
                            item(key = "header_recent") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.Search,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Recent Searches",
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    TextButton(
                                        onClick = onClearRecentSearches,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) {
                                        Text(
                                            text = "Clear",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }

                            items(
                                items = recentSearchedStations,
                                key = { "recent_" + it.id }
                            ) { station ->
                                val isAlreadySaved = savedStationIds.contains(station.id)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        StationSearchResultRow(
                                            station = station,
                                            isSaved = isAlreadySaved,
                                            distanceText = "Recently viewed",
                                            onAddClick = {
                                                focusManager.clearFocus()
                                                keyboardController?.hide()
                                                coroutineScope.launch {
                                                    onStationSelected(station)
                                                }
                                            }
                                        )
                                    }
                                    IconButton(
                                        onClick = { onRemoveRecentSearch(station.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Remove from history",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

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
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
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
                                focusManager.clearFocus()
                                keyboardController?.hide()
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
