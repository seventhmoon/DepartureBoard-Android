package com.androidfung.departureboard.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.androidfung.departureboard.appContainer
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Background worker that periodically refreshes all placed DepartureBoardWidget instances.
 */
class WidgetUpdateWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val manager = GlanceAppWidgetManager(appContext)
            val glanceIds = manager.getGlanceIds(DepartureBoardWidget::class.java)
            if (glanceIds.isEmpty()) {
                return Result.success()
            }
            val nowMs = System.currentTimeMillis()
            val widget = DepartureBoardWidget()
            glanceIds.forEach { glanceId ->
                updateAppWidgetState(appContext, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    prefs.toMutablePreferences().apply {
                        this[DepartureBoardWidget.PREF_LAST_REFRESH_TIMESTAMP] = nowMs
                    }
                }
                widget.update(appContext, glanceId)
            }

            // Check for service interruptions or closures on pinned stations/lines and notify user
            try {
                val repository = appContext.appContainer.transitRepository
                WidgetDisruptionNotificationManager.checkAndNotifyDisruptions(appContext, repository)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("WidgetUpdateWorker", "Disruption notification check encountered an error", e)
            }

            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "DepartureBoardWidgetUpdateWork"

        fun cancelPeriodicUpdate(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        fun enqueuePeriodicUpdate(context: Context, forceUpdate: Boolean = false) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                if (forceUpdate) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
