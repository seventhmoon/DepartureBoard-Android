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
import com.androidfung.departureboard.wear.ui.WearDepartureBoardActivity

/**
 * Publishes native Wear OS notifications directly into the Watch Notification Tray:
 *
 * Why this is required on Wear OS:
 * 1. Wear OS never bridges `.setOngoing(true)` foreground service notifications from a paired phone.
 * 2. When a companion watch app with the same `applicationId` is installed, Wear OS expects the
 *    watch app to post its own local notifications with watch-native `PendingIntent`s.
 */
object WearNotificationPublisher {

    private const val CHANNEL_TRACKING_ID = "wear_live_tracking_channel"
    private const val CHANNEL_ALERTS_ID = "wear_concourse_alerts_channel"

    private const val NOTIFICATION_ID_TRACKED = 3001
    private const val NOTIFICATION_ID_ALERT = 3002

    fun postTrackedJourneyNotification(
        context: Context,
        journey: WearTrackedJourneyState
    ) {
        if (!hasNotificationPermission(context)) return
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            ensureChannels(nm)

            val openIntent = Intent(context, WearDepartureBoardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                301,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val platLabel = if (journey.isPlatformTbd) "Plat TBD" else "Plat ${journey.platformCode}"
            val title = "$platLabel • ${journey.formattedCountdown} → ${journey.destinationName}"
            val subtitle = if (!journey.targetAlightStopName.isNullOrBlank()) {
                val stopsStr = journey.stopsAway?.let { " ($it stops)" }.orEmpty()
                "Alight: ${journey.targetAlightStopName}$stopsStr"
            } else {
                journey.currentLocation?.takeIf { it.isNotBlank() } ?: journey.lineName
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_TRACKING_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(subtitle)
                .setOngoing(!journey.isCancelled && journey.remainingSeconds > 0)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setContentIntent(pendingIntent)
                .build()

            nm.notify(NOTIFICATION_ID_TRACKED, notification)
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
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                302,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
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
            "Live Train Tracking",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Mirrors your active tracked train countdown on your watch"
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
