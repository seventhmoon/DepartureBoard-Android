package com.androidfung.departureboard.wear.haptic

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.androidfung.departureboard.wear.model.WearTrackedJourneyState
import com.androidfung.departureboard.wear.sync.WearCompanionSyncReceiver
import com.androidfung.departureboard.wear.ui.WearDepartureBoardActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Publishes native Wear OS notifications across distinct Notification Channels and manages
 * per-type user notification preferences on the watch:
 *
 * Channels:
 * 1. `wear_live_tracking_channel` — Live Train Tracker (ongoing hardware chronometer + progress bar)
 * 2. `wear_sprint_hud_launch_channel` — Instant Sprint HUD Launch (wakes watch on Phone Track tap)
 * 3. `wear_platform_alerts_channel` — Platform Announcement & Change Alerts (`TBD -> Plat X`, `Plat A -> Plat B`)
 * 4. `wear_alighting_alerts_channel` — Target Alighting Stop Alerts ("Get Ready to Alight" 1 stop before)
 * 5. `wear_disruption_alerts_channel` — Delay Repay & Disruption Alerts
 */
object WearNotificationPublisher {

    const val CHANNEL_TRACKING_ID = "wear_live_tracking_channel"
    const val CHANNEL_SPRINT_HUD_ID = "wear_sprint_hud_launch_channel"
    const val CHANNEL_PLATFORM_ALERTS_ID = "wear_platform_alerts_channel"
    const val CHANNEL_ALIGHTING_ALERTS_ID = "wear_alighting_alerts_channel"
    const val CHANNEL_DISRUPTION_ALERTS_ID = "wear_disruption_alerts_channel"

    private const val NOTIFICATION_ID_TRACKED = 3001
    private const val NOTIFICATION_ID_PLATFORM_ALERT = 3002
    private const val NOTIFICATION_ID_ALIGHTING_ALERT = 3003
    private const val NOTIFICATION_ID_DISRUPTION_ALERT = 3004

    const val EXTRA_OPEN_SPRINT_HUD = "extra_open_sprint_hud"

    private const val PREFS_NAME = "wear_notification_preferences"
    private const val KEY_LIVE_TRACKING = "pref_wear_live_tracking"
    private const val KEY_AUTO_HUD = "pref_wear_auto_hud"
    private const val KEY_PLATFORM_ALERTS = "pref_wear_platform_alerts"
    private const val KEY_ALIGHTING_ALERTS = "pref_wear_alighting_alerts"
    private const val KEY_DISRUPTION_ALERTS = "pref_wear_disruption_alerts"

    data class WearNotificationPreferences(
        val liveTrackingEnabled: Boolean = true,
        val autoLaunchSprintHudEnabled: Boolean = true,
        val platformAlertsEnabled: Boolean = true,
        val alightingAlertsEnabled: Boolean = true,
        val disruptionAlertsEnabled: Boolean = true
    )

    private val _preferencesFlow = MutableStateFlow(WearNotificationPreferences())
    val preferencesFlow: StateFlow<WearNotificationPreferences> = _preferencesFlow.asStateFlow()

    fun loadPreferences(context: Context): WearNotificationPreferences {
        return runCatching {
            val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val prefs = WearNotificationPreferences(
                liveTrackingEnabled = sp.getBoolean(KEY_LIVE_TRACKING, true),
                autoLaunchSprintHudEnabled = sp.getBoolean(KEY_AUTO_HUD, true),
                platformAlertsEnabled = sp.getBoolean(KEY_PLATFORM_ALERTS, true),
                alightingAlertsEnabled = sp.getBoolean(KEY_ALIGHTING_ALERTS, true),
                disruptionAlertsEnabled = sp.getBoolean(KEY_DISRUPTION_ALERTS, true)
            )
            _preferencesFlow.value = prefs
            prefs
        }.getOrElse { _preferencesFlow.value }
    }

