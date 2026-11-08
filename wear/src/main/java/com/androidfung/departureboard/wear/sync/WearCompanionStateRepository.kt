package com.androidfung.departureboard.wear.sync

import android.content.Context
import android.content.Intent
import com.androidfung.departureboard.wear.haptic.WearConcourseHapticAnnouncer
import com.androidfung.departureboard.wear.model.WearAiConcourseResponse
import com.androidfung.departureboard.wear.model.WearAiPromptType
import com.androidfung.departureboard.wear.model.WearDepartureItem
import com.androidfung.departureboard.wear.model.WearSnapshotFormatter
import com.androidfung.departureboard.wear.model.WearStationBoard
import com.androidfung.departureboard.wear.model.WearTrackedJourneyState
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages the Wear OS side of the Phone <-> Watch Companion Sync Bridge:
 * - Holds synced favorite/commute station boards (`syncedStationBoardsFlow`) pushed from the phone.
 * - Holds the active tracked train (`trackedJourneyFlow`) mirrored from `TrainTrackingService`.
 * - Automatically triggers wrist haptic alerts when a phone-tracked train announces a platform (`TBD -> Plat X`)
 *   or approaches the user's selected alighting stop (`stopsAway == 1`).
 * - Dispatches Watch -> Phone handoff intents (`openOnPhone`, `startRemoteTrackingOnPhone`, `stopRemoteTrackingOnPhone`).
 */
object WearCompanionStateRepository {

    const val PHONE_PACKAGE = "com.androidfung.departureboard"

    const val ACTION_OPEN_ON_PHONE = "com.androidfung.departureboard.action.WEAR_OPEN_ON_PHONE"
    const val ACTION_START_TRACKING_FROM_WATCH = "com.androidfung.departureboard.action.WEAR_START_TRACKING"
    const val ACTION_STOP_TRACKING_FROM_WATCH = "com.androidfung.departureboard.action.WEAR_STOP_TRACKING"
    const val ACTION_REQUEST_PHONE_BOARDS = "com.androidfung.departureboard.action.WEAR_REQUEST_BOARDS"
    const val ACTION_ADD_STATION_FROM_WATCH = "com.androidfung.departureboard.action.WEAR_ADD_STATION"
    const val ACTION_REMOVE_STATION_FROM_WATCH = "com.androidfung.departureboard.action.WEAR_REMOVE_STATION"
    const val ACTION_ASK_AI_FROM_WATCH = "com.androidfung.departureboard.action.WEAR_ASK_AI"
    const val ACTION_OPEN_AI_ON_PHONE = "com.androidfung.departureboard.action.WEAR_OPEN_AI_ON_PHONE"

    const val EXTRA_STATION_ID = "extra_station_id"
    const val EXTRA_STATION_NAME = "extra_station_name"
    const val EXTRA_DEPARTURE_ID = "extra_departure_id"
    const val EXTRA_DESTINATION_NAME = "extra_destination_name"
    const val EXTRA_LINE_NAME = "extra_line_name"
    const val EXTRA_PLATFORM_CODE = "extra_platform_code"
    const val EXTRA_COUNTDOWN_MINUTES = "extra_countdown_minutes"
    const val EXTRA_AI_PROMPT_TYPE = "extra_ai_prompt_type"
    const val EXTRA_AI_QUERY_TEXT = "extra_ai_query_text"

    const val PATH_REQUEST_BOARDS = "/ekiboard/cmd/request_boards"
    const val PATH_OPEN_ON_PHONE = "/ekiboard/cmd/open_on_phone"
    const val PATH_START_TRACKING = "/ekiboard/cmd/start_tracking"
    const val PATH_STOP_TRACKING = "/ekiboard/cmd/stop_tracking"
    const val PATH_ADD_STATION = "/ekiboard/cmd/add_station"
    const val PATH_REMOVE_STATION = "/ekiboard/cmd/remove_station"
    const val PATH_ASK_AI = "/ekiboard/cmd/ask_ai"
    const val PATH_OPEN_AI_ON_PHONE = "/ekiboard/cmd/open_ai_on_phone"

