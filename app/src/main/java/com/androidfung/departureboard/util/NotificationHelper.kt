package com.androidfung.departureboard.util

import android.content.Context
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {

    /**
     * Checks if notifications are enabled for the application across all Android versions.
     * On Android 13+ (API 33), this verifies the POST_NOTIFICATIONS runtime permission.
     * On Android 12 and below, this checks whether notifications are turned on in system settings.
     */
    fun areNotificationsEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
