package com.androidfung.departureboard.wear.sync

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * Google Play Services Wearable Data Layer listener on the Watch (`:wear`).
 * Receives live station boards, active tracked train countdowns, platform announcements,
 * and AI Concourse Triage answers pushed from the paired Phone app (`:app`).
 */
class WearDataLayerListenerService : WearableListenerService() {

    companion object {
        const val PATH_SYNC_STATION_BOARDS = "/ekiboard/sync/station_boards"
        const val PATH_SYNC_TRACKED_DEPARTURE = "/ekiboard/sync/tracked_departure"
        const val PATH_STOP_TRACKED_DEPARTURE = "/ekiboard/sync/stop_tracking"
        const val PATH_SYNC_AI_RESPONSE = "/ekiboard/sync/ai_response"
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        val payload = messageEvent.data?.let { String(it, Charsets.UTF_8) }.orEmpty()
        when (messageEvent.path) {
            PATH_SYNC_STATION_BOARDS -> {
                if (payload.isNotBlank()) {
                    WearCompanionStateRepository.onReceiveStationBoardsJson(payload)
                }
            }
            PATH_SYNC_TRACKED_DEPARTURE -> {
                if (payload.isNotBlank()) {
                    WearCompanionStateRepository.onReceiveTrackedDepartureJson(applicationContext, payload)
                }
            }
            PATH_STOP_TRACKED_DEPARTURE -> {
                WearCompanionStateRepository.onReceiveStopTrackedDeparture(applicationContext)
            }
            PATH_SYNC_AI_RESPONSE -> {
                if (payload.isNotBlank()) {
                    WearCompanionStateRepository.onReceiveAiResponseJson(payload)
                }
            }
        }
    }
}
