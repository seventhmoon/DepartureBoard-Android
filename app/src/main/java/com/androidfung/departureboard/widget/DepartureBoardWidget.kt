package com.androidfung.departureboard.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
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
import com.androidfung.departureboard.data.model.DefaultStations
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.repository.TransitRepositoryImpl
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
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = TransitRepositoryImpl(context)

        // Glance currentState reads the per-widget preferences directly in provideContent or provideGlance
        val prefs: Preferences = androidx.glance.appwidget.state.getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val customStationId = prefs[PREF_STATION_ID]
        val customStationName = prefs[PREF_STATION_NAME]
        val filterLineId = prefs[PREF_FILTER_LINE_ID]
        val filterLineName = prefs[PREF_FILTER_LINE_NAME]

        val targetStation = if (!customStationId.isNullOrBlank()) {
            Station(
                id = customStationId,
                name = customStationName ?: "Departure Board",
                modes = listOf("tube"),
                isFavorite = true
            )
        } else {
            DefaultStations.POPULAR_STATIONS.first()
        }

        val allDepartures = try {
            val live = repository.getDepartures(targetStation.id, targetStation.name)
            if (live.isSuccess) {
                live.getOrDefault(emptyList())
            } else {
                DefaultStations.getFallbackDepartures(targetStation.id, targetStation.name)
            }
        } catch (_: Exception) {
            DefaultStations.getFallbackDepartures(targetStation.id, targetStation.name)
        }

        val filteredDepartures = if (!filterLineId.isNullOrBlank()) {
            allDepartures.filter {
                it.lineId.equals(filterLineId, ignoreCase = true) ||
                it.lineName.equals(filterLineId, ignoreCase = true) ||
                (!filterLineName.isNullOrBlank() && it.lineBadge.displayName.equals(filterLineName, ignoreCase = true))
            }
        } else {
            allDepartures
        }

        provideContent {
            GlanceTheme {
                WidgetContent(
                    station = targetStation,
                    filterLabel = filterLineName,
                    departures = filteredDepartures
                )
            }
        }
    }

    @Composable
    private fun WidgetContent(
        station: Station,
        filterLabel: String?,
        departures: List<Departure>
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
            // Header Row: Mind The Board Branding + Station
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
                    val subtitle = if (!filterLabel.isNullOrBlank()) {
                        "Mind The Board • $filterLabel"
                    } else {
                        "Mind The Board • Live"
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
                        text = "No live departures",
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

        val badgeText = if (isBus) {
            routeLineLabel
        } else {
            when (departure.lineId.lowercase()) {
                "hammersmith-city", "hammersmith & city" -> "H&C"
                "waterloo-city", "waterloo & city" -> "W&C"
                "elizabeth-line", "elizabeth" -> "Eliz"
                "overground" -> "Over"
                "district" -> "Dist"
                "piccadilly" -> "Picc"
                "metropolitan" -> "Met"
                "victoria" -> "Vic"
                "central" -> "Cent"
                "bakerloo" -> "Bak"
                "northern" -> "North"
                "jubilee" -> "Jub"
                "circle" -> "Circ"
                else -> if (badge.displayName.length <= 4) badge.displayName else badge.displayName.take(4)
            }
        }

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
            // Line / Route Pill Badge for both Tube/Rail and Buses using official line colors
            Box(
                modifier = GlanceModifier
                    .size(width = 38.dp, height = 24.dp)
                    .cornerRadius(6.dp)
                    .background(badge.backgroundColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeText,
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = androidx.glance.color.ColorProvider(day = badge.textColor, night = badge.textColor)
                    ),
                    maxLines = 1
                )
            }
            Spacer(modifier = GlanceModifier.width(8.dp))

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
}

class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        // Enqueue immediate background update worker as well to ensure WorkManager schedule stays alive
        WidgetUpdateWorker.enqueuePeriodicUpdate(context.applicationContext)
        DepartureBoardWidget().update(context, glanceId)
    }
}