    fun updatePreferences(
        context: Context,
        liveTrackingEnabled: Boolean = _preferencesFlow.value.liveTrackingEnabled,
        autoLaunchSprintHudEnabled: Boolean = _preferencesFlow.value.autoLaunchSprintHudEnabled,
        platformAlertsEnabled: Boolean = _preferencesFlow.value.platformAlertsEnabled,
        alightingAlertsEnabled: Boolean = _preferencesFlow.value.alightingAlertsEnabled,
        disruptionAlertsEnabled: Boolean = _preferencesFlow.value.disruptionAlertsEnabled
    ) {
        val updated = WearNotificationPreferences(
            liveTrackingEnabled = liveTrackingEnabled,
            autoLaunchSprintHudEnabled = autoLaunchSprintHudEnabled,
            platformAlertsEnabled = platformAlertsEnabled,
            alightingAlertsEnabled = alightingAlertsEnabled,
            disruptionAlertsEnabled = disruptionAlertsEnabled
        )
        _preferencesFlow.value = updated
        runCatching {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit(commit = false) {
                    putBoolean(KEY_LIVE_TRACKING, liveTrackingEnabled)
                    putBoolean(KEY_AUTO_HUD, autoLaunchSprintHudEnabled)
                    putBoolean(KEY_PLATFORM_ALERTS, platformAlertsEnabled)
                    putBoolean(KEY_ALIGHTING_ALERTS, alightingAlertsEnabled)
                    putBoolean(KEY_DISRUPTION_ALERTS, disruptionAlertsEnabled)
                }
        }
        if (!liveTrackingEnabled) {
            cancelTrackedJourneyNotification(context)
        }
    }

    fun applySyncedPhonePreferences(context: Context?, prefsJson: String) {
        val current = _preferencesFlow.value
        val platformEnabled = extractBool(prefsJson, "platformAlertsEnabled", current.platformAlertsEnabled)
        val alightingEnabled = extractBool(prefsJson, "alightingAlertsEnabled", current.alightingAlertsEnabled)
        val disruptionEnabled = extractBool(prefsJson, "disruptionAlertsEnabled", current.disruptionAlertsEnabled)
        val autoHudEnabled = extractBool(prefsJson, "wearAutoLaunchHudEnabled", current.autoLaunchSprintHudEnabled)
        if (context != null) {
            updatePreferences(
                context = context,
                liveTrackingEnabled = current.liveTrackingEnabled,
                autoLaunchSprintHudEnabled = autoHudEnabled,
                platformAlertsEnabled = platformEnabled,
                alightingAlertsEnabled = alightingEnabled,
                disruptionAlertsEnabled = disruptionEnabled
            )
        } else {
            _preferencesFlow.value = current.copy(
                autoLaunchSprintHudEnabled = autoHudEnabled,
                platformAlertsEnabled = platformEnabled,
                alightingAlertsEnabled = alightingEnabled,
                disruptionAlertsEnabled = disruptionEnabled
            )
        }
    }

    private fun extractBool(json: String, key: String, default: Boolean): Boolean {
        try {
            val obj = JSONObject(json)
            if (obj.has(key)) return obj.optBoolean(key, default)
        } catch (_: Throwable) {
        }
        return Regex(""""$key"\s*:\s*(true|false)""")
            .find(json)
            ?.groupValues
            ?.getOrNull(1)
            ?.toBooleanStrictOrNull() ?: default
    }

