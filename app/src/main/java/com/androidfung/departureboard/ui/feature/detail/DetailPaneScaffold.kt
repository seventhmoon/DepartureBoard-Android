package com.androidfung.departureboard.ui.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.androidfung.departureboard.R

/**
 * Circular detail header action button with an accessible 48dp touch target around a 36dp circle.
 */
@Composable
fun DetailHeaderIconButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconSize: Dp = 19.dp
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(containerColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

/**
 * Reusable detail pane scaffold that standardizes:
 * - Surface container highest -> surface gradient background.
 * - Back button rendering respecting [LocalBackButtonVisibility] alongside header actions.
 * - Standard header layout with leading icon/badge and title/subtitle area.
 * - Consistent insets and padding across detail panes (StationDetailScreen, TrainJourneyScreen, etc.).
 */
@Composable
fun DetailPaneScaffold(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
    headerLeading: (@Composable () -> Unit)? = null,
    headerTitle: (@Composable ColumnScope.() -> Unit)? = null,
    headerActions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.(PaddingValues) -> Unit
) {
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
            MaterialTheme.colorScheme.surface
        )
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent
    ) { innerPadding ->
        val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
        val bottomInset = innerPadding.calculateBottomPadding()
        val contentBottomPadding = contentPadding.calculateBottomPadding()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(
                    top = innerPadding.calculateTopPadding() + contentPadding.calculateTopPadding(),
                    start = innerPadding.calculateLeftPadding(layoutDirection) + contentPadding.calculateLeftPadding(layoutDirection),
                    end = innerPadding.calculateRightPadding(layoutDirection) + contentPadding.calculateRightPadding(layoutDirection)
                )
        ) {
            val showBackButton = LocalBackButtonVisibility.current

            if (showBackButton) {
                // Phone / single-pane: Navigation bar on top (Back on left, Actions on right)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DetailHeaderIconButton(
                        onClick = onBack,
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back)
                    )

                    if (headerActions != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            headerActions()
                        }
                    }
                }

                // Full-width title row below navigation bar on phones
                if (headerLeading != null || headerTitle != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (headerLeading != null) {
                            headerLeading()
                            if (headerTitle != null) {
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                        }
                        if (headerTitle != null) {
                            Column(modifier = Modifier.weight(1f)) {
                                headerTitle()
                            }
                        }
                    }
                }
            } else {
                // Two-pane / Three-pane (Foldable/Tablet): Unified single row with Leading + Title on left, Actions on right!
                if (headerLeading != null || headerTitle != null || headerActions != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (headerLeading != null) {
                                headerLeading()
                                if (headerTitle != null) {
                                    Spacer(modifier = Modifier.width(12.dp))
                                }
                            }
                            if (headerTitle != null) {
                                Column(modifier = Modifier.weight(1f)) {
                                    headerTitle()
                                }
                            }
                        }

                        if (headerActions != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                headerActions()
                            }
                        }
                    }
                }
            }

            content(PaddingValues(bottom = bottomInset + contentBottomPadding))
        }
    }
}
