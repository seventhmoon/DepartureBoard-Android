package com.androidfung.departureboard.core.wearcontract

/**
 * Single source of truth for Phone (`:app`) <-> Wear OS (`:wear`) IPC constants:
 * - Application ID & signature-level permission
 * - Wearable Data Layer message paths (`/ekiboard/...`)
 * - Broadcast actions (`ACTION_*`) and Intent extras (`EXTRA_*`)
 */
object WearSyncContract {

    /**
     * Shared `applicationId` used by both `:app` and `:wear` so Play Services Wearable Data Layer
     * and signature-protected explicit broadcasts route reliably across devices and emulators.
     */
    const val COMPANION_APPLICATION_ID = "com.androidfung.departureboard"

    const val PERMISSION_COMPANION_SYNC = "com.androidfung.departureboard.permission.COMPANION_SYNC"

    // =========================================================================
    // Phone -> Watch Broadcast Actions & Data Layer Paths
    // =========================================================================
    const val ACTION_SYNC_STATION_BOARDS = "com.androidfung.departureboard.wear.action.SYNC_STATION_BOARDS"
    const val ACTION_SYNC_TRACKED_DEPARTURE = "com.androidfung.departureboard.wear.action.SYNC_TRACKED_DEPARTURE"
    const val ACTION_STOP_TRACKED_DEPARTURE = "com.androidfung.departureboard.wear.action.STOP_TRACKED_DEPARTURE"
    const val ACTION_SYNC_AI_RESPONSE = "com.androidfung.departureboard.wear.action.SYNC_AI_RESPONSE"
    const val ACTION_SYNC_NOTIFICATION_PREFS = "com.androidfung.departureboard.wear.action.SYNC_NOTIFICATION_PREFS"

    const val PATH_SYNC_STATION_BOARDS = "/ekiboard/sync/station_boards"
    const val PATH_SYNC_TRACKED_DEPARTURE = "/ekiboard/sync/tracked_departure"
    const val PATH_STOP_TRACKED_DEPARTURE = "/ekiboard/sync/stop_tracking"
    const val PATH_SYNC_AI_RESPONSE = "/ekiboard/sync/ai_response"
    const val PATH_SYNC_NOTIFICATION_PREFS = "/ekiboard/sync/notification_prefs"

    const val EXTRA_BOARDS_JSON = "extra_boards_json"
    const val EXTRA_TRACKED_JSON = "extra_tracked_json"
    const val EXTRA_AI_RESPONSE_JSON = "extra_ai_response_json"
    const val EXTRA_NOTIFICATION_PREFS_JSON = "extra_notification_prefs_json"

    // =========================================================================
    // Watch -> Phone Broadcast Actions & Data Layer Paths
    // =========================================================================
    const val ACTION_OPEN_ON_PHONE = "com.androidfung.departureboard.action.WEAR_OPEN_ON_PHONE"
    const val ACTION_START_TRACKING_FROM_WATCH = "com.androidfung.departureboard.action.WEAR_START_TRACKING"
    const val ACTION_STOP_TRACKING_FROM_WATCH = "com.androidfung.departureboard.action.WEAR_STOP_TRACKING"
    const val ACTION_REQUEST_PHONE_BOARDS = "com.androidfung.departureboard.action.WEAR_REQUEST_BOARDS"
    const val ACTION_ADD_STATION_FROM_WATCH = "com.androidfung.departureboard.action.WEAR_ADD_STATION"
    const val ACTION_REMOVE_STATION_FROM_WATCH = "com.androidfung.departureboard.action.WEAR_REMOVE_STATION"
    const val ACTION_ASK_AI_FROM_WATCH = "com.androidfung.departureboard.action.WEAR_ASK_AI"
    const val ACTION_OPEN_AI_ON_PHONE = "com.androidfung.departureboard.action.WEAR_OPEN_AI_ON_PHONE"
    const val ACTION_SET_ALIGHT_STOP_FROM_WATCH = "com.androidfung.departureboard.action.WEAR_SET_ALIGHT_STOP"
    const val ACTION_SHARE_ETA_FROM_WATCH = "com.androidfung.departureboard.action.WEAR_SHARE_ETA"

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

    // =========================================================================
    // Watch -> Phone Command Extras
    // =========================================================================
    const val EXTRA_STATION_ID = "extra_station_id"
    const val EXTRA_STATION_NAME = "extra_station_name"
    const val EXTRA_DEPARTURE_ID = "extra_departure_id"
    const val EXTRA_DESTINATION_NAME = "extra_destination_name"
    const val EXTRA_LINE_NAME = "extra_line_name"
    const val EXTRA_PLATFORM_CODE = "extra_platform_code"
    const val EXTRA_COUNTDOWN_MINUTES = "extra_countdown_minutes"
    const val EXTRA_AI_PROMPT_TYPE = "extra_ai_prompt_type"
    const val EXTRA_AI_QUERY_TEXT = "extra_ai_query_text"
    const val EXTRA_TARGET_ALIGHT_STOP = "extra_target_alight_stop"
    const val EXTRA_SHARE_ETA_TEXT = "extra_share_eta_text"
}
