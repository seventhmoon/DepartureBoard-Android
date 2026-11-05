package com.androidfung.departureboard

import android.app.Application
import com.androidfung.departureboard.data.repository.TransitRepository
import com.androidfung.departureboard.di.AppContainer
import com.androidfung.departureboard.di.DefaultAppContainer

class DepartureBoardApp : Application() {

    lateinit var container: AppContainer
        private set

    val repository: TransitRepository
        get() = container.transitRepository

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = DefaultAppContainer(this)
        // Initialize Google Mobile Ads SDK on a background thread to prevent blocking main thread
        com.google.android.gms.ads.MobileAds.initialize(this) {}
    }

    companion object {
        lateinit var instance: DepartureBoardApp
            private set

        val repository: TransitRepository
            get() = instance.repository
    }
}
