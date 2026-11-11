package com.androidfung.departureboard.wear.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androidfung.departureboard.wear.model.WearAiConcourseResponse
import com.androidfung.departureboard.wear.model.WearAiPromptType
import com.androidfung.departureboard.wear.model.WearStationBoard

@Composable
internal fun WearAskAiScreen(
    station: WearStationBoard,
    aiResponse: WearAiConcourseResponse?,
    onSelectPrompt: (WearAiPromptType, String) -> Unit,
    onOpenAiOnPhone: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val inputFocusRequester = remember { FocusRequester() }
    var textQuery by remember { mutableStateOf("") }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            if (spoken.isNotBlank()) {
                textQuery = spoken
                onSelectPrompt(WearAiPromptType.VOICE_QUERY, spoken)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item(key = "back_button") {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF1E2229),
                    border = BorderStroke(1.dp, Color(0xFF4FC3F7)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onBack)
                ) {
                    Text(
                        text = "← ${station.stationCrs} Board",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = Color(0xFF81D4FA),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }

            // Glanceable 2-Line AI Concourse Answer Card
            aiResponse?.let { resp ->
                item(key = "ai_response_card") {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF1C1429),
                        border = BorderStroke(1.dp, Color(0xFFB388FF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (resp.isFromPhone) "✨ PHONE AI TRIAGE" else "✨ CONCOURSE TRIAGE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 9.sp
                                ),
                                color = Color(0xFFB388FF)
                            )
                            Text(
                                text = resp.headline,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp
                                ),
                                color = Color.White
                            )
                            Text(
                                text = resp.summary,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                color = Color(0xFFE0E0E0)
                            )
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color(0xFF311B92),
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .clip(RoundedCornerShape(50))
                                    .clickable { onOpenAiOnPhone(resp.rawQuery) }
                            ) {
                                Text(
                                    text = "📱 Continue in Phone AI",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp
                                    ),
                                    color = Color(0xFFD1C4E9),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Combined Text + Voice Input Pill (opens Wear OS Gboard swipe/voice keyboard or speech recognizer)
            item(key = "text_and_voice_input") {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF0D2538),
                    border = BorderStroke(1.dp, Color(0xFF4FC3F7)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    inputFocusRequester.requestFocus()
                                    keyboardController?.show()
                                }
                        ) {
                            if (textQuery.isEmpty()) {
                                Text(
                                    text = "⌨️ Type or ask AI…",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = Color(0xFF90CAF9),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = textQuery,
                                onValueChange = { textQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                cursorBrush = SolidColor(Color(0xFF4FC3F7)),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(
                                    onSend = {
                                        val q = textQuery.trim()
                                        if (q.isNotBlank()) {
                                            keyboardController?.hide()
                                            onSelectPrompt(WearAiPromptType.VOICE_QUERY, q)
                                        }
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(inputFocusRequester)
                            )
                        }

                        if (textQuery.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0277BD))
                                    .clickable {
                                        val q = textQuery.trim()
                                        if (q.isNotBlank()) {
                                            keyboardController?.hide()
                                            onSelectPrompt(WearAiPromptType.VOICE_QUERY, q)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = "Send AI query",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }

                        // Voice Dictation Button (falls back to Wear Gboard voice/keyboard if standalone speech service is absent)
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E3A5F))
                                .clickable {
                                    try {
                                        val voiceIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                            putExtra(
                                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                            )
                                            putExtra(
                                                RecognizerIntent.EXTRA_PROMPT,
                                                "Ask about ${station.compactStationName}…"
                                            )
                                        }
                                        speechLauncher.launch(voiceIntent)
                                    } catch (_: Exception) {
                                        inputFocusRequester.requestFocus()
                                        keyboardController?.show()
                                        Toast.makeText(
                                            context,
                                            "Use keyboard mic or swipe to type",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = "Speak question",
                                tint = Color(0xFF81D4FA),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }

            // 1-Tap Concourse Triage Prompts
            val prompts = listOf(
                WearAiPromptType.FASTEST_TRAIN to "⚡ Fastest / Best Train",
                WearAiPromptType.DELAYS_AND_BACKLOG to "⚠️ Delays & Delay Repay",
                WearAiPromptType.LAST_TRAIN to "🌙 Last Train Tonight"
            )
            items(
                items = prompts,
                key = { it.first.name }
            ) { (promptType, label) ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF14171C),
                    border = BorderStroke(1.dp, Color(0xFF262B33)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectPrompt(promptType, "") }
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        ),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