    fun ensureAllChannels(context: Context) {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            ensureChannels(context, nm)
        }
    }

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

    fun launchSprintHudImmediately(context: Context) {
        if (!loadPreferences(context).autoLaunchSprintHudEnabled) return
        runCatching {
            val openHudIntent = Intent(context, WearDepartureBoardActivity::class.java).apply {
                putExtra(EXTRA_OPEN_SPRINT_HUD, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(openHudIntent)
        }
    }

    fun postTrackedJourneyNotification(
        context: Context,
        journey: WearTrackedJourneyState,
        isInitialStart: Boolean = false
    ) {
        val prefs = loadPreferences(context)
        if (!prefs.liveTrackingEnabled) return
        if (!hasNotificationPermission(context)) return
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            ensureChannels(context, nm)

            val openHudIntent = Intent(context, WearDepartureBoardActivity::class.java).apply {
                putExtra(EXTRA_OPEN_SPRINT_HUD, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openHudPendingIntent = PendingIntent.getActivity(
                context,
                301,
                openHudIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val stopIntent = Intent(context, WearCompanionSyncReceiver::class.java).apply {
                action = WearCompanionSyncReceiver.ACTION_LOCAL_STOP_TRACKING_BUTTON
            }
            val stopPendingIntent = PendingIntent.getBroadcast(
                context,
                303,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val platLabel = if (journey.isPlatformTbd) {
                context.getString(com.androidfung.departureboard.wear.R.string.wear_plat_tbd)
            } else {
                context.getString(com.androidfung.departureboard.wear.R.string.wear_plat_code, journey.cleanPlatformCode)
            }
            val title = "$platLabel • ${journey.destinationName}"
            val subtitle = if (!journey.targetAlightStopName.isNullOrBlank()) {
                val stopsStr = journey.stopsAway?.let {
                    context.resources.getQuantityString(com.androidfung.departureboard.wear.R.plurals.wear_stops_suffix, it, it)
                }.orEmpty()
                "🎯 " + context.getString(
                    com.androidfung.departureboard.wear.R.string.wear_alight_target,
                    journey.targetAlightStopName,
                    stopsStr
                )
            } else {
                journey.currentLocation?.takeIf { it.isNotBlank() } ?: journey.lineName
            }

            val isOngoing = !journey.isCancelled && journey.remainingSeconds > 0
            val targetTimestampMs = System.currentTimeMillis() + (journey.remainingSeconds.coerceAtLeast(0) * 1000L)
            val shouldWakeWithFullScreen = isInitialStart && prefs.autoLaunchSprintHudEnabled

            val builder = NotificationCompat.Builder(
                context,
                if (shouldWakeWithFullScreen) CHANNEL_SPRINT_HUD_ID else CHANNEL_TRACKING_ID
            )
                .setSmallIcon(com.androidfung.departureboard.wear.R.drawable.ic_train)
                .setColor(journey.lineColorArgb.toInt())
                .setColorized(true)
                .setSubText(journey.lineName)
                .setContentTitle(title)
                .setContentText(subtitle)
                .setProgress(100, journey.progressPercent, false)
                .setWhen(targetTimestampMs)
                .setShowWhen(isOngoing)
                .setUsesChronometer(isOngoing)
                .setChronometerCountDown(isOngoing)
                .setOngoing(isOngoing)
                .setOnlyAlertOnce(!shouldWakeWithFullScreen)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
                .setContentIntent(openHudPendingIntent)
                .addAction(
                    android.R.drawable.ic_menu_compass,
                    context.getString(com.androidfung.departureboard.wear.R.string.wear_action_sprint_hud),
                    openHudPendingIntent
                )
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    context.getString(com.androidfung.departureboard.wear.R.string.wear_action_stop),
                    stopPendingIntent
                )
                .extend(
                    NotificationCompat.WearableExtender()
                        .setContentAction(0)
                )

            if (shouldWakeWithFullScreen) {
                val canUseFsi = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                    nm.canUseFullScreenIntent()
                if (canUseFsi) {
                    builder.setFullScreenIntent(openHudPendingIntent, true)
                }
            }

            if (isOngoing) {
                runCatching {
                    val statusText = "$platLabel • ${journey.formattedMmSsCountdown}"
                    val ongoingStatus = androidx.wear.ongoing.Status.Builder()
                        .addTemplate(statusText)
                        .build()
                    val ongoingActivity = androidx.wear.ongoing.OngoingActivity.Builder(
                        context.applicationContext,
                        NOTIFICATION_ID_TRACKED,
                        builder
                    )
                        .setStaticIcon(com.androidfung.departureboard.wear.R.drawable.ic_train)
                        .setTouchIntent(openHudPendingIntent)
                        .setStatus(ongoingStatus)
                        .build()
                    ongoingActivity.apply(context.applicationContext)
                }
            }

            nm.notify(NOTIFICATION_ID_TRACKED, builder.build())
        }
    }

    fun cancelTrackedJourneyNotification(context: Context) {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            nm.cancel(NOTIFICATION_ID_TRACKED)
        }
    }

    fun isAlertTypeEnabled(
        context: Context,
        type: WearConcourseHapticAnnouncer.WristAlertType
    ): Boolean {
        val prefs = loadPreferences(context)
        return when (type) {
            WearConcourseHapticAnnouncer.WristAlertType.PLATFORM_ANNOUNCED,
            WearConcourseHapticAnnouncer.WristAlertType.PLATFORM_CHANGED -> prefs.platformAlertsEnabled
            WearConcourseHapticAnnouncer.WristAlertType.ALIGHT_NEXT_STOP -> prefs.alightingAlertsEnabled
            WearConcourseHapticAnnouncer.WristAlertType.DELAY_REPAY_ELIGIBLE -> prefs.disruptionAlertsEnabled
        }
    }

    fun postConcourseAlertNotification(
        context: Context,
        event: WearConcourseHapticAnnouncer.WristHapticEvent
    ) {
        if (!isAlertTypeEnabled(context, event.type)) return
        if (!hasNotificationPermission(context)) return
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            ensureChannels(context, nm)

            val (channelId, notificationId) = when (event.type) {
                WearConcourseHapticAnnouncer.WristAlertType.PLATFORM_ANNOUNCED,
                WearConcourseHapticAnnouncer.WristAlertType.PLATFORM_CHANGED ->
                    CHANNEL_PLATFORM_ALERTS_ID to NOTIFICATION_ID_PLATFORM_ALERT
                WearConcourseHapticAnnouncer.WristAlertType.ALIGHT_NEXT_STOP ->
                    CHANNEL_ALIGHTING_ALERTS_ID to NOTIFICATION_ID_ALIGHTING_ALERT
                WearConcourseHapticAnnouncer.WristAlertType.DELAY_REPAY_ELIGIBLE ->
                    CHANNEL_DISRUPTION_ALERTS_ID to NOTIFICATION_ID_DISRUPTION_ALERT
            }

            val openIntent = Intent(context, WearDepartureBoardActivity::class.java).apply {
                putExtra(EXTRA_OPEN_SPRINT_HUD, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(com.androidfung.departureboard.wear.R.drawable.ic_railway_alert)
                .setContentTitle("🔔 ${event.headline}")
                .setContentText(event.detail)
                .setStyle(NotificationCompat.BigTextStyle().bigText(event.detail))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pendingIntent)
                .build()

            nm.notify(notificationId, notification)
        }
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    @Volatile
    private var channelsInitialized = false

    private fun ensureChannels(context: Context, nm: NotificationManager) {
        if (channelsInitialized) return
        val trackingChannel = NotificationChannel(
            CHANNEL_TRACKING_ID,
            context.getString(com.androidfung.departureboard.wear.R.string.wear_channel_tracking_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(com.androidfung.departureboard.wear.R.string.wear_channel_tracking_desc)
            setShowBadge(true)
        }

        val sprintHudChannel = NotificationChannel(
            CHANNEL_SPRINT_HUD_ID,
            context.getString(com.androidfung.departureboard.wear.R.string.wear_channel_sprint_hud_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(com.androidfung.departureboard.wear.R.string.wear_channel_sprint_hud_desc)
            enableVibration(true)
            setShowBadge(true)
        }

        val platformAlertsChannel = NotificationChannel(
            CHANNEL_PLATFORM_ALERTS_ID,
            context.getString(com.androidfung.departureboard.wear.R.string.wear_channel_platform_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(com.androidfung.departureboard.wear.R.string.wear_channel_platform_desc)
            enableVibration(true)
            setShowBadge(true)
        }

        val alightingAlertsChannel = NotificationChannel(
            CHANNEL_ALIGHTING_ALERTS_ID,
            context.getString(com.androidfung.departureboard.wear.R.string.wear_channel_alighting_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(com.androidfung.departureboard.wear.R.string.wear_channel_alighting_desc)
            enableVibration(true)
            setShowBadge(true)
        }

        val disruptionAlertsChannel = NotificationChannel(
            CHANNEL_DISRUPTION_ALERTS_ID,
            context.getString(com.androidfung.departureboard.wear.R.string.wear_channel_disruption_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(com.androidfung.departureboard.wear.R.string.wear_channel_disruption_desc)
            enableVibration(true)
            setShowBadge(true)
        }

        nm.createNotificationChannel(trackingChannel)
        nm.createNotificationChannel(sprintHudChannel)
        nm.createNotificationChannel(platformAlertsChannel)
        nm.createNotificationChannel(alightingAlertsChannel)
        nm.createNotificationChannel(disruptionAlertsChannel)
        channelsInitialized = true
    }
}
