package com.androidfung.departureboard

import android.app.Application
import com.androidfung.departureboard.data.network.TflNetworkClient
import com.androidfung.departureboard.data.repository.NationalRailStationCodes

class DepartureBoardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TflNetworkClient.initialize(this)
        NationalRailStationCodes.initialize(this)
        // Initialize Google Mobile Ads SDK on a background thread to prevent blocking main thread
        com.google.android.gms.ads.MobileAds.initialize(this) {}
    }
}
