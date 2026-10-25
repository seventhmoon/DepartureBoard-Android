package com.androidfung.departureboard.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Standard transit modes supported by the search filter chips.
 */
val SEARCH_FILTER_MODES = listOf("All", "Tube", "Elizabeth line", "Overground", "DLR", "National Rail", "Tram", "Bus")

/**
 * Horizontal scrolling row of transit mode filter chips (Tube, Elizabeth line, Overground, DLR, National Rail, Tram, Bus).
 */
@Composable
fun SearchFilterChipsRow(
    selectedMode: String,
    onModeSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    modes: List<String> = SEARCH_FILTER_MODES
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        modes.forEach { mode ->
            val isSelected = selectedMode == mode
            FilterChip(
                selected = isSelected,
                onClick = { onModeSelected(mode) },
                label = {
                    Text(
                        text = mode,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    )
                },
                shape = RoundedCornerShape(12.dp),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                ),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
