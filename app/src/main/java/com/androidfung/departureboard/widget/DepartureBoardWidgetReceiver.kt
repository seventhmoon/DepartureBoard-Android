package com.androidfung.departureboard.widget

import android.content.Context
import androidx.core.content.edit
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class DepartureBoardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DepartureBoardWidget()

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val pendingResult = runCatching { goAsync() }.getOrNull()
        val appContext = context.applicationContext
        receiverScope.launch {
            try {
                withTimeoutOrNull(8_000L) {
                    WidgetUpdateWorker.enqueuePeriodicUpdate(appContext, forceUpdate = true)
                    WidgetDisruptionNotificationManager.createNotificationChannel(appContext)
                }
            } finally {
                pendingResult?.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val pendingResult = runCatching { goAsync() }.getOrNull()
        val appContext = context.applicationContext
        val idsCopy = appWidgetIds.copyOf()
        receiverScope.launch {
            try {
                withTimeoutOrNull(8_000L) {
                    val sp = appContext.getSharedPreferences("widget_config", Context.MODE_PRIVATE)
                    val removedTargets = idsCopy.toList().mapNotNull { id ->
                        val stationId = sp.getString("station_id_$id", null)
                        val filterLineId = sp.getString("filter_line_id_$id", null)
                        stationId?.takeIf { it.isNotBlank() }?.let { it to filterLineId }
                    }
                    sp.edit(commit = true) {
                        for (id in idsCopy) {
                            remove("station_id_$id")
                            remove("station_name_$id")
                            remove("filter_line_id_$id")
                            remove("filter_line_name_$id")
                            remove("filter_direction_$id")
                        }
                    }
                    val remainingPairs = sp.all.keys
                        .filter { it.startsWith("station_id_") }
                        .mapNotNull { key ->
                            val wid = key.removePrefix("station_id_")
                            val stId = sp.getString(key, null) ?: return@mapNotNull null
                            val lnId = sp.getString("filter_line_id_$wid", null)
                            stId to lnId
                        }
                        .toSet()

                    for ((stationId, filterLineId) in removedTargets) {
                        if ((stationId to filterLineId) !in remainingPairs) {
                            WidgetDisruptionNotificationManager.cancelNotificationForWidget(appContext, stationId, filterLineId)
                        }
                    }
                }
            } finally {
                pendingResult?.finish()
            }
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        val pendingResult = runCatching { goAsync() }.getOrNull()
        val appContext = context.applicationContext
        receiverScope.launch {
            try {
                withTimeoutOrNull(8_000L) {
                    WidgetUpdateWorker.cancelPeriodicUpdate(appContext)
                    WidgetDisruptionNotificationManager.cancelAllDisruptionNotifications(appContext)
                }
            } finally {
                pendingResult?.finish()
            }
        }
    }
}
