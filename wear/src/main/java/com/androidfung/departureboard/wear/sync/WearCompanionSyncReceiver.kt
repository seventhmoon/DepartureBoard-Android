package com.androidfung.departureboard.wear.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.androidfung.departureboard.core.wearcontract.WearSyncContract

/**
 * Receives live station boards and tracked train updates pushed from the Phone app (`:app`).
 */
class WearCompanionSyncReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SYNC_STATION_BOARDS = WearSyncContract.ACTION_SYNC_STATION_BOARDS
        const val ACTION_SYNC_TRACKED_DEPARTURE = WearSyncContract.ACTION_SYNC_TRACKED_DEPARTURE
        const val ACTION_STOP_TRACKED_DEPARTURE = WearSyncContract.ACTION_STOP_TRACKED_DEPARTURE
        const val ACTION_SYNC_AI_RESPONSE = WearSyncContract.ACTION_SYNC_AI_RESPONSE
        const val ACTION_SYNC_NOTIFICATION_PREFS = WearSyncContract.ACTION_SYNC_NOTIFICATION_PREFS

        const val EXTRA_BOARDS_JSON = WearSyncContract.EXTRA_BOARDS_JSON
        const val EXTRA_TRACKED_JSON = WearSyncContract.EXTRA_TRACKED_JSON
        const val EXTRA_AI_RESPONSE_JSON = WearSyncContract.EXTRA_AI_RESPONSE_JSON
        const val EXTRA_NOTIFICATION_PREFS_JSON = WearSyncContract.EXTRA_NOTIFICATION_PREFS_JSON
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        when (action) {
            ACTION_SYNC_STATION_BOARDS -> {
                val boardsJson = intent.getStringExtra(EXTRA_BOARDS_JSON) ?: return
                WearCompanionStateRepository.onReceiveStationBoardsJson(boardsJson, context)
            }
            ACTION_SYNC_TRACKED_DEPARTURE -> {
                val trackedJson = intent.getStringExtra(EXTRA_TRACKED_JSON) ?: return
                WearCompanionStateRepository.onReceiveTrackedDepartureJson(context, trackedJson)
            }
            ACTION_STOP_TRACKED_DEPARTURE -> {
                WearCompanionStateRepository.onReceiveStopTrackedDeparture(context)
                WearCompanionStateRepository.stopRemoteTrackingOnPhone(context)
            }
            ACTION_SYNC_AI_RESPONSE -> {
                val aiJson = intent.getStringExtra(EXTRA_AI_RESPONSE_JSON) ?: return
                WearCompanionStateRepository.onReceiveAiResponseJson(aiJson)
            }
            ACTION_SYNC_NOTIFICATION_PREFS -> {
                val prefsJson = intent.getStringExtra(EXTRA_NOTIFICATION_PREFS_JSON) ?: return
                com.androidfung.departureboard.wear.haptic.WearNotificationPublisher.applySyncedPhonePreferences(context, prefsJson)
            }
        }
    }
}
