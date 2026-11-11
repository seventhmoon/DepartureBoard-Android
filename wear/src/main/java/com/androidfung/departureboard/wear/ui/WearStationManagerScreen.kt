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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
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
import com.androidfung.departureboard.core.wearcontract.CompactTransitFormatter
import com.androidfung.departureboard.wear.model.WearSnapshotFormatter
import com.androidfung.departureboard.wear.model.WearStationBoard

@Composable
internal fun WearStationManagerScreen(
    activeBoards: List<WearStationBoard>,
    onAddStation: (WearStationBoard) -> Unit,
    onRemoveStation: (String) -> Unit,
    onSelectStation: (Int) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }
    var searchQuery by remember { mutableStateOf("") }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            if (spoken.isNotBlank()) {
                searchQuery = spoken
            }
        }
    }

    val catalog = remember { WearSnapshotFormatter.stationCatalog() }
    val availableToAdd = remember(activeBoards, catalog, searchQuery) {
        val q = searchQuery.trim().lowercase()
        catalog.filter { candidate ->
            activeBoards.none { it.stationId.equals(candidate.stationId, ignoreCase = true) } &&
                (q.isBlank() ||
                    candidate.stationName.lowercase().contains(q) ||
                    candidate.stationCrs.lowercase().contains(q))
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
                        text = "← Back to Board",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = Color(0xFF81D4FA),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }

            // Station Search (Text + Voice Input)
            item(key = "station_search_bar") {
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
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = Color(0xFF81D4FA),
                            modifier = Modifier.size(13.dp)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    searchFocusRequester.requestFocus()
                                    keyboardController?.show()
                                }
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search / add station…",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = Color(0xFF90CAF9),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                cursorBrush = SolidColor(Color(0xFF4FC3F7)),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = { keyboardController?.hide() }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(searchFocusRequester)
                            )
                        }

                        if (searchQuery.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF263238))
                                    .clickable { searchQuery = "" },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear search",
                                    tint = Color.White,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }

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
                                                "Speak station name…"
                                            )
                                        }
                                        speechLauncher.launch(voiceIntent)
                                    } catch (_: Exception) {
                                        searchFocusRequester.requestFocus()
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
                                contentDescription = "Speak station name",
                                tint = Color(0xFF81D4FA),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }

            // Custom Phone-Resolved Station Add option when user types/speaks any station name
            if (searchQuery.trim().length >= 2 && availableToAdd.none { it.stationName.equals(searchQuery.trim(), ignoreCase = true) }) {
                item(key = "add_custom_phone_station") {
                    val customName = searchQuery.trim()
                    val customCrs = CompactTransitFormatter.deriveStationShortCode("search:$customName", customName)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF102A3B),
                        border = BorderStroke(1.dp, Color(0xFF4FC3F7)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val customBoard = WearStationBoard(
                                    stationId = "search:$customName",
                                    stationName = customName,
                                    stationCrs = customCrs,
                                    lastUpdatedLabel = "📱 Syncing from Phone…",
                                    commuteLabel = "★ Pinned",
                                    departures = emptyList()
                                )
                                onAddStation(customBoard)
                                searchQuery = ""
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "+ Add \"$customName\" via Phone",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = Color(0xFF81D4FA),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                tint = Color(0xFF81D4FA),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            item(key = "active_header") {
                Text(
                    text = "Your Stations (${activeBoards.size})",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp
                    ),
                    color = Color.White,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            items(
                items = activeBoards.mapIndexed { idx, board -> idx to board },
                key = { (_, board) -> "active_${board.stationId}" }
            ) { (idx, board) ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF14171C),
                    border = BorderStroke(1.dp, Color(0xFF262B33)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectStation(idx) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${board.stationName} (${board.stationCrs})",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            board.commuteLabel?.let { tag ->
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = Color(0xFF90A4AE)
                                )
                            }
                        }
                        if (activeBoards.size > 1) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4A1C1C))
                                    .clickable { onRemoveStation(board.stationId) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Remove ${board.stationName}",
                                    tint = Color(0xFFFF8A80),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (availableToAdd.isNotEmpty()) {
                item(key = "add_header") {
                    Text(
                        text = "+ Add Concourse Hub",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp
                        ),
                        color = Color(0xFF81D4FA),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                items(
                    items = availableToAdd,
                    key = { "add_${it.stationId}" }
                ) { candidate ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF101D26),
                        border = BorderStroke(1.dp, Color(0xFF0277BD).copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onAddStation(candidate) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${candidate.stationName} (${candidate.stationCrs})",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                ),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0277BD)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Add ${candidate.stationName}",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
