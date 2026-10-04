package com.androidfung.departureboard.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import com.androidfung.departureboard.ui.components.rememberReorderableStaggeredGridState
import com.androidfung.departureboard.ui.components.reorderableStaggeredGrid
import com.androidfung.departureboard.ui.components.reorderableStaggeredItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.androidfung.departureboard.data.model.DefaultStations
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TflLineColors
import com.androidfung.departureboard.ui.components.EmptyDashboardState
import com.androidfung.departureboard.ui.components.SearchStationBottomSheet
import com.androidfung.departureboard.ui.components.StationDepartureCard
import com.androidfung.departureboard.ui.components.StationDetailBottomSheet
import com.androidfung.departureboard.ui.theme.DepartureBoardTheme

/**
 * Departure Board Dashboard Screen.
 *
 * Implements:
 * - Pixel Clock-inspired departure cards with TfL line badges, platform details, and live countdowns.
 * - Material 3 Expressive design with pill chips, smooth gradient backgrounds, and expressive floating action button (+).
 * - Pull-to-refresh & auto-refresh (via ViewModel).
 * - Swipe-to-delete with Snackbar undo action.
 * - Clean empty state when no stations are saved.
 * - Search station picker & station details bottom sheets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    initialDetailStation: Station? = null,
    onNavigateToSearch: () -> Unit = {},
    onStationClick: (Station) -> Unit = {},
    viewModel: DashboardViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showSearchSheet by rememberSaveable { mutableStateOf(false) }
    var showAiSheet by rememberSaveable { mutableStateOf(false) }
    var selectedStationForDetail by remember { mutableStateOf<Station?>(null) }

    LaunchedEffect(initialDetailStation) {
        if (initialDetailStation != null) {
            selectedStationForDetail = initialDetailStation
        }
    }

    // Handle user snackbar notifications (e.g., station deletion undo)
    LaunchedEffect(uiState.userMessage) {
        val message = uiState.userMessage
        if (message != null) {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoRemoveStation()
            }
            viewModel.clearUserMessage()
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(uiState.stationCards.size) {
        if (uiState.stationCards.isNotEmpty()) {
            viewModel.updateNearestStation(context)
        }
    }

    DashboardContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onRefresh = { viewModel.refreshDepartures(isManualPullToRefresh = true) },
        onDismissStation = { station -> viewModel.removeStation(station) },
        onMoveStation = { fromIndex, toIndex -> viewModel.moveStation(fromIndex, toIndex) },
        onAddStationClick = {
            showSearchSheet = true
            onNavigateToSearch()
        },
        onStationClick = { station ->
            selectedStationForDetail = station
            onStationClick(station)
        },
        onToggleStationExpand = { stationId -> viewModel.toggleStationExpand(stationId) },
        onExpandAll = { viewModel.expandAll() },
        onCollapseAll = { viewModel.collapseAll() },
        onAiAssistantClick = { showAiSheet = true },
        modifier = modifier
    )

    // Search & Add Station Sheet
    if (showSearchSheet) {
        SearchStationBottomSheet(
            onDismissRequest = { showSearchSheet = false },
            onStationSelected = { station ->
                viewModel.addStation(station)
                showSearchSheet = false
            },
            savedStationIds = remember(uiState.stationCards) {
                uiState.stationCards.map { it.station.id }.toSet()
            },
            onSearchQuery = { query -> viewModel.searchStations(query) }
        )
    }

    // Station Departure Details Sheet
    selectedStationForDetail?.let { station ->
        val cardModel = uiState.stationCards.firstOrNull { it.station.id == station.id }
        StationDetailBottomSheet(
            station = station,
            departures = cardModel?.departures ?: emptyList(),
            isLoading = cardModel?.isLoading ?: false,
            onRefresh = { viewModel.refreshStation(station) },
            onRemoveStation = { stationToRemove ->
                viewModel.removeStation(stationToRemove)
                selectedStationForDetail = null
            },
            onDismissRequest = { selectedStationForDetail = null }
        )
    }

    // Gemini AI Natural Language Transit Assistant Sheet
    if (showAiSheet) {
        val application = androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application
        val repository = remember { com.androidfung.departureboard.data.repository.TransitRepositoryImpl(application) }
        val nearest = uiState.stationCards.firstOrNull { it.station.id == uiState.nearestStationId }?.station
        com.androidfung.departureboard.ui.components.AiTransitSheet(
            onDismissRequest = { showAiSheet = false },
            repository = repository,
            savedStations = uiState.stationCards.map { it.station },
            nearestStation = nearest,
            initialTriggerSpeech = false
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(
    uiState: DashboardUiState,
    snackbarHostState: SnackbarHostState,
    onRefresh: () -> Unit,
    onDismissStation: (Station) -> Unit,
    onMoveStation: (fromIndex: Int, toIndex: Int) -> Unit,
    onAddStationClick: () -> Unit,
    onStationClick: (Station) -> Unit,
    modifier: Modifier = Modifier,
    onToggleStationExpand: (String) -> Unit = {},
    onExpandAll: () -> Unit = {},
    onCollapseAll: () -> Unit = {},
    onAiAssistantClick: () -> Unit = {}
) {
    val pullToRefreshState = rememberPullToRefreshState()
    val staggeredGridState = rememberLazyStaggeredGridState()
    val reorderState = rememberReorderableStaggeredGridState(
        staggeredGridState = staggeredGridState,
        onMove = onMoveStation
    )

    // Expressive gradient background matching the design mockup (top dark-indigo / slate-blue to rich deep surface)
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
            MaterialTheme.colorScheme.surface
        )
    )

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            // Expressive FAB for Prompt Departure Transit AI Assistant
            FloatingActionButton(
                onClick = onAiAssistantClick,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .padding(end = 4.dp, bottom = 4.dp)
                    .size(62.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = "Ask Prompt Departure AI",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            state = pullToRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyVerticalStaggeredGrid(
                state = staggeredGridState,
                columns = StaggeredGridCells.Adaptive(minSize = 360.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .reorderableStaggeredGrid(reorderState),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 96.dp // Extra clearance for the expressive FAB
                ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalItemSpacing = 20.dp
            ) {
                // Header section: "Prompt Departure" title spanning full width
                item(key = "header", span = StaggeredGridItemSpan.FullLine) {
                    val allExpanded = uiState.stationCards.all { it.isExpanded }
                    DashboardHeader(
                        allExpanded = allExpanded,
                        onToggleExpandAll = {
                            if (allExpanded) onCollapseAll() else onExpandAll()
                        },
                        onAddStationClick = onAddStationClick
                    )
                }

                if (uiState.isInitialLoading && uiState.stationCards.isEmpty()) {
                    item(key = "loading", span = StaggeredGridItemSpan.FullLine) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else if (uiState.stationCards.isEmpty()) {
                    // Empty state when all stations removed spanning full width
                    item(key = "empty_state", span = StaggeredGridItemSpan.FullLine) {
                        EmptyDashboardState(
                            onAddStationClick = onAddStationClick,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                } else {
                    // Station Departure Cards in adaptive staggered grid
                    // If nearest station exists on wide screens / foldables, span the nearest card full line for prominent visibility
                    items(
                        items = uiState.stationCards,
                        key = { it.station.id + it.station.name },
                        span = { cardModel ->
                            val isNearest = uiState.nearestStationId == cardModel.station.id
                            if (isNearest) StaggeredGridItemSpan.FullLine else StaggeredGridItemSpan.SingleLane
                        }
                    ) { cardModel ->
                        val isNearest = uiState.nearestStationId == cardModel.station.id
                        StationDepartureCard(
                            cardModel = cardModel,
                            onDismissStation = onDismissStation,
                            onStationClick = onStationClick,
                            isNearest = isNearest,
                            distanceMeters = if (isNearest) uiState.nearestStationDistanceMeters else null,
                            onToggleExpand = { onToggleStationExpand(cardModel.station.id) },
                            modifier = Modifier
                                .reorderableStaggeredItem(reorderState, cardModel.station.id)
                                .animateItem()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Expressive header component with clean title typography.
 */
