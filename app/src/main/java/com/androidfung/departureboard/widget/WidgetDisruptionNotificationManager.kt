package com.androidfung.departureboard.widget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.androidfung.departureboard.MainActivity
import com.androidfung.departureboard.R
import com.androidfung.departureboard.data.model.AlertEffect
import com.androidfung.departureboard.data.model.AlertSeverity
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TransitAlert
import com.androidfung.departureboard.data.model.gb.TflLineStatusItem
import com.androidfung.departureboard.data.repository.TransitRepository
import com.androidfung.departureboard.ui.dashboard.StationBadgeSynthesizer
import com.androidfung.departureboard.ui.designsystem.TransitIconHelper
import com.androidfung.departureboard.util.DepartureFilterHelper
import com.androidfung.departureboard.util.NotificationHelper
import kotlinx.coroutines.flow.first

/**
 * Monitors transit stations and lines placed on the home screen as Glance widgets.
 * Evaluates real-time service disruptions, delays, cancellations, and unexpected station closures
 * (e.g. lack of staff, emergency, fire alert, flooding), notifying users via high-priority notifications.
 *
 * Normal scheduled overnight closures (e.g. end of regular service) do NOT trigger notifications.
 *
 * Line Filter Guarantee:
 * If a user filtered a specific line on a widget, notifications are ONLY triggered for issues
 * affecting that specific line. Disruptions or closures on other lines serving the same station are ignored.
 */
object WidgetDisruptionNotificationManager {

    private const val TAG = "WidgetDisruptionMonitor"
    const val CHANNEL_ID = "widget_disruption_channel"
    private const val PREFS_DISRUPTION_STATE = "widget_disruption_state"
    private const val PREF_KEY_PREFIX = "last_disruption_fp_"
    private val URL_REGEX = Regex("""https?://[^\s]+""")

    data class MonitoredWidgetTarget(
        val appWidgetId: Int,
        val stationId: String,
        val stationName: String,
        val filterLineId: String? = null,
        val filterLineName: String? = null,
        val filterDirection: String? = null
    )

    data class DisruptionEvaluation(
        val isDisrupted: Boolean,
        val isSevere: Boolean,
        val title: String,
        val message: String,
        val fingerprint: String
    )

