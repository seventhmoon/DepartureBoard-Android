package com.androidfung.departureboard.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
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
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = TransitRepositoryImpl(context)

        // Glance currentState reads the per-widget preferences directly in provideContent or provideGlance
        val prefs = androidx.glance.appwidget.state.getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val customStationId = prefs[PREF_STATION_ID]
        val customStationName = prefs[PREF_STATION_NAME]

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

        val departures = try {
            val live = repository.getDepartures(targetStation.id, targetStation.name).getOrNull()
            if (!live.isNullOrEmpty()) live
            else DefaultStations.getFallbackDepartures(targetStation.id, targetStation.name)
        } catch (_: Exception) {
            DefaultStations.getFallbackDepartures(targetStation.id, targetStation.name)
        }

        provideContent {
            val activePrefs = currentState<Preferences>()
            val stationId = activePrefs[PREF_STATION_ID] ?: targetStation.id
            val stationName = activePrefs[PREF_STATION_NAME] ?: targetStation.name
            val activeStation = targetStation.copy(id = stationId, name = stationName)

            GlanceTheme {
                WidgetContent(
                    station = activeStation,
                    departures = departures
                )
            }
        }
    }

    @Composable
    private fun WidgetContent(
        station: Station,
        departures: List<Departure>
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(22.dp)
                .padding(14.dp)
                .clickable(actionStartActivity<MainActivity>())
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
                        text = station.name,
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = GlanceTheme.colors.onSurface
                        ),
                        maxLines = 1
                    )
                    Text(
                        text = "Mind The Board • Live",
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            color = GlanceTheme.colors.onSurfaceVariant
                        )
                    )
                }

                // Interactive Refresh Button
                Box(
                    modifier = GlanceModifier
                        .size(30.dp)
                        .cornerRadius(15.dp)
                        .background(GlanceTheme.colors.surfaceVariant)
                        .clickable(actionRunCallback<RefreshWidgetAction>()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "↻",
                        style = TextStyle(
                            fontSize = 16.sp,
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
                        WidgetDepartureRow(departure = departure)
                    }
                }
            }
        }
    }

    @Composable
    private fun WidgetDepartureRow(departure: Departure) {
        val minutes = departure.timeToStationSeconds / 60
        val isDue = departure.timeToStationSeconds <= 30
        val countdownText = if (isDue) "DUE" else "${minutes}m"

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                    text = departure.platformName.ifBlank { departure.lineName },
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = GlanceTheme.colors.onSurfaceVariant
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
        DepartureBoardWidget().update(context, glanceId)
    }
}