@Composable
fun DashboardHeader(
    modifier: Modifier = Modifier,
    allExpanded: Boolean = true,
    onToggleExpandAll: () -> Unit = {},
    onAddStationClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Prompt Departure",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    lineHeight = 34.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Expand All / Collapse All Toggle Button
            IconButton(onClick = onToggleExpandAll) {
                Icon(
                    imageVector = if (allExpanded) Icons.Rounded.UnfoldLess else Icons.Rounded.UnfoldMore,
                    contentDescription = if (allExpanded) "Collapse all cards" else "Expand all cards",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Add Station Action Button
            IconButton(
                onClick = onAddStationClick,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Add Station",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// =================== PREVIEWS ===================

@Preview(name = "Dashboard - Light Mode", showBackground = true)
@Composable
fun DashboardScreenLightPreview() {
    val kxStation = DefaultStations.POPULAR_STATIONS[1]
    val oxStation = DefaultStations.POPULAR_STATIONS[0]

    val state = DashboardUiState(
        isInitialLoading = false,
        stationCards = listOf(
            StationCardUiModel(
                station = kxStation,
                departures = DefaultStations.getFallbackDepartures(kxStation.id, kxStation.name),
                availableLineBadges = listOf(
                    TflLineColors.getLineBadge("victoria", "Victoria", "tube"),
                    TflLineColors.getLineBadge("northern", "Northern", "tube"),
                    TflLineColors.getLineBadge("piccadilly", "Piccadilly", "tube")
                )
            ),
            StationCardUiModel(
                station = oxStation,
                departures = DefaultStations.getFallbackDepartures(oxStation.id, oxStation.name),
                availableLineBadges = listOf(
                    TflLineColors.getLineBadge("central", "Central", "tube"),
                    TflLineColors.getLineBadge("bakerloo", "Bakerloo", "tube")
                )
            )
        )
    )

    DepartureBoardTheme(darkTheme = false) {
        DashboardContent(
            uiState = state,
            snackbarHostState = remember { SnackbarHostState() },
            onRefresh = {},
            onDismissStation = {},
            onMoveStation = { _, _ -> },
            onAddStationClick = {},
            onAiAssistantClick = {},
            onStationClick = {}
        )
    }
}

@Preview(name = "Dashboard - Dark Mode", showBackground = true)
@Composable
fun DashboardScreenDarkPreview() {
    val kxStation = DefaultStations.POPULAR_STATIONS[1]
    val oxStation = DefaultStations.POPULAR_STATIONS[0]

    val state = DashboardUiState(
        isInitialLoading = false,
        stationCards = listOf(
            StationCardUiModel(
                station = kxStation,
                departures = DefaultStations.getFallbackDepartures(kxStation.id, kxStation.name),
                availableLineBadges = listOf(
                    TflLineColors.getLineBadge("victoria", "Victoria", "tube"),
                    TflLineColors.getLineBadge("northern", "Northern", "tube"),
                    TflLineColors.getLineBadge("piccadilly", "Piccadilly", "tube")
                )
            ),
            StationCardUiModel(
                station = oxStation,
                departures = DefaultStations.getFallbackDepartures(oxStation.id, oxStation.name),
                availableLineBadges = listOf(
                    TflLineColors.getLineBadge("central", "Central", "tube"),
                    TflLineColors.getLineBadge("bakerloo", "Bakerloo", "tube")
                )
            )
        )
    )

    DepartureBoardTheme(darkTheme = true) {
        DashboardContent(
            uiState = state,
            snackbarHostState = remember { SnackbarHostState() },
            onRefresh = {},
            onDismissStation = {},
            onMoveStation = { _, _ -> },
            onAddStationClick = {},
            onAiAssistantClick = {},
            onStationClick = {}
        )
    }
}
