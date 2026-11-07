package com.androidfung.departureboard.ui.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Remembers a shared shimmer translation animation [androidx.compose.runtime.State].
 */
@Composable
fun rememberShimmerTranslateAnim(): androidx.compose.runtime.State<Float> {
    val transition = rememberInfiniteTransition(label = "ShimmerTransition")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShimmerTranslate"
    )
}

/**
 * Applies a high-performance shimmer loading effect to a placeholder element.
 * The animation position is read exclusively inside [drawBehind], deferring updates
 * entirely to the Draw phase and preventing per-frame recomposition cycles.
 */
fun Modifier.shimmerPlaceholder(
    translateAnim: androidx.compose.runtime.State<Float>,
    shape: Shape = RoundedCornerShape(4.dp),
    shimmerColors: List<Color>
): Modifier {
    return this
        .clip(shape)
        .drawBehind {
            val progress = translateAnim.value
            val brush = Brush.linearGradient(
                colors = shimmerColors,
                start = Offset(progress - 400f, progress - 400f),
                end = Offset(progress, progress)
            )
            drawRect(brush = brush)
        }
}

/**
 * Creates a shimmering brush modifier for skeleton placeholders.
 * Note: Prefer [shimmerPlaceholder] to defer animation reads to the Draw phase.
 */
@Composable
fun rememberShimmerBrush(
    shimmerColors: List<Color> = listOf(
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f),
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    )
): Brush {
    val translateAnim = rememberShimmerTranslateAnim()
    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim.value - 400f, translateAnim.value - 400f),
        end = Offset(translateAnim.value, translateAnim.value)
    )
}

/**
 * Skeleton placeholder for a Station Departure Card shown during initial load.
 */
@Composable
fun StationCardSkeleton(
    modifier: Modifier = Modifier
) {
    val defaultColors = listOf(
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f),
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    )
    val translateAnim = rememberShimmerTranslateAnim()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp)),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        val topGradientColor = MaterialTheme.colorScheme.surfaceContainerLow
        val bottomGradientColor = MaterialTheme.colorScheme.surfaceContainer

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(topGradientColor, bottomGradientColor)
                    )
                )
                .padding(top = 22.dp, bottom = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header section skeleton
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Roundel placeholder
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .shimmerPlaceholder(translateAnim, CircleShape, defaultColors)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        // Station Name placeholder
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.65f)
                                .height(22.dp)
                                .shimmerPlaceholder(translateAnim, RoundedCornerShape(8.dp), defaultColors)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Line Badges placeholders
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                .width(64.dp)
                                .height(30.dp)
                                .shimmerPlaceholder(translateAnim, RoundedCornerShape(10.dp), defaultColors)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Departures Container Skeleton
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(26.dp)
                        )
                        .padding(vertical = 12.dp, horizontal = 14.dp)
                ) {
                    DepartureRowsSkeleton(
                        count = 3
                    )
                }
            }
        }
    }
}

/**
 * Shimmering placeholder rows representing upcoming departures during refresh or filtering.
 */
@Composable
fun DepartureRowsSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 3,
    shimmerBrush: Brush? = null
) {
    val defaultColors = listOf(
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f),
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    )
    val translateAnim = if (shimmerBrush == null) rememberShimmerTranslateAnim() else null

    fun Modifier.placeholder(shape: Shape): Modifier = if (shimmerBrush != null) {
        this.clip(shape).background(shimmerBrush)
    } else {
        this.shimmerPlaceholder(translateAnim!!, shape, defaultColors)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(count) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Line indicator pill placeholder
                Box(
                    modifier = Modifier
                        .size(width = 38.dp, height = 28.dp)
                        .placeholder(RoundedCornerShape(6.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(14.dp)
                            .placeholder(RoundedCornerShape(4.dp))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(11.dp)
                            .placeholder(RoundedCornerShape(4.dp))
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                // Countdown pill placeholder
                Box(
                    modifier = Modifier
                        .size(width = 46.dp, height = 24.dp)
                        .placeholder(RoundedCornerShape(12.dp))
                )
            }
            if (index < count - 1) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 48.dp, end = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                    thickness = 0.8.dp
                )
            }
        }
    }
}
