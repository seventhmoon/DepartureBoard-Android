package com.androidfung.departureboard.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Traffic
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import com.androidfung.departureboard.BuildConfig
import com.androidfung.departureboard.ui.theme.LocalWindowWidthClass
import com.androidfung.departureboard.ui.theme.WindowWidthClass
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DefaultStations
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TflLineColors
import com.androidfung.departureboard.ui.components.EmptyDashboardState
import com.androidfung.departureboard.ui.components.StationDepartureCard
import com.androidfung.departureboard.ui.theme.DepartureBoardTheme

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
    onDepartureClick: ((Departure) -> Unit)? = null,
    onAiAssistantClick: () -> Unit = {},
    onUpgradeClick: () -> Unit = {},
    onOpenLineStatus: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenDebugSettings: () -> Unit = {},
    onOpenHelpFaq: () -> Unit = {},
    staggeredGridState: androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState = rememberLazyStaggeredGridState()
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val pullToRefreshState = rememberPullToRefreshState()
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
            .background(backgroundBrush)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    val windowWidthClass = LocalWindowWidthClass.current
                    val isTwoPane = com.androidfung.departureboard.ui.components.LocalIsTwoPaneMode.current
                    val (titleFontSize, titleLineHeight) = when {
                        isTwoPane -> 20.sp to 24.sp
                        windowWidthClass == WindowWidthClass.NARROW -> 20.sp to 24.sp
                        windowWidthClass == WindowWidthClass.REGULAR -> 24.sp to 28.sp
                        windowWidthClass == WindowWidthClass.EXPANDED -> 28.sp to 32.sp
                        else -> 32.sp to 36.sp
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "EkiBoard",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = titleFontSize,
                                lineHeight = titleLineHeight
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )

                        if (uiState.isPro) {
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

                        // Debug Region Switcher Chip (Shown when HK experimental is enabled)
                        if (uiState.isHkRegionEnabled) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .clickable { onOpenDebugSettings() }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "${uiState.selectedRegion.flagEmoji} ${uiState.selectedRegion.id}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                },
                actions = {
                    var showMenu by remember { mutableStateOf(false) }
                    val disruptedCount = remember(uiState.lineStatuses, uiState.selectedRegion) {
                        com.androidfung.departureboard.ui.components.buildDisplayLineStatusItems(
                            uiState.lineStatuses,
                            uiState.selectedRegion
                        ).count { it.isDisrupted }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box {
                            IconButton(onClick = onOpenLineStatus) {
                                Icon(
                                    imageVector = Icons.Rounded.Traffic,
                                    contentDescription = "Live Network Status",
                                    tint = if (disruptedCount > 0) {
                                        com.androidfung.departureboard.ui.theme.MinorDelaysAmber
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                            if (disruptedCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 8.dp, end = 8.dp)
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(com.androidfung.departureboard.ui.theme.MinorDelaysAmber)
                                )
                            }
                        }

                        if (!uiState.isPro) {
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
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (disruptedCount > 0) {
                                                "Network Status ($disruptedCount)"
                                            } else {
                                                "Network Status"
                                            }
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Traffic,
                                            contentDescription = null,
                                            tint = if (disruptedCount > 0) {
                                                com.androidfung.departureboard.ui.theme.MinorDelaysAmber
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            }
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        onOpenLineStatus()
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Settings,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        onOpenSettings()
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Help & FAQ") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        onOpenHelpFaq()
                                    }
                                )

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

                                if (!uiState.isPro) {
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            // Expressive Extended FAB for EkiBoard Transit AI Assistant
            ExtendedFloatingActionButton(
                text = { Text("Ask AI") },
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = "Ask EkiBoard AI",
                        modifier = Modifier.size(24.dp)
                    )
                },
                expanded = !staggeredGridState.isScrollInProgress,
                onClick = onAiAssistantClick,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                modifier = Modifier.padding(end = 4.dp, bottom = 4.dp)
            )
        }
    ) { innerPadding ->
        val windowWidthClass = LocalWindowWidthClass.current
        val isTwoPane = com.androidfung.departureboard.ui.components.LocalIsTwoPaneMode.current
        val isThreePane = com.androidfung.departureboard.ui.components.LocalIsThreePaneMode.current
        val gridColumns = when {
            isThreePane -> StaggeredGridCells.Fixed(1)
            isTwoPane && windowWidthClass == WindowWidthClass.EXTRA_LARGE -> StaggeredGridCells.Fixed(2)
            isTwoPane -> StaggeredGridCells.Fixed(1)
            windowWidthClass == WindowWidthClass.NARROW || windowWidthClass == WindowWidthClass.REGULAR -> StaggeredGridCells.Fixed(1)
            windowWidthClass == WindowWidthClass.EXPANDED || windowWidthClass == WindowWidthClass.LARGE -> StaggeredGridCells.Fixed(2)
            else -> StaggeredGridCells.Fixed(3)
        }

        val showInGridAd = !uiState.isPro
        val adInterval = 3
        val initialAdOffset = 2

        val (firstChunk, remainingChunks) = remember(uiState.stationCards, showInGridAd) {
            val first = uiState.stationCards.take(initialAdOffset)
            val remaining = uiState.stationCards.drop(initialAdOffset).chunked(adInterval)
            Pair(first, remaining)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                state = pullToRefreshState,
                modifier = Modifier.fillMaxSize()
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
                        top = 10.dp,
                        bottom = 96.dp // Extra clearance for the expressive FAB
                    ),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalItemSpacing = 20.dp
                ) {
                    // Search bar as the first item spanning full line
                    item(key = "search_bar", span = StaggeredGridItemSpan.FullLine) {
                        DashboardSearchBar(
                            onClick = onAddStationClick,
                            lastUpdatedTimestamp = uiState.lastUpdatedTimestamp,
                            isOnline = uiState.isOnline,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (!uiState.isOnline) {
                        item(key = "offline_banner", span = StaggeredGridItemSpan.FullLine) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.LocationOn,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = "Offline • Showing cached departures",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (uiState.isInitialLoading && uiState.stationCards.isEmpty()) {
                        // Shimmer skeleton cards matching adaptive grid columns during initial load
                        items(
                            count = 4,
                            key = { "skeleton_$it" },
                            span = { StaggeredGridItemSpan.SingleLane }
                        ) {
                            com.androidfung.departureboard.ui.components.StationCardSkeleton()
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
                        // Station Departure Cards in adaptive staggered grid.
                        // Dynamically interleave NativeAdCard: first after 2nd station, then every 3 stations thereafter.
                        if (!showInGridAd) {
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
                                    maxDepartureRows = uiState.departureRowsCount,
                                    modifier = Modifier
                                        .reorderableStaggeredItem(reorderState, cardModel.station.id)
                                        .animateItem()
                                )
                            }
                        } else {
                            items(
                                items = firstChunk,
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
                                    maxDepartureRows = uiState.departureRowsCount,
                                    modifier = Modifier
                                        .reorderableStaggeredItem(reorderState, cardModel.station.id)
                                        .animateItem()
                                )
                            }

                            // First Native Ad Card
                            item(key = "native_ad_card_0", span = StaggeredGridItemSpan.SingleLane) {
                                com.androidfung.departureboard.ui.components.NativeAdViewContainer(
                                    style = com.androidfung.departureboard.ui.components.NativeAdStyle.CARD,
                                    modifier = Modifier.animateItem()
                                )
                            }

                            // Subsequent chunks, each followed by a native ad card
                            remainingChunks.forEachIndexed { chunkIndex, chunk ->
                                items(
                                    items = chunk,
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
                                        maxDepartureRows = uiState.departureRowsCount,
                                        modifier = Modifier
                                            .reorderableStaggeredItem(reorderState, cardModel.station.id)
                                            .animateItem()
                                    )
                                }

                                item(key = "native_ad_card_${chunkIndex + 1}", span = StaggeredGridItemSpan.SingleLane) {
                                    com.androidfung.departureboard.ui.components.NativeAdViewContainer(
                                        style = com.androidfung.departureboard.ui.components.NativeAdStyle.CARD,
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Material 3 Search Bar Pill Anchor displayed directly below the Dashboard AppBar.
 * Tapping it smoothly launches the station search and discovery experience.
 */
@Composable
fun DashboardSearchBar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    lastUpdatedTimestamp: Long = 0L,
    isOnline: Boolean = true
) {
    val updatedTimeText = remember(lastUpdatedTimestamp) {
        if (lastUpdatedTimestamp <= 0L) null
        else java.time.Instant.ofEpochMilli(lastUpdatedTimestamp)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Search station, line, or bus stop…",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Normal,
                        fontSize = 15.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (updatedTimeText != null && isOnline) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(com.androidfung.departureboard.ui.theme.GoodServiceGreen)
                    )
                    Text(
                        text = updatedTimeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }

                // Quick nearby indicator icon
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = "Nearby stations",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
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
