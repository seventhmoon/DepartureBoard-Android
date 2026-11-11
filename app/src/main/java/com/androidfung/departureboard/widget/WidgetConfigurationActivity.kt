package com.androidfung.departureboard.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.androidfung.departureboard.R
import com.androidfung.departureboard.appContainer
import com.androidfung.departureboard.billing.BillingDataSource
import com.androidfung.departureboard.billing.BillingRepository
import com.androidfung.departureboard.billing.SubscriptionTier
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.gb.GbDefaultStations
import com.androidfung.departureboard.ui.feature.paywall.PaywallBottomSheet
import com.androidfung.departureboard.ui.theme.DepartureBoardTheme
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WidgetConfigurationActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Set the result to CANCELED. This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)

        // Find the widget id from the intent.
        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val billingRepo = BillingRepository.getInstance(applicationContext)

        setContent {
            DepartureBoardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isFinalizing) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .safeDrawingPadding(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator(strokeWidth = 3.dp)
                                Text(
                                    text = stringResource(R.string.widget_config_setting_up),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        WidgetConfigurationContent(
                            billingRepository = billingRepo,
                            onStationAndLineSelected = { station, lineBadge, direction ->
                                selectStationAndFinish(station, lineBadge, direction)
                            },
                            onCancel = {
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }

    private var isFinalizing by mutableStateOf(false)

    private fun selectStationAndFinish(
        station: Station,
        lineBadge: com.androidfung.departureboard.data.model.LineBadgeInfo?,
        direction: String?
    ) {
        isFinalizing = true

        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // Persist keyed to this appWidgetId on Dispatchers.IO before Glance update & finish()
            val sp = applicationContext.getSharedPreferences("widget_config", MODE_PRIVATE)
            sp.edit(commit = true) {
                putString("station_id_$appWidgetId", station.id)
                    .putString("station_name_$appWidgetId", station.displayName)
                    .apply {
                        if (lineBadge != null) {
                            putString("filter_line_id_$appWidgetId", lineBadge.lineId)
                            putString("filter_line_name_$appWidgetId", lineBadge.displayName)
                        } else {
                            remove("filter_line_id_$appWidgetId")
                            remove("filter_line_name_$appWidgetId")
                        }
                        if (!direction.isNullOrBlank()) {
                            putString("filter_direction_$appWidgetId", direction)
                        } else {
                            remove("filter_direction_$appWidgetId")
                        }
                    }
            }

            try {
                val glanceManager = GlanceAppWidgetManager(applicationContext)
                val glanceId = glanceManager.getGlanceIdBy(appWidgetId)
                android.util.Log.d("WidgetConfig", "Updating glanceId $glanceId for appWidgetId $appWidgetId with station ${station.displayName}")

                updateAppWidgetState(applicationContext, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    prefs.toMutablePreferences().apply {
                        this[DepartureBoardWidget.PREF_STATION_ID] = station.id
                        this[DepartureBoardWidget.PREF_STATION_NAME] = station.displayName
                        if (lineBadge != null) {
                            this[DepartureBoardWidget.PREF_FILTER_LINE_ID] = lineBadge.lineId
                            this[DepartureBoardWidget.PREF_FILTER_LINE_NAME] = lineBadge.displayName
                        } else {
                            remove(DepartureBoardWidget.PREF_FILTER_LINE_ID)
                            remove(DepartureBoardWidget.PREF_FILTER_LINE_NAME)
                        }
                        if (!direction.isNullOrBlank()) {
                            this[DepartureBoardWidget.PREF_FILTER_DIRECTION] = direction
                        } else {
                            remove(DepartureBoardWidget.PREF_FILTER_DIRECTION)
                        }
                    }
                }

                DepartureBoardWidget().update(applicationContext, glanceId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("WidgetConfig", "Failed to update widget state for appWidgetId $appWidgetId", e)
            }

            // Ensure the periodic auto-refresh work is running
            WidgetUpdateWorker.enqueuePeriodicUpdate(applicationContext)

            // Allow Glance session to cleanly commit before returning result to launcher host
            delay(300L.milliseconds)

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                val resultValue = Intent().apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                setResult(RESULT_OK, resultValue)
                finish()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private sealed interface WidgetLimitReason {
    data class FreeLimit(val max: Int, val proMax: Int) : WidgetLimitReason
    data class MaxLimit(val max: Int) : WidgetLimitReason
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetConfigurationContent(
    billingRepository: BillingDataSource,
    onStationAndLineSelected: (Station, com.androidfung.departureboard.data.model.LineBadgeInfo?, String?) -> Unit,
    onCancel: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isCheckingLimits by remember { mutableStateOf(true) }
    var limitReachedReason by remember { mutableStateOf<WidgetLimitReason?>(null) }
    var showPaywall by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val glanceManager = GlanceAppWidgetManager(context)
        val existingGlanceIds = glanceManager.getGlanceIds(DepartureBoardWidget::class.java)
        val userPlan = billingRepository.userPlanFlow.first()

        // Glance returns ALL registered instance IDs. For a new placement, the new appWidgetId
        // is already included in existingGlanceIds.size by the time this Activity starts.
        // E.g., if you have 1 widget on screen, and are adding a 2nd, the count is already 2.
        // If we are currently configuring a widget, its ID is already registered in existingGlanceIds.
        // Since we can't reliably map GlanceId to appWidgetId synchronously without exceptions, 
        // we just subtract 1 to account for the current widget being added.
        val activeCount = (existingGlanceIds.size - 1).coerceAtLeast(0)

        if (!userPlan.isPaidOrAbove && activeCount >= userPlan.maxWidgets) {
            limitReachedReason = WidgetLimitReason.FreeLimit(userPlan.maxWidgets, SubscriptionTier.PRO_MAX_WIDGETS)
        } else if (userPlan.isPaidOrAbove && activeCount >= userPlan.maxWidgets) {
            limitReachedReason = WidgetLimitReason.MaxLimit(userPlan.maxWidgets)
        }
        isCheckingLimits = false
    }

    if (isCheckingLimits) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(strokeWidth = 3.dp)
        }
    } else if (limitReachedReason != null) {
        val reasonText = when (val reason = limitReachedReason) {
            is WidgetLimitReason.FreeLimit -> stringResource(R.string.widget_config_limit_free, reason.max, reason.proMax)
            is WidgetLimitReason.MaxLimit -> stringResource(R.string.widget_config_limit_max, reason.max)
            null -> ""
        }

        AlertDialog(
            onDismissRequest = onCancel,
            title = {
                Text(
                    text = stringResource(R.string.widget_config_limit_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = reasonText,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { showPaywall = true }
                ) {
                    Text(stringResource(R.string.widget_config_upgrade_pro))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.widget_config_cancel))
                }
            }
        )

        if (showPaywall) {
            PaywallBottomSheet(
                onDismissRequest = {
                    showPaywall = false
                    onCancel()
                },
                billingRepository = billingRepository,
                reasonMessage = reasonText
            )
        }
    } else {
        WidgetStationPickerScreen(
            onStationAndLineSelected = onStationAndLineSelected,
            onCancel = onCancel
        )
    }
}

@Composable
fun WidgetStationPickerScreen(
    onStationAndLineSelected: (Station, com.androidfung.departureboard.data.model.LineBadgeInfo?, String?) -> Unit,
    onCancel: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember(context) {
        context.appContainer.transitRepository
    }
    var searchQuery by remember { mutableStateOf("") }
    var stations by remember { mutableStateOf<List<Station>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Step 2: Line selection sheet state when a multi-line station is tapped
    var selectedStationForLines by remember { mutableStateOf<Station?>(null) }
    var stationDepartures by remember { mutableStateOf<List<com.androidfung.departureboard.data.model.Departure>>(emptyList()) }
    var isLoadingLines by remember { mutableStateOf(false) }

    LaunchedEffect(selectedStationForLines) {
        val st = selectedStationForLines
        if (st != null) {
            isLoadingLines = true
            val deps = try {
                repository.getDepartures(st.id, st.name).getOrDefault(emptyList())
            } catch (_: Exception) {
                emptyList()
            }
            stationDepartures = deps
            isLoadingLines = false
        }
    }

    LaunchedEffect(Unit) {
        val saved = try {
            val activeRegion = repository.selectedRegionFlow.first()
            repository.savedStationsFlow.first().ifEmpty { activeRegion.defaultStations }
        } catch (_: Exception) {
            GbDefaultStations.POPULAR_STATIONS
        }
        stations = saved
        isLoading = false
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            isLoading = true
            delay(300L.milliseconds)
            val result = repository.searchStations(searchQuery)
            stations = result.getOrDefault(emptyList())
            isLoading = false
        } else {
            val saved = try {
                val activeRegion = repository.selectedRegionFlow.first()
                repository.savedStationsFlow.first().ifEmpty { activeRegion.defaultStations }
            } catch (_: Exception) {
                GbDefaultStations.POPULAR_STATIONS
            }
            stations = saved
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.widget_config_select_station),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.widget_config_select_station_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.widget_config_cancel),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(stringResource(R.string.widget_config_search_hint)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(strokeWidth = 3.dp)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = stations,
                    key = { it.id + it.name },
                    contentType = { "station_item" }
                ) { station ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedStationForLines = station
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(
                                        com.androidfung.departureboard.ui.designsystem.TransitIconHelper.getTransitModeIconRes(station.primaryTransitMode)
                                    ),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column {
                                Text(
                                    text = station.displayName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val stationRegion = com.androidfung.departureboard.data.model.TransitRegion.fromStationId(station.id)
                                val subtitleLabel = when {
                                    stationRegion == com.androidfung.departureboard.data.model.TransitRegion.HK ->
                                        station.zone ?: stringResource(R.string.widget_config_hk_mtr)
                                    station.displayZone != null -> stringResource(R.string.widget_config_zone, station.displayZone ?: "")
                                    station.zone != null -> station.zone
                                    stationRegion != com.androidfung.departureboard.data.model.TransitRegion.GB ->
                                        "${stationRegion.flagEmoji} ${stringResource(stationRegion.displayNameRes)}"
                                    else -> stringResource(R.string.widget_config_network)
                                }
                                Text(
                                    text = subtitleLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Sheet or Dialog to pick specific line/route and direction
    val activeStation = selectedStationForLines
    if (activeStation != null) {
        var selectedLineForFilter by remember { mutableStateOf<com.androidfung.departureboard.data.model.LineBadgeInfo?>(null) }

        val availableLines = remember(stationDepartures) {
            stationDepartures.map { it.lineBadge }.distinctBy { it.lineId }
        }

        val availableDirections = remember(stationDepartures, selectedLineForFilter) {
            com.androidfung.departureboard.util.DepartureFilterHelper.getAvailableDirections(
                departures = stationDepartures,
                lineId = selectedLineForFilter?.lineId
            )
        }

        AlertDialog(
            onDismissRequest = { selectedStationForLines = null },
            title = {
                Text(
                    text = activeStation.displayName,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.widget_config_customize),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Step 1: Line Selection
                    Text(
                        text = stringResource(R.string.widget_config_select_line),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // All lines chip
                        val isAllSelected = selectedLineForFilter == null
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isAllSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { selectedLineForFilter = null }
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.widget_config_all_lines),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (isAllSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        availableLines.forEach { badge ->
                            val isSelected = selectedLineForFilter?.lineId == badge.lineId
                            Box(
                                modifier = Modifier
                                    .height(32.dp)
                                    .defaultMinSize(minWidth = 56.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(badge.backgroundColor)
                                    .clickable { selectedLineForFilter = badge }
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isSelected) "✓ ${badge.displayName}" else badge.displayName,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = badge.textColor,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (isLoadingLines) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(stringResource(R.string.widget_config_loading_routes), style = MaterialTheme.typography.bodySmall)
                        }
                    } else if (availableDirections.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.widget_config_select_direction),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Any direction option
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onStationAndLineSelected(activeStation, selectedLineForFilter, null)
                                    },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Text(
                                    text = stringResource(R.string.widget_config_all_directions),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            availableDirections.forEach { dir ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onStationAndLineSelected(activeStation, selectedLineForFilter, dir)
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                                ) {
                                    val dirIndicator = com.androidfung.departureboard.data.model.TransitDirection.fromString(dir)?.indicatorArrow ?: dir
                                    Text(
                                        text = dirIndicator,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                onStationAndLineSelected(activeStation, selectedLineForFilter, null)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.widget_config_save))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedStationForLines = null }) {
                    Text(stringResource(R.string.widget_config_cancel))
                }
            }
        )
    }
}
