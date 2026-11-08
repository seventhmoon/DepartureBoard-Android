package com.androidfung.departureboard.wear.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives live station boards and tracked train updates pushed from the Phone app (`:app`).
 */
class WearCompanionSyncReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SYNC_STATION_BOARDS = "com.androidfung.departureboard.wear.action.SYNC_STATION_BOARDS"
        const val ACTION_SYNC_TRACKED_DEPARTURE = "com.androidfung.departureboard.wear.action.SYNC_TRACKED_DEPARTURE"
        const val ACTION_STOP_TRACKED_DEPARTURE = "com.androidfung.departureboard.wear.action.STOP_TRACKED_DEPARTURE"
        const val ACTION_SYNC_AI_RESPONSE = "com.androidfung.departureboard.wear.action.SYNC_AI_RESPONSE"

        const val EXTRA_BOARDS_JSON = "extra_boards_json"
        const val EXTRA_TRACKED_JSON = "extra_tracked_json"
        const val EXTRA_AI_RESPONSE_JSON = "extra_ai_response_json"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        when (action) {
            ACTION_SYNC_STATION_BOARDS -> {
                val boardsJson = intent.getStringExtra(EXTRA_BOARDS_JSON) ?: return
                WearCompanionStateRepository.onReceiveStationBoardsJson(boardsJson)
            }
            ACTION_SYNC_TRACKED_DEPARTURE -> {
                val trackedJson = intent.getStringExtra(EXTRA_TRACKED_JSON) ?: return
                WearCompanionStateRepository.onReceiveTrackedDepartureJson(context, trackedJson)
            }
            ACTION_STOP_TRACKED_DEPARTURE -> {
                WearCompanionStateRepository.onReceiveStopTrackedDeparture(context)
            }
            ACTION_SYNC_AI_RESPONSE -> {
                val aiJson = intent.getStringExtra(EXTRA_AI_RESPONSE_JSON) ?: return
                WearCompanionStateRepository.onReceiveAiResponseJson(aiJson)
            }
        }
    }
}
