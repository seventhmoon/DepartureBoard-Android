package com.androidfung.departureboard

import android.app.Application
import com.androidfung.departureboard.data.network.TflNetworkClient
import com.androidfung.departureboard.data.repository.NationalRailStationCodes
import com.androidfung.departureboard.data.repository.TransitRepository
import com.androidfung.departureboard.data.repository.TransitRepositoryImpl

class DepartureBoardApp : Application() {

    val repository: TransitRepository by lazy {
        TransitRepositoryImpl(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        TflNetworkClient.initialize(this)
        NationalRailStationCodes.initialize(this)
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
