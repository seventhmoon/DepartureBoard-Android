package com.androidfung.departureboard.widget

import android.content.Context
import androidx.core.content.edit
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class DepartureBoardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DepartureBoardWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateWorker.enqueuePeriodicUpdate(context, forceUpdate = true)
        WidgetDisruptionNotificationManager.createNotificationChannel(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val sp = context.getSharedPreferences("widget_config", Context.MODE_PRIVATE)
        val removedTargets = appWidgetIds.toList().mapNotNull { id ->
            val stationId = sp.getString("station_id_$id", null)
            val filterLineId = sp.getString("filter_line_id_$id", null)
            stationId?.takeIf { it.isNotBlank() }?.let { it to filterLineId }
        }
        sp.edit {
            for (id in appWidgetIds) {
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
                WidgetDisruptionNotificationManager.cancelNotificationForWidget(context, stationId, filterLineId)
            }
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetUpdateWorker.cancelPeriodicUpdate(context)
        WidgetDisruptionNotificationManager.cancelAllDisruptionNotifications(context)
    }
}
