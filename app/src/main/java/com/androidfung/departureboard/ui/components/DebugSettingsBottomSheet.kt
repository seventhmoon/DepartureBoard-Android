package com.androidfung.departureboard.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.StarRate
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.androidfung.departureboard.ai.AiModelType
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androidfung.departureboard.billing.BillingDataSource
import com.androidfung.departureboard.billing.SubscriptionTier
import com.androidfung.departureboard.widget.WidgetUpdateWorker
import kotlinx.coroutines.launch

/**
 * Developer Debug Settings Sheet for quick plan toggling, quota resets,
 * widget test triggers, and state resets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugSettingsBottomSheet(
    onDismissRequest: () -> Unit,
    billingRepository: BillingDataSource,
    isPro: Boolean,
    onTogglePro: () -> Unit = {},
    onResetStations: () -> Unit,
    modifier: Modifier = Modifier,
    userPlan: com.androidfung.departureboard.billing.UserPlan = if (isPro) com.androidfung.departureboard.billing.UserPlan.PRO else com.androidfung.departureboard.billing.UserPlan.FREE,
    onSelectPlan: (com.androidfung.departureboard.billing.UserPlan) -> Unit = { onTogglePro() },
    selectedAiModel: AiModelType = AiModelType.DEFAULT,
    onSelectAiModel: (AiModelType) -> Unit = {},
    selectedRegion: com.androidfung.departureboard.data.model.TransitRegion = com.androidfung.departureboard.data.model.TransitRegion.GB,
    onSelectRegion: (com.androidfung.departureboard.data.model.TransitRegion) -> Unit = {},
    isHkRegionEnabled: Boolean = false,
    onToggleHkRegionEnabled: (Boolean) -> Unit = {},
    onTriggerInAppReview: () -> Unit = {},
    onResetInAppReviewData: () -> Unit = {},
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    DebugSettingsContent(
        onDismissRequest = onDismissRequest,
        billingRepository = billingRepository,
        userPlan = userPlan,
        onSelectPlan = onSelectPlan,
        selectedAiModel = selectedAiModel,
        onSelectAiModel = onSelectAiModel,
        selectedRegion = selectedRegion,
        onSelectRegion = onSelectRegion,
        isHkRegionEnabled = isHkRegionEnabled,
        onToggleHkRegionEnabled = onToggleHkRegionEnabled,
        onResetStations = onResetStations,
        onTriggerInAppReview = onTriggerInAppReview,
        onResetInAppReviewData = onResetInAppReviewData,
        sheetState = sheetState,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugSettingsContent(
    onDismissRequest: () -> Unit,
    billingRepository: BillingDataSource,
    userPlan: com.androidfung.departureboard.billing.UserPlan,
    onSelectPlan: (com.androidfung.departureboard.billing.UserPlan) -> Unit,
    selectedAiModel: AiModelType,
    onSelectAiModel: (AiModelType) -> Unit,
    selectedRegion: com.androidfung.departureboard.data.model.TransitRegion,
    onSelectRegion: (com.androidfung.departureboard.data.model.TransitRegion) -> Unit,
    isHkRegionEnabled: Boolean,
    onToggleHkRegionEnabled: (Boolean) -> Unit,
    onResetStations: () -> Unit,
    onTriggerInAppReview: () -> Unit,
    onResetInAppReviewData: () -> Unit,
    sheetState: SheetState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var remainingAiQueries by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        remainingAiQueries = billingRepository.getRemainingAiQueries()
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        DebugSettingsBody(
            onDismissRequest = onDismissRequest,
            userPlan = userPlan,
            onSelectPlan = onSelectPlan,
            selectedAiModel = selectedAiModel,
            onSelectAiModel = onSelectAiModel,
            selectedRegion = selectedRegion,
            onSelectRegion = onSelectRegion,
            isHkRegionEnabled = isHkRegionEnabled,
            onToggleHkRegionEnabled = onToggleHkRegionEnabled,
            remainingAiQueries = remainingAiQueries,
            onResetStations = onResetStations,
            onTriggerInAppReview = onTriggerInAppReview,
            onResetInAppReviewData = onResetInAppReviewData
        )
    }
}

@Composable
fun DebugSettingsBody(
    onDismissRequest: () -> Unit,
    userPlan: com.androidfung.departureboard.billing.UserPlan,
    onSelectPlan: (com.androidfung.departureboard.billing.UserPlan) -> Unit,
    selectedAiModel: AiModelType,
    onSelectAiModel: (AiModelType) -> Unit,
    selectedRegion: com.androidfung.departureboard.data.model.TransitRegion,
    onSelectRegion: (com.androidfung.departureboard.data.model.TransitRegion) -> Unit,
    isHkRegionEnabled: Boolean,
    onToggleHkRegionEnabled: (Boolean) -> Unit,
    remainingAiQueries: Int,
    onResetStations: () -> Unit,
    onTriggerInAppReview: () -> Unit,
    onResetInAppReviewData: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE91E63).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BugReport,
                            contentDescription = null,
                            tint = Color(0xFFE91E63),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Debug Settings",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Developer testing controls",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 1: Monetization / Tier Switch
            Text(
                text = "SUBSCRIPTION PLAN",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    com.androidfung.departureboard.billing.UserPlan.entries.forEachIndexed { index, plan ->
                        val isSelected = userPlan == plan
                        val planColor = when (plan) {
                            com.androidfung.departureboard.billing.UserPlan.FREE -> MaterialTheme.colorScheme.onSurfaceVariant
                            com.androidfung.departureboard.billing.UserPlan.PRO -> Color(0xFFFFB300)
                            com.androidfung.departureboard.billing.UserPlan.MAX -> Color(0xFF9C27B0)
                        }
                        val desc = when (plan) {
                            com.androidfung.departureboard.billing.UserPlan.FREE ->
                                "Max ${SubscriptionTier.FREE_MAX_STATIONS} stations, ${SubscriptionTier.FREE_MAX_WIDGETS} widgets, ${SubscriptionTier.FREE_MAX_DAILY_AI_QUERIES} AI/day"
                            com.androidfung.departureboard.billing.UserPlan.PRO ->
                                "Max ${SubscriptionTier.PRO_MAX_STATIONS} stations, ${SubscriptionTier.PRO_MAX_WIDGETS} widgets, ${SubscriptionTier.PRO_MAX_DAILY_AI_QUERIES} AI/day"
                            com.androidfung.departureboard.billing.UserPlan.MAX ->
                                "Max ${SubscriptionTier.MAX_MAX_STATIONS} stations, ${SubscriptionTier.MAX_MAX_WIDGETS} widgets, Unlimited AI (Debug only)"
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectPlan(plan) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WorkspacePremium,
                                    contentDescription = null,
                                    tint = if (isSelected) planColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${plan.name} Plan",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (plan == com.androidfung.departureboard.billing.UserPlan.MAX) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0xFF9C27B0).copy(alpha = 0.2f))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "DEBUG ONLY",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = Color(0xFF9C27B0)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            RadioButton(
                                selected = isSelected,
                                onClick = { onSelectPlan(plan) }
                            )
                        }

                        if (index < com.androidfung.departureboard.billing.UserPlan.entries.size - 1) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 2: AI Model Selection (Local vs Cloud vs Logic Fallback)
            Text(
                text = "AI ENGINE SELECTION",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    AiModelType.entries.forEachIndexed { index, model ->
                        val modelIcon = when (model) {
                            AiModelType.DEFAULT -> Icons.Rounded.AutoAwesome
                            AiModelType.LOCAL -> Icons.Rounded.SmartToy
                            AiModelType.CLOUD -> Icons.Rounded.Cloud
                            AiModelType.LOGIC_FALLBACK -> Icons.Rounded.Bolt
                        }
                        val isSelected = selectedAiModel == model

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectAiModel(model) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = modelIcon,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = model.title,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = model.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            RadioButton(
                                selected = isSelected,
                                onClick = { onSelectAiModel(model) }
                            )
                        }
                        if (index < AiModelType.entries.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 3: Multi-Region Testing
            Text(
                text = "REGION TESTING (EXPERIMENTAL)",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable Hong Kong (Experimental)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Unlock MTR and KMB/Citybus departures",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isHkRegionEnabled,
                            onCheckedChange = { onToggleHkRegionEnabled(it) }
                        )
                    }

                    if (isHkRegionEnabled) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                        com.androidfung.departureboard.data.model.TransitRegion.entries.forEachIndexed { index, region ->
                            val isSelected = selectedRegion == region
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectRegion(region) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onSelectRegion(region) },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = region.flagEmoji,
                                        fontSize = 20.sp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "${androidx.compose.ui.res.stringResource(region.displayNameRes)} (${region.code})",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            ),
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = region.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onSelectRegion(region) }
                                )
                            }
                            if (index < com.androidfung.departureboard.data.model.TransitRegion.entries.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 4: Quick Action Triggers
            Text(
                text = "TESTING ACTIONS",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    DebugActionItem(
                        icon = Icons.Rounded.RestartAlt,
                        title = "Reset Saved Stations",
                        subtitle = "Restores 6 popular London stations",
                        onClick = {
                            onResetStations()
                            Toast.makeText(context, "Saved stations reset to defaults", Toast.LENGTH_SHORT).show()
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    DebugActionItem(
                        icon = Icons.Rounded.Widgets,
                        title = "Force Widget Update",
                        subtitle = "Triggers Glance WidgetUpdateWorker now",
                        onClick = {
                            WidgetUpdateWorker.enqueuePeriodicUpdate(context)
                            Toast.makeText(context, "Enqueued widget update work", Toast.LENGTH_SHORT).show()
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    DebugActionItem(
                        icon = Icons.Rounded.StarRate,
                        title = "Test In-App Review Flow",
                        subtitle = "Launches Google Play In-App Review dialog",
                        onClick = onTriggerInAppReview
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    DebugActionItem(
                        icon = Icons.Rounded.RestartAlt,
                        title = "Reset In-App Review Tracker",
                        subtitle = "Clears first launch, open count & cooldown",
                        onClick = onResetInAppReviewData
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
}

@Composable
private fun DebugActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
