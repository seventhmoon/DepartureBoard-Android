package com.androidfung.departureboard.ui.feature.detail

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.DesktopWindows
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.androidfung.departureboard.R
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.ui.ads.NativeAdStyle
import com.androidfung.departureboard.ui.ads.NativeAdUnits
import com.androidfung.departureboard.ui.ads.NativeAdViewContainer
import com.androidfung.departureboard.ui.ads.rememberNativeAds
import com.androidfung.departureboard.ui.designsystem.DepartureRowItem
import com.androidfung.departureboard.ui.designsystem.DepartureRowVariant
import com.androidfung.departureboard.ui.designsystem.LastTrainServiceBanner
import com.androidfung.departureboard.ui.designsystem.LineDisruptionBanner
import com.androidfung.departureboard.ui.designsystem.MultipleDisruptionsBanner
import com.androidfung.departureboard.ui.designsystem.TransitIconHelper

private sealed interface StationDetailListItem {
    data class DepartureItem(val departure: Departure, val index: Int, val stableKey: String) : StationDetailListItem
    data class AdItem(val key: String, val nativeAd: com.google.android.gms.ads.nativead.NativeAd, val style: NativeAdStyle = NativeAdStyle.ROW) : StationDetailListItem
}

@Composable
fun StationDetailScreen(
    station: Station,
    departures: List<Departure>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isSaved: Boolean = false,
    savedStationIds: Set<String> = if (isSaved) setOf(station.id) else emptySet(),
    onToggleSaveStation: (Station) -> Unit = {},
    initialSelectedLineId: String? = null,
    onDepartureClick: ((departure: Departure, contextDepartures: List<Departure>) -> Unit)? = null,
    serviceHoursInfo: com.androidfung.departureboard.data.model.gb.StationServiceHoursInfo? = null,
    accessibilityAlerts: List<com.androidfung.departureboard.data.model.TransitAlert> = emptyList(),
    isPro: Boolean = false,
    stations: List<Station> = listOf(station),
    onStationChange: ((Station) -> Unit)? = null,
    onOpenDeskClock: ((Station) -> Unit)? = null,
    isBikeshareEnabled: Boolean = false,
    lineStatuses: Map<String, com.androidfung.departureboard.data.model.gb.TflLineStatusItem> = emptyMap()
) {
    val stationList = remember(stations, station) {
        val combined = if (stations.none { it.id == station.id }) {
            listOf(station) + stations
        } else {
            stations
        }
        combined.distinctBy { it.id }
    }

    val initialIndex = remember(stationList, station.id) {
        stationList.indexOfFirst { it.id == station.id }.coerceAtLeast(0)
    }

    if (stationList.size <= 1) {
        StationDetailContent(
            station = station,
            departures = departures,
            isLoading = isLoading,
            onRefresh = onRefresh,
            onBack = onBack,
            modifier = modifier,
            isSaved = isSaved,
            onToggleSaveStation = onToggleSaveStation,
            initialSelectedLineId = initialSelectedLineId,
            onDepartureClick = onDepartureClick,
            serviceHoursInfo = serviceHoursInfo,
            accessibilityAlerts = accessibilityAlerts,
            isPro = isPro,
            onOpenDeskClock = onOpenDeskClock,
            isBikeshareEnabled = isBikeshareEnabled,
            lineStatuses = lineStatuses
        )
    } else {
        val pagerState = androidx.compose.foundation.pager.rememberPagerState(
            initialPage = initialIndex,
            pageCount = { stationList.size }
        )

        LaunchedEffect(station.id) {
            val targetPage = stationList.indexOfFirst { it.id == station.id }
            if (targetPage >= 0 && targetPage != pagerState.currentPage) {
                pagerState.scrollToPage(targetPage)
            }
        }

        LaunchedEffect(pagerState.settledPage, stationList) {
            val settledIdx = pagerState.settledPage
            if (settledIdx in stationList.indices) {
                val currentStation = stationList[settledIdx]
                if (currentStation.id != station.id) {
                    onStationChange?.invoke(currentStation)
                }
            }
        }

        val coroutineScope = rememberCoroutineScope()

        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = modifier.fillMaxSize(),
            key = { stationList[it].id }
        ) { page ->
            val pageStation = stationList[page]
            val pageDepartures = if (pageStation.id == station.id) departures else emptyList()
            val pageLoading = if (pageStation.id == station.id) isLoading else true

            StationDetailContent(
                station = pageStation,
                departures = pageDepartures,
                isLoading = pageLoading,
                onRefresh = onRefresh,
                onBack = onBack,
                modifier = Modifier.fillMaxSize(),
                isSaved = pageStation.id in savedStationIds || (pageStation.id == station.id && isSaved),
                onToggleSaveStation = onToggleSaveStation,
                initialSelectedLineId = initialSelectedLineId,
                onDepartureClick = onDepartureClick,
                serviceHoursInfo = if (pageStation.id == station.id) serviceHoursInfo else null,
                accessibilityAlerts = if (pageStation.id == station.id) accessibilityAlerts else emptyList(),
                isPro = isPro,
                pageInfo = stringResource(R.string.pager_page_of_total, page + 1, stationList.size),
                pageCount = stationList.size,
                currentPage = page,
                onPageClick = { targetPage ->
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(targetPage)
                    }
                },
                onOpenDeskClock = onOpenDeskClock,
                isBikeshareEnabled = isBikeshareEnabled,
                lineStatuses = lineStatuses
            )
        }
    }
}

