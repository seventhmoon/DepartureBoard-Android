package com.androidfung.departureboard.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androidfung.departureboard.R
import com.androidfung.departureboard.data.model.CoachInfo
import com.androidfung.departureboard.data.model.OccupancyStatus
import com.androidfung.departureboard.data.model.TrainFormation

/**
 * Modern visual carriage formation bar showing coach segments, crowding heatmap,
 * and accessibility indicators ("Where to stand on the platform").
 */
@Composable
fun CarriageFormationBar(
    formation: TrainFormation,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
    recommendedCoachLabels: Set<String> = emptySet()
) {
    if (formation.coaches.isEmpty() && formation.coachCount <= 0) return

    val coaches = if (formation.coaches.isNotEmpty()) {
        formation.coaches
    } else {
        (1..formation.coachCount).map { CoachInfo(coachNumber = "$it") }
    }

    val scrollState = rememberScrollState()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            coaches.forEachIndexed { index, coach ->
                val label = coach.coachNumber ?: "${index + 1}"
                CarriageSegment(
                    coach = coach,
                    coachIndex = index,
                    totalCoaches = coaches.size,
                    isCompact = isCompact,
                    isRecommended = label in recommendedCoachLabels
                )

                // Inter-carriage coupler graphic
                if (index < coaches.lastIndex) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(if (isCompact) 8.dp else 12.dp)
                            .background(
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(1.dp)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun CarriageSegment(
    coach: CoachInfo,
    coachIndex: Int,
    totalCoaches: Int,
    isCompact: Boolean,
    isRecommended: Boolean = false
) {
    val occupancy = coach.parsedOccupancyStatus
    val occupancyColor = when (occupancy) {
        OccupancyStatus.EMPTY,
        OccupancyStatus.MANY_SEATS_AVAILABLE -> Color(0xFF4CAF50)
        OccupancyStatus.FEW_SEATS_AVAILABLE -> Color(0xFFFFB300)
        OccupancyStatus.STANDING_ROOM_ONLY,
        OccupancyStatus.CRUSHED_STANDING_ROOM_ONLY -> Color(0xFFFF7043)
        OccupancyStatus.FULL,
        OccupancyStatus.NOT_ACCEPTING_PASSENGERS -> Color(0xFFE53935)
        null -> MaterialTheme.colorScheme.outlineVariant
    }

    val coachLabel = coach.coachNumber ?: "${coachIndex + 1}"
    val isFront = coachIndex == 0
    val isRear = coachIndex == totalCoaches - 1

    val shape = when {
        isFront && totalCoaches > 1 -> RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 3.dp, bottomEnd = 3.dp)
        isRear && totalCoaches > 1 -> RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp, topEnd = 8.dp, bottomEnd = 8.dp)
        else -> RoundedCornerShape(3.dp)
    }

    val width = if (isCompact) 28.dp else 38.dp
    val height = if (isCompact) 20.dp else 30.dp
    val containerColor = if (isRecommended) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val borderColor = if (isRecommended) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    }

    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(shape)
            .background(containerColor)
            .border(if (isRecommended) 1.5.dp else 1.dp, borderColor, shape)
            .semantics {
                val recSuffix = if (isRecommended) ", recommended for exit" else ""
                contentDescription = "Coach $coachLabel, ${occupancy?.name ?: "occupancy normal"}$recSuffix"
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = coachLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = if (isCompact) 9.sp else 10.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (coach.isWheelchairAccessible == true && !isCompact) {
                    Text(text = "♿", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp))
                }
            }

            // Occupancy dot
            if (occupancy != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(if (isCompact) 4.dp else 5.dp)
                        .clip(CircleShape)
                        .background(occupancyColor)
                )
            }
        }
    }
}