    private val _stationBoardsFlow = MutableStateFlow(WearSnapshotFormatter.defaultConcourseBoards())
    val stationBoardsFlow: StateFlow<List<WearStationBoard>> = _stationBoardsFlow.asStateFlow()

    private val _trackedJourneyFlow = MutableStateFlow<WearTrackedJourneyState?>(null)
    val trackedJourneyFlow: StateFlow<WearTrackedJourneyState?> = _trackedJourneyFlow.asStateFlow()

    private val _isPhoneSyncedFlow = MutableStateFlow(false)
    val isPhoneSyncedFlow: StateFlow<Boolean> = _isPhoneSyncedFlow.asStateFlow()

    private val _latestWristAlertFlow = MutableStateFlow<WearConcourseHapticAnnouncer.WristHapticEvent?>(null)
    val latestWristAlertFlow: StateFlow<WearConcourseHapticAnnouncer.WristHapticEvent?> = _latestWristAlertFlow.asStateFlow()

    private val _latestAiResponseFlow = MutableStateFlow<WearAiConcourseResponse?>(null)
    val latestAiResponseFlow: StateFlow<WearAiConcourseResponse?> = _latestAiResponseFlow.asStateFlow()

    private var lastAlertedAlightStop: String? = null

    fun clearWristAlert() {
        _latestWristAlertFlow.value = null
    }

    fun clearAiResponse() {
        _latestAiResponseFlow.value = null
    }

    /**
     * Evaluates a 1-tap or voice Concourse AI prompt locally for immediate wrist feedback,
     * and dispatches `ACTION_ASK_AI_FROM_WATCH` to the Phone for deeper timetable/backlog verification.
     */
    fun askAiConcourseTriage(
        context: Context?,
        station: WearStationBoard,
        promptType: WearAiPromptType,
        queryText: String = ""
    ): WearAiConcourseResponse {
        val localResponse = WearSnapshotFormatter.evaluateAiPromptLocally(station, promptType, queryText)
        _latestAiResponseFlow.value = localResponse

        context?.let { ctx ->
            runCatching {
                val intent = Intent(ACTION_ASK_AI_FROM_WATCH).apply {
                    setPackage(PHONE_PACKAGE)
                    putExtra(EXTRA_STATION_ID, station.stationId)
                    putExtra(EXTRA_STATION_NAME, station.stationName)
                    putExtra(EXTRA_AI_PROMPT_TYPE, promptType.name)
                    putExtra(EXTRA_AI_QUERY_TEXT, localResponse.rawQuery)
                }
                ctx.sendBroadcast(intent)
            }
            sendWearableMessageToPhone(
                context = ctx,
                path = PATH_ASK_AI,
                payload = JSONObject()
                    .put(EXTRA_STATION_ID, station.stationId)
                    .put(EXTRA_STATION_NAME, station.stationName)
                    .put(EXTRA_AI_PROMPT_TYPE, promptType.name)
                    .put(EXTRA_AI_QUERY_TEXT, localResponse.rawQuery)
                    .toString()
            )
        }
        return localResponse
    }

    /**
     * Hands off the active AI question from the Watch directly into the Phone's AI Assistant bottom sheet.
     */
    fun openAiOnPhone(
        context: Context,
        stationId: String,
        stationName: String,
        queryText: String
    ) {
        runCatching {
            val intent = Intent(ACTION_OPEN_AI_ON_PHONE).apply {
                setPackage(PHONE_PACKAGE)
                putExtra(EXTRA_STATION_ID, stationId)
                putExtra(EXTRA_STATION_NAME, stationName)
                putExtra(EXTRA_AI_QUERY_TEXT, queryText)
            }
            context.sendBroadcast(intent)
        }
        sendWearableMessageToPhone(
            context = context,
            path = PATH_OPEN_AI_ON_PHONE,
            payload = JSONObject()
                .put(EXTRA_STATION_ID, stationId)
                .put(EXTRA_STATION_NAME, stationName)
                .put(EXTRA_AI_QUERY_TEXT, queryText)
                .toString()
        )
    }

