package com.androidfung.departureboard.wear

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONObject

/**
 * Google Play Services Wearable Data Layer listener on the Phone (`:app`).
 * Receives cross-device messages sent from a paired Wear OS watch (`:wear`) over Bluetooth, Wi-Fi,
 * or the Android Studio Emulator Pair Bridge and forwards them to [PhoneCompanionSyncReceiver].
 */
class PhoneWearableListenerService : WearableListenerService() {

    companion object {
        private const val TAG = "PhoneWearableListener"

        const val PATH_REQUEST_BOARDS = "/ekiboard/cmd/request_boards"
        const val PATH_OPEN_ON_PHONE = "/ekiboard/cmd/open_on_phone"
        const val PATH_START_TRACKING = "/ekiboard/cmd/start_tracking"
        const val PATH_STOP_TRACKING = "/ekiboard/cmd/stop_tracking"
        const val PATH_ADD_STATION = "/ekiboard/cmd/add_station"
        const val PATH_REMOVE_STATION = "/ekiboard/cmd/remove_station"
        const val PATH_ASK_AI = "/ekiboard/cmd/ask_ai"
        const val PATH_OPEN_AI_ON_PHONE = "/ekiboard/cmd/open_ai_on_phone"
        const val PATH_SET_ALIGHT_STOP = "/ekiboard/cmd/set_alight_stop"
        const val PATH_SHARE_ETA = "/ekiboard/cmd/share_eta"
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        val path = messageEvent.path
        val payloadStr = String(messageEvent.data, Charsets.UTF_8)
        val json = runCatching { if (payloadStr.isNotBlank()) JSONObject(payloadStr) else JSONObject() }
            .getOrDefault(JSONObject())

        val action = when (path) {
            PATH_REQUEST_BOARDS -> PhoneCompanionSyncReceiver.ACTION_REQUEST_PHONE_BOARDS
            PATH_OPEN_ON_PHONE -> PhoneCompanionSyncReceiver.ACTION_OPEN_ON_PHONE
            PATH_START_TRACKING -> PhoneCompanionSyncReceiver.ACTION_START_TRACKING_FROM_WATCH
            PATH_STOP_TRACKING -> PhoneCompanionSyncReceiver.ACTION_STOP_TRACKING_FROM_WATCH
            PATH_ADD_STATION -> PhoneCompanionSyncReceiver.ACTION_ADD_STATION_FROM_WATCH
            PATH_REMOVE_STATION -> PhoneCompanionSyncReceiver.ACTION_REMOVE_STATION_FROM_WATCH
            PATH_ASK_AI -> PhoneCompanionSyncReceiver.ACTION_ASK_AI_FROM_WATCH
            PATH_OPEN_AI_ON_PHONE -> PhoneCompanionSyncReceiver.ACTION_OPEN_AI_ON_PHONE
            PATH_SET_ALIGHT_STOP -> PhoneCompanionSyncReceiver.ACTION_SET_ALIGHT_STOP_FROM_WATCH
            PATH_SHARE_ETA -> PhoneCompanionSyncReceiver.ACTION_SHARE_ETA_FROM_WATCH
            else -> {
                Log.d(TAG, "Ignoring unrecognized Wearable message path: $path")
                return
            }
        }

        val intent = Intent(action).apply {
            putExtra(
                PhoneCompanionSyncReceiver.EXTRA_STATION_ID,
                json.optString(PhoneCompanionSyncReceiver.EXTRA_STATION_ID)
            )
            putExtra(
                PhoneCompanionSyncReceiver.EXTRA_STATION_NAME,
                json.optString(PhoneCompanionSyncReceiver.EXTRA_STATION_NAME)
            )
            putExtra(
                PhoneCompanionSyncReceiver.EXTRA_DEPARTURE_ID,
                json.optString(PhoneCompanionSyncReceiver.EXTRA_DEPARTURE_ID)
            )
            putExtra(
                PhoneCompanionSyncReceiver.EXTRA_DESTINATION_NAME,
                json.optString(PhoneCompanionSyncReceiver.EXTRA_DESTINATION_NAME)
            )
            putExtra(
                PhoneCompanionSyncReceiver.EXTRA_LINE_NAME,
                json.optString(PhoneCompanionSyncReceiver.EXTRA_LINE_NAME)
            )
            putExtra(
                PhoneCompanionSyncReceiver.EXTRA_PLATFORM_CODE,
                json.optString(PhoneCompanionSyncReceiver.EXTRA_PLATFORM_CODE)
            )
            putExtra(
                PhoneCompanionSyncReceiver.EXTRA_COUNTDOWN_MINUTES,
                json.optInt(PhoneCompanionSyncReceiver.EXTRA_COUNTDOWN_MINUTES, 5)
            )
            putExtra(
                PhoneCompanionSyncReceiver.EXTRA_AI_PROMPT_TYPE,
                json.optString(PhoneCompanionSyncReceiver.EXTRA_AI_PROMPT_TYPE)
            )
            putExtra(
                PhoneCompanionSyncReceiver.EXTRA_AI_QUERY_TEXT,
                json.optString(PhoneCompanionSyncReceiver.EXTRA_AI_QUERY_TEXT)
            )
            putExtra(
                PhoneCompanionSyncReceiver.EXTRA_TARGET_ALIGHT_STOP,
                json.optString(PhoneCompanionSyncReceiver.EXTRA_TARGET_ALIGHT_STOP)
            )
            putExtra(
                PhoneCompanionSyncReceiver.EXTRA_SHARE_ETA_TEXT,
                json.optString(PhoneCompanionSyncReceiver.EXTRA_SHARE_ETA_TEXT)
            )
        }

        PhoneCompanionSyncReceiver().onReceive(applicationContext, intent)
    }
}
