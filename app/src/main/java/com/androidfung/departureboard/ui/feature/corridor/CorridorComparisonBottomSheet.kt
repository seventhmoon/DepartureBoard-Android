package com.androidfung.departureboard.ui.feature.corridor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androidfung.departureboard.R
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.ui.dashboard.StationCardUiModel
import com.androidfung.departureboard.ui.designsystem.DepartureRowItem
import com.androidfung.departureboard.ui.theme.dimensions
import com.androidfung.departureboard.util.CorridorRouteComparator
import com.androidfung.departureboard.util.CorridorServiceType

/**
 * Pinned Commute Corridor / A-to-B Route & Departure Comparison Bottom Sheet.
 * Compares Express vs. Local Stoppers and highlights train overtakes along a travel corridor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorridorComparisonBottomSheet(
    onDismissRequest: () -> Unit,
    stationCards: List<StationCardUiModel>,
    modifier: Modifier = Modifier,
    initialTargetStation: String = "",
    onDepartureClick: ((Departure) -> Unit)? = null,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        CorridorComparisonContent(
            onDismissRequest = onDismissRequest,
            stationCards = stationCards,
            initialTargetStation = initialTargetStation,
            onDepartureClick = onDepartureClick
        )
    }
}

@Composable
fun CorridorComparisonContent(
    onDismissRequest: () -> Unit,
    stationCards: List<StationCardUiModel>,
    modifier: Modifier = Modifier,
    initialTargetStation: String = "",
    onDepartureClick: ((Departure) -> Unit)? = null
) {
    val dimensions = MaterialTheme.dimensions

    if (stationCards.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Corridor Route Comparison",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Add at least 1 origin station to your saved board to compare corridor departures.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    var originStationIndex by remember { mutableIntStateOf(0) }
    val originCard = stationCards.getOrNull(originStationIndex) ?: stationCards[0]

    // Default target: the 2nd saved station's name or initial target or most popular terminus
    var targetStationInput by remember {
        mutableStateOf(
            initialTargetStation.ifBlank {
                stationCards.getOrNull(1)?.station?.displayName ?: originCard.departures.firstOrNull()?.destinationName.orEmpty()
            }
        )
    }

    val corridorItems = remember(originCard.departures, targetStationInput) {
        CorridorRouteComparator.evaluateCorridorDepartures(originCard.departures, targetStationInput)
    }

    val overtakeAlert = remember(corridorItems) {
        CorridorRouteComparator.detectFasterOvertake(corridorItems)
    }

    val scrollState = rememberScrollState()
    val isDark = com.androidfung.departureboard.ui.theme.LocalIsDarkTheme.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(scrollState)
            .padding(horizontal = dimensions.paddingScreenEdge, vertical = dimensions.paddingMedium)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(dimensions.iconContainerSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Route,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(dimensions.iconMedium)
                    )
                }
                Spacer(modifier = Modifier.width(dimensions.paddingLarge))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Corridor Departure Status",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Express vs. Local Stopper Comparison",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onDismissRequest) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.cd_close),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Origin & Destination Inputs
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Origin Picker Box
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        originStationIndex = (originStationIndex + 1) % stationCards.size
                    }
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        text = stringResource(R.string.corridor_origin_tap_switch),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = originCard.station.displayName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )

            // Destination Input Box
            OutlinedTextField(
                value = targetStationInput,
                onValueChange = { targetStationInput = it },
                label = { Text(stringResource(R.string.corridor_destination_stop), fontSize = 11.sp) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1.2f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Overtake / Faster Arrival Alert
        if (overtakeAlert != null) {
            val overtakeAccentColor = if (isDark) Color(0xFFFFB300) else Color(0xFF8C5000)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFFFB300).copy(alpha = 0.16f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = overtakeAccentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.corridor_express_recommendation_title),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = overtakeAccentColor
                        )
                        Text(
                            text = overtakeAlert.recommendationMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Corridor Departures List
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = stringResource(R.string.corridor_departures_serving, targetStationInput, corridorItems.size),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (corridorItems.isEmpty()) {
                    Text(
                        text = stringResource(R.string.corridor_no_departures_towards, targetStationInput, originCard.station.displayName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    corridorItems.forEachIndexed { idx, item ->
                        val dep = item.departure
                        val serviceColor = when (item.serviceType) {
                            CorridorServiceType.EXPRESS -> if (isDark) Color(0xFF4CAF50) else Color(0xFF1B5E20)
                            CorridorServiceType.SEMI_FAST -> if (isDark) Color(0xFFFF9800) else Color(0xFF8C5000)
                            CorridorServiceType.ALL_STATIONS -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        val serviceTypeLabel = when (item.serviceType) {
                            CorridorServiceType.EXPRESS -> stringResource(R.string.corridor_service_express)
                            CorridorServiceType.SEMI_FAST -> stringResource(R.string.corridor_service_semi_fast)
                            CorridorServiceType.ALL_STATIONS -> stringResource(R.string.corridor_service_all_stations)
                        }
                        val stopsCountLabel = androidx.compose.ui.res.pluralStringResource(
                            R.plurals.corridor_stops_count,
                            item.stopsCountToDestination,
                            item.stopsCountToDestination
                        )

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = serviceColor.copy(alpha = 0.15f),
                                    border = androidx.compose.foundation.BorderStroke(0.8.dp, serviceColor.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "$serviceTypeLabel ($stopsCountLabel)",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                        color = serviceColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                Text(
                                    text = item.targetArrivalTime?.let {
                                        stringResource(R.string.corridor_est_arrival, it)
                                    } ?: item.estimatedTravelMinutes?.let {
                                        stringResource(R.string.corridor_est_transit_mins, it)
                                    }.orEmpty(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            DepartureRowItem(
                                departure = dep,
                                isFirst = idx == 0,
                                showLineBadge = true,
                                onClick = { onDepartureClick?.invoke(dep) }
                            )

                            if (idx < corridorItems.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 38.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
