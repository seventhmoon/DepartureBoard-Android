package com.androidfung.departureboard

import android.app.Application
import android.content.Context
import com.androidfung.departureboard.data.repository.TransitRepository
import com.androidfung.departureboard.di.AppContainer
import com.androidfung.departureboard.di.DefaultAppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DepartureBoardApp : Application() {

    lateinit var container: AppContainer
        private set

    val repository: TransitRepository
        get() = container.transitRepository

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
        com.androidfung.departureboard.ai.FirebaseCloudAiClient.initAppCheck(this, BuildConfig.DEBUG)
        // Initialize notification channels, station code DB warmup, and Mobile Ads off the Main thread
        CoroutineScope(Dispatchers.IO).launch {
            com.androidfung.departureboard.util.NotificationHelper.ensureAllNotificationChannels(this@DepartureBoardApp)
            com.androidfung.departureboard.data.repository.region.gb.NationalRailStationCodes.initialize(this@DepartureBoardApp)
            com.google.android.gms.ads.MobileAds.initialize(this@DepartureBoardApp) {}
        }
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as DepartureBoardApp).container
