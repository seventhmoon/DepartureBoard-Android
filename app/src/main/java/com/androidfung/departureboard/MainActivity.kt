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
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_STATION_ID = "extra_station_id"
        const val EXTRA_STATION_NAME = "extra_station_name"
    }

    private var initialStation by mutableStateOf<Station?>(null)
    private lateinit var appUpdateManager: AppUpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        com.androidfung.departureboard.widget.WidgetUpdateWorker.enqueuePeriodicUpdate(applicationContext)

        // Check for Google Play In-App Updates
        appUpdateManager = AppUpdateManagerFactory.create(this)
        checkForAppUpdate()

        setContent {
            // Enable Dynamic Color / Monet theming on Android 12+ while maintaining TfL brand accents
            DepartureBoardTheme(dynamicColor = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DashboardScreen(initialDetailStation = initialStation)
                }
            }
        }
    }

    private fun checkForAppUpdate() {
        try {
            val appUpdateInfoTask = appUpdateManager.appUpdateInfo
            appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                ) {
                    // Flexible in-app update available
                    try {
                        appUpdateManager.startUpdateFlowForResult(
                            appUpdateInfo,
                            AppUpdateType.FLEXIBLE,
                            this,
                            1001
                        )
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        try {
            appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
                if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    appUpdateManager.startUpdateFlowForResult(
                        info,
                        AppUpdateType.FLEXIBLE,
                        this,
                        1001
                    )
                }
            }
        } catch (_: Exception) {}
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
        } else {
            initialStation = null
        }
    }
}
