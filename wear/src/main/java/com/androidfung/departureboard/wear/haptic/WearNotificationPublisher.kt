package com.androidfung.departureboard.wear.haptic

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.androidfung.departureboard.wear.model.WearTrackedJourneyState
import com.androidfung.departureboard.wear.sync.WearCompanionSyncReceiver
import com.androidfung.departureboard.wear.ui.WearDepartureBoardActivity

/**
 * Publishes native Wear OS Live Tracker notifications directly into the Watch Notification Tray:
 *
 * Features:
 * 1. **Hardware Per-Second Countdown (`setUsesChronometer(true)` + `setChronometerCountDown(true)`)**:
 *    Ticks down every second (`03:59 -> 03:58`) natively on the watch without waking the CPU.
 * 2. **Live Progress Bar (`setProgress(100, progressPercent, false)`)** + Line Brand Accent Color.
 * 3. **Direct Watch Actions**:
 *    - `🎯 Sprint HUD`: Launches straight into the full-screen Concourse Sprint & Alighting HUD.
 *    - `⏹ Stop`: Stops tracking on both watch and phone in 1 tap from the notification shade.
 */
object WearNotificationPublisher {

    private const val CHANNEL_TRACKING_ID = "wear_live_tracking_channel"
    private const val CHANNEL_ALERTS_ID = "wear_concourse_alerts_channel"

    private const val NOTIFICATION_ID_TRACKED = 3001
    private const val NOTIFICATION_ID_ALERT = 3002

    const val EXTRA_OPEN_SPRINT_HUD = "extra_open_sprint_hud"

    fun launchSprintHudImmediately(context: Context) {
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
        if (!hasNotificationPermission(context)) return
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            ensureChannels(nm)

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
                action = WearCompanionSyncReceiver.ACTION_STOP_TRACKED_DEPARTURE
            }
            val stopPendingIntent = PendingIntent.getBroadcast(
                context,
                303,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val platLabel = if (journey.isPlatformTbd) "Plat TBD" else "Plat ${journey.cleanPlatformCode}"
            val title = "$platLabel • ${journey.destinationName}"
            val subtitle = if (!journey.targetAlightStopName.isNullOrBlank()) {
                val stopsStr = journey.stopsAway?.let { " ($it stops)" }.orEmpty()
                "🎯 Alight: ${journey.targetAlightStopName}$stopsStr"
            } else {
                journey.currentLocation?.takeIf { it.isNotBlank() } ?: journey.lineName
            }

            val isOngoing = !journey.isCancelled && journey.remainingSeconds > 0
            val targetTimestampMs = System.currentTimeMillis() + (journey.remainingSeconds.coerceAtLeast(0) * 1000L)

            val builder = NotificationCompat.Builder(context, if (isInitialStart) CHANNEL_ALERTS_ID else CHANNEL_TRACKING_ID)
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
                .setOnlyAlertOnce(!isInitialStart)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
                .setContentIntent(openHudPendingIntent)
                .addAction(
                    android.R.drawable.ic_menu_compass,
                    "Sprint HUD",
                    openHudPendingIntent
                )
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Stop",
                    stopPendingIntent
                )
                .extend(
                    NotificationCompat.WearableExtender()
                        .setContentAction(0)
                )

            if (isInitialStart) {
                builder.setFullScreenIntent(openHudPendingIntent, true)
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

    fun postConcourseAlertNotification(
        context: Context,
        event: WearConcourseHapticAnnouncer.WristHapticEvent
    ) {
        if (!hasNotificationPermission(context)) return
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            ensureChannels(nm)

            val openIntent = Intent(context, WearDepartureBoardActivity::class.java).apply {
                putExtra(EXTRA_OPEN_SPRINT_HUD, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                302,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS_ID)
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

            nm.notify(NOTIFICATION_ID_ALERT, notification)
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

    private fun ensureChannels(nm: NotificationManager) {
        val trackingChannel = NotificationChannel(
            CHANNEL_TRACKING_ID,
            "Live Train Tracker",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Live countdown timer and progress bar for your tracked train"
            setShowBadge(true)
        }

        val alertsChannel = NotificationChannel(
            CHANNEL_ALERTS_ID,
            "Platform & Alighting Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Heads-up alerts when a platform is announced (TBD -> Plat X) or 1 stop before alighting"
            enableVibration(true)
            setShowBadge(true)
        }

        nm.createNotificationChannel(trackingChannel)
        nm.createNotificationChannel(alertsChannel)
    }
}
