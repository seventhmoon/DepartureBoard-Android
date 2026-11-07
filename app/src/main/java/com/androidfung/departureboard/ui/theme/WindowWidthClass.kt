package com.androidfung.departureboard.ui.theme

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
 * Foldable posture metadata for tabletop / half-opened horizontal hinge layouts.
 */
@Immutable
data class WindowPostureInfo(
    val isTabletop: Boolean = false,
    val hingeTopDp: Dp? = null,
    val hingeBottomDp: Dp? = null,
    val hingeHeightDp: Dp = 0.dp
)

/**
 * CompositionLocal providing the current [WindowWidthClass] down the Compose tree.
 */
val LocalWindowWidthClass = staticCompositionLocalOf { WindowWidthClass.REGULAR }

/**
 * CompositionLocal providing the current foldable [WindowPostureInfo] down the Compose tree.
 */
val LocalWindowPosture = staticCompositionLocalOf { WindowPostureInfo() }

/**
 * Wraps content with [CompositionLocalProvider] supplying the dynamic [WindowWidthClass]
 * and dynamic foldable [WindowPostureInfo].
 */
@Composable
fun ProvideWindowWidthClass(
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val widthDp = with(density) { windowInfo.containerSize.width.toDp() }
    val windowClass = remember(widthDp) { WindowWidthClass.fromWidth(widthDp) }

    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val posture = adaptiveInfo.windowPosture
    val horizontalHinge = posture.hingeList.firstOrNull { !it.isVertical }
    val isTabletop = posture.isTabletop || (horizontalHinge != null && !horizontalHinge.isFlat)

    val postureInfo = remember(isTabletop, horizontalHinge, density) {
        if (isTabletop) {
            if (horizontalHinge != null) {
                val topDp = with(density) { horizontalHinge.bounds.top.toDp() }
                val bottomDp = with(density) { horizontalHinge.bounds.bottom.toDp() }
                WindowPostureInfo(
                    isTabletop = true,
                    hingeTopDp = topDp,
                    hingeBottomDp = bottomDp,
                    hingeHeightDp = (bottomDp - topDp).coerceAtLeast(0.dp)
                )
            } else {
                WindowPostureInfo(isTabletop = true)
            }
        } else {
            WindowPostureInfo(isTabletop = false)
        }
    }

    CompositionLocalProvider(
        LocalWindowWidthClass provides windowClass,
        LocalWindowPosture provides postureInfo
    ) {
        content()
    }
}
