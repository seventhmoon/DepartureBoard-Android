package com.androidfung.departureboard.util

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.androidfung.departureboard.MainActivity
import com.androidfung.departureboard.R
import com.androidfung.departureboard.data.model.Station

/**
 * Publishes dynamic launcher shortcuts for the user's top saved stations,
 * allowing instant 1-tap departure board access directly from the Android Home Screen.
 */
object DynamicShortcutHelper {

    fun updateShortcuts(context: Context, stations: List<Station>) {
        try {
            val topStations = stations.take(3)
            val shortcuts = topStations.mapIndexed { index, station ->
                val intent = Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    putExtra(MainActivity.EXTRA_STATION_ID, station.id)
                    putExtra(MainActivity.EXTRA_STATION_NAME, station.name)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }

                val cleanName = station.displayName.substringBefore("(").trim()

                ShortcutInfoCompat.Builder(context, "station_${station.id}")
                    .setShortLabel(cleanName.take(15))
                    .setLongLabel("$cleanName departures")
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_train))
                    .setIntent(intent)
                    .setRank(index)
                    .build()
            }

            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        } catch (_: Throwable) {
            // Graceful fallback if shortcuts cannot be updated on legacy launcher
        }
    }

    fun reportStationUsed(context: Context, stationId: String) {
        if (stationId.isBlank()) return
        try {
            ShortcutManagerCompat.reportShortcutUsed(context, "station_$stationId")
        } catch (_: Throwable) {
            // Ignore on unsupported launchers
        }
    }
}
