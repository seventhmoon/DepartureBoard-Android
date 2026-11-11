package com.androidfung.departureboard.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.service.quicksettings.PendingIntentActivityWrapper
import androidx.core.service.quicksettings.TileServiceCompat
import com.androidfung.departureboard.MainActivity
import com.androidfung.departureboard.R

/**
 * Android Quick Settings Tile providing instant access to nearest / active commute station departures.
 */
class NearestDepartureTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            label = getString(R.string.app_name)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = getString(R.string.widget_description)
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val wrapper = PendingIntentActivityWrapper(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT,
            false
        )
        TileServiceCompat.startActivityAndCollapse(this, wrapper)
    }
}
