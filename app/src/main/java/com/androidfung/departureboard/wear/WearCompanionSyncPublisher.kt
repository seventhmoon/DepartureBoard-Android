package com.androidfung.departureboard.wear

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import com.androidfung.departureboard.core.wearcontract.CompactTransitFormatter
import com.androidfung.departureboard.core.wearcontract.WearSyncContract
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.ui.dashboard.StationCardUiModel
import com.androidfung.departureboard.util.CommuteTag
import com.androidfung.departureboard.util.DelayRepayCalculator
import com.google.android.gms.wearable.Wearable
import org.json.JSONArray
import org.json.JSONObject

/**
 * Publishes real-time Phone -> Wear OS companion state:
 * 1. Pinned / Favorite / Commute station boards (`ACTION_SYNC_STATION_BOARDS`) so the watch
 *    displays the user's saved stations without draining watch battery on direct API polling.
 * 2. Active Live-Tracked Train (`ACTION_SYNC_TRACKED_DEPARTURE`) including `TBD -> Plat X`
 *    platform announcements and `targetAlightStop` countdowns.
 */
object WearCompanionSyncPublisher {

    private const val TAG = "WearCompanionSync"
    const val WEAR_PACKAGE = WearSyncContract.COMPANION_APPLICATION_ID
    const val PERMISSION_COMPANION_SYNC = WearSyncContract.PERMISSION_COMPANION_SYNC

    const val ACTION_SYNC_STATION_BOARDS = WearSyncContract.ACTION_SYNC_STATION_BOARDS
    const val ACTION_SYNC_TRACKED_DEPARTURE = WearSyncContract.ACTION_SYNC_TRACKED_DEPARTURE
    const val ACTION_STOP_TRACKED_DEPARTURE = WearSyncContract.ACTION_STOP_TRACKED_DEPARTURE
    const val ACTION_SYNC_AI_RESPONSE = WearSyncContract.ACTION_SYNC_AI_RESPONSE
    const val ACTION_SYNC_NOTIFICATION_PREFS = WearSyncContract.ACTION_SYNC_NOTIFICATION_PREFS

    const val PATH_SYNC_STATION_BOARDS = WearSyncContract.PATH_SYNC_STATION_BOARDS
    const val PATH_SYNC_TRACKED_DEPARTURE = WearSyncContract.PATH_SYNC_TRACKED_DEPARTURE
    const val PATH_STOP_TRACKED_DEPARTURE = WearSyncContract.PATH_STOP_TRACKED_DEPARTURE
    const val PATH_SYNC_AI_RESPONSE = WearSyncContract.PATH_SYNC_AI_RESPONSE
    const val PATH_SYNC_NOTIFICATION_PREFS = WearSyncContract.PATH_SYNC_NOTIFICATION_PREFS

    const val EXTRA_BOARDS_JSON = WearSyncContract.EXTRA_BOARDS_JSON
    const val EXTRA_TRACKED_JSON = WearSyncContract.EXTRA_TRACKED_JSON
    const val EXTRA_AI_RESPONSE_JSON = WearSyncContract.EXTRA_AI_RESPONSE_JSON
    const val EXTRA_NOTIFICATION_PREFS_JSON = WearSyncContract.EXTRA_NOTIFICATION_PREFS_JSON

    @Volatile
    private var lastPublishedBoardsJson: String? = null

    @Volatile
    private var lastPublishedTrackedJson: String? = null