    /**
     * Creates the Android notification channel for home screen disruption alerts.
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.notification_channel_widget_disruptions)
            val descriptionText = context.getString(R.string.notification_channel_widget_disruptions_desc)
            val channel = NotificationChannel(
                CHANNEL_ID,
                name,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = descriptionText
                enableVibration(true)
                setShowBadge(true)
            }
            val notificationManager = context.getSystemService<NotificationManager>()
            notificationManager?.createNotificationChannel(channel)
        }
    }

    /**
     * Resolves all currently placed DepartureBoardWidget instances and their configured stations and lines.
     */
    suspend fun getMonitoredTargets(context: Context): List<MonitoredWidgetTarget> {
        val glanceManager = GlanceAppWidgetManager(context)
        val glanceIds = try {
            glanceManager.getGlanceIds(DepartureBoardWidget::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve Glance widget IDs", e)
            return emptyList()
        }

        if (glanceIds.isEmpty()) return emptyList()

        val sp = context.getSharedPreferences("widget_config", Context.MODE_PRIVATE)
        val targets = mutableListOf<MonitoredWidgetTarget>()

        for (glanceId in glanceIds) {
            val appWidgetId = try {
                glanceManager.getAppWidgetId(glanceId)
            } catch (_: Exception) {
                -1
            }

            val prefs = try {
                getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
            } catch (_: Exception) {
                null
            }

            val fastStationId = if (appWidgetId != -1) sp.getString("station_id_$appWidgetId", null) else null
            val fastStationName = if (appWidgetId != -1) sp.getString("station_name_$appWidgetId", null) else null
            val fastFilterLineId = if (appWidgetId != -1) sp.getString("filter_line_id_$appWidgetId", null) else null
            val fastFilterLineName = if (appWidgetId != -1) sp.getString("filter_line_name_$appWidgetId", null) else null
            val fastFilterDirection = if (appWidgetId != -1) sp.getString("filter_direction_$appWidgetId", null) else null

            val stationId = prefs?.get(DepartureBoardWidget.PREF_STATION_ID) ?: fastStationId
            val stationName = prefs?.get(DepartureBoardWidget.PREF_STATION_NAME) ?: fastStationName
            val filterLineId = prefs?.get(DepartureBoardWidget.PREF_FILTER_LINE_ID) ?: fastFilterLineId
            val filterLineName = prefs?.get(DepartureBoardWidget.PREF_FILTER_LINE_NAME) ?: fastFilterLineName
            val filterDirection = prefs?.get(DepartureBoardWidget.PREF_FILTER_DIRECTION) ?: fastFilterDirection

            if (!stationId.isNullOrBlank()) {
                targets.add(
                    MonitoredWidgetTarget(
                        appWidgetId = appWidgetId,
                        stationId = stationId,
                        stationName = stationName ?: context.getString(R.string.widget_station_name_fallback),
                        filterLineId = filterLineId?.takeIf { it.isNotBlank() },
                        filterLineName = filterLineName?.takeIf { it.isNotBlank() },
                        filterDirection = filterDirection?.takeIf { it.isNotBlank() }
                    )
                )
            }
        }
        return targets
    }

    /**
     * Pure, testable disruption evaluation for a given monitored target.
     * Enforces strict line filtering: if [MonitoredWidgetTarget.filterLineId] is present,
     * disruptions or closures on other lines at the same station are ignored.
     *
     * Note: Normal scheduled overnight closures do NOT trigger notifications.
     * Notifications are only triggered for delays, cancellations, and unexpected station closures
     * (e.g. lack of staff, emergency, fire alarm, flooding).
     */
    fun evaluateTargetDisruption(
        target: MonitoredWidgetTarget,
        allDepartures: List<Departure>,
        lineStatuses: Map<String, TflLineStatusItem>,
        transitAlerts: List<TransitAlert>,
        context: Context? = null
    ): DisruptionEvaluation {
        // 1. Filter departures to the target line and direction if specified
        val filteredDepartures = DepartureFilterHelper.filterDepartures(
            departures = allDepartures,
            lineId = target.filterLineId,
            direction = target.filterDirection
        )

        val targetStation = Station(id = target.stationId, name = target.stationName)

        // 2. Build line badges to check line-specific statuses and unexpected closure reasons
        val badges = StationBadgeSynthesizer.buildStationLineBadges(targetStation, allDepartures, lineStatuses)
        val relevantBadges = if (!target.filterLineId.isNullOrBlank()) {
            badges.filter { badge ->
                TransitIconHelper.isSameLine(badge.lineId, target.filterLineId) ||
                    badge.displayName.equals(target.filterLineName, ignoreCase = true) ||
                    badge.lineId.equals(target.filterLineId, ignoreCase = true)
            }
        } else {
            badges
        }

        // 3. Check for unexpected station / line closure (e.g. lack of staff, emergency, closed status)
        val unexpectedClosureBadge = relevantBadges.firstOrNull { badge ->
            badge.statusSeverity in listOf(1, 2, 4, 5, 16, 20) ||
                badge.statusDescription?.lowercase().orEmpty().let { it.contains("closed") || it.contains("closure") || it.contains("suspended") } ||
                badge.disruptionReason?.lowercase().orEmpty().let { it.contains("closed") || it.contains("closure") || it.contains("staff") }
        }

        // 4. Check applicable GTFS-RT / transit alerts with line isolation
        val applicableAlert = transitAlerts.firstOrNull { alert ->
            if (!target.filterLineId.isNullOrBlank()) {
                // Must explicitly match the filtered line, or apply to the station without a competing line restriction
                alert.appliesToLine(target.filterLineId) || (
                    alert.appliesToStation(target.stationId) &&
                        alert.informedEntities.none { entity ->
                            !entity.lineId.isNullOrBlank() && !TransitIconHelper.isSameLine(entity.lineId, target.filterLineId)
                        }
                )
            } else {
                alert.appliesToStation(target.stationId)
            }
        }

        val hasUnexpectedAlertClosure = applicableAlert != null && (
            applicableAlert.effect == AlertEffect.NO_SERVICE ||
                applicableAlert.severity == AlertSeverity.SEVERE ||
                applicableAlert.headerText.lowercase().let { it.contains("closed") || it.contains("closure") || it.contains("staff") }
        )

        // 5. Evaluate general delays and cancellations via WidgetInterruptionHelper
        val interruption = WidgetInterruptionHelper.resolveInterruption(
            station = targetStation,
            departures = allDepartures,
            filteredDepartures = filteredDepartures,
            lineStatuses = lineStatuses,
            filterLineId = target.filterLineId,
            filterLineName = target.filterLineName,
            context = context
        )

        val title = if (!target.filterLineName.isNullOrBlank()) {
            context?.getString(R.string.widget_notification_disruption_title_line, target.stationName, target.filterLineName)
                ?: "${target.stationName} • ${target.filterLineName}"
        } else {
            target.stationName
        }

        return when {
            // Priority 1: Unexpected station or line closure flagged in line status / reason (e.g. lack of staff)
            unexpectedClosureBadge != null -> {
                val cleanReason = unexpectedClosureBadge.disruptionReason?.replace(URL_REGEX, "")?.trim()
                val message = when {
                    !cleanReason.isNullOrBlank() -> cleanReason
                    !unexpectedClosureBadge.statusDescription.isNullOrBlank() -> unexpectedClosureBadge.statusDescription
                    else -> context?.getString(R.string.widget_notification_station_closed) ?: "Station is closed"
                }
                DisruptionEvaluation(
                    isDisrupted = true,
                    isSevere = true,
                    title = title,
                    message = message,
                    fingerprint = "CLOSED_${unexpectedClosureBadge.statusSeverity}_${message}"
                )
            }
            // Priority 2: Unexpected closure or severe incident in transit alerts (e.g. NRCC / GTFS-RT)
            hasUnexpectedAlertClosure && applicableAlert != null -> {
                DisruptionEvaluation(
                    isDisrupted = true,
                    isSevere = true,
                    title = title,
                    message = applicableAlert.headerText,
                    fingerprint = "ALERT_${applicableAlert.id}_${applicableAlert.headerText}"
                )
            }
            // Priority 3: Delays or cancellations on the monitored line
            interruption.level != WidgetInterruptionHelper.InterruptionLevel.NONE -> {
                val message = interruption.statusText
                    ?: context?.getString(R.string.travel_alert_delays)
                    ?: "Delays"
                DisruptionEvaluation(
                    isDisrupted = true,
                    isSevere = interruption.level == WidgetInterruptionHelper.InterruptionLevel.SEVERE,
                    title = title,
                    message = message,
                    fingerprint = "${interruption.level}_${message}"
                )
            }
            // Normal operating state
            else -> {
                DisruptionEvaluation(
                    isDisrupted = false,
                    isSevere = false,
                    title = title,
                    message = "",
                    fingerprint = "NORMAL"
                )
            }
        }
    }

    /**
     * Checks all home screen widgets for delays or closures and dispatches notifications when issues arise.
     */
    suspend fun checkAndNotifyDisruptions(
        context: Context,
        repository: TransitRepository
    ) {
        if (!NotificationHelper.areNotificationsEnabled(context)) {
            Log.d(TAG, "Notifications disabled, skipping disruption check")
            return
        }

        val isFeatureEnabled = try {
            repository.widgetDisruptionNotificationsEnabledFlow.first()
        } catch (_: Exception) {
            true
        }

        if (!isFeatureEnabled) {
            Log.d(TAG, "Widget disruption notifications disabled by user")
            return
        }

        val targets = getMonitoredTargets(context)
        if (targets.isEmpty()) return

        createNotificationChannel(context)

        // Deduplicate targets having identical station, line, and direction filters
        val uniqueTargets = targets.distinctBy {
            "${it.stationId}_${it.filterLineId.orEmpty()}_${it.filterDirection.orEmpty()}"
        }

        // Pre-fetch shared line statuses and transit alerts once per background cycle
        val lineStatuses = try {
            repository.getLineStatuses()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch line statuses", e)
            emptyMap()
        }

        val transitAlerts = try {
            repository.getTransitAlerts()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch transit alerts", e)
            emptyList()
        }

        val statePrefs = context.getSharedPreferences(PREFS_DISRUPTION_STATE, Context.MODE_PRIVATE)
        val notificationManager = context.getSystemService<NotificationManager>() ?: return

        for (target in uniqueTargets) {
            val departuresResult = repository.getDepartures(target.stationId, target.stationName)
            val allDepartures = departuresResult.getOrDefault(emptyList())

            val evaluation = evaluateTargetDisruption(
                target = target,
                allDepartures = allDepartures,
                lineStatuses = lineStatuses,
                transitAlerts = transitAlerts,
                context = context
            )

            val targetKey = "${target.stationId}_${target.filterLineId.orEmpty()}"
            val prefKey = "$PREF_KEY_PREFIX$targetKey"
            val lastFingerprint = statePrefs.getString(prefKey, null)
            val notificationId = getNotificationId(target.stationId, target.filterLineId)

            if (evaluation.isDisrupted) {
                if (lastFingerprint != evaluation.fingerprint) {
                    postNotification(
                        context = context,
                        notificationManager = notificationManager,
                        notificationId = notificationId,
                        target = target,
                        evaluation = evaluation
                    )
                    statePrefs.edit().putString(prefKey, evaluation.fingerprint).apply()
                    Log.i(TAG, "Posted disruption notification for ${target.stationName} (${target.filterLineName}): ${evaluation.message}")
                }
            } else {
                if (lastFingerprint != null && lastFingerprint != "NORMAL") {
                    // Service has recovered: dismiss notification and clear stored disruption fingerprint
                    notificationManager.cancel(notificationId)
                    statePrefs.edit().remove(prefKey).apply()
                    Log.i(TAG, "Disruption resolved for ${target.stationName} (${target.filterLineName}), notification cleared")
                }
            }
        }
    }

    private fun postNotification(
        context: Context,
        notificationManager: NotificationManager,
        notificationId: Int,
        target: MonitoredWidgetTarget,
        evaluation: DisruptionEvaluation
    ) {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_STATION_ID, target.stationId)
            putExtra(MainActivity.EXTRA_STATION_NAME, target.stationName)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_railway_alert)
            .setContentTitle(evaluation.title)
            .setContentText(evaluation.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(evaluation.message))
            .setPriority(if (evaluation.isSevere) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    /**
     * Cancels all active disruption notifications and resets tracking fingerprints.
     */
    fun cancelAllDisruptionNotifications(context: Context) {
        val notificationManager = context.getSystemService<NotificationManager>()
        val statePrefs = context.getSharedPreferences(PREFS_DISRUPTION_STATE, Context.MODE_PRIVATE)

        statePrefs.all.keys.filter { it.startsWith(PREF_KEY_PREFIX) }.forEach { key ->
            val targetKey = key.removePrefix(PREF_KEY_PREFIX)
            val parts = targetKey.split("_", limit = 2)
            val stationId = parts.getOrNull(0).orEmpty()
            val filterLineId = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
            val notificationId = getNotificationId(stationId, filterLineId)
            notificationManager?.cancel(notificationId)
        }
        statePrefs.edit().clear().apply()
    }

    /**
     * Cancels a specific notification when a widget is removed from the home screen.
     */
    fun cancelNotificationForWidget(context: Context, stationId: String, filterLineId: String?) {
        val notificationManager = context.getSystemService<NotificationManager>()
        val notificationId = getNotificationId(stationId, filterLineId)
        notificationManager?.cancel(notificationId)

        val statePrefs = context.getSharedPreferences(PREFS_DISRUPTION_STATE, Context.MODE_PRIVATE)
        val targetKey = "${stationId}_${filterLineId.orEmpty()}"
        statePrefs.edit().remove("$PREF_KEY_PREFIX$targetKey").apply()
    }

    fun getNotificationId(stationId: String, filterLineId: String?): Int {
        return ("widget_disruption_${stationId}_${filterLineId.orEmpty()}").hashCode()
    }
}