/**
 * Visual indicator pills representing the current station in a multi-station horizontal pager.
 */
@Composable
private fun StationDetailPagerIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
    onPageClick: ((Int) -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val pagerStateDesc = stringResource(R.string.cd_station_pager_dot, currentPage + 1, pageCount)
    val maxVisibleDots = 7
    val shouldLimitDots = pageCount > maxVisibleDots
    val visiblePageRange = if (shouldLimitDots) {
        val half = maxVisibleDots / 2
        val start = (currentPage - half).coerceIn(0, pageCount - maxVisibleDots)
        start until (start + maxVisibleDots)
    } else {
        0 until pageCount
    }

    Row(
        modifier = modifier
            .semantics {
                stateDescription = pagerStateDesc
            },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in visiblePageRange) {
            val isSelected = i == currentPage
            val isEdge = shouldLimitDots && (i == visiblePageRange.first || i == visiblePageRange.last) && (i != 0 && i != pageCount - 1)
            val baseDotSize = if (isEdge) 4.dp else 6.dp
            val dotWidthState = animateDpAsState(
                targetValue = if (isSelected) 16.dp else baseDotSize,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "dotWidth_$i"
            )
            val dotHeight = if (isSelected) 6.dp else baseDotSize
            val dotColorState = animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isEdge) 0.2f else 0.35f)
                },
                label = "dotColor_$i"
            )

            val clickModifier = if (onPageClick != null) {
                val switchLabel = stringResource(R.string.cd_station_pager_switch, i + 1)
                Modifier.clickable(
                    role = Role.Tab,
                    onClickLabel = switchLabel
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onPageClick(i)
                }
            } else Modifier

            Box(
                modifier = Modifier
                    .then(clickModifier)
                    .padding(vertical = 4.dp, horizontal = 1.dp)
                    .layout { measurable, _ ->
                        val wPx = dotWidthState.value.roundToPx()
                        val hPx = dotHeight.roundToPx()
                        val placeable = measurable.measure(
                            Constraints.fixed(wPx, hPx)
                        )
                        layout(wPx, hPx) {
                            placeable.placeRelative(0, 0)
                        }
                    }
                    .clip(CircleShape)
                    .drawBehind {
                        drawRect(dotColorState.value)
                    }
            )
        }
    }
}