    internal fun onReceiveAiResponseJson(responseJson: String) {
        val parsed = parseAiResponseJson(responseJson) ?: return
        _latestAiResponseFlow.value = parsed
        _isPhoneSyncedFlow.value = true
    }

    internal fun parseAiResponseJson(responseJson: String): WearAiConcourseResponse? {
        try {
            val obj = JSONObject(responseJson)
            val headline = obj.optString("headline")
            if (!headline.isNullOrBlank()) {
                return WearAiConcourseResponse(
                    stationId = obj.optString("stationId"),
                    headline = headline,
                    summary = obj.optString("summary"),
                    rawQuery = obj.optString("rawQuery"),
                    isFromPhone = obj.optBoolean("isFromPhone", true)
                )
            }
        } catch (_: Throwable) {
        }
        fun extractString(key: String): String? =
            Regex(""""$key"\s*:\s*"([^"]*)"""").find(responseJson)?.groupValues?.getOrNull(1)
        val headline = extractString("headline")?.takeIf { it.isNotBlank() } ?: return null
        return WearAiConcourseResponse(
            stationId = extractString("stationId").orEmpty(),
            headline = headline,
            summary = extractString("summary").orEmpty(),
            rawQuery = extractString("rawQuery").orEmpty(),
            isFromPhone = true
        )
    }

    /**
     * Requests the phone app to push its latest favorite/commute station boards and active tracked train.
     */
    fun requestPhoneSync(context: Context) {
        runCatching {
            val intent = Intent(ACTION_REQUEST_PHONE_BOARDS).apply {
                setPackage(PHONE_PACKAGE)
            }
            context.sendBroadcast(intent)
        }
        sendWearableMessageToPhone(context, PATH_REQUEST_BOARDS, "")
    }

    /**
     * Sends a Watch -> Phone handoff intent to open the selected station and departure journey timeline on the phone.
     */
    fun openOnPhone(
        context: Context,
        stationId: String,
        stationName: String,
        departureId: String
    ) {
        runCatching {
            val intent = Intent(ACTION_OPEN_ON_PHONE).apply {
                setPackage(PHONE_PACKAGE)
                putExtra(EXTRA_STATION_ID, stationId)
                putExtra(EXTRA_STATION_NAME, stationName)
                putExtra(EXTRA_DEPARTURE_ID, departureId)
            }
            context.sendBroadcast(intent)
        }
        sendWearableMessageToPhone(
            context = context,
            path = PATH_OPEN_ON_PHONE,
            payload = JSONObject()
                .put(EXTRA_STATION_ID, stationId)
                .put(EXTRA_STATION_NAME, stationName)
                .put(EXTRA_DEPARTURE_ID, departureId)
                .toString()
        )
    }

    /**
     * Starts live train tracking on the phone directly from the watch.
     */
    fun startRemoteTrackingOnPhone(
        context: Context,
        station: WearStationBoard,
        departure: WearDepartureItem
    ) {
        runCatching {
            val intent = Intent(ACTION_START_TRACKING_FROM_WATCH).apply {
                setPackage(PHONE_PACKAGE)
                putExtra(EXTRA_STATION_ID, station.stationId)
                putExtra(EXTRA_STATION_NAME, station.stationName)
                putExtra(EXTRA_DEPARTURE_ID, departure.id)
                putExtra(EXTRA_DESTINATION_NAME, departure.destination)
                putExtra(EXTRA_LINE_NAME, departure.lineName)
                putExtra(EXTRA_PLATFORM_CODE, departure.platformCode)
                putExtra(EXTRA_COUNTDOWN_MINUTES, departure.countdownMinutes)
            }
            context.sendBroadcast(intent)
        }
        sendWearableMessageToPhone(
            context = context,
            path = PATH_START_TRACKING,
            payload = JSONObject()
                .put(EXTRA_STATION_ID, station.stationId)
                .put(EXTRA_STATION_NAME, station.stationName)
                .put(EXTRA_DEPARTURE_ID, departure.id)
                .put(EXTRA_DESTINATION_NAME, departure.destination)
                .put(EXTRA_LINE_NAME, departure.lineName)
                .put(EXTRA_PLATFORM_CODE, departure.platformCode)
                .put(EXTRA_COUNTDOWN_MINUTES, departure.countdownMinutes)
                .toString()
        )
    }

    fun stopRemoteTrackingOnPhone(context: Context) {
        _trackedJourneyFlow.value = null
        lastAlertedAlightStop = null
        runCatching {
            val intent = Intent(ACTION_STOP_TRACKING_FROM_WATCH).apply {
                setPackage(PHONE_PACKAGE)
            }
            context.sendBroadcast(intent)
        }
        sendWearableMessageToPhone(context, PATH_STOP_TRACKING, "")
    }

    /**
     * Adds a station directly from the Watch UI and syncs the addition to the Phone app's saved stations.
     */
    fun addStationFromWatch(context: Context?, station: WearStationBoard) {
        val current = _stationBoardsFlow.value
        if (current.none { it.stationId.equals(station.stationId, ignoreCase = true) }) {
            _stationBoardsFlow.value = current + station
        }
        context?.let { ctx ->
            runCatching {
                val intent = Intent(ACTION_ADD_STATION_FROM_WATCH).apply {
                    setPackage(PHONE_PACKAGE)
                    putExtra(EXTRA_STATION_ID, station.stationId)
                    putExtra(EXTRA_STATION_NAME, station.stationName)
                }
                ctx.sendBroadcast(intent)
            }
            sendWearableMessageToPhone(
                context = ctx,
                path = PATH_ADD_STATION,
                payload = JSONObject()
                    .put(EXTRA_STATION_ID, station.stationId)
                    .put(EXTRA_STATION_NAME, station.stationName)
                    .toString()
            )
        }
    }

    /**
     * Removes a station from the Watch UI (keeping at least 1 station) and syncs the removal to the Phone app.
     */
    fun removeStationFromWatch(context: Context?, stationId: String) {
        val current = _stationBoardsFlow.value
        if (current.size <= 1) return
        _stationBoardsFlow.value = current.filterNot { it.stationId.equals(stationId, ignoreCase = true) }
        context?.let { ctx ->
            runCatching {
                val intent = Intent(ACTION_REMOVE_STATION_FROM_WATCH).apply {
                    setPackage(PHONE_PACKAGE)
                    putExtra(EXTRA_STATION_ID, stationId)
                }
                ctx.sendBroadcast(intent)
            }
            sendWearableMessageToPhone(
                context = ctx,
                path = PATH_REMOVE_STATION,
                payload = JSONObject()
                    .put(EXTRA_STATION_ID, stationId)
                    .toString()
            )
        }
    }

    private fun sendWearableMessageToPhone(context: Context, path: String, payload: String) {
        runCatching {
            val bytes = payload.toByteArray(Charsets.UTF_8)
            val appCtx = context.applicationContext ?: context
            val nodeClient = Wearable.getNodeClient(appCtx)
            val messageClient = Wearable.getMessageClient(appCtx)
            nodeClient.connectedNodes.addOnSuccessListener { nodes ->
                for (node in nodes) {
                    messageClient.sendMessage(node.id, path, bytes)
                }
            }
        }
    }

    internal fun onReceiveStationBoardsJson(boardsJson: String) {
        val parsed = parseStationBoardsJson(boardsJson)
        if (parsed.isNotEmpty()) {
            _stationBoardsFlow.value = parsed
            _isPhoneSyncedFlow.value = true
        }
    }

    internal fun onReceiveTrackedDepartureJson(context: Context?, trackedJson: String) {
        val previous = _trackedJourneyFlow.value
        val current = parseTrackedDepartureJson(trackedJson) ?: return
        _trackedJourneyFlow.value = current
        _isPhoneSyncedFlow.value = true

        // 1. Check if platform transitioned from TBD -> Confirmed or changed on the phone-tracked train
        if (previous != null && previous.departureId == current.departureId) {
            val wasTbd = previous.isPlatformTbd
            val isNowConfirmed = !current.isPlatformTbd
            if (wasTbd && isNowConfirmed) {
                val event = WearConcourseHapticAnnouncer.WristHapticEvent(
                    type = WearConcourseHapticAnnouncer.WristAlertType.PLATFORM_ANNOUNCED,
                    departureId = current.departureId,
                    headline = "Plat ${current.platformCode} Announced!",
                    detail = "${current.destinationName} is boarding at Platform ${current.platformCode}"
                )
                _latestWristAlertFlow.value = event
                context?.let { WearConcourseHapticAnnouncer.triggerWristPulse(it, event.type) }
            } else if (!wasTbd && isNowConfirmed && !previous.platformCode.equals(current.platformCode, ignoreCase = true)) {
                val event = WearConcourseHapticAnnouncer.WristHapticEvent(
                    type = WearConcourseHapticAnnouncer.WristAlertType.PLATFORM_CHANGED,
                    departureId = current.departureId,
                    headline = "Platform Changed → Plat ${current.platformCode}",
                    detail = "${current.destinationName} moved from Plat ${previous.platformCode} to Plat ${current.platformCode}"
                )
                _latestWristAlertFlow.value = event
                context?.let { WearConcourseHapticAnnouncer.triggerWristPulse(it, event.type) }
            }
        }

        // 2. Check if 1 stop away from Target Alighting Stop
        val targetStop = current.targetAlightStopName?.takeIf { it.isNotBlank() }
        if (targetStop != null && current.stopsAway == 1 && lastAlertedAlightStop != targetStop) {
            lastAlertedAlightStop = targetStop
            val event = WearConcourseHapticAnnouncer.WristHapticEvent(
                type = WearConcourseHapticAnnouncer.WristAlertType.ALIGHT_NEXT_STOP,
                departureId = current.departureId,
                headline = "Get Ready to Alight",
                detail = "Next stop is $targetStop on ${current.lineName}"
            )
            _latestWristAlertFlow.value = event
            context?.let { WearConcourseHapticAnnouncer.triggerWristPulse(it, event.type) }
        }
    }

    internal fun onReceiveStopTrackedDeparture() {
        _trackedJourneyFlow.value = null
        lastAlertedAlightStop = null
    }

    internal fun parseStationBoardsJson(boardsJson: String): List<WearStationBoard> {
        return try {
            val array = JSONArray(boardsJson)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val depsArray = obj.optJSONArray("departures") ?: JSONArray()
                    val departures = buildList {
                        for (j in 0 until depsArray.length()) {
                            val d = depsArray.getJSONObject(j)
                            add(
                                WearDepartureItem(
                                    id = d.optString("id"),
                                    lineName = d.optString("lineName"),
                                    destination = d.optString("destination"),
                                    destinationCrs = d.optString("destinationCrs", "DEST"),
                                    platformCode = d.optString("platformCode", "TBD"),
                                    isPlatformConfirmed = d.optBoolean("isPlatformConfirmed", false),
                                    isPlatformChanged = d.optBoolean("isPlatformChanged", false),
                                    previousPlatformCode = d.optString("previousPlatformCode").takeIf { it.isNotBlank() },
                                    countdownMinutes = d.optInt("countdownMinutes", 0),
                                    scheduledTime = d.optString("scheduledTime", ""),
                                    statusLabel = d.optString("statusLabel", "On time"),
                                    lineColorArgb = d.optLong("lineColorArgb", 0xFF0098D4L),
                                    delayRepayBadge = d.optString("delayRepayBadge").takeIf { it.isNotBlank() },
                                    isLastTrain = d.optBoolean("isLastTrain", false)
                                )
                            )
                        }
                    }
                    add(
                        WearStationBoard(
                            stationId = obj.optString("stationId"),
                            stationName = obj.optString("stationName"),
                            stationCrs = obj.optString("stationCrs", "STN"),
                            lastUpdatedLabel = "📱 Synced from Phone",
                            commuteLabel = obj.optString("commuteLabel").takeIf { it.isNotBlank() },
                            departures = departures,
                            isAtConcourse = obj.optBoolean("isAtConcourse", false)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    internal fun parseTrackedDepartureJson(trackedJson: String): WearTrackedJourneyState? {
        try {
            val obj = JSONObject(trackedJson)
            val depId = obj.optString("departureId")
            if (!depId.isNullOrBlank()) {
                val stopsAwayRaw = obj.optInt("stopsAway", -1)
                return WearTrackedJourneyState(
                    departureId = depId,
                    stationId = obj.optString("stationId"),
                    stationName = obj.optString("stationName"),
                    destinationName = obj.optString("destinationName"),
                    lineName = obj.optString("lineName"),
                    platformCode = obj.optString("platformCode", "TBD"),
                    isPlatformConfirmed = obj.optBoolean("isPlatformConfirmed", false),
                    remainingSeconds = obj.optInt("remainingSeconds", 0),
                    currentLocation = obj.optString("currentLocation").takeIf { it.isNotBlank() },
                    stopsAway = if (stopsAwayRaw >= 0) stopsAwayRaw else null,
                    targetAlightStopName = obj.optString("targetAlightStopName").takeIf { it.isNotBlank() },
                    isCancelled = obj.optBoolean("isCancelled", false),
                    lineColorArgb = obj.optLong("lineColorArgb", 0xFF1976D2L)
                )
            }
        } catch (_: Throwable) {
            // Fallback for local JVM unit test environment where org.json.JSONObject is stubbed
        }

        fun extractString(key: String): String? =
            Regex(""""$key"\s*:\s*"([^"]*)"""").find(trackedJson)?.groupValues?.getOrNull(1)
        fun extractInt(key: String, default: Int): Int =
            Regex(""""$key"\s*:\s*(-?[0-9]+)""").find(trackedJson)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: default
        fun extractLong(key: String, default: Long): Long =
            Regex(""""$key"\s*:\s*(-?[0-9]+)""").find(trackedJson)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: default
        fun extractBool(key: String): Boolean =
            Regex(""""$key"\s*:\s*(true|false)""").find(trackedJson)?.groupValues?.getOrNull(1)?.toBooleanStrictOrNull() ?: false

        val depId = extractString("departureId")?.takeIf { it.isNotBlank() } ?: return null
        val stopsAwayRaw = extractInt("stopsAway", -1)
        return WearTrackedJourneyState(
            departureId = depId,
            stationId = extractString("stationId").orEmpty(),
            stationName = extractString("stationName").orEmpty(),
            destinationName = extractString("destinationName").orEmpty(),
            lineName = extractString("lineName").orEmpty(),
            platformCode = extractString("platformCode")?.ifBlank { "TBD" } ?: "TBD",
            isPlatformConfirmed = extractBool("isPlatformConfirmed"),
            remainingSeconds = extractInt("remainingSeconds", 0),
            currentLocation = extractString("currentLocation")?.takeIf { it.isNotBlank() },
            stopsAway = if (stopsAwayRaw >= 0) stopsAwayRaw else null,
            targetAlightStopName = extractString("targetAlightStopName")?.takeIf { it.isNotBlank() },
            isCancelled = extractBool("isCancelled"),
            lineColorArgb = extractLong("lineColorArgb", 0xFF1976D2L)
        )
    }
}
