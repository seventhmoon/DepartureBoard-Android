package com.androidfung.departureboard.ui.feature.detail

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Holds contrast-compliant colors for transit lines and train location indicators,
 * adapting cleanly between light/dark themes and dark/light transit lines.
 */
@Immutable
data class AccessibleTimelineColors(
    val effectiveLineColor: Color,
    val badgeBackground: Color,
    val badgeContent: Color,
    val badgeBorder: Color,
    val pulseHaloColor: Color,
    val coreNodeColor: Color
)

/**
 * Computes accessible colors for transit line indicators, badges, and timeline nodes.
 * Automatically adapts when the line color is too dark (e.g. London Underground Northern Line)
 * or too light (e.g. Circle Line yellow) relative to the active theme surface.
 */
@Composable
fun rememberAccessibleTimelineColors(lineColor: Color): AccessibleTimelineColors {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant

    return remember(lineColor, surfaceColor, onSurfaceColor, primaryColor, outlineVariantColor) {
        resolveAccessibleTimelineColors(
            lineColor = lineColor,
            surfaceColor = surfaceColor,
            onSurfaceColor = onSurfaceColor,
            primaryColor = primaryColor,
            outlineVariantColor = outlineVariantColor
        )
    }
}

/**
 * Pure calculation function for [AccessibleTimelineColors], allowing deterministic unit testing.
 */
fun resolveAccessibleTimelineColors(
    lineColor: Color,
    surfaceColor: Color,
    onSurfaceColor: Color,
    primaryColor: Color,
    outlineVariantColor: Color
): AccessibleTimelineColors {
    val surfaceLuminance = surfaceColor.luminance()
    val isDarkSurface = surfaceLuminance < 0.5f
    val lineLuminance = lineColor.luminance()

    val lighter = maxOf(surfaceLuminance, lineLuminance)
    val darker = minOf(surfaceLuminance, lineLuminance)
    val contrastRatio = (lighter + 0.05f) / (darker + 0.05f)

    return if (isDarkSurface && contrastRatio < 3.0f) {
        // Dark line on dark surface (e.g. Northern Line black or deep navy in Dark Mode)
        AccessibleTimelineColors(
            effectiveLineColor = outlineVariantColor.copy(alpha = 0.85f),
            badgeBackground = outlineVariantColor.copy(alpha = 0.25f),
            badgeContent = onSurfaceColor,
            badgeBorder = outlineVariantColor.copy(alpha = 0.6f),
            pulseHaloColor = primaryColor,
            coreNodeColor = onSurfaceColor
        )
    } else if (!isDarkSurface && contrastRatio < 3.0f) {
        // Light line on light surface (e.g. Circle Line yellow in Light Mode)
        AccessibleTimelineColors(
            effectiveLineColor = Color(0xFF8A7200),
            badgeBackground = lineColor.copy(alpha = 0.25f),
            badgeContent = onSurfaceColor,
            badgeBorder = lineColor.copy(alpha = 0.7f),
            pulseHaloColor = Color(0xFF8A7200),
            coreNodeColor = Color(0xFF8A7200)
        )
    } else {
        // High-contrast vibrant lines (e.g. Victoria blue, Central red, etc.)
        AccessibleTimelineColors(
            effectiveLineColor = lineColor,
            badgeBackground = lineColor.copy(alpha = if (isDarkSurface) 0.18f else 0.12f),
            badgeContent = lineColor,
            badgeBorder = lineColor.copy(alpha = 0.35f),
            pulseHaloColor = lineColor,
            coreNodeColor = lineColor
        )
    }
}