@Composable
private fun StationDetailContent(
    station: Station,
    departures: List<Departure>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isSaved: Boolean = false,
    onToggleSaveStation: (Station) -> Unit = {},
    initialSelectedLineId: String? = null,
    onDepartureClick: ((departure: Departure, contextDepartures: List<Departure>) -> Unit)? = null,
    serviceHoursInfo: com.androidfung.departureboard.data.model.gb.StationServiceHoursInfo? = null,
    accessibilityAlerts: List<com.androidfung.departureboard.data.model.TransitAlert> = emptyList(),
    isPro: Boolean = false,
    pageInfo: String? = null,
    pageCount: Int = 1,
    currentPage: Int = 0,
    onPageClick: ((Int) -> Unit)? = null,
    onOpenDeskClock: ((Station) -> Unit)? = null,
    isBikeshareEnabled: Boolean = false,
    lineStatuses: Map<String, com.androidfung.departureboard.data.model.gb.TflLineStatusItem> = emptyMap()
) {
    val modeIconRes = TransitIconHelper.getTransitModeIconRes(station.primaryTransitMode)

    var selectedLineId by remember(station.id, initialSelectedLineId) { mutableStateOf(initialSelectedLineId) }
    var selectedDirection by remember(station.id) { mutableStateOf<String?>(null) }
    var selectedStopId by remember(station.id) { mutableStateOf<String?>(null) }

    // Distinct sub-stops (bays/platforms) present in departures (e.g. Stop A, Stop P, Stop Z4, Platform 1, etc.)
    val distinctStops = remember(departures) {
        departures
            .asSequence()
            .filter { it.hasSpecificPlatform }
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

    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val screenHeightDp = remember(density, windowInfo) {
        with(density) { windowInfo.containerSize.height.toDp().value.toInt() }
    }

    val departureItemHeightDp = 72 // DepartureDetailItemRow height + 8dp spacing
    val headerHeightEstimateDp = 220 // Top bar, station header, line chips, section title
    val cardAdHeightDp = 240 // Height needed for a comfortable rich media card ad
    val estimatedContentHeight = headerHeightEstimateDp + (filteredDepartures.size * departureItemHeightDp)
    val hasRoomForBottomCardAd = (screenHeightDp - estimatedContentHeight) >= cardAdHeightDp

    val maxAdsNeeded = remember(filteredDepartures.size, isPro, hasRoomForBottomCardAd) {
        if (isPro || filteredDepartures.isEmpty()) {
            0
        } else if (hasRoomForBottomCardAd) {
            1
        } else {
            // Ads at index 2, 8, 14, ...
            if (filteredDepartures.size <= 2) 0 else (1 + (filteredDepartures.size - 3) / 6).coerceAtMost(3)
        }
    }

    val adUnitId = remember(hasRoomForBottomCardAd) {
        if (com.androidfung.departureboard.BuildConfig.DEBUG) {
            com.androidfung.departureboard.ui.ads.TEST_NATIVE_AD_UNIT_ID
        } else {
            if (hasRoomForBottomCardAd) NativeAdUnits.DASHBOARD else NativeAdUnits.STATION_DETAIL
        }
    }

    val loadedAds = rememberNativeAds(
        adUnitId = adUnitId,
        count = maxAdsNeeded,
        enabled = maxAdsNeeded > 0
    )

    // Dynamically calculate whether the departures fit with ample space for a rich media Card ad,
    // or whether they fill/overflow the viewport, requiring in-stream compact ROW ads.
    val displayItems = remember(filteredDepartures, loadedAds, hasRoomForBottomCardAd) {
        val seenIds = HashMap<String, Int>()
        fun uniqueKeyFor(dep: Departure): String {
            val base = dep.id.ifBlank { "${dep.stationId}_${dep.lineId}_${dep.destinationName}_${dep.expectedArrivalIso.orEmpty()}" }
            val count = seenIds.getOrDefault(base, 0)
            seenIds[base] = count + 1
            return if (count == 0) base else "${base}#$count"
        }

        buildList<StationDetailListItem> {
            if (hasRoomForBottomCardAd) {
                // Viewport has room below all departures: show uninterrupted departures + bottom rich card ad if loaded
                filteredDepartures.forEachIndexed { index, departure ->
                    add(StationDetailListItem.DepartureItem(departure, index, uniqueKeyFor(departure)))
                }
                loadedAds.firstOrNull()?.let { ad ->
                    add(StationDetailListItem.AdItem("ad_bottom_card", nativeAd = ad, style = NativeAdStyle.CARD))
                }
            } else {
                // List fills or overflows screen: interleave loaded compact ROW ads so they don't dominate the content
                var adIndex = 0
                filteredDepartures.forEachIndexed { index, departure ->
                    add(StationDetailListItem.DepartureItem(departure, index, uniqueKeyFor(departure)))
                    if (index == 2 || (index > 2 && (index - 2) % 6 == 0)) {
                        if (adIndex < loadedAds.size) {
                            val ad = loadedAds[adIndex]
                            add(StationDetailListItem.AdItem("ad_$index", nativeAd = ad, style = NativeAdStyle.ROW))
                            adIndex++
                        }
                    }
                }
            }
        }
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
                com.androidfung.departureboard.util.NotificationHelper.startTrackingWithPermissionCheck(context, dep) { pendingDep ->
                    pendingTrackDeparture = pendingDep
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
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
                    painter = androidx.compose.ui.res.painterResource(modeIconRes),
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

            if (pageCount > 1) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                ) {
                    StationDetailPagerIndicator(
                        pageCount = pageCount,
                        currentPage = currentPage,
                        onPageClick = onPageClick
                    )
                    if (!pageInfo.isNullOrBlank()) {
                        Text(
                            text = pageInfo,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else if (!pageInfo.isNullOrBlank()) {
                Text(
                    text = pageInfo,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            // Subtitle: Bus stop "towards ..." indicator for single-direction bus stops
            val distinctTowards = remember(departures) {
                departures
                    .asSequence()
                    .mapNotNull { it.towards?.takeIf { t -> t.isNotBlank() && t.lowercase() != "null" } }
                    .distinct()
                    .toList()
            }
            val isSingleDirectionBusStop = station.isBusOnly && distinctTowards.size == 1

            if (isSingleDirectionBusStop) {
                Text(
                    text = stringResource(R.string.station_towards, distinctTowards.first()),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val displayZone = station.displayZone
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (displayZone != null) {
                    Text(
                        text = stringResource(R.string.station_zone, displayZone),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Station accessibility badge
                val wheelchairStatus = station.wheelchairStatus
                if (wheelchairStatus != com.androidfung.departureboard.data.model.WheelchairBoardingStatus.UNKNOWN) {
                    val statusText = when (wheelchairStatus) {
                        com.androidfung.departureboard.data.model.WheelchairBoardingStatus.STEP_FREE_LEVEL_BOARDING -> stringResource(R.string.status_level_boarding)
                        com.androidfung.departureboard.data.model.WheelchairBoardingStatus.STEP_FREE_WITH_RAMP -> stringResource(R.string.status_ramp_boarding)
                        com.androidfung.departureboard.data.model.WheelchairBoardingStatus.NOT_ACCESSIBLE -> stringResource(R.string.status_not_accessible)
                        com.androidfung.departureboard.data.model.WheelchairBoardingStatus.UNKNOWN -> null
                    }
                    if (statusText != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (wheelchairStatus == com.androidfung.departureboard.data.model.WheelchairBoardingStatus.NOT_ACCESSIBLE) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            }
                        ) {
                            Text(
                                text = "♿ $statusText",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                color = if (wheelchairStatus == com.androidfung.departureboard.data.model.WheelchairBoardingStatus.NOT_ACCESSIBLE) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                },
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
            DetailHeaderIconButton(
                onClick = {
                    val queryText = TransitIconHelper.buildStationMapsQuery(station)
                    val encodedQuery = java.net.URLEncoder.encode(queryText, "UTF-8")

                    val geoUri = if (station.lat != null && station.lon != null) {
                        "geo:${station.lat},${station.lon}?q=$encodedQuery".toUri()
                    } else {
                        "geo:0,0?q=$encodedQuery".toUri()
                    }
                    val mapIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                        setPackage("com.google.android.apps.maps")
                    }
                    try {
                        context.startActivity(mapIntent)
                    } catch (_: android.content.ActivityNotFoundException) {
                        val fallbackIntent = Intent(Intent.ACTION_VIEW, geoUri)
                        try {
                            context.startActivity(fallbackIntent)
                        } catch (_: android.content.ActivityNotFoundException) {
                            val browserUri = "https://www.google.com/maps/search/?api=1&query=$encodedQuery".toUri()
                            context.startActivity(Intent(Intent.ACTION_VIEW, browserUri))
                        }
                    }
                },
                imageVector = Icons.Rounded.Map,
                contentDescription = stringResource(R.string.cd_open_in_maps)
            )

            // Split-Flap Desk Clock Button
            if (onOpenDeskClock != null) {
                DetailHeaderIconButton(
                    onClick = { onOpenDeskClock(station) },
                    imageVector = Icons.Rounded.DesktopWindows,
                    contentDescription = stringResource(R.string.cd_open_desk_clock),
                    iconSize = 18.dp
                )
            }

            // Refresh Departures Button
            DetailHeaderIconButton(
                onClick = onRefresh,
                imageVector = Icons.Rounded.Refresh,
                contentDescription = stringResource(R.string.cd_refresh_departures)
            )

            // Bookmark / Pin to Home icon button
            DetailHeaderIconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleSaveStation(station)
                },
                imageVector = if (isSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                contentDescription = if (isSaved) {
                    stringResource(R.string.cd_remove_from_home)
                } else {
                    stringResource(R.string.cd_pin_to_home)
                },
                containerColor = if (isSaved) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                contentColor = if (isSaved) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    ) { scaffoldPadding ->

        // Lines badges serving this station - now interactive filter chips
        val distinctLines = remember(departures) {
            departures.map { it.lineBadge }
                .distinctBy { it.lineId }
                .sortedWith(com.androidfung.departureboard.data.model.LineBadgeInfo.NATURAL_COMPARATOR)
        }
        // Show line badges if multiple lines exist, OR if single line has multiple directions to filter
        val singleLineHasDirections = remember(distinctLines, departures) {
            distinctLines.size == 1 && departures.mapNotNull { it.direction }.distinct().size > 1
        }

        val isTwoPane = LocalIsTwoPaneMode.current
        val isThreePane = LocalIsThreePaneMode.current
        val isMultiPane = isTwoPane || isThreePane

        val synthesizedDisruptedBadges = remember(station, departures, lineStatuses) {
            com.androidfung.departureboard.ui.dashboard.StationBadgeSynthesizer
                .buildStationLineBadges(station, departures, lineStatuses)
                .filter { it.isDisrupted }
        }
        val allDisruptedBadges = remember(distinctLines, synthesizedDisruptedBadges) {
            (distinctLines.filter { it.isDisrupted } + synthesizedDisruptedBadges)
                .distinctBy { it.lineId.lowercase() }
        }

        val disruptedBadgesToShow = remember(allDisruptedBadges, selectedLineId, isMultiPane) {
            if (selectedLineId != null) {
                allDisruptedBadges.filter { TransitIconHelper.isSameLine(it.lineId, selectedLineId) }
            } else if (!isMultiPane) {
                allDisruptedBadges
            } else {
                emptyList()
            }
        }

        val isBusStop = station.isBusOnly

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp + scaffoldPadding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Station Overview & Fares (GTFS-Fares v2 & OSM Station Accessibility)
            item(key = "station_meta_chips", contentType = "station_meta_chips") {
                val stationRegion = com.androidfung.departureboard.data.model.TransitRegion.fromStationId(station.id)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (stationRegion.supportsGbPeakFareIndicator) {
                        val currentFarePeriod = com.androidfung.departureboard.data.model.FarePeriod.currentGbFarePeriod()
                        val isPeak = currentFarePeriod == com.androidfung.departureboard.data.model.FarePeriod.PEAK
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isPeak) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Text(
                                text = if (isPeak) "🟠 ${stringResource(R.string.fare_peak)}" else "🟢 ${stringResource(R.string.fare_off_peak)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isPeak) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (!station.zone.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.fare_zone_pill, station.zone),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    val a11y = station.accessibility
                    if (a11y?.hasTactilePaving == true) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Text(
                                text = "🦯 ${stringResource(R.string.amenity_tactile_paving)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    if (a11y?.hasHearingLoop == true) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Text(
                                text = "🦻 ${stringResource(R.string.amenity_hearing_loop)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    if (a11y?.hasAccessibleToilet == true) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Text(
                                text = "🚻 ${stringResource(R.string.amenity_accessible_toilet)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Micromobility & Bikeshare Hubs (GBFS station_status.json)
            if (isBikeshareEnabled && station.micromobilityHubs.isNotEmpty()) {
                item(key = "station_micromobility", contentType = "station_micromobility") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        station.micromobilityHubs.forEach { hub ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "🚲 ${hub.operatorName ?: hub.name}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    val bikesLabel = stringResource(R.string.micromobility_bikes_available, hub.totalBikesAvailable)
                                    val docksLabel = stringResource(R.string.micromobility_docks_available, hub.totalDocksAvailable)
                                    val ebikesSuffix = if (hub.totalEbikesAvailable > 0) {
                                        " (${stringResource(R.string.micromobility_ebikes_available, hub.totalEbikesAvailable)})"
                                    } else ""
                                    Text(
                                        text = "$bikesLabel$ebikesSuffix • $docksLabel",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Station Inter-Platform Transfers (GTFS transfers.txt & NeTEx/IFOPT Pathways)
            if (station.transfers.isNotEmpty()) {
                item(key = "station_transfers", contentType = "station_transfers") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        station.transfers.forEach { transfer ->
                            val transferTypeLabel = when (transfer.type) {
                                com.androidfung.departureboard.data.model.TransferType.RECOMMENDED -> stringResource(R.string.transfer_recommended)
                                com.androidfung.departureboard.data.model.TransferType.TIMED_TRANSFER -> stringResource(R.string.transfer_timed)
                                else -> null
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "${transfer.fromPlatform} ➔ ${transfer.toPlatform}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val timeLabel = transfer.displayTransferTime
                                    if (!timeLabel.isNullOrBlank()) {
                                        Text(
                                            text = "• $timeLabel",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (transfer.isStepFree) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.tertiaryContainer
                                        ) {
                                            Text(
                                                text = "♿ ${stringResource(R.string.transfer_step_free)}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    if (transferTypeLabel != null) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = transferTypeLabel,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 1. Lines filter chips row (scrolls seamlessly with departures, no background artifact)
            if (distinctLines.size > 1 || singleLineHasDirections) {
                item(key = "line_filters", contentType = "line_filters") {
                    val hasActiveSelection = selectedLineId != null
                    val windowWidthClass = com.androidfung.departureboard.ui.theme.LocalWindowWidthClass.current
                    val isNarrow = windowWidthClass.isNarrow

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val chipSelectedDesc = stringResource(R.string.cd_selected)
                        val chipNotSelectedDesc = stringResource(R.string.cd_not_selected)
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

                            // Detect very dark badge backgrounds (e.g. Northern line black) to add a subtle contrasting border
                            val badgeLuminance = 0.299f * badge.backgroundColor.red + 0.587f * badge.backgroundColor.green + 0.114f * badge.backgroundColor.blue
                            val isVeryDarkBadge = badgeLuminance < 0.2f
                            val defaultBorderWidth = if (isVeryDarkBadge) 1.dp else 0.dp
                            val defaultBorderColor = if (isVeryDarkBadge) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f) else Color.Transparent

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
                                        width = if (isSelected) 2.dp else defaultBorderWidth,
                                        color = if (isSelected) Color.White else defaultBorderColor,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable(role = Role.Checkbox) {
                                        val availableDirections = com.androidfung.departureboard.util.DepartureFilterHelper.getAvailableDirections(
                                            departures = departures,
                                            lineId = badge.lineId
                                        )

                                        if (!TransitIconHelper.isSameLine(selectedLineId, badge.lineId)) {
                                            selectedLineId = badge.lineId
                                            selectedDirection = null
                                        } else if (availableDirections.size > 1) {
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
                                            selectedLineId = null
                                            selectedDirection = null
                                        }
                                    }
                                    .semantics {
                                        this.selected = isSelected
                                        this.role = Role.Checkbox
                                        this.stateDescription = if (isSelected) chipSelectedDesc else chipNotSelectedDesc
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
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
                        }
                    }
                }

            // 1.9 Live Step-Free Lift Outage Guard
            if (accessibilityAlerts.isNotEmpty()) {
                item(key = "step_free_outage_guard", contentType = "step_free_outage_guard") {
                    com.androidfung.departureboard.ui.designsystem.StepFreeOutageBanner(
                        alerts = accessibilityAlerts
                    )
                }
            }

            // 2. Interruption & Disruption Banners (inside LazyColumn so they scroll away):
            if (disruptedBadgesToShow.isNotEmpty()) {
                item(key = "disruptions", contentType = "disruptions") {
                    if (disruptedBadgesToShow.size == 1) {
                        LineDisruptionBanner(badge = disruptedBadgesToShow.first())
                    } else {
                        MultipleDisruptionsBanner(disruptedBadges = disruptedBadgesToShow)
                    }
                }
            }

            // 2.5 Last Train Home / Night Service Banner
            if (serviceHoursInfo != null && (serviceHoursInfo.isApproachingLastTrain || serviceHoursInfo.isNightServiceActive)) {
                item(key = "last_train_banner", contentType = "last_train_banner") {
                    LastTrainServiceBanner(
                        serviceHoursInfo = serviceHoursInfo
                    )
                }
            }

            // 3. Sub-stops / stand filter chips for bus stops
            if (isBusStop && distinctStops.size > 1) {
                item(key = "sub_stops", contentType = "sub_stops") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.station_detail_stops_stands),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        val stopSelectedDesc = stringResource(R.string.cd_selected)
                        val stopNotSelectedDesc = stringResource(R.string.cd_not_selected)
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            distinctStops.forEach { subStop ->
                                val isSelected = selectedStopId.equals(subStop.platformName, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(role = Role.Checkbox) {
                                            selectedStopId = if (selectedStopId.equals(subStop.platformName, ignoreCase = true)) {
                                                null
                                            } else {
                                                subStop.platformName
                                            }
                                        }
                                        .semantics {
                                            this.selected = isSelected
                                            this.role = Role.Checkbox
                                            this.stateDescription = if (isSelected) stopSelectedDesc else stopNotSelectedDesc
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
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
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
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
                }
            }

            // 4. Live Departures Header
            item(key = "live_departures_header", contentType = "live_departures_header") {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isTunnelScheduled = filteredDepartures.any {
                            it.dataFreshness == com.androidfung.departureboard.data.model.DataFreshness.OFFLINE_SCHEDULED
                        }
                        val isCachedSnapshot = !isTunnelScheduled && filteredDepartures.any {
                            it.dataFreshness == com.androidfung.departureboard.data.model.DataFreshness.CACHED_LIVE
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!isTunnelScheduled && !isCachedSnapshot) {
                                // Pulsing live indicator deferred to Draw phase
                                PulsingLiveDot()
                            }
                            Text(
                                text = stringResource(
                                    when {
                                        isTunnelScheduled -> R.string.badge_tunnel_mode_scheduled
                                        isCachedSnapshot -> R.string.badge_cached_predictions
                                        else -> R.string.station_detail_live_departures
                                    }
                                ),
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
                                text = stringResource(R.string.station_detail_predictions_count, filteredDepartures.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 5. Departures list or empty state
            if (filteredDepartures.isEmpty() && !isLoading) {
                item(key = "empty_state", contentType = "empty_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (serviceHoursInfo?.isClosedForNight == true) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 24.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.station_detail_service_ended),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                val lastText = serviceHoursInfo.lastTrainTime?.let {
                                    stringResource(R.string.station_detail_last_train, it)
                                } ?: ""
                                val firstText = serviceHoursInfo.firstTrainTime?.let {
                                    stringResource(R.string.station_detail_first_train, it)
                                } ?: ""
                                Text(
                                    text = listOf(lastText, firstText).filter { it.isNotBlank() }.joinToString("\n"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = 22.sp
                                )
                            }
                        } else if (serviceHoursInfo?.isApproachingLastTrain == true && !serviceHoursInfo.lastTrainTime.isNullOrBlank()) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 24.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.departures_none_upcoming_awaiting),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Text(
                                    text = stringResource(R.string.departures_service_awaiting_last_train, serviceHoursInfo.lastTrainTime),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        } else {
                            Text(
                                text = if (selectedLineId != null) {
                                    stringResource(R.string.station_detail_no_departures_line)
                                } else {
                                    stringResource(R.string.station_detail_no_departures)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(
                    items = displayItems,
                    key = { item ->
                        when (item) {
                            is StationDetailListItem.DepartureItem -> item.stableKey
                            is StationDetailListItem.AdItem -> item.key
                        }
                    },
                    contentType = { item ->
                        when (item) {
                            is StationDetailListItem.DepartureItem -> "departure_row"
                            is StationDetailListItem.AdItem -> "native_ad"
                        }
                    }
                ) { item ->
                    when (item) {
                        is StationDetailListItem.DepartureItem -> {
                            val departure = item.departure
                            DepartureDetailItemRow(
                                departure = departure,
                                isTracked = trackedDepartureId == departure.id,
                                onToggleTrackDeparture = onToggleTrackDeparture,
                                onClick = onDepartureClick?.let { { it(departure, filteredDepartures) } }
                            )
                        }
                        is StationDetailListItem.AdItem -> {
                            NativeAdViewContainer(
                                nativeAd = item.nativeAd,
                                style = item.style,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PulsingLiveDot(modifier: Modifier = Modifier) {
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
    Box(
        modifier = modifier
            .size(10.dp)
            .graphicsLayer { alpha = pulseAlpha }
            .background(Color(0xFF22C55E), CircleShape)
    )
}

/**
 * Detailed departure row item utilizing the unified DepartureRowItem component.
 */
@Composable
fun DepartureDetailItemRow(
    departure: Departure,
    modifier: Modifier = Modifier,
    isTracked: Boolean = false,
    onToggleTrackDeparture: (Departure) -> Unit = {},
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
                            contentDescription = if (isTracked) {
                                stringResource(R.string.cd_stop_live_notification)
                            } else {
                                stringResource(R.string.cd_start_live_notification)
                            },
                            modifier = Modifier.size(16.dp),
                            tint = if (isTracked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        )
    }
}
