package com.androidfung.departureboard.ui.feature.twinconcourse

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CompareArrows
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
import com.androidfung.departureboard.ui.theme.dimensions

/**
 * Dual-Hub Concourse / "Which Station?" Race sheet comparing live departures from two nearby competing hubs.
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

@Suppress("DEPRECATION")
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Twin Concourse Race",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Add at least 2 stations to your saved board to compare live departures head-to-head.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    var stationAIndex by remember { mutableIntStateOf(0) }
    var stationBIndex by remember { mutableIntStateOf(1.coerceAtMost(stationCards.lastIndex)) }

    val cardA = stationCards.getOrNull(stationAIndex) ?: stationCards[0]
    val cardB = stationCards.getOrNull(stationBIndex) ?: stationCards[1]

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = dimensions.paddingScreenEdge, vertical = dimensions.paddingMedium)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(dimensions.iconContainerSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CompareArrows,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(dimensions.iconMedium)
                    )
                }
                Spacer(modifier = Modifier.width(dimensions.paddingLarge))
                Column {
                    Text(
                        text = "Twin Concourse Race",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Head-to-head live station comparison",
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

        // Station Selector Pill Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StationPickerBox(
                label = "Hub A",
                currentStation = cardA.station,
                allStations = stationCards.map { it.station },
                currentIndex = stationAIndex,
                onSelectIndex = { stationAIndex = it },
                modifier = Modifier.weight(1f)
            )

            StationPickerBox(
                label = "Hub B",
                currentStation = cardB.station,
                allStations = stationCards.map { it.station },
                currentIndex = stationBIndex,
                onSelectIndex = { stationBIndex = it },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Who leaves next? Winner banner
        val firstA = cardA.departures.firstOrNull()
        val firstB = cardB.departures.firstOrNull()

        val winnerCard = when {
            firstA != null && firstB != null -> {
                if (firstA.timeToStationSeconds <= firstB.timeToStationSeconds) cardA else cardB
            }
            firstA != null -> cardA
            firstB != null -> cardB
            else -> null
        }

        val winningDeparture = if (winnerCard === cardA) firstA else firstB

        if (winnerCard != null && winningDeparture != null) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFFFD54F).copy(alpha = 0.16f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.5f)),
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
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Next Immediate Departure: ${winnerCard.station.displayName}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFFFB300)
                        )
                        val mins = winningDeparture.timeToStationSeconds / 60
                        val minLabel = if (mins == 0) "Due now" else "in $mins mins"
                        Text(
                            text = "${winningDeparture.lineName} to ${winningDeparture.destinationName} • $minLabel",
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
            hubTag = "Hub A",
            onSelectStation = onSelectStation,
            onDepartureClick = onDepartureClick
        )

        Spacer(modifier = Modifier.height(16.dp))

        HubDeparturesSection(
            card = cardB,
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
    allStations: List<Station>,
    currentIndex: Int,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier.clickable {
            val next = (currentIndex + 1) % allStations.size
            onSelectIndex(next)
        }
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = "$label (Tap to switch)",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.primary
            )
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
    hubTag: String,
    onSelectStation: ((Station) -> Unit)?,
    onDepartureClick: ((Departure) -> Unit)?
) {
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
                Text(
                    text = "$hubTag • ${card.station.displayName}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                if (onSelectStation != null) {
                    Text(
                        text = "View Station",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onSelectStation(card.station) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (card.departures.isEmpty()) {
                Text(
                    text = "No active departures",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val displayList = card.departures.take(3)
                displayList.forEachIndexed { idx, dep ->
                    DepartureRowItem(
                        departure = dep,
                        isFirst = idx == 0,
                        showLineBadge = true,
                        onClick = { onDepartureClick?.invoke(dep) }
                    )
                    if (idx < displayList.lastIndex) {
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
