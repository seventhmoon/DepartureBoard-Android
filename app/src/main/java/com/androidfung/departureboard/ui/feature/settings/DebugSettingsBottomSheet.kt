package com.androidfung.departureboard.ui.feature.settings

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
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LayersClear
import androidx.compose.material.icons.rounded.Public
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
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.androidfung.departureboard.R
import com.androidfung.departureboard.ai.AiModelType
import com.androidfung.departureboard.billing.BillingDataSource
import com.androidfung.departureboard.billing.SubscriptionTier
import com.androidfung.departureboard.ui.theme.DebugAccentPink
import com.androidfung.departureboard.ui.theme.FeatureInstalledGreen
import com.androidfung.departureboard.ui.theme.TierMaxPurple
import com.androidfung.departureboard.ui.theme.TierProAmber
import com.androidfung.departureboard.ui.theme.dimensions
import com.androidfung.departureboard.util.LocaleHelper
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
    onResetStations: () -> Unit,
    modifier: Modifier = Modifier,
    onTogglePro: () -> Unit = {},
    userPlan: com.androidfung.departureboard.billing.UserPlan = if (isPro) com.androidfung.departureboard.billing.UserPlan.PRO else com.androidfung.departureboard.billing.UserPlan.FREE,
    onSelectPlan: (com.androidfung.departureboard.billing.UserPlan) -> Unit = { onTogglePro() },
    selectedAiModel: AiModelType = AiModelType.DEFAULT,
    onSelectAiModel: (AiModelType) -> Unit = {},
    selectedRegion: com.androidfung.departureboard.data.model.TransitRegion = com.androidfung.departureboard.data.model.TransitRegion.GB,
    onSelectRegion: (com.androidfung.departureboard.data.model.TransitRegion) -> Unit = {},
    onOpenRegionPicker: (() -> Unit)? = null,
    isHkRegionEnabled: Boolean = false,
    onToggleHkRegionEnabled: (Boolean) -> Unit = {},
    isBilingualEnabled: Boolean = true,
    onToggleBilingualEnabled: (Boolean) -> Unit = {},
    onTriggerInAppReview: () -> Unit = {},
    onResetInAppReviewData: () -> Unit = {},
    onOpenDeskClock: (() -> Unit)? = null,
    onOpenLabs: (() -> Unit)? = null,
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
        onOpenRegionPicker = onOpenRegionPicker,
        isHkRegionEnabled = isHkRegionEnabled,
        onToggleHkRegionEnabled = onToggleHkRegionEnabled,
        isBilingualEnabled = isBilingualEnabled,
        onToggleBilingualEnabled = onToggleBilingualEnabled,
        onResetStations = onResetStations,
        onTriggerInAppReview = onTriggerInAppReview,
        onResetInAppReviewData = onResetInAppReviewData,
        onOpenDeskClock = onOpenDeskClock,
        onOpenLabs = onOpenLabs,
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
    modifier: Modifier = Modifier,
    isBilingualEnabled: Boolean = true,
    onToggleBilingualEnabled: (Boolean) -> Unit = {},
    onOpenRegionPicker: (() -> Unit)? = null,
    onOpenDeskClock: (() -> Unit)? = null,
    onOpenLabs: (() -> Unit)? = null
) {
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
            onOpenRegionPicker = onOpenRegionPicker,
            isHkRegionEnabled = isHkRegionEnabled,
            onToggleHkRegionEnabled = onToggleHkRegionEnabled,
            isBilingualEnabled = isBilingualEnabled,
            onToggleBilingualEnabled = onToggleBilingualEnabled,
            remainingAiQueries = remainingAiQueries,
            onResetStations = onResetStations,
            onTriggerInAppReview = onTriggerInAppReview,
            onResetInAppReviewData = onResetInAppReviewData,
            onOpenDeskClock = onOpenDeskClock,
            onOpenLabs = onOpenLabs
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
    modifier: Modifier = Modifier,
    isBilingualEnabled: Boolean = true,
    onToggleBilingualEnabled: (Boolean) -> Unit = {},
    onOpenRegionPicker: (() -> Unit)? = null,
    onOpenDeskClock: (() -> Unit)? = null,
    onOpenLabs: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val dimensions = MaterialTheme.dimensions

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = dimensions.paddingScreenEdge, vertical = dimensions.paddingMedium)
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
                            .size(dimensions.iconContainerSize)
                            .clip(CircleShape)
                            .background(DebugAccentPink.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BugReport,
                            contentDescription = null,
                            tint = DebugAccentPink,
                            modifier = Modifier.size(dimensions.iconMedium)
                        )
                    }
                    Spacer(modifier = Modifier.width(dimensions.paddingLarge))
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
                        contentDescription = stringResource(R.string.cd_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(dimensions.cornerRadiusExtraLarge))

            // Section 1: Monetization / Tier Switch
            Text(
                text = "SUBSCRIPTION PLAN",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(dimensions.paddingMedium))

            Card(
                shape = RoundedCornerShape(dimensions.cardRadius),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    com.androidfung.departureboard.billing.UserPlan.entries.forEachIndexed { index, plan ->
                        val isSelected = userPlan == plan
                        val planColor = when (plan) {
                            com.androidfung.departureboard.billing.UserPlan.FREE -> MaterialTheme.colorScheme.onSurfaceVariant
                            com.androidfung.departureboard.billing.UserPlan.PRO -> TierProAmber
                            com.androidfung.departureboard.billing.UserPlan.MAX -> TierMaxPurple
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
                                .padding(horizontal = dimensions.paddingExtraLarge, vertical = dimensions.paddingLarge),
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
                                    modifier = Modifier.size(dimensions.iconStandard)
                                )
                                Spacer(modifier = Modifier.width(dimensions.paddingLarge))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${plan.name} Plan",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (plan == com.androidfung.departureboard.billing.UserPlan.MAX) {
                                            Spacer(modifier = Modifier.width(dimensions.paddingMedium - dimensions.paddingTiny))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(dimensions.paddingSmall))
                                                    .background(TierMaxPurple.copy(alpha = 0.2f))
                                                    .padding(horizontal = dimensions.paddingSmall, vertical = dimensions.paddingTiny)
                                            ) {
                                                Text(
                                                    text = "DEBUG ONLY",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = TierMaxPurple
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
                                modifier = Modifier.padding(horizontal = dimensions.paddingExtraLarge)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(dimensions.cornerRadiusExtraLarge))

            // Section 2: AI Model Selection (Local vs Cloud vs Logic Fallback)
            Text(
                text = "AI ENGINE SELECTION",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(dimensions.paddingMedium))

            Card(
                shape = RoundedCornerShape(dimensions.cardRadius),
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
                                .padding(horizontal = dimensions.paddingExtraLarge, vertical = dimensions.paddingLarge),
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
                                    modifier = Modifier.size(dimensions.iconStandard)
                                )
                                Spacer(modifier = Modifier.width(dimensions.paddingLarge))
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

            // Section 3: Region & Language Settings
            Text(
                text = stringResource(R.string.settings_section_region_language),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(dimensions.paddingMedium))

            Card(
                shape = RoundedCornerShape(dimensions.cardRadius),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(dimensions.paddingExtraLarge)
                ) {
                    // Region / Transit Network
                    if (onOpenRegionPicker != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDismissRequest()
                                    onOpenRegionPicker()
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Public,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(dimensions.iconStandard)
                                )
                                Spacer(modifier = Modifier.width(dimensions.paddingLarge))
                                Column {
                                    Text(
                                        text = stringResource(R.string.switch_region_title),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${selectedRegion.flagEmoji} ${stringResource(selectedRegion.displayNameRes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(dimensions.cornerRadiusExtraLarge)
                            )
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            modifier = Modifier.padding(vertical = dimensions.paddingExtraLarge)
                        )
                    }

                    // App Language Selector
                    var currentLanguageTag = LocaleHelper.getAppLocale()?.toLanguageTag().orEmpty()
                    var showLanguagePicker by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLanguagePicker = true }
                            .padding(vertical = dimensions.paddingLarge),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(dimensions.iconStandard)
                        )
                        Spacer(modifier = Modifier.width(dimensions.paddingLarge))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_language_title),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val currentLangName = SUPPORTED_LANGUAGES.find {
                                if (it.tag.isEmpty()) currentLanguageTag.isEmpty()
                                else currentLanguageTag.startsWith(it.tag, ignoreCase = true)
                            }?.nativeName ?: stringResource(R.string.language_system)
                            Text(
                                text = currentLangName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(dimensions.cornerRadiusExtraLarge)
                        )
                    }

                    if (showLanguagePicker) {
                        @OptIn(ExperimentalMaterial3Api::class)
                        LanguagePickerBottomSheet(
                            currentLanguageTag = currentLanguageTag,
                            onSelectLanguage = { tag ->
                                showLanguagePicker = false
                                LocaleHelper.setAppLanguage(tag.ifEmpty { null })
                            },
                            onDismissRequest = { showLanguagePicker = false }
                        )
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        modifier = Modifier.padding(vertical = dimensions.paddingExtraLarge)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = stringResource(R.string.settings_bilingual_title),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.settings_bilingual_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isBilingualEnabled,
                            onCheckedChange = onToggleBilingualEnabled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(dimensions.cornerRadiusExtraLarge))

            // Section 4: Quick Action Triggers
            Text(
                text = "TESTING ACTIONS",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(dimensions.paddingMedium))

            Card(
                shape = RoundedCornerShape(dimensions.cardRadius),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (onOpenDeskClock != null) {
                        DebugActionItem(
                            icon = Icons.Rounded.DesktopWindows,
                            title = stringResource(R.string.desk_clock_menu_item),
                            subtitle = "Full-screen Split-Flap / LED display & Tabletop mode",
                            onClick = {
                                onDismissRequest()
                                onOpenDeskClock()
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    }

                    if (onOpenLabs != null) {
                        DebugActionItem(
                            icon = Icons.Rounded.Extension,
                            title = "EkiBoard Labs",
                            subtitle = "Experimental transit features & previews",
                            onClick = {
                                onDismissRequest()
                                onOpenLabs()
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    }

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

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    DebugActionItem(
                        icon = Icons.Rounded.LayersClear,
                        title = "Clear Offline HTTP Cache",
                        subtitle = "Deletes cached TfL & transit API network responses",
                        onClick = {
                            val cacheDir = context.cacheDir
                            val okHttpCacheDir = java.io.File(cacheDir, "tfl_http_cache")
                            val deleted = if (okHttpCacheDir.exists()) {
                                okHttpCacheDir.deleteRecursively()
                            } else {
                                true
                            }
                            if (deleted) {
                                Toast.makeText(context, "Offline HTTP cache cleared", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to clear cache", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(dimensions.cornerRadiusExtraLarge))

            // Section 5: Play Dynamic Feature Modules
            Text(
                text = "DYNAMIC FEATURE MODULES",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(dimensions.paddingMedium))

            val dynamicAiProvider = remember { com.androidfung.departureboard.ai.DynamicAiAssistantProvider(context) }
            var isDynamicAiInstalled by remember { androidx.compose.runtime.mutableStateOf(dynamicAiProvider.isFeatureInstalled) }
            var installStatusText by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
            var isInstallingModule by remember { androidx.compose.runtime.mutableStateOf(false) }

            Card(
                shape = RoundedCornerShape(dimensions.cardRadius),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dimensions.paddingExtraLarge, vertical = dimensions.paddingLarge),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Extension,
                                contentDescription = null,
                                tint = if (isDynamicAiInstalled) FeatureInstalledGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(dimensions.iconStandard)
                            )
                            Spacer(modifier = Modifier.width(dimensions.paddingLarge))
                            Column {
                                Text(
                                    text = ":features:ai_assistant",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isDynamicAiInstalled) {
                                        "Installed (On-Device LLM & LiteRT runtime ready)"
                                    } else {
                                        installStatusText ?: "Not installed (On-demand Play feature)"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isDynamicAiInstalled) FeatureInstalledGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (!isDynamicAiInstalled) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                        DebugActionItem(
                            icon = Icons.Rounded.Download,
                            title = if (isInstallingModule) "Downloading module..." else "Trigger Feature Delivery Install",
                            subtitle = "Requests SplitInstallManager to download AI feature",
                            onClick = {
                                if (!isInstallingModule) {
                                    isInstallingModule = true
                                    installStatusText = "Initiating SplitInstall request..."
                                    coroutineScope.launch {
                                        dynamicAiProvider.requestInstall().collect { status ->
                                            when (status) {
                                                is com.androidfung.departureboard.ai.AiFeatureInstallStatus.Downloading -> {
                                                    val pct = (status.progress * 100).toInt()
                                                    installStatusText = "Downloading: $pct%"
                                                }
                                                is com.androidfung.departureboard.ai.AiFeatureInstallStatus.Installed -> {
                                                    isDynamicAiInstalled = true
                                                    isInstallingModule = false
                                                    installStatusText = null
                                                    Toast.makeText(context, "AI Feature Module installed successfully", Toast.LENGTH_SHORT).show()
                                                }
                                                is com.androidfung.departureboard.ai.AiFeatureInstallStatus.Failed -> {
                                                    isInstallingModule = false
                                                    installStatusText = "Install failed: code ${status.errorCode}"
                                                    Toast.makeText(context, "Install failed (code ${status.errorCode})", Toast.LENGTH_SHORT).show()
                                                }
                                                is com.androidfung.departureboard.ai.AiFeatureInstallStatus.NotInstalled -> {
                                                    isInstallingModule = false
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(dimensions.paddingScreenEdge))
        }
}

@Composable
private fun DebugActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val dimensions = MaterialTheme.dimensions
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = dimensions.paddingExtraLarge, vertical = dimensions.paddingLarge),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(dimensions.iconMedium)
        )
        Spacer(modifier = Modifier.width(dimensions.paddingLarge))
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
