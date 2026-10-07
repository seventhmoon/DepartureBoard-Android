package com.androidfung.departureboard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Centralized dimension constants to keep paddings, borders, and rounded corners
 * consistent across the DepartureBoard app without hard-coding magic numbers.
 */
@Immutable
data class Dimensions(
    // Paddings & Margins
    val paddingTiny: Dp = 2.dp,
    val paddingSmall: Dp = 4.dp,
    val paddingMedium: Dp = 8.dp,
    val paddingLarge: Dp = 12.dp,
    val paddingExtraLarge: Dp = 16.dp,
    val paddingScreenEdge: Dp = 24.dp,

    // Corner Radii (Shapes)
    val cornerRadiusSmall: Dp = 8.dp,
    val cornerRadiusMedium: Dp = 10.dp,
    val cornerRadiusLarge: Dp = 12.dp,
    val cornerRadiusExtraLarge: Dp = 20.dp,

    // Border Widths
    val borderThin: Dp = 0.8.dp,
    val borderStandard: Dp = 1.dp,
    val borderThick: Dp = 2.dp,

    // Transit Specific UI sizes
    val badgeWidth: Dp = 38.dp,
    val badgeHeight: Dp = 28.dp,
    val mapIconSize: Dp = 18.dp
)

val LocalDimensions = staticCompositionLocalOf { Dimensions() }

val MaterialTheme.dimensions: Dimensions
    @Composable
    @ReadOnlyComposable
    get() = LocalDimensions.current
