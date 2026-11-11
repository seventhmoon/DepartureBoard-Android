package com.androidfung.departureboard.ui.feature.ai

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androidfung.departureboard.R
import com.androidfung.departureboard.ai.AiTransitResult
import com.androidfung.departureboard.ai.TransitAiAssistant
import com.androidfung.departureboard.billing.BillingDataSource
import com.androidfung.departureboard.billing.SubscriptionTier
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.repository.TransitRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Intelligent Transit Assistant bottom sheet powered by Gemini / Natural Language Queries.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiTransitSheet(
    onDismissRequest: () -> Unit,
    repository: TransitRepository,
    savedStations: List<Station>,
    modifier: Modifier = Modifier,
    nearestStation: Station? = null,
    initialTriggerSpeech: Boolean = false,
    initialQuery: String? = null,
    autoSubmitInitialQuery: Boolean = true,
    billingRepository: BillingDataSource? = null,
    aiModelType: com.androidfung.departureboard.ai.AiModelType = com.androidfung.departureboard.ai.AiModelType.DEFAULT,
    selectedRegion: com.androidfung.departureboard.data.model.TransitRegion = com.androidfung.departureboard.data.model.TransitRegion.GB,
    onUpgradeClick: () -> Unit = {},
    hasLocationPermission: Boolean = false,
    onRequestLocationPermission: (() -> Unit)? = null,
    onRefreshNearestStation: (suspend () -> Station?)? = null,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val coroutineScope = rememberCoroutineScope()
    val localContext = LocalContext.current
    val resources = LocalResources.current
    val assistant = remember(repository, localContext) { TransitAiAssistant(repository, localContext) }
    val dynamicAiProvider = remember(localContext) {
        (localContext.applicationContext as? com.androidfung.departureboard.DepartureBoardApp)?.container?.dynamicAiAssistantProvider
            ?: com.androidfung.departureboard.ai.DynamicAiAssistantProvider(localContext.applicationContext)
    }
    var isInstallingModule by remember { mutableStateOf(false) }
    var installProgress by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var isFeatureInstalled by remember { mutableStateOf(dynamicAiProvider.isFeatureInstalled) }

    var tts: TextToSpeech? by remember { mutableStateOf(null) }
    var isSpeaking by remember { mutableStateOf(false) }

    androidx.compose.runtime.DisposableEffect(localContext) {
        val speech = TextToSpeech(localContext) { _ -> }
        tts = speech
        onDispose {
            speech.stop()
            speech.shutdown()
        }
    }

    fun speakAnswer(text: String) {
        if (isSpeaking) {
            tts?.stop()
            isSpeaking = false
        } else {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_answer")
            isSpeaking = true
        }
    }

    fun installAiFeature() {
        isInstallingModule = true
        coroutineScope.launch {
            dynamicAiProvider.requestInstall().collect { status ->
                when (status) {
                    is com.androidfung.departureboard.ai.AiFeatureInstallStatus.Downloading -> {
                        installProgress = status.progress
                    }
                    is com.androidfung.departureboard.ai.AiFeatureInstallStatus.Installed -> {
                        isInstallingModule = false
                        isFeatureInstalled = true
                        Toast.makeText(localContext, resources.getString(R.string.ai_toast_engine_installed), Toast.LENGTH_SHORT).show()
                    }
                    is com.androidfung.departureboard.ai.AiFeatureInstallStatus.Failed -> {
                        isInstallingModule = false
                        Toast.makeText(localContext, resources.getString(R.string.ai_toast_install_failed), Toast.LENGTH_SHORT).show()
                    }
                    else -> {}
                }
            }
        }
    }

    var queryText by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }
    var aiResult by remember { mutableStateOf<AiTransitResult?>(null) }

    // Dynamic, location-aware & frequently asked suggestions
    val quickPrompts = remember(nearestStation, savedStations, selectedRegion, resources) {
        val prompts = mutableListOf<String>()
        val currentHour = java.time.LocalTime.now().hour

        // Stationmaster High-Impact Prompts
        prompts.add("🏃 Should I run for the next train?")

        // 1. Time-of-day contextual prompts
        when (currentHour) {
            in 6..10 -> {
                prompts.add(resources.getString(R.string.ai_prompt_morning_commute))
                if (nearestStation != null) {
                    val name = nearestStation.displayName.substringBefore("(").trim()
                    prompts.add(resources.getString(R.string.ai_prompt_next_train_from, name))
                } else {
                    prompts.add(resources.getString(R.string.ai_prompt_next_train_near_me))
                }
                prompts.add("⚠️ Any delays on my lines?")
            }
            in 16..20 -> {
                prompts.add("⚠️ Why is there a delay?")
                prompts.add(resources.getString(R.string.ai_prompt_evening_delays))
                if (nearestStation != null) {
                    val name = nearestStation.displayName.substringBefore("(").trim()
                    prompts.add(resources.getString(R.string.ai_prompt_next_train_from, name))
                }
            }
            in 21..23, in 0..4 -> {
                prompts.add("🌙 Last train home countdown")
                when (selectedRegion) {
                    com.androidfung.departureboard.data.model.TransitRegion.GB -> {
                        prompts.add(resources.getString(R.string.ai_prompt_last_tube))
                        prompts.add(resources.getString(R.string.ai_prompt_night_bus))
                    }
                    else -> prompts.add(resources.getString(R.string.ai_prompt_last_train))
                }
            }
            else -> {
                if (nearestStation != null) {
                    val shortName = nearestStation.displayName.substringBefore("(").trim()
                    if (nearestStation.isBusOnly) {
                        prompts.add(resources.getString(R.string.ai_prompt_next_bus_from, shortName))
                    } else {
                        prompts.add(resources.getString(R.string.ai_prompt_next_train_from, shortName))
                    }
                } else {
                    prompts.add(resources.getString(R.string.ai_prompt_next_departure_near_me))
                }
                prompts.add("📍 Where to stand on platform")
            }
        }

        // 2. Specific saved station prompt if available
        val secondary = savedStations.firstOrNull { it.id != nearestStation?.id }
        if (secondary != null) {
            val shortName = secondary.displayName.substringBefore("(").trim()
            prompts.add(resources.getString(R.string.ai_prompt_next_train_from, shortName))
        }

        // 3. High-frequency transit corridors by region
        when (selectedRegion) {
            com.androidfung.departureboard.data.model.TransitRegion.HK -> {
                prompts.add("MTR service status")
                prompts.add("East Rail Line status")
                prompts.add("Island Line status")
            }
            com.androidfung.departureboard.data.model.TransitRegion.SG -> {
                prompts.add("MRT service disruptions")
                prompts.add("East West Line status")
                prompts.add("Downtown Line status")
            }
            com.androidfung.departureboard.data.model.TransitRegion.JP -> {
                prompts.add("Yamanote Line status")
                prompts.add("Tokyo Metro service status")
                prompts.add("Chuo Line status")
            }
            com.androidfung.departureboard.data.model.TransitRegion.AU -> {
                prompts.add("Sydney Trains service status")
                prompts.add("T1 North Shore & Western Line")
                prompts.add("B-Line bus status")
            }
            com.androidfung.departureboard.data.model.TransitRegion.GB -> {
                prompts.add("Any Tube delays or disruptions?")
                prompts.add("Elizabeth line status")
                prompts.add("Central line vs Elizabeth line")
            }
        }
        prompts.distinct().take(5)
    }

    var isQuotaExceeded by remember { mutableStateOf(false) }

    fun submitQuery(prompt: String) {
        if (prompt.isBlank()) return
        queryText = prompt
        isThinking = true
        isQuotaExceeded = false

        coroutineScope.launch {
            try {
                // Check daily AI quota if billingRepository is provided
                val allowed = billingRepository?.tryConsumeAiQuery() ?: true
                if (!allowed) {
                    isQuotaExceeded = true
                    aiResult = AiTransitResult(
                        answer = resources.getString(R.string.ai_quota_exceeded, SubscriptionTier.FREE_MAX_DAILY_AI_QUERIES)
                    )
                    isThinking = false
                    return@launch
                }

                var effectiveNearest = nearestStation
                val cleanPrompt = prompt.trim().lowercase()
                val isExplicitOrigin = cleanPrompt.contains("from ")
                val isLocationIntent = !isExplicitOrigin && (cleanPrompt.contains("near me") ||
                        cleanPrompt.contains("nearby") ||
                        cleanPrompt.contains("around me") ||
                        cleanPrompt.contains("closest") ||
                        cleanPrompt.contains("nearest") ||
                        cleanPrompt.contains("here") ||
                        cleanPrompt.contains("last tube") ||
                        cleanPrompt.contains("last train") ||
                        cleanPrompt.contains("next tube") ||
                        cleanPrompt.contains("next train") ||
                        cleanPrompt.contains("next bus"))

                if (isLocationIntent) {
                    if (!hasLocationPermission && onRequestLocationPermission != null) {
                        onRequestLocationPermission.invoke()
                    }
                    if (onRefreshNearestStation != null) {
                        val freshNearest = onRefreshNearestStation.invoke()
                        if (freshNearest != null) {
                            effectiveNearest = freshNearest
                        }
                    }
                }

                android.util.Log.d("AiTransitSheet", "submitting query: '$prompt', nearestStation=${effectiveNearest?.name}, model=$aiModelType")
                val res = assistant.answerQuery(prompt, savedStations, effectiveNearest, aiModelType)
                android.util.Log.d("AiTransitSheet", "got answer: ${res.answer}")
                aiResult = res
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("AiTransitSheet", "error answering query", e)
            } finally {
                isThinking = false
            }
        }
    }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                queryText = spokenText
                submitQuery(spokenText)
            }
        }
    }

    val launchSpeechToText = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PROMPT, resources.getString(R.string.ai_speech_prompt))
        }
        try {
            speechRecognizerLauncher.launch(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(localContext, resources.getString(R.string.ai_toast_speech_unavailable), Toast.LENGTH_SHORT).show()
        }
    }

    androidx.compose.runtime.LaunchedEffect(initialTriggerSpeech) {
        if (initialTriggerSpeech) {
            launchSpeechToText()
        }
    }

    androidx.compose.runtime.LaunchedEffect(initialQuery, autoSubmitInitialQuery) {
        if (!initialQuery.isNullOrBlank()) {
            if (autoSubmitInitialQuery) {
                submitQuery(initialQuery)
            } else {
                queryText = initialQuery
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 8.dp)
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
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.ai_sheet_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.ai_sheet_subtitle),
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

            // Dynamic On-Demand AI Module Banner (when Local AI is selected but module is not installed)
            if (aiModelType == com.androidfung.departureboard.ai.AiModelType.LOCAL && !isFeatureInstalled) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = stringResource(R.string.ai_module_title),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.ai_module_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        if (isInstallingModule) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                androidx.compose.material3.LinearProgressIndicator(
                                    progress = { installProgress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = stringResource(R.string.ai_module_downloading, (installProgress * 100).toInt()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        } else {
                            Button(
                                onClick = { installAiFeature() },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary,
                                    contentColor = MaterialTheme.colorScheme.onTertiary
                                )
                            ) {
                                Text(stringResource(R.string.ai_module_download_button))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // AI Answer Bubble
            if (isThinking) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.ai_query_checking),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val result = aiResult
                if (result != null) {
                    val a2uiResponse = result.a2uiResponse
                    if (a2uiResponse != null) {
                        A2UiRenderer(
                            response = a2uiResponse,
                            modifier = Modifier.fillMaxWidth(),
                            onSpeak = { speakAnswer(it) },
                            onActionClick = { action ->
                                if (action is com.androidfung.departureboard.ai.A2UiAction.QueryPrompt) {
                                    queryText = action.text
                                    submitQuery(action.text)
                                }
                            }
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Virtual Stationmaster",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    IconButton(
                                        onClick = { speakAnswer(result.answer) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isSpeaking) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                                            contentDescription = "Read out answer",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = result.answer,
                                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                if (result.matchedDepartures.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    result.matchedDepartures.forEach { dep ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = dep.destinationName,
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "${dep.lineName} • ${dep.platformName}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Text(
                                                text = dep.formattedTimeToArrival,
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (isQuotaExceeded) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onUpgradeClick,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WorkspacePremium,
                                contentDescription = null,
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.ai_upgrade_pro),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Prompt Suggestions
            Text(
                text = stringResource(R.string.ai_suggested_questions),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                quickPrompts.forEach { prompt ->
                    SuggestionChip(
                        onClick = {
                            queryText = prompt
                            submitQuery(prompt)
                        },
                        label = { Text(prompt, fontSize = 12.sp) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Query Input Field
            OutlinedTextField(
                value = queryText,
                onValueChange = { queryText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        stringResource(R.string.ai_input_placeholder),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        // Voice Prompt (Speech-to-Text) Button
                        IconButton(
                            onClick = launchSpeechToText,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = stringResource(R.string.cd_speak_prompt),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Submit Query Button
                        IconButton(
                            onClick = { submitQuery(queryText) },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = stringResource(R.string.cd_send_query),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Send
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSend = { submitQuery(queryText) }
                ),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
