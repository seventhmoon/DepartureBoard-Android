package com.androidfung.departureboard.ui.feature.detail

import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidfung.departureboard.R
import com.androidfung.departureboard.data.model.CallingPoint
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.ui.ads.NativeAdStyle
import com.androidfung.departureboard.ui.ads.NativeAdViewContainer
import com.androidfung.departureboard.ui.designsystem.LineDisruptionBanner
import com.androidfung.departureboard.ui.designsystem.UnifiedDepartureTimeBadge

private sealed interface JourneyTimelineListItem {
    data class CallingPointItem(val callingPoint: CallingPoint, val index: Int) : JourneyTimelineListItem
    data class AdItem(val key: String, val style: NativeAdStyle = NativeAdStyle.CARD) : JourneyTimelineListItem
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
                pageInfo = stringResource(R.string.pager_page_of_total, page + 1, departureList.size)
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

    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val screenHeightDp = remember(density, windowInfo) {
        with(density) { windowInfo.containerSize.height.toDp().value.toInt() }
    }

    val timelineItems = remember(callingPoints, isPro, screenHeightDp) {
        buildList<JourneyTimelineListItem> {
            callingPoints.forEachIndexed { index, cp ->
                add(JourneyTimelineListItem.CallingPointItem(cp, index))
            }
            // Always place ad at the end of the journey timeline so the calling points sequence is never interrupted.
            // If the route is short and leaves empty space on screen, use a large CARD ad; otherwise a compact ROW ad at the end.
            if (!isPro && callingPoints.isNotEmpty()) {
                val callingPointItemHeightDp = 52
                val headerHeightEstimateDp = 220
                val cardAdHeightDp = 240
                val estimatedContentHeight = headerHeightEstimateDp + (callingPoints.size * callingPointItemHeightDp)
                val hasRoomForCard = (screenHeightDp - estimatedContentHeight) >= cardAdHeightDp

                val adStyle = if (hasRoomForCard) NativeAdStyle.CARD else NativeAdStyle.ROW
                add(JourneyTimelineListItem.AdItem("ad_bottom", style = adStyle))
            }
        }
    }

    val currentStationIndex = remember(callingPoints) {
        callingPoints.indexOfFirst { it.isCurrentStation }
    }

    val activeTrainIndex = remember(callingPoints, departure.currentLocation, departure.timeToStationSeconds, departure.status, currentStationIndex) {
        if (callingPoints.isEmpty()) return@remember -1
        val loc = departure.currentLocation?.trim()?.lowercase() ?: ""

        // Only mark "Your station" if the train is actually at or arriving at the boarding platform right now
        val isAtCurrentStation = departure.timeToStationSeconds <= 30 ||
            departure.status == com.androidfung.departureboard.data.model.DepartureStatus.DUE ||
            loc.contains("at platform") ||
            (currentStationIndex >= 0 && loc.contains("at ${callingPoints[currentStationIndex].stationName.lowercase()}"))

        if (isAtCurrentStation && currentStationIndex >= 0) {
            return@remember currentStationIndex
        }

        // If the train has not yet arrived at the boarding station (e.g. countdown > 30s and train is still approaching),
        // we must NOT match downstream calling points (e.g., if train is approaching northbound to Mill Hill East,
        // it may currently be at Tufnell Park heading north, but Tufnell Park also appears downstream on the passenger trip!).
        val isTrainYetToArriveAtOrigin = currentStationIndex >= 0 && departure.timeToStationSeconds > 30

        if (loc.isNotBlank() && !isTrainYetToArriveAtOrigin) {
            val matchIndex = callingPoints.indexOfFirst { cp ->
                if (!cp.stationId.isNullOrBlank() && (loc.contains(cp.stationId, ignoreCase = true) || cp.stationId.equals(loc, ignoreCase = true))) {
                    return@indexOfFirst true
                }
                val name = cp.stationName.trim().lowercase()
                name.length >= 3 && (loc.contains(name) || name.contains(loc))
            }
            if (matchIndex >= 0) return@remember matchIndex
        }

        // Train is en route / approaching before reaching the boarding station
        -1
    }

