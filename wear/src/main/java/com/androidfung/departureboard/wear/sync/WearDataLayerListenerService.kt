package com.androidfung.departureboard.wear.sync

import com.androidfung.departureboard.core.wearcontract.WearSyncContract
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * Google Play Services Wearable Data Layer listener on the Watch (`:wear`).
 * Receives live station boards, active tracked train countdowns, platform announcements,
 * and AI Concourse Triage answers pushed from the paired Phone app (`:app`).
 */
class WearDataLayerListenerService : WearableListenerService() {

    companion object {
        const val PATH_SYNC_STATION_BOARDS = WearSyncContract.PATH_SYNC_STATION_BOARDS
        const val PATH_SYNC_TRACKED_DEPARTURE = WearSyncContract.PATH_SYNC_TRACKED_DEPARTURE
        const val PATH_STOP_TRACKED_DEPARTURE = WearSyncContract.PATH_STOP_TRACKED_DEPARTURE
        const val PATH_SYNC_AI_RESPONSE = WearSyncContract.PATH_SYNC_AI_RESPONSE
        const val PATH_SYNC_NOTIFICATION_PREFS = WearSyncContract.PATH_SYNC_NOTIFICATION_PREFS
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        val payload = String(messageEvent.data, Charsets.UTF_8)
        when (messageEvent.path) {
            PATH_SYNC_STATION_BOARDS -> {
                if (payload.isNotBlank()) {
                    WearCompanionStateRepository.onReceiveStationBoardsJson(payload, applicationContext)
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
            PATH_SYNC_NOTIFICATION_PREFS -> {
                if (payload.isNotBlank()) {
                    com.androidfung.departureboard.wear.haptic.WearNotificationPublisher.applySyncedPhonePreferences(
                        applicationContext,
                        payload
                    )
                }
            }
        }
    }
}
