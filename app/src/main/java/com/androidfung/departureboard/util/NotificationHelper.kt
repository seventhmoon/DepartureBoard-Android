package com.androidfung.departureboard.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.core.content.getSystemService
import com.androidfung.departureboard.R
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.service.TrainTrackingService
import com.androidfung.departureboard.widget.WidgetDisruptionNotificationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NotificationHelper {

    private const val PREFS_NAME = "notification_type_preferences"
    private const val KEY_PLATFORM_ALERTS = "pref_platform_alerts_enabled"
    private const val KEY_ALIGHTING_ALERTS = "pref_alighting_alerts_enabled"
    private const val KEY_DISRUPTION_ALERTS = "pref_disruption_alerts_enabled"
    private const val KEY_WEAR_AUTO_HUD = "pref_wear_auto_hud_enabled"

    data class NotificationTypePreferences(
        val platformAlertsEnabled: Boolean = true,
        val alightingAlertsEnabled: Boolean = true,
        val disruptionAlertsEnabled: Boolean = true,
        val wearAutoLaunchHudEnabled: Boolean = true
    )

    private val _preferencesFlow = MutableStateFlow(NotificationTypePreferences())
    val preferencesFlow: StateFlow<NotificationTypePreferences> = _preferencesFlow.asStateFlow()

    fun loadPreferences(context: Context): NotificationTypePreferences {
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val prefs = NotificationTypePreferences(
            platformAlertsEnabled = sp.getBoolean(KEY_PLATFORM_ALERTS, true),
            alightingAlertsEnabled = sp.getBoolean(KEY_ALIGHTING_ALERTS, true),
            disruptionAlertsEnabled = sp.getBoolean(KEY_DISRUPTION_ALERTS, true),
            wearAutoLaunchHudEnabled = sp.getBoolean(KEY_WEAR_AUTO_HUD, true)
        )
        _preferencesFlow.value = prefs
        return prefs
    }

    fun isPlatformAlertsEnabled(context: Context): Boolean =
        loadPreferences(context).platformAlertsEnabled

    fun isAlightingAlertsEnabled(context: Context): Boolean =
        loadPreferences(context).alightingAlertsEnabled

    fun isDisruptionAlertsEnabled(context: Context): Boolean =
        loadPreferences(context).disruptionAlertsEnabled

    fun isWearAutoLaunchHudEnabled(context: Context): Boolean =
        loadPreferences(context).wearAutoLaunchHudEnabled

    fun setPlatformAlertsEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_PLATFORM_ALERTS, enabled)
        }
        loadPreferences(context)
    }

    fun setAlightingAlertsEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_ALIGHTING_ALERTS, enabled)
        }
        loadPreferences(context)
    }

    fun setDisruptionAlertsEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_DISRUPTION_ALERTS, enabled)
        }
        loadPreferences(context)
    }

    fun setWearAutoLaunchHudEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_WEAR_AUTO_HUD, enabled)
        }
        loadPreferences(context)
    }

    /**
     * Registers all distinct notification channels upfront so users can inspect and toggle
     * individual channels in Android System Settings at any time.
     */
    fun ensureAllNotificationChannels(context: Context) {
        try {
            val nm = context.getSystemService<NotificationManager>() ?: return

            val liveChannel = NotificationChannel(
                TrainTrackingService.CHANNEL_ID,
                context.getString(R.string.notification_channel_live_tracking),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_live_tracking_desc)
                setShowBadge(true)
            }

            val platformChannel = NotificationChannel(
                TrainTrackingService.PLATFORM_ALERT_CHANNEL_ID,
                context.getString(R.string.notification_channel_platform_alerts),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_platform_alerts_desc)
                enableVibration(true)
                setShowBadge(true)
            }

            val alightingChannel = NotificationChannel(
                TrainTrackingService.ALIGHTING_ALERT_CHANNEL_ID,
                context.getString(R.string.notification_channel_alighting_alerts),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_alighting_alerts_desc)
                enableVibration(true)
                setShowBadge(true)
            }

            val disruptionChannel = NotificationChannel(
                TrainTrackingService.ALERT_CHANNEL_ID,
                context.getString(R.string.notification_channel_alerts),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_alerts_desc)
                enableVibration(true)
                setShowBadge(true)
            }

            nm.createNotificationChannels(
                listOf(liveChannel, platformChannel, alightingChannel, disruptionChannel)
            )
            WidgetDisruptionNotificationManager.createNotificationChannel(context)
        } catch (_: Exception) {
        }
    }

    /**
     * Opens the Android System Notification Settings screen for this app (or a specific channel).
     */
    fun openSystemNotificationSettings(context: Context, channelId: String? = null) {
        runCatching {
            val intent = if (channelId != null) {
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        }
    }

    /**
     * Checks if notifications are enabled for the application across all Android versions.
     * On Android 13+ (API 33), this verifies the POST_NOTIFICATIONS runtime permission.
     * On Android 12 and below, this checks whether notifications are turned on in system settings.
     */
    fun areNotificationsEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * Checks whether the application is permitted to post promoted ongoing (Live Update)
     * notifications on Android 16+ (API 36+). Returns false on older Android versions.
     */
    fun canPostPromotedNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 36) {
            return try {
                context.getSystemService<NotificationManager>()?.canPostPromotedNotifications() ?: false
            } catch (_: Throwable) {
                false
            }
        }
        return false
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
