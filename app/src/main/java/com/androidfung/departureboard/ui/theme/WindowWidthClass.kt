package com.androidfung.departureboard.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp

/**
 * Window width class hierarchy supporting foldables, compact phones,
 * standard phones, unfolded tablets, and wide displays.
 */
enum class WindowWidthClass {
    /** Narrow fold cover screens (e.g. Galaxy Z Fold ~340dp), ultra-compact phones (< 380dp) */
    NARROW,

    /** Standard modern smartphones (Pixel, Galaxy S, iPhone: 380dp–599dp) */
    REGULAR,

    /** Foldables unfolded (Z Fold inner display ~670dp), small tablets (600dp–839dp) */
    EXPANDED,

    /** Standard tablets, landscape tablets (840dp–1199dp) */
    LARGE,

    /** Ultra-wide desktop, landscape monitors, TV (1200dp+) */
    EXTRA_LARGE;

    val isNarrow: Boolean get() = this == NARROW
    val isMultiColumn: Boolean get() = this >= EXPANDED

    companion object {
        fun fromWidthDp(widthDp: Float): WindowWidthClass = when {
            widthDp < 380f -> NARROW
            widthDp < 600f -> REGULAR
            widthDp < 840f -> EXPANDED
            widthDp < 1200f -> LARGE
            else -> EXTRA_LARGE
        }

        fun fromWidth(width: Dp): WindowWidthClass = fromWidthDp(width.value)
    }
}

/**
 * CompositionLocal providing the current [WindowWidthClass] down the Compose tree.
 */
val LocalWindowWidthClass = staticCompositionLocalOf { WindowWidthClass.REGULAR }

/**
 * Wraps content with [CompositionLocalProvider] supplying the dynamic [WindowWidthClass].
 */
@Composable
fun ProvideWindowWidthClass(
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val widthDp = with(density) { windowInfo.containerSize.width.toDp() }
    val windowClass = remember(widthDp) { WindowWidthClass.fromWidth(widthDp) }

    CompositionLocalProvider(LocalWindowWidthClass provides windowClass) {
        content()
    }
}
