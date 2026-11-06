package com.androidfung.departureboard.ui.feature.detail

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AccessibleTimelineColorsTest {

    private val darkSurface = Color(0xFF1C1B1F)
    private val darkOnSurface = Color(0xFFE6E1E5)
    private val darkPrimary = Color(0xFFD0BCFF)
    private val darkOutlineVariant = Color(0xFF49454F)

    private val lightSurface = Color(0xFFFEF7FF)
    private val lightOnSurface = Color(0xFF1C1B1F)
    private val lightPrimary = Color(0xFF6750A4)
    private val lightOutlineVariant = Color(0xFFCAC4D0)

    @Test
    fun northernLine_black_inDarkTheme_resolvesAccessibleColors() {
        // Northern line branding is pure black #000000
        val northernLineColor = Color(0xFF000000)

        val colors = resolveAccessibleTimelineColors(
            lineColor = northernLineColor,
            surfaceColor = darkSurface,
            onSurfaceColor = darkOnSurface,
            primaryColor = darkPrimary,
            outlineVariantColor = darkOutlineVariant
        )

        // Text/icon content MUST NOT be black on dark background
        assertNotEquals(northernLineColor, colors.badgeContent)
        assertEquals(darkOnSurface, colors.badgeContent)

        // Halo and core node must be visible (not translucent black)
        assertEquals(darkPrimary, colors.pulseHaloColor)
        assertEquals(darkOnSurface, colors.coreNodeColor)

        // Timeline track must not be invisible black
        assertEquals(darkOutlineVariant.copy(alpha = 0.85f), colors.effectiveLineColor)
    }

    @Test
    fun northernLine_black_inLightTheme_preservesBlackBranding() {
        val northernLineColor = Color(0xFF000000)

        val colors = resolveAccessibleTimelineColors(
            lineColor = northernLineColor,
            surfaceColor = lightSurface,
            onSurfaceColor = lightOnSurface,
            primaryColor = lightPrimary,
            outlineVariantColor = lightOutlineVariant
        )

        // High contrast against white: preserves branded black
        assertEquals(northernLineColor, colors.badgeContent)
        assertEquals(northernLineColor, colors.effectiveLineColor)
    }

    @Test
    fun circleLine_yellow_inLightTheme_avoidsYellowOnWhite() {
        // Circle line branding is bright yellow #FFD300
        val circleLineYellow = Color(0xFFFFD300)

        val colors = resolveAccessibleTimelineColors(
            lineColor = circleLineYellow,
            surfaceColor = lightSurface,
            onSurfaceColor = lightOnSurface,
            primaryColor = lightPrimary,
            outlineVariantColor = lightOutlineVariant
        )

        // Text/icon content MUST NOT be bright yellow on light background (contrast failure)
        assertNotEquals(circleLineYellow, colors.badgeContent)
        assertEquals(lightOnSurface, colors.badgeContent)
    }

    @Test
    fun circleLine_yellow_inDarkTheme_preservesVibrantYellow() {
        val circleLineYellow = Color(0xFFFFD300)

        val colors = resolveAccessibleTimelineColors(
            lineColor = circleLineYellow,
            surfaceColor = darkSurface,
            onSurfaceColor = darkOnSurface,
            primaryColor = darkPrimary,
            outlineVariantColor = darkOutlineVariant
        )

        // High contrast against dark background: yellow is very readable
        assertEquals(circleLineYellow, colors.badgeContent)
        assertEquals(circleLineYellow, colors.effectiveLineColor)
    }

    @Test
    fun victoriaLine_inBothThemes_maintainsBrandedColor() {
        val victoriaBlue = Color(0xFF0098D4)

        val darkColors = resolveAccessibleTimelineColors(
            lineColor = victoriaBlue,
            surfaceColor = darkSurface,
            onSurfaceColor = darkOnSurface,
            primaryColor = darkPrimary,
            outlineVariantColor = darkOutlineVariant
        )
        val lightColors = resolveAccessibleTimelineColors(
            lineColor = victoriaBlue,
            surfaceColor = lightSurface,
            onSurfaceColor = lightOnSurface,
            primaryColor = lightPrimary,
            outlineVariantColor = lightOutlineVariant
        )

        assertEquals(victoriaBlue, darkColors.badgeContent)
        assertEquals(victoriaBlue, lightColors.badgeContent)
    }
}
