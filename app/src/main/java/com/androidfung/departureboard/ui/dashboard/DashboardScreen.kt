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
import androidx.compose.foundation.shape.RoundedCornerShape
import com.androidfung.departureboard.ui.components.rememberReorderableStaggeredGridState
import com.androidfung.departureboard.ui.components.reorderableStaggeredGrid
import com.androidfung.departureboard.ui.components.reorderableStaggeredItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.androidfung.departureboard.BuildConfig
import com.androidfung.departureboard.ui.components.DebugSettingsBottomSheet
import com.androidfung.departureboard.ui.components.PaywallBottomSheet
import com.androidfung.departureboard.ui.theme.LocalWindowWidthClass
import com.androidfung.departureboard.ui.theme.WindowWidthClass
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
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DefaultStations
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TflLineColors
import com.androidfung.departureboard.ui.components.EmptyDashboardState
import com.androidfung.departureboard.ui.components.SearchStationBottomSheet
import com.androidfung.departureboard.ui.components.StationDepartureCard
import com.androidfung.departureboard.ui.components.StationDetailBottomSheet
import com.androidfung.departureboard.ui.components.TrainJourneyBottomSheet
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
    var showDebugSheet by rememberSaveable { mutableStateOf(false) }
    var selectedDepartureForJourney by remember { mutableStateOf<Departure?>(null) }

    LaunchedEffect(initialDetailStation) {
        if (initialDetailStation != null) {
            viewModel.openStationDetail(initialDetailStation)
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

    // Automatically pause auto-refresh polling and the 1s countdown ticker when the
    // screen is backgrounded or inactive, so neither keeps running with the screen off.
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        viewModel.startAutoRefreshPolling()
        viewModel.startCountdownTicker()
        onPauseOrDispose {
            viewModel.stopAutoRefreshPolling()
            viewModel.stopCountdownTicker()
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current

    // Nearest-station detection requires a location permission, which is requested
    // contextually with a rationale (e.g. when opening search nearby or AI assistant),
    // rather than abruptly at app launch.
    @OptIn(com.google.accompanist.permissions.ExperimentalPermissionsApi::class)
    val fineLocationPermission = rememberPermissionState(android.Manifest.permission.ACCESS_FINE_LOCATION)
    @OptIn(com.google.accompanist.permissions.ExperimentalPermissionsApi::class)
    val coarseLocationPermission = rememberPermissionState(android.Manifest.permission.ACCESS_COARSE_LOCATION)
    var showLocationRationale by remember { mutableStateOf(false) }

    @OptIn(com.google.accompanist.permissions.ExperimentalPermissionsApi::class)
    val fineStatus = fineLocationPermission.status
    @OptIn(com.google.accompanist.permissions.ExperimentalPermissionsApi::class)
    val coarseStatus = coarseLocationPermission.status
    @OptIn(com.google.accompanist.permissions.ExperimentalPermissionsApi::class)
    val locationGranted =
        fineStatus is PermissionStatus.Granted ||
        coarseStatus is PermissionStatus.Granted

    val requestLocationWithRationale = {
        if (!locationGranted) {
            showLocationRationale = true
        }
    }

    // Re-run the nearest-station lookup whenever permission is granted (or re-granted).
    // Uses local candidate stations at startup without firing extra HTTP queries.
    LaunchedEffect(locationGranted) {
        if (locationGranted) {
            viewModel.updateNearestStation(context, fetchApiNearby = false)
        }
    }

    DashboardContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onRefresh = { viewModel.refreshDepartures(isManualPullToRefresh = true) },
        onDismissStation = { station -> viewModel.removeStation(station) },
        onMoveStation = { fromIndex, toIndex -> viewModel.moveStation(fromIndex, toIndex) },
        onAddStationClick = {
            if (locationGranted) {
                viewModel.updateNearestStation(context, fetchApiNearby = true)
            }
            showSearchSheet = true
            onNavigateToSearch()
        },
        onStationClick = { station ->
            viewModel.openStationDetail(station)
            onStationClick(station)
        },
        onDepartureClick = { departure ->
            selectedDepartureForJourney = departure
        },
        onAiAssistantClick = { showAiSheet = true },
        onUpgradeClick = { viewModel.showPaywall("Unlock Prompt Departure Pro") },
        onOpenDebugSettings = {
            android.util.Log.d("DashboardScreen", "onOpenDebugSettings clicked!")
            showDebugSheet = true
        },
        modifier = modifier
    )

    // Search Stations Sheet (opens station details directly without auto-saving)
    if (showSearchSheet) {
        SearchStationBottomSheet(
            onDismissRequest = { showSearchSheet = false },
            onStationSelected = { station ->
                showSearchSheet = false
                viewModel.openStationDetail(station)
            },
            savedStationIds = remember(uiState.stationCards) {
                uiState.stationCards.asSequence().map { it.station.id }.toSet()
            },
            nearbyStations = uiState.nearbyStations,
            hasLocationPermission = locationGranted,
            onRequestLocation = requestLocationWithRationale,
            onSearchQuery = { query -> viewModel.searchStations(query) }
        )
    }

    // Station Departure Details Sheet
    uiState.selectedDetailStation?.let { station ->
        val isSaved = uiState.stationCards.any { it.station.id == station.id }
        StationDetailBottomSheet(
            station = station,
            departures = uiState.detailDepartures,
            isLoading = uiState.isDetailLoading,
            isSaved = isSaved,
            onToggleSaveStation = { st ->
                if (isSaved) {
                    viewModel.removeStation(st)
                } else {
                    viewModel.addStation(st)
                }
            },
            onRefresh = { viewModel.openStationDetail(station) },
            onDepartureClick = { departure ->
                selectedDepartureForJourney = departure
            },
            onDismissRequest = { viewModel.closeStationDetail() }
        )
    }

    // Train Journey & Calling Points Details Sheet
    selectedDepartureForJourney?.let { departure ->
        TrainJourneyBottomSheet(
            departure = departure,
            onDismissRequest = { selectedDepartureForJourney = null },
            onLoadCallingPoints = { dep -> viewModel.getCallingPoints(dep) }
        )
    }

    // Natural Language Transit Assistant Sheet
    if (showAiSheet) {
        val nearest = uiState.nearestStation
            ?: uiState.stationCards.firstOrNull { it.station.id == uiState.nearestStationId }?.station
        com.androidfung.departureboard.ui.components.AiTransitSheet(
            onDismissRequest = { showAiSheet = false },
            repository = viewModel.repository,
            savedStations = uiState.stationCards.map { it.station },
            nearestStation = nearest,
            initialTriggerSpeech = false,
            billingRepository = viewModel.billingRepository,
            aiModelType = uiState.aiModelType,
            onUpgradeClick = { viewModel.showPaywall("Unlock unlimited Prompt Departure AI queries with Pro") }
        )
    }

    // Location Permission Rationale Dialog
    if (showLocationRationale) {
        @OptIn(com.google.accompanist.permissions.ExperimentalPermissionsApi::class)
        com.androidfung.departureboard.ui.components.LocationRationaleDialog(
            onConfirm = {
                showLocationRationale = false
                fineLocationPermission.launchPermissionRequest()
            },
            onDismiss = {
                showLocationRationale = false
            }
        )
    }

    // Paywall Dialog Sheet
    if (uiState.paywallPromptReason != null) {
        PaywallBottomSheet(
            onDismissRequest = { viewModel.dismissPaywall() },
            billingRepository = viewModel.billingRepository,
            reasonMessage = uiState.paywallPromptReason
        )
    }

    // Developer Debug Settings Sheet (Only enabled in debug builds)
    if (showDebugSheet && BuildConfig.DEBUG) {
        DebugSettingsBottomSheet(
            onDismissRequest = { showDebugSheet = false },
            billingRepository = viewModel.billingRepository,
            isPro = uiState.isPro,
            onTogglePro = { viewModel.toggleDebugPro() },
            onResetStations = { viewModel.resetSavedStationsToDefaults() },
            selectedAiModel = uiState.aiModelType,
            onSelectAiModel = { model -> viewModel.setAiModelType(model) }
        )
    }
}

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
    onDepartureClick: ((Departure) -> Unit)? = null,
    onAiAssistantClick: () -> Unit = {},
    onUpgradeClick: () -> Unit = {},
    onOpenDebugSettings: () -> Unit = {}
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
        val windowWidthClass = LocalWindowWidthClass.current
        val gridColumns = when (windowWidthClass) {
            WindowWidthClass.NARROW, WindowWidthClass.REGULAR -> StaggeredGridCells.Fixed(1)
            WindowWidthClass.EXPANDED, WindowWidthClass.LARGE -> StaggeredGridCells.Fixed(2)
            WindowWidthClass.EXTRA_LARGE -> StaggeredGridCells.Fixed(3)
        }

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
                columns = gridColumns,
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
                    DashboardHeader(
                        isPro = uiState.isPro,
                        onAddStationClick = onAddStationClick,
                        onUpgradeClick = onUpgradeClick,
                        onOpenDebugSettings = onOpenDebugSettings
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
                    // All cards uniformly occupy 1 column for consistent grid layout
                    items(
                        items = uiState.stationCards,
                        key = { it.station.id + it.station.name },
                        span = { StaggeredGridItemSpan.SingleLane }
                    ) { cardModel ->
                        val isNearest = uiState.nearestStationId == cardModel.station.id
                        StationDepartureCard(
                            cardModel = cardModel,
                            onDismissStation = onDismissStation,
                            onStationClick = onStationClick,
                            onDepartureClick = onDepartureClick,
                            isNearest = isNearest,
                            distanceMeters = if (isNearest) uiState.nearestStationDistanceMeters else null,
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
 * Expressive header component with clean title typography, Pro badge, Add button, and Overflow Menu.
 */
@Composable
fun DashboardHeader(
    modifier: Modifier = Modifier,
    isPro: Boolean = false,
    onAddStationClick: () -> Unit = {},
    onUpgradeClick: () -> Unit = {},
    onOpenDebugSettings: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val windowWidthClass = LocalWindowWidthClass.current
        val (titleFontSize, titleLineHeight) = when (windowWidthClass) {
            WindowWidthClass.NARROW -> 20.sp to 24.sp
            WindowWidthClass.REGULAR -> 24.sp to 28.sp
            WindowWidthClass.EXPANDED -> 28.sp to 32.sp
            else -> 32.sp to 36.sp
        }

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Prompt Departure",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = titleFontSize,
                    lineHeight = titleLineHeight
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )

            if (isPro) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFFFFD700),
                                    Color(0xFFFF9800)
                                )
                            )
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "PRO",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                        color = Color.Black,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (!isPro) {
                // Upgrade to Pro Action Button
                IconButton(
                    onClick = onUpgradeClick,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WorkspacePremium,
                        contentDescription = "Upgrade to Pro",
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Search Station Action Button (opens search sheet to check live departures without auto-adding)
            IconButton(
                onClick = onAddStationClick,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Search Stations",
                    modifier = Modifier.size(24.dp)
                )
            }

            // 3-dot Overflow Menu Button (Only rendered if there are menu options)
            val showOverflowMenu = BuildConfig.DEBUG || !isPro
            if (showOverflowMenu) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "More options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (BuildConfig.DEBUG) {
                            DropdownMenuItem(
                                text = { Text("Debug Settings") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.BugReport,
                                        contentDescription = null,
                                        tint = Color(0xFFE91E63)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onOpenDebugSettings()
                                }
                            )
                        }

                        if (!isPro) {
                            DropdownMenuItem(
                                text = { Text("Upgrade to Pro") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.WorkspacePremium,
                                        contentDescription = null,
                                        tint = Color(0xFFFFB300)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onUpgradeClick()
                                }
                            )
                        }
                    }
                }
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