    val isTrainApproachingBeforeBoarding = remember(activeTrainIndex, currentStationIndex, departure.status) {
        activeTrainIndex == -1 && currentStationIndex >= 0 &&
            departure.status != com.androidfung.departureboard.data.model.DepartureStatus.CANCELLED
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val haptic = LocalHapticFeedback.current
    val trackedDepartureId by com.androidfung.departureboard.service.TrainTrackingService.currentlyTrackedDepartureIdFlow
        .collectAsStateWithLifecycle()
    val targetAlightStop by com.androidfung.departureboard.service.TrainTrackingService.targetAlightStopFlow
        .collectAsStateWithLifecycle()
    val isTracked = trackedDepartureId == departure.id

    val delayRepay = remember(departure, callingPoints) {
        com.androidfung.departureboard.util.DelayRepayCalculator.evaluate(departure, callingPoints)
    }

    LaunchedEffect(departure.id, delayRepay) {
        if (delayRepay != null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.androidfung.departureboard.util.DelayRepayReceiptLog.recordReceipt(
                    context = context,
                    departureId = departure.id,
                    stationName = departure.stationName,
                    destinationName = departure.destinationName,
                    lineName = departure.lineName,
                    trainCode = departure.displayTrainCode,
                    delayMinutes = delayRepay.delayMinutes,
                    isCancelled = false,
                    badgeLabel = delayRepay.badgeLabel,
                    compensationSummary = delayRepay.compensationSummary
                )
            }
        }
    }

