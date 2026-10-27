package com.androidfung.departureboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androidfung.departureboard.data.model.CallingPoint
import com.androidfung.departureboard.data.model.Departure

private sealed interface JourneyTimelineListItem {
    data class CallingPointItem(val callingPoint: CallingPoint, val index: Int) : JourneyTimelineListItem
    data class AdItem(val key: String) : JourneyTimelineListItem
}

@Composable
fun TrainJourneyScreen(
    departure: Departure,
    onBack: () -> Unit,
    onLoadCallingPoints: suspend (Departure) -> List<CallingPoint>,
    modifier: Modifier = Modifier,
    isPro: Boolean = false,
    departures: List<Departure> = listOf(departure),
    onDepartureChange: ((Departure) -> Unit)? = null,
    onCallingPointClick: (CallingPoint, List<CallingPoint>) -> Unit = { _, _ -> }
) {
    val departureList = remember(departures, departure) {
        if (departures.none { it.id == departure.id }) {
            listOf(departure) + departures
        } else {
            departures
        }
    }

    val initialIndex = remember(departureList, departure.id) {
        departureList.indexOfFirst { it.id == departure.id }.coerceAtLeast(0)
    }

    if (departureList.size <= 1) {
        TrainJourneyContent(
            departure = departure,
            onBack = onBack,
            onLoadCallingPoints = onLoadCallingPoints,
            modifier = modifier,
            isPro = isPro,
            onCallingPointClick = onCallingPointClick
        )
    } else {
        val pagerState = androidx.compose.foundation.pager.rememberPagerState(
            initialPage = initialIndex,
            pageCount = { departureList.size }
        )

        LaunchedEffect(departure.id) {
            val targetPage = departureList.indexOfFirst { it.id == departure.id }
            if (targetPage >= 0 && targetPage != pagerState.currentPage) {
                pagerState.scrollToPage(targetPage)
            }
        }

        LaunchedEffect(pagerState.settledPage) {
            val currentDep = departureList[pagerState.settledPage]
            if (currentDep.id != departure.id) {
                onDepartureChange?.invoke(currentDep)
            }
        }

        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = modifier.fillMaxSize(),
            key = { departureList[it].id }
        ) { page ->
            TrainJourneyContent(
                departure = departureList[page],
                onBack = onBack,
                onLoadCallingPoints = onLoadCallingPoints,
                modifier = Modifier.fillMaxSize(),
                isPro = isPro,
                onCallingPointClick = onCallingPointClick,
                pageInfo = "${page + 1} of ${departureList.size}"
            )
        }
    }
}

