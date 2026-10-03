package com.androidfung.departureboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.androidfung.departureboard.ui.dashboard.DashboardScreen
import com.androidfung.departureboard.ui.theme.DepartureBoardTheme

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.androidfung.departureboard.data.model.Station

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_STATION_ID = "extra_station_id"
        const val EXTRA_STATION_NAME = "extra_station_name"
    }

    private var initialStation by mutableStateOf<Station?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        setContent {
            DepartureBoardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DashboardScreen(initialDetailStation = initialStation)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data
        val stationId = intent?.getStringExtra(EXTRA_STATION_ID)
            ?: if (uri != null && uri.scheme == "mindtheboard" && uri.host == "station") uri.lastPathSegment else null
        val stationName = intent?.getStringExtra(EXTRA_STATION_NAME)
            ?: uri?.getQueryParameter("name")

        if (!stationId.isNullOrBlank()) {
            initialStation = Station(
                id = stationId,
                name = stationName ?: "Station",
                modes = listOf("tube"),
                isFavorite = true
            )
        }
    }
}
