package com.androidfung.departureboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androidfung.departureboard.data.model.DefaultStations
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TflLineColors
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.model.isHkBus
import com.androidfung.departureboard.data.model.isHkMtr
import com.androidfung.departureboard.ui.theme.DepartureBoardTheme

/**
 * Individual station row item in the search sheet.
 */
@Composable
fun StationSearchResultRow(
    station: Station,
    isSaved: Boolean,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
    distanceText: String? = null
) {
    val primaryMode = listOf("tube", "elizabeth-line", "national-rail", "overground", "dlr", "tram")
        .firstOrNull { it in station.modes }
        ?: station.modes.firstOrNull() ?: "tube"
    val modeIcon = TransitIconHelper.getTransitIconForMode(primaryMode)

    Surface(
        onClick = onAddClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Mode Icon & Name info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = modeIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = station.name,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (distanceText != null) {
                            Text(
                                text = distanceText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        } else if (station.displayZone != null) {
                            Text(
                                text = "Zone ${station.displayZone}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }

                        // Filter modes: For rail/tube/interchange stations, omit the generic "bus" mode tag
                        // (as bus departures are served at individual street stops like 490000138E, not inside the train station)
                        val displayModes = if (!station.isBusOnly && station.modes.size > 1) {
                            station.modes.filter { !it.equals("bus", ignoreCase = true) }
                        } else {
                            station.modes
                        }

                        // Mode badges with standardized official terminology and branding
                        displayModes.take(3).forEach { modeStr ->
                            val transitMode = TransitMode.fromModeString(modeStr)
                            val badge = if (station.isHkMtr) {
                                val primaryLine = station.lines.firstOrNull()?.id ?: "ISL"
                                com.androidfung.departureboard.data.model.HkMtrLineRegistry.getLineBadge(primaryLine)
                            } else if (station.isHkBus) {
                                LineBadgeInfo("bus", "Bus", com.androidfung.departureboard.data.model.HkDefaultStations.ColorKmbRed, Color.White, TransitMode.BUS)
                            } else {
                                TflLineColors.getLineBadge(
                                    lineId = transitMode.id,
                                    lineName = transitMode.displayName,
                                    modeName = transitMode.id
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(badge.backgroundColor.copy(alpha = 0.85f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = transitMode.displayName,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = badge.textColor
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action Indicator: "Saved" badge or Chevron to view departures
            if (isSaved) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Bookmark,
                            contentDescription = "Pinned to home",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Pinned",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = "View departures",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

// =================== PREVIEWS ===================

@Preview(name = "Search Station Item - Not Saved", showBackground = true)
@Composable
fun StationSearchResultRowPreview() {
    DepartureBoardTheme {
        StationSearchResultRow(
            station = DefaultStations.POPULAR_STATIONS[0],
            isSaved = false,
            onAddClick = {}
        )
    }
}

@Preview(name = "Search Station Item - Saved", showBackground = true)
@Composable
fun StationSearchResultRowSavedPreview() {
    DepartureBoardTheme {
        StationSearchResultRow(
            station = DefaultStations.POPULAR_STATIONS[1],
            isSaved = true,
            onAddClick = {}
        )
    }
}
