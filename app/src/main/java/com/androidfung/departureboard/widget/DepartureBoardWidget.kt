package com.androidfung.departureboard.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.androidfung.departureboard.MainActivity
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.repository.TransitRepositoryImpl
import kotlinx.coroutines.flow.first
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.coroutines.flow.first

class DepartureBoardWidget : GlanceAppWidget() {

    override var stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    companion object {
        val PREF_STATION_ID = stringPreferencesKey("widget_station_id")
        val PREF_STATION_NAME = stringPreferencesKey("widget_station_name")
        val PREF_FILTER_LINE_ID = stringPreferencesKey("widget_filter_line_id")
        val PREF_FILTER_LINE_NAME = stringPreferencesKey("widget_filter_line_name")
        val PREF_FILTER_DIRECTION = stringPreferencesKey("widget_filter_direction")
        val PREF_LAST_REFRESH_TIMESTAMP = androidx.datastore.preferences.core.longPreferencesKey("widget_last_refresh")
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = (context.applicationContext as? com.androidfung.departureboard.DepartureBoardApp)?.repository
            ?: TransitRepositoryImpl(context)

        val appWidgetId = try {
            androidx.glance.appwidget.GlanceAppWidgetManager(context).getAppWidgetId(id)
        } catch (_: Exception) {
            -1
        }

        provideContent {
            GlanceTheme {
                val prefs = currentState<Preferences>()

                // Check local SharedPreferences fallback strictly keyed to this specific appWidgetId
                val sp = context.getSharedPreferences("widget_config", Context.MODE_PRIVATE)
                val fastStationId = if (appWidgetId != -1) sp.getString("station_id_$appWidgetId", null) else null
                val fastStationName = if (appWidgetId != -1) sp.getString("station_name_$appWidgetId", null) else null
                val fastFilterLineId = if (appWidgetId != -1) sp.getString("filter_line_id_$appWidgetId", null) else null
                val fastFilterLineName = if (appWidgetId != -1) sp.getString("filter_line_name_$appWidgetId", null) else null
                val fastFilterDirection = if (appWidgetId != -1) sp.getString("filter_direction_$appWidgetId", null) else null

                val stationId = prefs[PREF_STATION_ID] ?: fastStationId
                val stationName = prefs[PREF_STATION_NAME] ?: fastStationName
                val filterLineId = prefs[PREF_FILTER_LINE_ID] ?: fastFilterLineId
                val filterLineName = prefs[PREF_FILTER_LINE_NAME] ?: fastFilterLineName
                val filterDirection = prefs[PREF_FILTER_DIRECTION] ?: fastFilterDirection
                val refreshTimestamp = prefs[PREF_LAST_REFRESH_TIMESTAMP] ?: 0L

                val combinedFilterLabel = listOfNotNull(
                    filterLineName?.takeIf { it.isNotBlank() },
                    filterDirection?.takeIf { it.isNotBlank() }
                ).joinToString(" • ").ifBlank { null }

                if (stationId.isNullOrBlank()) {
                    WidgetUnconfiguredPlaceholder(appWidgetId = appWidgetId)
                } else {
                    val targetStation = Station(
                        id = stationId,
                        name = stationName ?: "Departure Board",
                        modes = listOf("tube"),
                        isFavorite = true
                    )

                    // Collect departures reactively when stationId or refreshTimestamp changes
                    val departuresResult by androidx.compose.runtime.remember(stationId, refreshTimestamp) {
                        repository.getDeparturesFlow(stationId, targetStation.name)
                    }.collectAsState(initial = null)

                    when (val result = departuresResult) {
                        null -> {
                            // Loading state
                            WidgetPreparingPlaceholder(
                                station = targetStation,
                                filterLabel = combinedFilterLabel
                            )
                        }
                        else -> {
                            val allDepartures = result.getOrNull() ?: emptyList()
                            val filteredDepartures = com.androidfung.departureboard.util.DepartureFilterHelper.filterDepartures(
                                departures = allDepartures,
                                lineId = filterLineId,
                                direction = filterDirection
                            )

                            val isSuccess = result.isSuccess && allDepartures.isNotEmpty()

                            WidgetContent(
                                station = targetStation,
                                filterLabel = combinedFilterLabel,
                                departures = filteredDepartures,
                                isLiveData = isSuccess,
                                isError = result.isFailure
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun WidgetContent(
        station: Station,
        filterLabel: String?,
        departures: List<Departure>,
        isLiveData: Boolean = true,
        isError: Boolean = false
    ) {
        val clickIntent = Intent(androidx.glance.LocalContext.current, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse("mindtheboard://station/${station.id}?name=${java.net.URLEncoder.encode(station.name, "UTF-8")}")
            putExtra(MainActivity.EXTRA_STATION_ID, station.id)
            putExtra(MainActivity.EXTRA_STATION_NAME, station.name)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(22.dp)
                .padding(14.dp)
                .clickable(actionStartActivity(clickIntent))
        ) {
            // Header Row: Prompt Departure Branding + Station
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Line accent / indicator
                Box(
                    modifier = GlanceModifier
                        .size(10.dp)
                        .cornerRadius(5.dp)
                        .background(GlanceTheme.colors.primary)
                ) {}

                Spacer(modifier = GlanceModifier.width(8.dp))

                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = station.displayName,
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = GlanceTheme.colors.onSurface
                        ),
                        maxLines = 1
                    )
                    val liveLabel = when {
                        isLiveData -> "Live"
                        isError -> "Offline"
                        else -> "No departures"
                    }
                    val subtitle = if (!filterLabel.isNullOrBlank()) {
                        "Prompt Departure • $filterLabel • $liveLabel"
                    } else {
                        "Prompt Departure • $liveLabel"
                    }
                    Text(
                        text = subtitle,
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            color = if (!filterLabel.isNullOrBlank()) GlanceTheme.colors.primary else GlanceTheme.colors.onSurfaceVariant
                        )
                    )
                }

                // Interactive Refresh Button with generous hit target
                Box(
                    modifier = GlanceModifier
                        .size(38.dp)
                        .cornerRadius(19.dp)
                        .background(GlanceTheme.colors.surfaceVariant)
                        .clickable(actionRunCallback<RefreshWidgetAction>()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "↻",
                        style = TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = GlanceTheme.colors.onSurfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(10.dp))

            if (departures.isEmpty()) {
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isError) "No departure information available" else "No departures scheduled",
                        style = TextStyle(
                            fontSize = 13.sp,
                            color = GlanceTheme.colors.onSurfaceVariant
                        )
                    )
                }
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(departures.take(5)) { departure ->
                        WidgetDepartureRow(
                            departure = departure,
                            station = station
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun WidgetDepartureRow(
        departure: Departure,
        station: Station
    ) {
        val clickIntent = Intent(androidx.glance.LocalContext.current, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse("mindtheboard://station/${station.id}?name=${java.net.URLEncoder.encode(station.name, "UTF-8")}")
            putExtra(MainActivity.EXTRA_STATION_ID, station.id)
            putExtra(MainActivity.EXTRA_STATION_NAME, station.name)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val minutes = departure.timeToStationSeconds / 60
        val isDue = departure.timeToStationSeconds <= 30
        val countdownText = if (isDue) "DUE" else "${minutes}m"

        val isBus = departure.modeName.equals("bus", ignoreCase = true) || departure.lineId.toIntOrNull() != null
        val badge = departure.lineBadge
        val routeLineLabel = departure.lineName.ifBlank { departure.lineId }
        val platformOrStand = departure.platformName.takeIf { it.isNotBlank() && it.lowercase() != "null" }

        val subtitleText = when {
            isBus && !platformOrStand.isNullOrBlank() -> "$routeLineLabel • $platformOrStand"
            isBus -> routeLineLabel
            !platformOrStand.isNullOrBlank() -> platformOrStand
            else -> routeLineLabel
        }

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 5.dp)
                .clickable(actionStartActivity(clickIntent)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isBus) {
                // Compact Bus Route Number Badge (e.g. "240", "221", "SL1")
                Box(
                    modifier = GlanceModifier
                        .size(width = 32.dp, height = 20.dp)
                        .cornerRadius(5.dp)
                        .background(badge.backgroundColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = routeLineLabel,
                        style = TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = androidx.glance.color.ColorProvider(day = badge.textColor, night = badge.textColor)
                        ),
                        maxLines = 1
                    )
                }
                Spacer(modifier = GlanceModifier.width(8.dp))
            } else {
                // Sleek Vertical Line Accent Bar for trains (Circle, Northern, Elizabeth, etc.) saving ~30dp
                Box(
                    modifier = GlanceModifier
                        .size(width = 4.dp, height = 30.dp)
                        .cornerRadius(2.dp)
                        .background(badge.backgroundColor)
                ) {}
                Spacer(modifier = GlanceModifier.width(8.dp))
            }

            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = departure.destinationName,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = GlanceTheme.colors.onSurface
                    ),
                    maxLines = 1
                )
                Text(
                    text = subtitleText,
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontWeight = FontWeight.Normal
                    ),
                    maxLines = 1
                )
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            // London Dot-Matrix Amber LED Pill or Alert Red DUE
            Box(
                modifier = GlanceModifier
                    .cornerRadius(8.dp)
                    .background(
                        if (isDue) GlanceTheme.colors.errorContainer
                        else GlanceTheme.colors.primaryContainer
                    )
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                val clockTime = departure.formattedActualClockTime
                val displayLabel = if (!clockTime.isNullOrBlank() && !isDue) "$countdownText • $clockTime" else countdownText

                Text(
                    text = displayLabel,
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDue) GlanceTheme.colors.onErrorContainer
                        else GlanceTheme.colors.onPrimaryContainer
                    )
                )
            }
        }
    }

    /**
     * State 1: Unconfigured State shown when a widget is first dropped onto the home screen
     * and has no station assigned yet. Tapping it directly opens station selection.
     */
    @Composable
    private fun WidgetUnconfiguredPlaceholder(
        appWidgetId: Int
    ) {
        val context = androidx.glance.LocalContext.current
        val configIntent = Intent(context, WidgetConfigurationActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (appWidgetId != -1) {
                putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(22.dp)
                .padding(16.dp)
                .clickable(actionStartActivity(configIntent)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Tonal station indicator pill
            Box(
                modifier = GlanceModifier
                    .size(width = 24.dp, height = 8.dp)
                    .cornerRadius(4.dp)
                    .background(GlanceTheme.colors.primary)
            ) {}

            Spacer(modifier = GlanceModifier.height(12.dp))

            Text(
                text = "Prompt Departure",
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onSurface
                ),
                maxLines = 1
            )

            Spacer(modifier = GlanceModifier.height(4.dp))

            Text(
                text = "No station selected yet",
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = GlanceTheme.colors.onSurfaceVariant
                ),
                maxLines = 1
            )

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Interactive prompt chip
            Box(
                modifier = GlanceModifier
                    .cornerRadius(10.dp)
                    .background(GlanceTheme.colors.primaryContainer)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Choose Station & Line ➔",
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.onPrimaryContainer
                    )
                )
            }
        }
    }

    /**
     * State 2: Configuring / Preparing State shown immediately when a station has just been
     * selected, while initial live arrivals or schedule data are being retrieved.
     */
    @Composable
    private fun WidgetPreparingPlaceholder(
        station: Station,
        filterLabel: String?
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(22.dp)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = GlanceModifier
                    .size(width = 16.dp, height = 8.dp)
                    .cornerRadius(4.dp)
                    .background(GlanceTheme.colors.primary)
            ) {}

            Spacer(modifier = GlanceModifier.height(10.dp))

            Text(
                text = station.displayName,
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onSurface
                ),
                maxLines = 1
            )

            val subtitle = if (!filterLabel.isNullOrBlank()) {
                "$filterLabel • Setting up live board…"
            } else {
                "Setting up live board…"
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            Text(
                text = subtitle,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = GlanceTheme.colors.onSurfaceVariant
                ),
                maxLines = 1
            )

            Spacer(modifier = GlanceModifier.height(8.dp))

            Text(
                text = "Fetching live arrivals…",
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = GlanceTheme.colors.primary
                )
            )
        }
    }
}

class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        // Mutate widget state to force Glance to invalidate cache and execute provideGlance fresh
        androidx.glance.appwidget.state.updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[DepartureBoardWidget.PREF_LAST_REFRESH_TIMESTAMP] = System.currentTimeMillis()
            }
        }
        DepartureBoardWidget().update(context, glanceId)
        WidgetUpdateWorker.enqueuePeriodicUpdate(context.applicationContext)
    }
}
