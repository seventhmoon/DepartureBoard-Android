package com.androidfung.departureboard.wear.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.androidfung.departureboard.core.wearcontract.WearSyncContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Receives live station boards and tracked train updates pushed from the Phone app (`:app`),
 * plus local notification action intents on the watch.
 */
class WearCompanionSyncReceiver : BroadcastReceiver() {

    companion object {
        private const val ASYNC_RECEIVER_TIMEOUT_MS = 8_000L
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        const val ACTION_SYNC_STATION_BOARDS = WearSyncContract.ACTION_SYNC_STATION_BOARDS
        const val ACTION_SYNC_TRACKED_DEPARTURE = WearSyncContract.ACTION_SYNC_TRACKED_DEPARTURE
        const val ACTION_STOP_TRACKED_DEPARTURE = WearSyncContract.ACTION_STOP_TRACKED_DEPARTURE
        const val ACTION_LOCAL_STOP_TRACKING_BUTTON = WearSyncContract.ACTION_LOCAL_STOP_TRACKING_BUTTON
        const val ACTION_SYNC_AI_RESPONSE = WearSyncContract.ACTION_SYNC_AI_RESPONSE
        const val ACTION_SYNC_NOTIFICATION_PREFS = WearSyncContract.ACTION_SYNC_NOTIFICATION_PREFS

        const val EXTRA_BOARDS_JSON = WearSyncContract.EXTRA_BOARDS_JSON
        const val EXTRA_TRACKED_JSON = WearSyncContract.EXTRA_TRACKED_JSON
        const val EXTRA_AI_RESPONSE_JSON = WearSyncContract.EXTRA_AI_RESPONSE_JSON
        const val EXTRA_NOTIFICATION_PREFS_JSON = WearSyncContract.EXTRA_NOTIFICATION_PREFS_JSON
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val boardsJson = intent.getStringExtra(EXTRA_BOARDS_JSON)
        val trackedJson = intent.getStringExtra(EXTRA_TRACKED_JSON)
        val aiJson = intent.getStringExtra(EXTRA_AI_RESPONSE_JSON)
        val prefsJson = intent.getStringExtra(EXTRA_NOTIFICATION_PREFS_JSON)
        val appContext = context.applicationContext

        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                withTimeoutOrNull(ASYNC_RECEIVER_TIMEOUT_MS) {
                    when (action) {
                        ACTION_SYNC_STATION_BOARDS -> {
                            if (boardsJson != null) {
                                WearCompanionStateRepository.onReceiveStationBoardsJson(boardsJson, appContext)
                            }
                        }
                        ACTION_SYNC_TRACKED_DEPARTURE -> {
                            if (trackedJson != null) {
                                WearCompanionStateRepository.onReceiveTrackedDepartureJson(appContext, trackedJson)
                            }
                        }
                        ACTION_STOP_TRACKED_DEPARTURE -> {
                            // Originated from Phone -> clear local watch tracking state WITHOUT echoing back to Phone
                            WearCompanionStateRepository.onReceiveStopTrackedDeparture(appContext)
                        }
                        ACTION_LOCAL_STOP_TRACKING_BUTTON -> {
                            // User explicitly tapped Stop on the Watch notification -> stop locally AND notify Phone
                            WearCompanionStateRepository.onReceiveStopTrackedDeparture(appContext)
                            WearCompanionStateRepository.stopRemoteTrackingOnPhone(appContext)
                        }
                        ACTION_SYNC_AI_RESPONSE -> {
                            if (aiJson != null) {
                                WearCompanionStateRepository.onReceiveAiResponseJson(aiJson)
                            }
                        }
                        ACTION_SYNC_NOTIFICATION_PREFS -> {
                            if (prefsJson != null) {
                                com.androidfung.departureboard.wear.haptic.WearNotificationPublisher.applySyncedPhonePreferences(appContext, prefsJson)
                            }
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
