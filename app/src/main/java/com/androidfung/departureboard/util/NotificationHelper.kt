package com.androidfung.departureboard.util

import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.service.TrainTrackingService

object NotificationHelper {

    /**
     * Checks if notifications are enabled for the application across all Android versions.
     * On Android 13+ (API 33), this verifies the POST_NOTIFICATIONS runtime permission.
     * On Android 12 and below, this checks whether notifications are turned on in system settings.
     */
    fun areNotificationsEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * Starts live tracking for [departure], transparently handling the Android 13+ (API 33)
     * POST_NOTIFICATIONS runtime permission.
     *
     * If notifications are disabled on Android 13+, [onPermissionNeeded] is invoked instead
     * of starting tracking, so the caller can record the pending departure and launch the
     * runtime permission request (starting tracking once granted). On Android 12 and below
     * there is no runtime permission, so tracking starts directly.
     */
    fun startTrackingWithPermissionCheck(
        context: Context,
        departure: Departure,
        onPermissionNeeded: (Departure) -> Unit
    ) {
        if (areNotificationsEnabled(context)) {
            TrainTrackingService.startTracking(context, departure)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onPermissionNeeded(departure)
        } else {
            TrainTrackingService.startTracking(context, departure)
        }
    }
}