    var pendingTrackDeparture by remember { mutableStateOf<Departure?>(null) }

    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val targetDeparture = pendingTrackDeparture ?: departure
            com.androidfung.departureboard.service.TrainTrackingService.startTracking(context, targetDeparture)
        }
        pendingTrackDeparture = null
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

                val trainCode = departure.displayTrainCode
                if (!trainCode.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.train_journey_train_code, trainCode),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1-Tap Share Live ETA button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val shareText = com.androidfung.departureboard.util.DelayRepayCalculator.formatShareLiveEtaText(
                                departure = departure,
                                callingPoints = callingPoints,
                                targetAlightStop = if (isTracked) targetAlightStop else null
                            )
                            val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(
                                android.content.Intent.createChooser(sendIntent, "Share Live Train ETA")
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Share Live ETA",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

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
                                    com.androidfung.departureboard.util.NotificationHelper.startTrackingWithPermissionCheck(context, departure) { pendingDeparture ->
                                        pendingTrackDeparture = pendingDeparture
                                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isTracked) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                            contentDescription = if (isTracked) {
                                stringResource(R.string.cd_stop_live_notification)
                            } else {
                                stringResource(R.string.cd_start_live_notification)
                            },
                            tint = if (isTracked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
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
            item(key = "destination_title", contentType = "destination_title") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.train_journey_title, departure.destinationName),
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

                    if (departure.stationName.isNotBlank() || departure.operator?.name?.isNotBlank() == true) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (departure.stationName.isNotBlank()) {
                                Text(
                                    text = stringResource(R.string.train_journey_from),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = departure.stationName,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            departure.operator?.name?.takeIf { it.isNotBlank() }?.let { operatorName ->
                                if (departure.stationName.isNotBlank()) {
                                    Text(
                                        text = "•",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.train_journey_operated_by, operatorName),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 2. Where the train is NOW banner (only show if it is a meaningful physical location and not a redundant status like 'Exp 16:52' or 'On time')
            val rawLocation = departure.currentLocation?.trim().orEmpty()
            val isTimeOrGenericStatus = rawLocation.startsWith("Exp ", ignoreCase = true) ||
                rawLocation.equals("On time", ignoreCase = true) ||
                rawLocation.equals("Delayed", ignoreCase = true) ||
                rawLocation.equals("Cancelled", ignoreCase = true) ||
                rawLocation.matches(Regex("^[0-9]{2}:[0-9]{2}$"))
            val hasMeaningfulLocation = rawLocation.isNotBlank() && !isTimeOrGenericStatus

            if (hasMeaningfulLocation) {
                item(key = "current_location", contentType = "current_location") {
                    val stopStatus = departure.vehicleStopStatus
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                if (stopStatus == com.androidfung.departureboard.data.model.VehicleStopStatus.INCOMING_AT) {
                                    PulsingRadarHalo(haloColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                }
                                Icon(
                                    imageVector = Icons.Rounded.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column {
                                val statusTag = when (stopStatus) {
                                    com.androidfung.departureboard.data.model.VehicleStopStatus.STOPPED_AT -> stringResource(R.string.status_at_platform)
                                    com.androidfung.departureboard.data.model.VehicleStopStatus.INCOMING_AT -> stringResource(R.string.status_approaching)
                                    com.androidfung.departureboard.data.model.VehicleStopStatus.IN_TRANSIT_TO -> stringResource(R.string.status_in_transit)
                                }
                                Text(
                                    text = "${stringResource(R.string.train_journey_live_location)} • $statusTag",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = rawLocation,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Platform Alteration Banner (SIRI-ET & Darwin platform change alerts)
            val platformInfo = departure.platform
            if (platformInfo?.isChanged == true && !platformInfo.previousPlatform.isNullOrBlank()) {
                item(key = "platform_change_banner", contentType = "platform_change_banner") {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "⚠️",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(R.string.platform_changed_alert, platformInfo.code,
                                    platformInfo.previousPlatform
                                ),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Concourse Platform Watcher ("Platform TBD" -> Haptic Alert on Announcement)
            val isPlatformTbd = departure.platformName.contains("TBD", ignoreCase = true) ||
                (!departure.resolvedPlatform.isConfirmed && departure.transitMode != com.androidfung.departureboard.data.model.TransitMode.BUS)
            if (isPlatformTbd && departure.status != com.androidfung.departureboard.data.model.DepartureStatus.CANCELLED) {
                item(key = "concourse_tbd_platform_watcher", contentType = "concourse_tbd_platform_watcher") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (isTracked) {
                                    com.androidfung.departureboard.service.TrainTrackingService.stopTracking(context)
                                } else {
                                    com.androidfung.departureboard.util.NotificationHelper.startTrackingWithPermissionCheck(context, departure) { pendingDeparture ->
                                        pendingTrackDeparture = pendingDeparture
                                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isTracked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = if (isTracked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (isTracked) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                                contentDescription = null,
                                tint = if (isTracked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isTracked) {
                                        "Watching for Platform Announcement"
                                    } else {
                                        "Platform TBD — Tap to Watch for Announcement"
                                    },
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isTracked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = if (isTracked) {
                                        "Concourse Haptic Alert active: your phone & watch will buzz as soon as the platform is announced."
                                    } else {
                                        "Waiting in the station concourse? Get an instant double-pulse haptic alert the moment the platform is confirmed."
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = (if (isTracked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer).copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            }

            // Delay Repay Compensation Eligibility Banner
            if (delayRepay != null) {
                item(key = "delay_repay_banner", contentType = "delay_repay_banner") {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF00897B).copy(alpha = 0.14f),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = Color(0xFF00897B).copy(alpha = 0.55f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "💸",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${delayRepay.badgeLabel} • Receipt Saved",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF26A69A)
                                    )
                                    Text(
                                        text = delayRepay.compensationSummary,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                    )
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF00897B).copy(alpha = 0.28f),
                                    modifier = Modifier.clickable {
                                        val claimUrl = com.androidfung.departureboard.util.DelayRepayReceiptLog.resolveOperatorClaimUrl(delayRepay.operatorLabel)
                                        val intent = android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(claimUrl)
                                        )
                                        runCatching { context.startActivity(intent) }
                                    }
                                ) {
                                    Text(
                                        text = "Claim Online ↗",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF4DB6AC),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier.clickable {
                                        val latestReceipt = com.androidfung.departureboard.util.DelayRepayReceiptLog
                                            .receiptsFlow.value.firstOrNull()
                                            ?: com.androidfung.departureboard.util.DelayRepayReceiptLog
                                                .loadReceipts(context)
                                                .firstOrNull()
                                        if (latestReceipt != null) {
                                            com.androidfung.departureboard.util.DelayRepayReceiptLog.copyReceiptToClipboard(context, latestReceipt)
                                            android.widget.Toast.makeText(context, "Delay Repay receipt copied", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Text(
                                        text = "Copy Receipt",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Train Carriage Formation Diagram
            val formation = departure.formation
            if (formation != null && formation.coaches.isNotEmpty()) {
                item(key = "train_formation_diagram", contentType = "train_formation_diagram") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = "${stringResource(R.string.train_formation_coaches)} (${formation.displayCoachCount})",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            formation.coaches.forEachIndexed { coachIdx, coach ->
                                val coachTitle = coach.coachNumber ?: "${coachIdx + 1}"
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.coach_label, coachTitle),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        // Amenities row
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (coach.isWheelchairAccessible == true) {
                                                val wheelchairDesc = stringResource(R.string.amenity_wheelchair)
                                                Text(
                                                    text = "♿",
                                                    modifier = Modifier.semantics { contentDescription = wheelchairDesc },
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                                                )
                                            }
                                            if (coach.hasBicycleRack == true) {
                                                val bikeDesc = stringResource(R.string.amenity_bike)
                                                Text(
                                                    text = "🚲",
                                                    modifier = Modifier.semantics { contentDescription = bikeDesc },
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                                                )
                                            }
                                            if (coach.hasToilet == true) {
                                                val toiletDesc = stringResource(R.string.amenity_toilet)
                                                Text(
                                                    text = "🚻",
                                                    modifier = Modifier.semantics { contentDescription = toiletDesc },
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                                                )
                                            }
                                            if (coach.isQuietCoach == true) {
                                                val quietDesc = stringResource(R.string.amenity_quiet)
                                                Text(
                                                    text = "🤫",
                                                    modifier = Modifier.semantics { contentDescription = quietDesc },
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                                                )
                                            }
                                        }
                                        // Coach crowding status
                                        val coachOccupancy = coach.parsedOccupancyStatus
                                        if (coachOccupancy != null) {
                                            val occupancyColor = when (coachOccupancy) {
                                                com.androidfung.departureboard.data.model.OccupancyStatus.EMPTY,
                                                com.androidfung.departureboard.data.model.OccupancyStatus.MANY_SEATS_AVAILABLE -> Color(0xFF4CAF50)
                                                com.androidfung.departureboard.data.model.OccupancyStatus.FEW_SEATS_AVAILABLE,
                                                com.androidfung.departureboard.data.model.OccupancyStatus.STANDING_ROOM_ONLY -> Color(0xFFFF9800)
                                                else -> Color(0xFFF44336)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(width = 24.dp, height = 3.dp)
                                                    .clip(RoundedCornerShape(1.5.dp))
                                                    .background(occupancyColor)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Darwin Train Portioning & Associations (train splits/joins)
                        if (departure.associations.isNotEmpty()) {
                            departure.associations.forEach { assoc ->
                                val assocText = assoc.notice ?: when (assoc.type) {
                                    com.androidfung.departureboard.data.model.TrainAssociationType.DIVIDES ->
                                        stringResource(R.string.train_divides_notice, assoc.junctionStationName, assoc.portionDestination ?: assoc.coachRange ?: "")
                                    com.androidfung.departureboard.data.model.TrainAssociationType.JOINS ->
                                        stringResource(R.string.train_joins_notice, assoc.junctionStationName, assoc.portionDestination ?: assoc.coachRange ?: "")
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "🔀",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                        Text(
                                            text = assocText,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Interruption & Disruption Banners (scrolls away with timeline)
            if (isDelayedOrCancelled || showLineDisruption) {
                item(key = "disruptions", contentType = "disruptions") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (departure.status == com.androidfung.departureboard.data.model.DepartureStatus.CANCELLED) {
                            TrainTripAlertBanner(
                                title = stringResource(R.string.tracking_service_cancelled_title),
                                description = departure.cancelReason?.takeIf { it.isNotBlank() }
                                    ?: departure.currentLocation?.takeIf { it.isNotBlank() && !it.equals("Cancelled", ignoreCase = true) }
                                    ?: stringResource(R.string.tracking_service_cancelled_desc),
                                isCancelled = true
                            )
                        } else if (departure.status == com.androidfung.departureboard.data.model.DepartureStatus.DELAYED) {
                            TrainTripAlertBanner(
                                title = stringResource(R.string.status_delayed),
                                description = departure.delayReason?.takeIf { it.isNotBlank() }
                                    ?: departure.currentLocation?.takeIf { it.isNotBlank() && !it.equals("Delayed", ignoreCase = true) }
                                    ?: stringResource(R.string.widget_interruption_delays),
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
            item(key = "calling_points_header", contentType = "calling_points_header") {
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
                            text = stringResource(R.string.train_journey_calling_points),
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
                                text = stringResource(R.string.train_journey_stops_count, callingPoints.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 5. Calling Points Timeline or Empty / Loading state
            if (callingPoints.isNotEmpty()) {
                if (isTrainApproachingBeforeBoarding) {
                    item(key = "incoming_train_beacon", contentType = "incoming_train_beacon") {
                        IncomingTrainTimelineNode(
                            currentLocation = departure.currentLocation,
                            lineColor = departure.lineBadge.backgroundColor
                        )
                    }
                }

                items(
                    items = timelineItems,
                    key = { item ->
                        when (item) {
                            is JourneyTimelineListItem.CallingPointItem -> "${item.callingPoint.stationName}_${item.index}"
                            is JourneyTimelineListItem.AdItem -> item.key
                        }
                    },
                    contentType = { item ->
                        when (item) {
                            is JourneyTimelineListItem.CallingPointItem -> "calling_point"
                            is JourneyTimelineListItem.AdItem -> "native_ad"
                        }
                    }
                ) { item ->
                    when (item) {
                        is JourneyTimelineListItem.CallingPointItem -> {
                            val isPassed = activeTrainIndex >= 0 && item.index < activeTrainIndex
                            val isTrainLocation = activeTrainIndex >= 0 && item.index == activeTrainIndex
                            val isDownstreamStop = !item.callingPoint.isCurrentStation &&
                                !isPassed &&
                                !item.callingPoint.isCancelled &&
                                (currentStationIndex < 0 || item.index > currentStationIndex)
                            val isTargetAlightStop = isTracked &&
                                !targetAlightStop.isNullOrBlank() &&
                                (item.callingPoint.stationName.equals(targetAlightStop, ignoreCase = true) ||
                                    item.callingPoint.resolveDisplayName().equals(targetAlightStop, ignoreCase = true))

                            CallingPointTimelineItem(
                                callingPoint = item.callingPoint,
                                isFirst = item.index == 0,
                                isLast = item.index == callingPoints.lastIndex,
                                lineColor = departure.lineBadge.backgroundColor,
                                isPassed = isPassed,
                                isTrainLocation = isTrainLocation,
                                hasIncomingTrainAbove = isTrainApproachingBeforeBoarding,
                                isTargetAlightStop = isTargetAlightStop,
                                onToggleTargetAlightStop = if (isDownstreamStop && departure.status != com.androidfung.departureboard.data.model.DepartureStatus.CANCELLED) {
                                    {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (isTargetAlightStop) {
                                            com.androidfung.departureboard.service.TrainTrackingService.setTargetAlightStop(null)
                                        } else {
                                            val stopName = item.callingPoint.resolveDisplayName()
                                            com.androidfung.departureboard.service.TrainTrackingService.setTargetAlightStop(stopName)
                                            if (!isTracked) {
                                                com.androidfung.departureboard.util.NotificationHelper.startTrackingWithPermissionCheck(context, departure) { pendingDeparture ->
                                                    pendingTrackDeparture = pendingDeparture
                                                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                                }
                                            }
                                        }
                                    }
                                } else null,
                                onClick = if (!item.callingPoint.isCurrentStation) {
                                    { onCallingPointClick(item.callingPoint, callingPoints) }
                                } else null
                            )
                        }
                        is JourneyTimelineListItem.AdItem -> {
                            NativeAdViewContainer(
                                style = item.style,
                                accentColor = departure.lineBadge.backgroundColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp, bottom = 8.dp)
                            )
                        }
                    }
                }
            } else if (isLoading) {
                item(key = "loading", contentType = "loading") {
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
                                text = stringResource(R.string.train_journey_loading_stops),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                item(key = "empty", contentType = "empty") {
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
                                text = stringResource(R.string.train_journey_no_stops),
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
 * Visual timeline node indicating that the train is currently in transit/approaching
 * before the user's boarding station ("Your station").
 */
@Composable
private fun PulsingRadarHalo(
    haloColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.70f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseAlpha"
    )

    Box(
        modifier = modifier
            .size(12.dp)
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
                alpha = pulseAlpha
            }
            .clip(CircleShape)
            .background(haloColor)
    )
}

@Composable
private fun IncomingTrainTimelineNode(
    currentLocation: String?,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    val accessibleColors = rememberAccessibleTimelineColors(lineColor)

    val labelText = when {
        !currentLocation.isNullOrBlank() && !currentLocation.equals("In transit", ignoreCase = true) -> currentLocation
        else -> stringResource(R.string.status_in_transit)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .width(30.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                // Pulsing radar ripple halo
                PulsingRadarHalo(haloColor = accessibleColors.pulseHaloColor)
                // Core train location indicator
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(accessibleColors.coreNodeColor)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                )
            }
            // Connecting line down to "Your station"
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .weight(1f)
                    .background(accessibleColors.effectiveLineColor.copy(alpha = 0.5f))
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(accessibleColors.badgeBackground)
                    .border(1.dp, accessibleColors.badgeBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = null,
                    tint = accessibleColors.badgeContent,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = labelText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    ),
                    color = accessibleColors.badgeContent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
    isPassed: Boolean = false,
    isTrainLocation: Boolean = false,
    hasIncomingTrainAbove: Boolean = false,
    isTargetAlightStop: Boolean = false,
    onToggleTargetAlightStop: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val accessibleColors = rememberAccessibleTimelineColors(lineColor)

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
                    .width(30.dp)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Upper connecting line segment
                val upperLineColor = when {
                    isFirst && hasIncomingTrainAbove -> accessibleColors.effectiveLineColor.copy(alpha = 0.5f)
                    isFirst -> Color.Transparent
                    isPassed || isTrainLocation -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    else -> accessibleColors.effectiveLineColor.copy(alpha = 0.5f)
                }
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .weight(1f)
                        .background(upperLineColor)
                )

                // Station node indicator
                if (isTrainLocation) {
                    Box(
                        modifier = Modifier.size(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Pulsing radar ripple halo
                        PulsingRadarHalo(haloColor = accessibleColors.pulseHaloColor)
                        // Core node indicator
                        Box(
                            modifier = Modifier
                                .size(13.dp)
                                .clip(CircleShape)
                                .background(accessibleColors.coreNodeColor)
                                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        )
                    }
                } else if (isPassed) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                    )
                } else if (callingPoint.isCurrentStation) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(accessibleColors.effectiveLineColor)
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
                            .background(accessibleColors.effectiveLineColor.copy(alpha = 0.6f))
                    )
                }

                // Lower connecting line segment
                val lowerLineColor = when {
                    isLast && !callingPoint.isCurrentStation -> Color.Transparent
                    isPassed -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    else -> accessibleColors.effectiveLineColor.copy(alpha = 0.5f)
                }
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .weight(1f)
                        .background(lowerLineColor)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Station name & calling time
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isSkipped = callingPoint.isCancelled
                val nameColor = when {
                    isSkipped -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    isPassed -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f)
                    callingPoint.isCurrentStation -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                }

                Text(
                    text = callingPoint.resolveDisplayName(),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (callingPoint.isCurrentStation || isLast || isTrainLocation) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 15.sp,
                        textDecoration = if (isSkipped) androidx.compose.ui.text.style.TextDecoration.LineThrough else androidx.compose.ui.text.style.TextDecoration.None
                    ),
                    color = nameColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val timeAlpha = if (isPassed || isSkipped) 0.45f else 1.0f
                    if (isSkipped) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.status_cancelled),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    } else if (!callingPoint.estimatedTime.isNullOrBlank() && callingPoint.scheduledTime != null && callingPoint.estimatedTime != callingPoint.scheduledTime) {
                        // If there's an estimated countdown badge (e.g. "3 mins" or "Due") separate from clock time
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isPassed) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    else if (callingPoint.estimatedTime.equals("Due", ignoreCase = true)) MaterialTheme.colorScheme.errorContainer
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
                                color = if (isPassed) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                else if (callingPoint.estimatedTime.equals("Due", ignoreCase = true)) MaterialTheme.colorScheme.onErrorContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val timeDisplay = callingPoint.scheduledTime ?: if (!isSkipped) callingPoint.estimatedTime else null
                    if (!timeDisplay.isNullOrBlank()) {
                        Text(
                            text = timeDisplay,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                textDecoration = if (isSkipped) androidx.compose.ui.text.style.TextDecoration.LineThrough else androidx.compose.ui.text.style.TextDecoration.None
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = timeAlpha)
                        )
                    }

                    if (onToggleTargetAlightStop != null) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isTargetAlightStop) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                )
                                .clickable(onClick = onToggleTargetAlightStop),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isTargetAlightStop) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                                contentDescription = if (isTargetAlightStop) "Cancel alight alert for ${callingPoint.stationName}" else "Set alight alert for ${callingPoint.stationName}",
                                tint = if (isTargetAlightStop) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }

        // Subtitle row for current station, current train location, target alighting stop, or transfer buffer risk
        val transferRisk = remember(callingPoint, isPassed, isTargetAlightStop) {
            if (!isPassed) {
                com.androidfung.departureboard.util.DelayRepayCalculator.evaluateCallingPointTransferRisk(
                    callingPoint = callingPoint,
                    isTargetAlightStop = isTargetAlightStop
                )
            } else null
        }
        val showSubtitleRow = callingPoint.isCurrentStation || isTrainLocation || isTargetAlightStop || transferRisk != null
        if (showSubtitleRow) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(30.dp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    val subtitleTrackColor = if (isLast) Color.Transparent
                    else if (isPassed) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    else accessibleColors.effectiveLineColor.copy(alpha = 0.5f)
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(subtitleTrackColor)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                FlowRow(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (callingPoint.isCurrentStation) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(
                                width = 0.8.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = com.androidfung.departureboard.ui.designsystem.PersonPinIcon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = stringResource(R.string.train_journey_your_station),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        lineHeight = 12.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (isTrainLocation) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(
                                width = 0.8.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.LocationOn,
                                    contentDescription = null,
                                    tint = accessibleColors.effectiveLineColor,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = stringResource(R.string.train_journey_current_location),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        lineHeight = 12.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (isTargetAlightStop) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = androidx.compose.foundation.BorderStroke(
                                width = 0.8.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "Alight Stop (1-stop alert active)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 12.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    if (transferRisk != null) {
                        val riskColor = if (transferRisk.isHighRisk) {
                            MaterialTheme.colorScheme.error
                        } else {
                            Color(0xFFF57C00)
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = riskColor.copy(alpha = 0.14f),
                            border = androidx.compose.foundation.BorderStroke(
                                width = 0.8.dp,
                                color = riskColor.copy(alpha = 0.5f)
                            )
                        ) {
                            Text(
                                text = transferRisk.badgeText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 12.sp
                                ),
                                color = riskColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
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
                    lineColor = bakerlooColor,
                    isPassed = false,
                    isTrainLocation = index == 0
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
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val containerColor = if (isCancelled) {
        if (isDark) Color(0xFF930006).copy(alpha = 0.20f) else Color(0xFFFFDAD6).copy(alpha = 0.45f)
    } else {
        if (isDark) Color(0xFFFFA000).copy(alpha = 0.12f) else Color(0xFFFFF3CD).copy(alpha = 0.65f)
    }
    val contentColor = if (isCancelled) {
        if (isDark) Color(0xFFFFB4A9) else Color(0xFFBA1A1A)
    } else {
        if (isDark) Color(0xFFFFB74D) else Color(0xFFC05600)
    }
    val borderColor = contentColor.copy(alpha = if (isDark) 0.45f else 0.35f)

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

