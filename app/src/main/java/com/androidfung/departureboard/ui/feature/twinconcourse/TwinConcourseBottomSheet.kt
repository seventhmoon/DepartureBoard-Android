package com.androidfung.departureboard.ui.feature.twinconcourse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.androidfung.departureboard.ui.theme.LocalIsDarkTheme
import com.androidfung.departureboard.ui.theme.dimensions
import com.androidfung.departureboard.util.TwinConcourseEvaluator

/**
 * Dual-Hub Concourse / "Which Station?" Race sheet comparing live departures from two nearby competing hubs,
 * adjusted for walking + concourse gate buffer time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TwinConcourseBottomSheet(
    onDismissRequest: () -> Unit,
    stationCards: List<StationCardUiModel>,
    modifier: Modifier = Modifier,
    onSelectStation: ((Station) -> Unit)? = null,
    onDepartureClick: ((Departure) -> Unit)? = null,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        TwinConcourseContent(
            onDismissRequest = onDismissRequest,
            stationCards = stationCards,
            onSelectStation = onSelectStation,
            onDepartureClick = onDepartureClick
        )
    }
}

@Composable
fun TwinConcourseContent(
    onDismissRequest: () -> Unit,
    stationCards: List<StationCardUiModel>,
    modifier: Modifier = Modifier,
    onSelectStation: ((Station) -> Unit)? = null,
    onDepartureClick: ((Departure) -> Unit)? = null
) {
    val dimensions = MaterialTheme.dimensions

    if (stationCards.size < 2) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.twin_concourse_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.twin_concourse_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val defaultPair = remember(stationCards.size) {
        TwinConcourseEvaluator.selectBestDefaultPair(stationCards)
    }
    var stationAIndex by remember(stationCards.size) { mutableIntStateOf(defaultPair.first) }
    var stationBIndex by remember(stationCards.size) { mutableIntStateOf(defaultPair.second.coerceAtMost(stationCards.lastIndex)) }

    val cardA = stationCards.getOrNull(stationAIndex) ?: stationCards[0]
    val cardB = stationCards.getOrNull(stationBIndex) ?: stationCards[1]

    val comparison = remember(cardA, cardB) {
        TwinConcourseEvaluator.compareHubs(cardA, cardB, maxRows = 3)
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .navigationBarsPadding()
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
                        imageVector = Icons.AutoMirrored.Rounded.CompareArrows,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(dimensions.iconMedium)
                    )
                }
                Spacer(modifier = Modifier.width(dimensions.paddingLarge))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.twin_concourse_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.twin_concourse_subtitle),
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

        val allStations = remember(stationCards) { stationCards.map { it.station } }

        // Station Selector Pill Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StationPickerBox(
                label = "Hub A",
                currentStation = cardA.station,
                walkMinutes = comparison.walkMinutesA,
                allStations = allStations,
                currentIndex = stationAIndex,
                onSelectIndex = { stationAIndex = it },
                modifier = Modifier.weight(1f)
            )

            StationPickerBox(
                label = "Hub B",
                currentStation = cardB.station,
                walkMinutes = comparison.walkMinutesB,
                allStations = allStations,
                currentIndex = stationBIndex,
                onSelectIndex = { stationBIndex = it },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Walk-adjusted catchable winner banner
        val winnerCard = comparison.winnerCard
        val winningEval = comparison.winningDeparture
        if (winnerCard != null && winningEval != null) {
            val winningDeparture = winningEval.departure
            val isDark = LocalIsDarkTheme.current
            val winnerAccentColor = if (isDark) Color(0xFFFFB300) else Color(0xFF8C5000)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFFFD54F).copy(alpha = 0.16f),
                border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.EmojiEvents,
                        contentDescription = null,
                        tint = winnerAccentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.twin_concourse_best_catchable, winnerCard.station.displayName),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = winnerAccentColor
                        )
                        val mins = winningDeparture.timeToStationSeconds / 60
                        val minLabel = if (mins == 0) stringResource(R.string.due_now) else stringResource(R.string.in_minutes_short, mins)
                        Text(
                            text = "${winningDeparture.lineName} → ${winningDeparture.destinationName} • $minLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Departures comparison
        HubDeparturesSection(
            card = cardA,
            evaluated = comparison.evaluatedA,
            walkMinutes = comparison.walkMinutesA,
            hubTag = "Hub A",
            onSelectStation = onSelectStation,
            onDepartureClick = onDepartureClick
        )

        Spacer(modifier = Modifier.height(16.dp))

        HubDeparturesSection(
            card = cardB,
            evaluated = comparison.evaluatedB,
            walkMinutes = comparison.walkMinutesB,
            hubTag = "Hub B",
            onSelectStation = onSelectStation,
            onDepartureClick = onDepartureClick
        )
    }
}

@Composable
private fun StationPickerBox(
    label: String,
    currentStation: Station,
    walkMinutes: Int?,
    allStations: List<Station>,
    currentIndex: Int,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier.clickable {
            val next = (currentIndex + 1) % allStations.size
            onSelectIndex(next)
        }
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
                if (walkMinutes != null) {
                    Text(
                        text = stringResource(R.string.twin_walk_minutes_badge, walkMinutes),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = currentStation.displayName,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HubDeparturesSection(
    card: StationCardUiModel,
    evaluated: List<TwinConcourseEvaluator.EvaluatedTwinDeparture>,
    walkMinutes: Int?,
    hubTag: String,
    onSelectStation: ((Station) -> Unit)?,
    onDepartureClick: ((Departure) -> Unit)?
) {
    val isDark = LocalIsDarkTheme.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "$hubTag • ${card.station.displayName}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (walkMinutes != null) {
                        Text(
                            text = stringResource(R.string.twin_walk_minutes_badge, walkMinutes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (onSelectStation != null) {
                    Text(
                        text = stringResource(R.string.twin_view_station),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onSelectStation(card.station) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (evaluated.isEmpty()) {
                Text(
                    text = stringResource(R.string.twin_no_active_departures),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                evaluated.forEachIndexed { idx, item ->
                    Column {
                        DepartureRowItem(
                            departure = item.departure,
                            isFirst = idx == 0,
                            showLineBadge = true,
                            onClick = { onDepartureClick?.invoke(item.departure) }
                        )
                        if (walkMinutes != null) {
                            val (pillBg, pillFg, labelRes) = when (item.catchability) {
                                TwinConcourseEvaluator.CatchabilityStatus.MAKEABLE -> Triple(
                                    Color(0xFF2E7D32).copy(alpha = 0.14f),
                                    if (isDark) Color(0xFF81C784) else Color(0xFF1B5E20),
                                    R.string.twin_catchability_makeable
                                )
                                TwinConcourseEvaluator.CatchabilityStatus.TIGHT_SPRINT -> Triple(
                                    Color(0xFFFFB300).copy(alpha = 0.16f),
                                    if (isDark) Color(0xFFFFD54F) else Color(0xFF8C5000),
                                    R.string.twin_catchability_tight
                                )
                                TwinConcourseEvaluator.CatchabilityStatus.TOO_CLOSE -> Triple(
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                                    MaterialTheme.colorScheme.onErrorContainer,
                                    R.string.twin_catchability_too_close
                                )
                            }
                            Box(modifier = Modifier.padding(start = 38.dp, bottom = 6.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = pillBg
                                ) {
                                    Text(
                                        text = stringResource(labelRes),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = pillFg,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                    if (idx < evaluated.lastIndex) {
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
