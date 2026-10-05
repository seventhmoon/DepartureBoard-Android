package com.androidfung.departureboard

import android.app.Application
import com.androidfung.departureboard.data.network.TflNetworkClient
import com.androidfung.departureboard.data.repository.NationalRailStationCodes

class DepartureBoardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TflNetworkClient.initialize(this)
        NationalRailStationCodes.initialize(this)
    }
}