@Composable
private fun TrainJourneyContent(
    departure: Departure,
    onBack: () -> Unit,
    onLoadCallingPoints: suspend (Departure) -> List<CallingPoint>,
    modifier: Modifier = Modifier,
    isPro: Boolean = false,
    onCallingPointClick: (CallingPoint, List<CallingPoint>) -> Unit = { _, _ -> },
    pageInfo: String? = null
) {
    var callingPoints by remember(departure.id) { mutableStateOf(departure.callingPoints) }
    var isLoading by remember(departure.id) { mutableStateOf(callingPoints.isEmpty()) }

    LaunchedEffect(departure.id) {
        if (callingPoints.isEmpty()) {
            isLoading = true
            callingPoints = onLoadCallingPoints(departure)
            isLoading = false
        }
    }

    val timelineItems = remember(callingPoints, isPro) {
        buildList<JourneyTimelineListItem> {
            callingPoints.forEachIndexed { index, cp ->
                add(JourneyTimelineListItem.CallingPointItem(cp, index))
                if (!isPro && callingPoints.size >= 4 && (index == 2 || (index > 2 && (index - 2) % 8 == 0))) {
                    add(JourneyTimelineListItem.AdItem("ad_$index"))
                }
            }
            if (!isPro && callingPoints.size < 4) {
                add(JourneyTimelineListItem.AdItem("ad_bottom"))
            }
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val haptic = LocalHapticFeedback.current
    val trackedDepartureId by com.androidfung.departureboard.service.TrainTrackingService.currentlyTrackedDepartureIdFlow
        .collectAsStateWithLifecycle()
    val isTracked = trackedDepartureId == departure.id

    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            com.androidfung.departureboard.service.TrainTrackingService.startTracking(context, departure)
        }
    }

    DetailPaneScaffold(
        onBack = onBack,
        modifier = modifier,
        headerLeading = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(departure.lineBadge.backgroundColor)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = departure.lineBadge.displayName,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        ),
                        color = departure.lineBadge.textColor
                    )
                }

                if (departure.platformName.isNotBlank()) {
                    Text(
                        text = departure.platformName,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

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
        },
        headerActions = {
            // Top-right corner live notification tracking toggle (hidden for cancelled services)
            if (departure.status != com.androidfung.departureboard.data.model.DepartureStatus.CANCELLED) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isTracked) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (isTracked) {
                                com.androidfung.departureboard.service.TrainTrackingService.stopTracking(context)
                            } else {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.POST_NOTIFICATIONS
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                                    if (hasPermission) {
                                        com.androidfung.departureboard.service.TrainTrackingService.startTracking(context, departure)
                                    } else {
                                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                } else {
                                    com.androidfung.departureboard.service.TrainTrackingService.startTracking(context, departure)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isTracked) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                        contentDescription = if (isTracked) "Stop Live Notification" else "Notify with Live Updates",
                        tint = if (isTracked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    ) { _ ->
        val isThreePane = LocalIsThreePaneMode.current
        val isDelayedOrCancelled = departure.status == com.androidfung.departureboard.data.model.DepartureStatus.CANCELLED ||
                departure.status == com.androidfung.departureboard.data.model.DepartureStatus.DELAYED
        val showLineDisruption = departure.lineBadge.isDisrupted && !isThreePane

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Main Destination Title & Departure Time Badge
            item(key = "destination_title") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Train to ${departure.destinationName}",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    UnifiedDepartureTimeBadge(
                        departure = departure,
                        isFirst = true
                    )
                }
            }

            // 2. Where the train is NOW banner
            item(key = "current_location") {
                val location = departure.currentLocation?.takeIf { it.isNotBlank() } ?: "In transit"
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "CURRENT TRAIN LOCATION",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    letterSpacing = 0.5.sp
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = location,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // 3. Interruption & Disruption Banners (scrolls away with timeline)
            if (isDelayedOrCancelled || showLineDisruption) {
                item(key = "disruptions") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (departure.status == com.androidfung.departureboard.data.model.DepartureStatus.CANCELLED) {
                            TrainTripAlertBanner(
                                title = "Service Cancelled",
                                description = departure.currentLocation?.takeIf { it.isNotBlank() && !it.equals("Cancelled", ignoreCase = true) }
                                    ?: "This service has been cancelled.",
                                isCancelled = true
                            )
                        } else if (departure.status == com.androidfung.departureboard.data.model.DepartureStatus.DELAYED) {
                            TrainTripAlertBanner(
                                title = "Service Delayed",
                                description = departure.currentLocation?.takeIf { it.isNotBlank() && !it.equals("Delayed", ignoreCase = true) }
                                    ?: "This service is experiencing delays.",
                                isCancelled = false
                            )
                        }

                        if (showLineDisruption) {
                            LineDisruptionBanner(badge = departure.lineBadge)
                        }
                    }
                }
            }

            // 4. Calling Points List Header & Divider
            item(key = "calling_points_header") {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Calling Points",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text = "${callingPoints.size} stops",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 5. Calling Points Timeline or Empty / Loading state
            if (callingPoints.isNotEmpty()) {
                items(
                    items = timelineItems,
                    key = { item ->
                        when (item) {
                            is JourneyTimelineListItem.CallingPointItem -> "${item.callingPoint.stationName}_${item.index}"
                            is JourneyTimelineListItem.AdItem -> item.key
                        }
                    }
                ) { item ->
                    when (item) {
                        is JourneyTimelineListItem.CallingPointItem -> {
                            CallingPointTimelineItem(
                                callingPoint = item.callingPoint,
                                isFirst = item.index == 0,
                                isLast = item.index == callingPoints.lastIndex,
                                lineColor = departure.lineBadge.backgroundColor,
                                onClick = if (!item.callingPoint.isCurrentStation) {
                                    { onCallingPointClick(item.callingPoint, callingPoints) }
                                } else null
                            )
                        }
                        is JourneyTimelineListItem.AdItem -> {
                            NativeAdViewContainer(
                                style = NativeAdStyle.TIMELINE,
                                accentColor = departure.lineBadge.backgroundColor,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            } else if (isLoading) {
                item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Loading calling points...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                item(key = "empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Calling points details not available for this service.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (!isPro) {
                                NativeAdViewContainer(
                                    style = NativeAdStyle.CARD,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Vertical timeline step for each station where the train calls.
 */
@Composable
private fun CallingPointTimelineItem(
    callingPoint: CallingPoint,
    isFirst: Boolean,
    isLast: Boolean,
    lineColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else Modifier
            )
    ) {
        // Station node dot + station name & times
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Timeline track and node dot
            Column(
                modifier = Modifier
                    .width(28.dp)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Upper connecting line segment
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .weight(1f)
                        .background(
                            if (isFirst) Color.Transparent else lineColor.copy(alpha = 0.5f)
                        )
                )

                // Station node indicator
                if (callingPoint.isCurrentStation) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(lineColor)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    )
                } else if (isLast) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(lineColor.copy(alpha = 0.6f))
                    )
                }

                // Lower connecting line segment
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .weight(1f)
                        .background(
                            if (isLast && !callingPoint.isCurrentStation) Color.Transparent else lineColor.copy(alpha = 0.5f)
                        )
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Station name & calling time
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = callingPoint.stationName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (callingPoint.isCurrentStation || isLast) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 15.sp
                    ),
                    color = if (callingPoint.isCurrentStation) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // If there's an estimated countdown badge (e.g. "3 mins" or "Due") separate from clock time
                    if (!callingPoint.estimatedTime.isNullOrBlank() && callingPoint.scheduledTime != null && callingPoint.estimatedTime != callingPoint.scheduledTime) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (callingPoint.estimatedTime.equals("Due", ignoreCase = true)) MaterialTheme.colorScheme.errorContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = callingPoint.estimatedTime,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (callingPoint.estimatedTime.equals("Due", ignoreCase = true)) MaterialTheme.colorScheme.onErrorContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val timeDisplay = callingPoint.scheduledTime ?: callingPoint.estimatedTime
                    if (!timeDisplay.isNullOrBlank()) {
                        Text(
                            text = timeDisplay,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!callingPoint.isCurrentStation) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = "View station",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        // Subtitle row for current station ("Your station")
        if (callingPoint.isCurrentStation) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(
                                if (isLast) Color.Transparent else lineColor.copy(alpha = 0.5f)
                            )
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = "Your station",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }
    }
}

@Preview(name = "Calling Points Timeline", showBackground = true, backgroundColor = 0xFF1C1B17)
@Composable
private fun CallingPointTimelinePreview() {
    com.androidfung.departureboard.ui.theme.DepartureBoardTheme(darkTheme = true) {
        val sampleCallingPoints = listOf(
            CallingPoint(
                stationName = "Waterloo",
                scheduledTime = "14:08",
                estimatedTime = "Due",
                isCurrentStation = true
            ),
            CallingPoint(
                stationName = "Embankment",
                scheduledTime = "14:10",
                estimatedTime = "1 min"
            ),
            CallingPoint(
                stationName = "Charing Cross",
                scheduledTime = "14:12",
                estimatedTime = "2 mins"
            ),
            CallingPoint(
                stationName = "Piccadilly Circus",
                scheduledTime = "14:13",
                estimatedTime = "4 mins"
            ),
            CallingPoint(
                stationName = "Oxford Circus",
                scheduledTime = "14:15",
                estimatedTime = "6 mins"
            ),
            CallingPoint(
                stationName = "Edgware Road (Bakerloo)",
                scheduledTime = "14:20",
                estimatedTime = "11 mins",
                isDestination = true
            )
        )

        val bakerlooColor = Color(0xFFB36305)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            sampleCallingPoints.forEachIndexed { index, cp ->
                CallingPointTimelineItem(
                    callingPoint = cp,
                    isFirst = index == 0,
                    isLast = index == sampleCallingPoints.lastIndex,
                    lineColor = bakerlooColor
                )
            }
        }
    }
}

/**
 * Trip-specific disruption alert banner (e.g. Cancelled, Delayed) for individual train services.
 */
@Composable
private fun TrainTripAlertBanner(
    title: String,
    description: String,
    isCancelled: Boolean,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isCancelled) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
    } else {
        androidx.compose.ui.graphics.Color(0xFFFFF3CD).copy(alpha = 0.2f)
    }
    val borderColor = if (isCancelled) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
    } else {
        androidx.compose.ui.graphics.Color(0xFFFFC107).copy(alpha = 0.5f)
    }
    val contentColor = if (isCancelled) {
        MaterialTheme.colorScheme.error
    } else {
        androidx.compose.ui.graphics.Color(0xFFFFC107)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.Top) {
            Text(
                text = if (isCancelled) "✕" else "⚠",
                color = contentColor,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontSize = 13.sp
                ),
                modifier = Modifier.padding(end = 6.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    ),
                    color = contentColor
                )
                if (description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        maxLines = 3,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

