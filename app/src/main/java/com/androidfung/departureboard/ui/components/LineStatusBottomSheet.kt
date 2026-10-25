package com.androidfung.departureboard.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Traffic
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androidfung.departureboard.data.model.HkMtrLineRegistry
import com.androidfung.departureboard.data.model.TflLineColors
import com.androidfung.departureboard.data.model.TflLineStatusDetail
import com.androidfung.departureboard.data.model.TflLineStatusItem
import com.androidfung.departureboard.data.model.TransitRegion
import com.androidfung.departureboard.ui.theme.GoodServiceGreen
import com.androidfung.departureboard.ui.theme.MinorDelaysAmber
import com.androidfung.departureboard.ui.theme.SevereDelaysRed

private val CANONICAL_GB_LINES = listOf(
    "bakerloo" to "Bakerloo",
    "central" to "Central",
    "circle" to "Circle",
    "district" to "District",
    "hammersmith-city" to "Hammersmith & City",
    "jubilee" to "Jubilee",
    "metropolitan" to "Metropolitan",
    "northern" to "Northern",
    "piccadilly" to "Piccadilly",
    "victoria" to "Victoria",
    "waterloo-city" to "Waterloo & City",
    "elizabeth" to "Elizabeth line",
    "dlr" to "DLR",
    "liberty" to "Liberty",
    "lioness" to "Lioness",
    "mildmay" to "Mildmay",
    "suffragette" to "Suffragette",
    "weaver" to "Weaver",
    "windrush" to "Windrush",
    "tram" to "London Trams"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LineStatusBottomSheet(
    lineStatuses: Map<String, TflLineStatusItem>,
    selectedRegion: TransitRegion = TransitRegion.GB,
    relevantLineIds: Set<String> = emptySet(),
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDisruptedOnly by remember { mutableStateOf(false) }
    var filterToMyLinesOnly by remember { mutableStateOf(false) }
    val expandedLineIds = remember { mutableStateMapOf<String, Boolean>() }

    val displayItems = remember(lineStatuses, selectedRegion, relevantLineIds) {
        buildDisplayLineStatusItems(lineStatuses, selectedRegion, relevantLineIds)
    }

    val hasRelevantLines = remember(displayItems) {
        displayItems.any { it.isRelevantToUser }
    }

    val disruptedCount = remember(displayItems) {
        displayItems.count { it.isDisrupted }
    }

    val filteredItems = remember(displayItems, showDisruptedOnly, filterToMyLinesOnly) {
        displayItems.filter { item ->
            (!showDisruptedOnly || item.isDisrupted) &&
                (!filterToMyLinesOnly || item.isRelevantToUser)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Traffic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = if (selectedRegion == TransitRegion.HK) "MTR Network Status" else "Live Network Status",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (selectedRegion == TransitRegion.HK) {
                                "Hong Kong MTR Lines"
                            } else {
                                "Tube, Elizabeth line, Overground, DLR & Bus"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh network status"
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close network status"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Summary Banner
            val bannerBgColor = if (disruptedCount == 0) {
                GoodServiceGreen.copy(alpha = 0.12f)
            } else {
                MinorDelaysAmber.copy(alpha = 0.15f)
            }
            val bannerAccentColor = if (disruptedCount == 0) GoodServiceGreen else MinorDelaysAmber

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = bannerBgColor,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (disruptedCount == 0) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = bannerAccentColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (disruptedCount == 0) {
                                "Good service across all monitored lines"
                            } else {
                                "$disruptedCount ${if (disruptedCount == 1) "line" else "lines"} reporting disruptions"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (hasRelevantLines) {
                            FilterChip(
                                selected = filterToMyLinesOnly,
                                onClick = { filterToMyLinesOnly = !filterToMyLinesOnly },
                                label = {
                                    Text(
                                        text = "My Lines",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }

                        if (disruptedCount > 0) {
                            FilterChip(
                                selected = showDisruptedOnly,
                                onClick = { showDisruptedOnly = !showDisruptedOnly },
                                label = {
                                    Text(
                                        text = if (showDisruptedOnly) "Show All" else "Disrupted ($disruptedCount)",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = filteredItems,
                    key = { it.id }
                ) { item ->
                    val isExpanded = expandedLineIds[item.id] == true
                    LineStatusCard(
                        item = item,
                        isExpanded = isExpanded,
                        onToggleExpand = {
                            if (!item.reason.isNullOrBlank()) {
                                expandedLineIds[item.id] = !isExpanded
                            }
                        }
                    )
                }
            }
        }
    }
}

internal data class DisplayLineStatusItem(
    val id: String,
    val name: String,
    val badgeCode: String,
    val badgeBgColor: Color,
    val badgeTextColor: Color,
    val statusDescription: String,
    val severity: Int,
    val reason: String?,
    val isRelevantToUser: Boolean = false
) {
    val isDisrupted: Boolean get() = severity != 10
}

internal fun buildDisplayLineStatusItems(
    lineStatuses: Map<String, TflLineStatusItem>,
    selectedRegion: TransitRegion,
    relevantLineIds: Set<String> = emptySet()
): List<DisplayLineStatusItem> {
    fun isLineRelevant(lineId: String): Boolean {
        if (relevantLineIds.isEmpty()) return false
        val normalized = lineId.lowercase()
        return relevantLineIds.any { rel ->
            rel == normalized ||
                (normalized == "elizabeth" && rel == "elizabeth-line") ||
                (normalized == "elizabeth-line" && rel == "elizabeth") ||
                normalized.contains(rel) ||
                rel.contains(normalized)
        }
    }

    if (selectedRegion == TransitRegion.HK) {
        val items = HkMtrLineRegistry.LINES.values.map { mtrLine ->
            val statusItem = lineStatuses[mtrLine.code.lowercase()]
            val primaryStatus = statusItem?.lineStatuses?.minByOrNull { it.statusSeverity }
            DisplayLineStatusItem(
                id = mtrLine.code,
                name = mtrLine.displayName,
                badgeCode = mtrLine.shortName,
                badgeBgColor = mtrLine.color,
                badgeTextColor = mtrLine.textColor,
                statusDescription = primaryStatus?.statusSeverityDescription ?: "Good Service",
                severity = primaryStatus?.statusSeverity ?: 10,
                reason = primaryStatus?.reason,
                isRelevantToUser = isLineRelevant(mtrLine.code)
            )
        }
        return items.sortedWith(
            compareByDescending<DisplayLineStatusItem> { it.isRelevantToUser }
                .thenByDescending { it.isDisrupted }
                .thenBy { it.severity }
        )
    }

    val canonicalResults = CANONICAL_GB_LINES.map { (lineId, fallbackName) ->
        val badge = TflLineColors.getLineBadge(lineId, fallbackName, null)
        val statusItem = lineStatuses[lineId]
            ?: if (lineId == "elizabeth") lineStatuses["elizabeth-line"] else null
        val primaryStatus: TflLineStatusDetail? = statusItem?.lineStatuses?.minByOrNull { it.statusSeverity }
        val severity = primaryStatus?.statusSeverity ?: 10
        val description = primaryStatus?.statusSeverityDescription ?: "Good Service"
        val distinctReasons = statusItem?.lineStatuses
            ?.mapNotNull { it.reason?.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            ?.joinToString("\n\n")

        DisplayLineStatusItem(
            id = lineId,
            name = statusItem?.name ?: fallbackName,
            badgeCode = badge.lineCode.ifBlank { badge.displayName.take(4).uppercase() },
            badgeBgColor = badge.backgroundColor,
            badgeTextColor = badge.textColor,
            statusDescription = description,
            severity = severity,
            reason = distinctReasons,
            isRelevantToUser = isLineRelevant(lineId)
        )
    }

    val bodsItems = lineStatuses.values
        .filter { it.id.startsWith("bods-") }
        .map { statusItem ->
            val primaryStatus = statusItem.lineStatuses.minByOrNull { it.statusSeverity }
            val badge = TflLineColors.getLineBadge(statusItem.id, statusItem.name, "bus")
            DisplayLineStatusItem(
                id = statusItem.id,
                name = "Bus ${statusItem.name}",
                badgeCode = badge.lineCode.ifBlank { badge.displayName },
                badgeBgColor = badge.backgroundColor,
                badgeTextColor = badge.textColor,
                statusDescription = primaryStatus?.statusSeverityDescription ?: "Disrupted",
                severity = primaryStatus?.statusSeverity ?: 9,
                reason = primaryStatus?.reason,
                isRelevantToUser = isLineRelevant(statusItem.id) || isLineRelevant(statusItem.name)
            )
        }

    // Sort: user's lines first, then disrupted lines first so commuters immediately see relevant issues
    return (canonicalResults + bodsItems).sortedWith(
        compareByDescending<DisplayLineStatusItem> { it.isRelevantToUser }
            .thenByDescending { it.isDisrupted }
            .thenBy { it.severity }
    )
}

@Composable
private fun LineStatusCard(
    item: DisplayLineStatusItem,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val hasReason = !item.reason.isNullOrBlank()
    val statusColor = when {
        !item.isDisrupted -> GoodServiceGreen
        item.severity <= 6 -> SevereDelaysRed
        else -> MinorDelaysAmber
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = hasReason, onClick = onToggleExpand)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Official line badge pill
                    Box(
                        modifier = Modifier
                            .widthIn(min = 46.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(item.badgeBgColor)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item.badgeCode,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp
                            ),
                            color = item.badgeTextColor,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Text(
                        text = item.statusDescription,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (item.isDisrupted) FontWeight.Bold else FontWeight.Medium,
                        color = if (item.isDisrupted) statusColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (hasReason) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse details" else "Expand details",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isExpanded && hasReason,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = item.reason.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}