    /**
     * Serializes the phone's active station cards (Favorites + Morning/Evening Commute)
     * and dispatches them to the Wear OS companion app.
     */
    fun publishStationBoards(
        context: Context,
        stationCards: List<StationCardUiModel>
    ) {
        if (stationCards.isEmpty()) return
        try {
            val boardsJson = serializeStationBoards(stationCards)
            lastPublishedBoardsJson = boardsJson

            val intent = Intent(ACTION_SYNC_STATION_BOARDS).apply {
                setPackage(WEAR_PACKAGE)
                putExtra(EXTRA_BOARDS_JSON, boardsJson)
            }
            context.sendBroadcast(intent, PERMISSION_COMPANION_SYNC)
            sendToWearableNodes(context, PATH_SYNC_STATION_BOARDS, boardsJson.toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to publish station boards to Wear companion", e)
        }
    }

    /**
     * Publishes an active tracked train update from [com.androidfung.departureboard.service.TrainTrackingService]
     * so the watch automatically mirrors the countdown, platform announcement, and alighting stop alert.
     */
    fun publishTrackedDeparture(
        context: Context,
        departureId: String,
        stationId: String,
        stationName: String,
        destinationName: String,
        lineName: String,
        platform: String,
        remainingSeconds: Int,
        currentLocation: String?,
        stopsAway: Int?,
        targetAlightStopName: String?,
        isCancelled: Boolean,
        lineColorArgb: Int
    ) {
        try {
            val trackedJson = buildTrackedDepartureJson(
                departureId = departureId,
                stationId = stationId,
                stationName = stationName,
                destinationName = destinationName,
                lineName = lineName,
                platform = platform,
                remainingSeconds = remainingSeconds,
                currentLocation = currentLocation,
                stopsAway = stopsAway,
                targetAlightStopName = targetAlightStopName,
                isCancelled = isCancelled,
                lineColorArgb = lineColorArgb
            )
            lastPublishedTrackedJson = trackedJson

            val intent = Intent(ACTION_SYNC_TRACKED_DEPARTURE).apply {
                setPackage(WEAR_PACKAGE)
                putExtra(EXTRA_TRACKED_JSON, trackedJson)
            }
            context.sendBroadcast(intent, PERMISSION_COMPANION_SYNC)
            sendToWearableNodes(context, PATH_SYNC_TRACKED_DEPARTURE, trackedJson.toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to publish tracked departure to Wear companion", e)
        }
    }

    fun publishTrackingStopped(context: Context) {
        lastPublishedTrackedJson = null
        try {
            val intent = Intent(ACTION_STOP_TRACKED_DEPARTURE).apply {
                setPackage(WEAR_PACKAGE)
            }
            context.sendBroadcast(intent, PERMISSION_COMPANION_SYNC)
            sendToWearableNodes(context, PATH_STOP_TRACKED_DEPARTURE, ByteArray(0))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to publish tracking stopped to Wear companion", e)
        }
    }

    /**
     * Publishes a concise 2-line AI Concourse Triage response back to the Wear OS companion app.
     */
    fun publishAiResponse(
        context: Context,
        stationId: String,
        headline: String,
        summary: String,
        rawQuery: String
    ) {
        try {
            val responseJson = JSONObject().apply {
                put("stationId", stationId)
                put("headline", headline)
                put("summary", summary)
                put("rawQuery", rawQuery)
                put("isFromPhone", true)
            }.toString()
            val intent = Intent(ACTION_SYNC_AI_RESPONSE).apply {
                setPackage(WEAR_PACKAGE)
                putExtra(EXTRA_AI_RESPONSE_JSON, responseJson)
            }
            context.sendBroadcast(intent, PERMISSION_COMPANION_SYNC)
            sendToWearableNodes(context, PATH_SYNC_AI_RESPONSE, responseJson.toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to publish AI response to Wear companion", e)
        }
    }

    /**
     * Publishes the user's per-channel notification toggles from the Phone to the Wear OS watch.
     */
    fun publishNotificationPreferences(context: Context) {
        try {
            val prefs = com.androidfung.departureboard.util.NotificationHelper.loadPreferences(context)
            val prefsJson = JSONObject().apply {
                put("platformAlertsEnabled", prefs.platformAlertsEnabled)
                put("alightingAlertsEnabled", prefs.alightingAlertsEnabled)
                put("disruptionAlertsEnabled", prefs.disruptionAlertsEnabled)
                put("wearAutoLaunchHudEnabled", prefs.wearAutoLaunchHudEnabled)
            }.toString()
            val intent = Intent(ACTION_SYNC_NOTIFICATION_PREFS).apply {
                setPackage(WEAR_PACKAGE)
                putExtra(EXTRA_NOTIFICATION_PREFS_JSON, prefsJson)
            }
            context.sendBroadcast(intent, PERMISSION_COMPANION_SYNC)
            sendToWearableNodes(context, PATH_SYNC_NOTIFICATION_PREFS, prefsJson.toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to publish notification preferences to Wear companion", e)
        }
    }

    /**
     * Re-sends the latest cached phone boards and tracked train state when the watch app wakes up
     * and requests an immediate sync (`ACTION_REQUEST_PHONE_BOARDS`).
     */
    fun resendCachedStateToWear(context: Context) {
        publishNotificationPreferences(context)
        lastPublishedBoardsJson?.let { boardsJson ->
            runCatching {
                context.sendBroadcast(
                    Intent(ACTION_SYNC_STATION_BOARDS).apply {
                        setPackage(WEAR_PACKAGE)
                        putExtra(EXTRA_BOARDS_JSON, boardsJson)
                    },
                    PERMISSION_COMPANION_SYNC
                )
                sendToWearableNodes(context, PATH_SYNC_STATION_BOARDS, boardsJson.toByteArray(Charsets.UTF_8))
            }
        }
        lastPublishedTrackedJson?.let { trackedJson ->
            runCatching {
                context.sendBroadcast(
                    Intent(ACTION_SYNC_TRACKED_DEPARTURE).apply {
                        setPackage(WEAR_PACKAGE)
                        putExtra(EXTRA_TRACKED_JSON, trackedJson)
                    },
                    PERMISSION_COMPANION_SYNC
                )
                sendToWearableNodes(context, PATH_SYNC_TRACKED_DEPARTURE, trackedJson.toByteArray(Charsets.UTF_8))
            }
        }
    }

    /**
     * Dispatches a payload over the Google Play Services Wearable Data Layer (`MessageClient`)
     * to all connected Wear OS watch nodes paired via Bluetooth or Android Studio Emulator Bridge.
     */
    private fun sendToWearableNodes(context: Context, path: String, payload: ByteArray) {
        runCatching {
            val appCtx = context.applicationContext
            Wearable.getNodeClient(appCtx).connectedNodes.addOnSuccessListener { nodes ->
                val msgClient = Wearable.getMessageClient(appCtx)
                for (node in nodes) {
                    msgClient.sendMessage(node.id, path, payload)
                }
            }
        }
    }

    internal fun serializeStationBoards(stationCards: List<StationCardUiModel>): String {
        val rootArray = JSONArray()
        val orderedCards = stationCards.sortedByDescending { it.isAtConcourseStation }
        orderedCards.take(8).forEach { card ->
            val st = card.station
            val stationObj = JSONObject().apply {
                put("stationId", st.id)
                put("stationName", st.name)
                put("stationCrs", deriveThreeLetterCode(st.id, st.name))
                put("isAtConcourse", card.isAtConcourseStation)
                put(
                    "commuteLabel",
                    when {
                        card.isAtConcourseStation -> card.concourseBadgeLabel ?: "📍 At Concourse"
                        st.commuteTag == CommuteTag.HOME -> "🏠 Home"
                        st.commuteTag == CommuteTag.WORK -> "💼 Work"
                        else -> if (st.isFavorite) "★ Pinned" else ""
                    }
                )
                val depsArray = JSONArray()
                card.departures.take(6).forEach { dep ->
                    val resolvedPlat = dep.resolvedPlatform
                    val platCode = compactPlatformForWatch(resolvedPlat.code)
                    val delayRepay = DelayRepayCalculator.evaluate(dep)
                    val depObj = JSONObject().apply {
                        put("id", dep.id)
                        put("lineName", dep.lineName)
                        put("destination", dep.destinationName)
                        put("destinationCrs", deriveThreeLetterCode(dep.destinationStationId ?: "", dep.destinationName))
                        put("platformCode", platCode)
                        put("isPlatformConfirmed", resolvedPlat.isConfirmed && !platCode.equals("TBD", ignoreCase = true))
                        put("isPlatformChanged", resolvedPlat.isChanged)
                        put("previousPlatformCode", resolvedPlat.previousPlatform ?: "")
                        put("countdownMinutes", (dep.timeToStationSeconds / 60).coerceAtLeast(0))
                        put("scheduledTime", dep.callingPoints.firstOrNull { it.isCurrentStation }?.scheduledTime ?: "")
                        put(
                            "statusLabel",
                            when (dep.status) {
                                DepartureStatus.CANCELLED -> "Cancelled"
                                DepartureStatus.DELAYED -> "Delayed"
                                DepartureStatus.DUE -> "Due"
                                else -> "On time"
                            }
                        )
                        put("lineColorArgb", dep.lineBadge.backgroundColor.toArgb().toLong() and 0xFFFFFFFFL)
                        put("delayRepayBadge", delayRepay?.badgeLabel ?: "")
                        put("isLastTrain", dep.isLastTrain)
                        put("coachCount", dep.formation?.coachCount ?: 0)
                        val curIdx = dep.callingPoints.indexOfFirst { it.isCurrentStation }
                        val upcomingStops = if (curIdx >= 0 && curIdx + 1 < dep.callingPoints.size) {
                            dep.callingPoints.subList(curIdx + 1, dep.callingPoints.size)
                        } else {
                            dep.callingPoints
                        }
                        val stopsSummary = upcomingStops
                            .map { it.stationName.replace("Underground Station", "", ignoreCase = true).replace("Rail Station", "", ignoreCase = true).replace("Station", "", ignoreCase = true).trim() }
                            .filter { it.isNotBlank() }
                            .take(5)
                            .joinToString(" → ")
                        put("callingPointsSummary", stopsSummary)
                    }
                    depsArray.put(depObj)
                }
                put("departures", depsArray)
            }
            rootArray.put(stationObj)
        }
        return rootArray.toString()
    }

    internal fun buildTrackedDepartureJson(
        departureId: String,
        stationId: String,
        stationName: String,
        destinationName: String,
        lineName: String,
        platform: String,
        remainingSeconds: Int,
        currentLocation: String?,
        stopsAway: Int?,
        targetAlightStopName: String?,
        isCancelled: Boolean,
        lineColorArgb: Int
    ): String {
        val cleanPlat = compactPlatformForWatch(platform)
        return JSONObject().apply {
            put("departureId", departureId)
            put("stationId", stationId)
            put("stationName", stationName)
            put("destinationName", destinationName)
            put("lineName", lineName)
            put("platformCode", cleanPlat)
            put("isPlatformConfirmed", !cleanPlat.equals("TBD", ignoreCase = true))
            put("remainingSeconds", remainingSeconds)
            put("currentLocation", currentLocation ?: "")
            put("stopsAway", stopsAway ?: -1)
            put("targetAlightStopName", targetAlightStopName ?: "")
            put("isCancelled", isCancelled)
            put("lineColorArgb", lineColorArgb.toLong() and 0xFFFFFFFFL)
        }.toString()
    }

    internal fun compactPlatformForWatch(raw: String): String =
        CompactTransitFormatter.compactPlatformCode(raw)

    internal fun deriveThreeLetterCode(stationId: String, stationName: String): String =
        CompactTransitFormatter.deriveStationShortCode(stationId, stationName)
}
