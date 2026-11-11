package com.androidfung.departureboard.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.MainActivity
import com.androidfung.departureboard.appContainer
import com.androidfung.departureboard.core.wearcontract.WearSyncContract
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TransitRegion
import com.androidfung.departureboard.service.TrainTrackingService
import com.androidfung.departureboard.util.DelayRepayCalculator
import com.androidfung.departureboard.util.PlatformBacklogDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Receives companion actions sent from the Wear OS app (`:wear`) to the Phone app (`:app`):
 * 1. `ACTION_OPEN_ON_PHONE`: Opens [MainActivity] directly to the requested station & departure journey timeline.
 * 2. `ACTION_START_TRACKING_FROM_WATCH`: Starts [TrainTrackingService] on the phone when the user taps
 *    "Watch Platform / Track" on their watch.
 * 3. `ACTION_STOP_TRACKING_FROM_WATCH`: Stops [TrainTrackingService] from the watch.
 * 4. `ACTION_REQUEST_PHONE_BOARDS`: Re-publishes cached phone station boards & active tracked train to the watch.
 */
class PhoneCompanionSyncReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PhoneCompanionReceiver"
        private const val ASYNC_BROADCAST_TIMEOUT_MS = 8_000L
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        const val ACTION_OPEN_ON_PHONE = WearSyncContract.ACTION_OPEN_ON_PHONE
        const val ACTION_START_TRACKING_FROM_WATCH = WearSyncContract.ACTION_START_TRACKING_FROM_WATCH
        const val ACTION_STOP_TRACKING_FROM_WATCH = WearSyncContract.ACTION_STOP_TRACKING_FROM_WATCH
        const val ACTION_REQUEST_PHONE_BOARDS = WearSyncContract.ACTION_REQUEST_PHONE_BOARDS
        const val ACTION_ADD_STATION_FROM_WATCH = WearSyncContract.ACTION_ADD_STATION_FROM_WATCH
        const val ACTION_REMOVE_STATION_FROM_WATCH = WearSyncContract.ACTION_REMOVE_STATION_FROM_WATCH
        const val ACTION_ASK_AI_FROM_WATCH = WearSyncContract.ACTION_ASK_AI_FROM_WATCH
        const val ACTION_OPEN_AI_ON_PHONE = WearSyncContract.ACTION_OPEN_AI_ON_PHONE
        const val ACTION_SET_ALIGHT_STOP_FROM_WATCH = WearSyncContract.ACTION_SET_ALIGHT_STOP_FROM_WATCH
        const val ACTION_SHARE_ETA_FROM_WATCH = WearSyncContract.ACTION_SHARE_ETA_FROM_WATCH

        const val EXTRA_STATION_ID = WearSyncContract.EXTRA_STATION_ID
        const val EXTRA_STATION_NAME = WearSyncContract.EXTRA_STATION_NAME
        const val EXTRA_DEPARTURE_ID = WearSyncContract.EXTRA_DEPARTURE_ID
        const val EXTRA_DESTINATION_NAME = WearSyncContract.EXTRA_DESTINATION_NAME
        const val EXTRA_LINE_NAME = WearSyncContract.EXTRA_LINE_NAME
        const val EXTRA_PLATFORM_CODE = WearSyncContract.EXTRA_PLATFORM_CODE
        const val EXTRA_COUNTDOWN_MINUTES = WearSyncContract.EXTRA_COUNTDOWN_MINUTES
        const val EXTRA_AI_PROMPT_TYPE = WearSyncContract.EXTRA_AI_PROMPT_TYPE
        const val EXTRA_AI_QUERY_TEXT = WearSyncContract.EXTRA_AI_QUERY_TEXT
        const val EXTRA_TARGET_ALIGHT_STOP = WearSyncContract.EXTRA_TARGET_ALIGHT_STOP
        const val EXTRA_SHARE_ETA_TEXT = WearSyncContract.EXTRA_SHARE_ETA_TEXT

        /**
         * Handles incoming Watch -> Phone companion commands safely from either a BroadcastReceiver
         * (`goAsync()` pendingResult) or `PhoneWearableListenerService` directly without instantiating
         * an unattached `BroadcastReceiver`.
         */
        fun handleCompanionCommand(
            context: Context,
            intent: Intent?,
            pendingResult: PendingResult? = null
        ) {
            val action = intent?.action
            if (action == null) {
                pendingResult?.finish()
                return
            }
            when (action) {
                ACTION_OPEN_ON_PHONE -> {
                    val stationId = com.androidfung.departureboard.navigation.DeepLinkIntentParser
                        .sanitizeTransitId(intent.getStringExtra(EXTRA_STATION_ID)).orEmpty()
                    val stationName = intent.getStringExtra(EXTRA_STATION_NAME).orEmpty()
                    val departureId = com.androidfung.departureboard.navigation.DeepLinkIntentParser
                        .sanitizeTransitId(intent.getStringExtra(EXTRA_DEPARTURE_ID)).orEmpty()
                    if (stationId.isNotBlank()) {
                        val deepLinkUri = Uri.Builder()
                            .scheme("mindtheboard")
                            .authority("station")
                            .appendPath(stationId)
                            .appendQueryParameter("name", stationName)
                            .appendQueryParameter("departureId", departureId)
                            .build()
                        val openIntent = Intent(Intent.ACTION_VIEW, deepLinkUri, context, MainActivity::class.java).apply {
                            putExtra(MainActivity.EXTRA_STATION_ID, stationId)
                            putExtra(MainActivity.EXTRA_STATION_NAME, stationName)
                            putExtra(
                                com.androidfung.departureboard.navigation.DeepLinkIntentParser.EXTRA_INTERNAL_TRUST_TOKEN,
                                com.androidfung.departureboard.navigation.DeepLinkIntentParser.INTERNAL_CAPABILITY_TOKEN
                            )
                            if (departureId.isNotBlank()) {
                                putExtra(MainActivity.EXTRA_DEPARTURE_ID, departureId)
                            }
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        runCatching { context.startActivity(openIntent) }
                            .onFailure { Log.w(TAG, "Failed to open station on phone from watch handoff", it) }
                    }
                    pendingResult?.finish()
                }

                ACTION_START_TRACKING_FROM_WATCH -> {
                    val departureId = com.androidfung.departureboard.navigation.DeepLinkIntentParser
                        .sanitizeTransitId(intent.getStringExtra(EXTRA_DEPARTURE_ID))
                    val sanitizedStationId = com.androidfung.departureboard.navigation.DeepLinkIntentParser
                        .sanitizeTransitId(intent.getStringExtra(EXTRA_STATION_ID))
                    if (!departureId.isNullOrBlank() && !sanitizedStationId.isNullOrBlank()) {
                        val stationId = sanitizedStationId
                        val stationName = intent.getStringExtra(EXTRA_STATION_NAME).orEmpty()
                        val destination = intent.getStringExtra(EXTRA_DESTINATION_NAME).orEmpty()
                        val region = TransitRegion.fromStationId(stationId)
                        val defaultMode = if (stationId.startsWith("910G")) "national-rail" else region.defaultPrimaryModeId
                        val defaultLineLabel = region.supportedModes.firstOrNull { it.mode != null }?.displayName ?: "Rail"
                        val lineName = intent.getStringExtra(EXTRA_LINE_NAME).orEmpty().ifBlank { defaultLineLabel }
                        val platformCode = intent.getStringExtra(EXTRA_PLATFORM_CODE).orEmpty().ifBlank { "TBD" }
                        val countdownMins = intent.getIntExtra(EXTRA_COUNTDOWN_MINUTES, 5).coerceAtLeast(1)

                        val departure = Departure(
                            id = departureId,
                            stationId = stationId,
                            stationName = stationName,
                            lineId = lineName.lowercase().replace(" ", "-"),
                            lineName = lineName,
                            platformName = "Plat $platformCode",
                            destinationName = destination,
                            towards = null,
                            timeToStationSeconds = countdownMins * 60,
                            expectedArrivalIso = null,
                            currentLocation = null,
                            modeName = defaultMode,
                            lineBadge = LineBadgeInfo(
                                lineId = lineName.lowercase(),
                                displayName = lineName,
                                backgroundColor = Color(0xFF1976D2)
                            )
                        )
                        runCatching { TrainTrackingService.startTracking(context, departure) }
                            .onFailure { Log.w(TAG, "Failed to start tracking from watch request", it) }
                    }
                    pendingResult?.finish()
                }

                ACTION_STOP_TRACKING_FROM_WATCH -> {
                    runCatching { TrainTrackingService.stopTracking(context, notifyWear = false) }
                        .onFailure { Log.w(TAG, "Failed to stop tracking from watch request", it) }
                    pendingResult?.finish()
                }

                ACTION_SET_ALIGHT_STOP_FROM_WATCH -> {
                    val targetStop = intent.getStringExtra(EXTRA_TARGET_ALIGHT_STOP)
                    TrainTrackingService.setTargetAlightStop(targetStop)
                    pendingResult?.finish()
                }

                ACTION_SHARE_ETA_FROM_WATCH -> {
                    val shareText = intent.getStringExtra(EXTRA_SHARE_ETA_TEXT)?.takeIf { it.isNotBlank() }
                    if (shareText != null) {
                        runCatching {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            val chooser = Intent.createChooser(sendIntent, null).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(chooser)
                        }.onFailure { Log.w(TAG, "Failed to launch share sheet from watch request", it) }
                    }
                    pendingResult?.finish()
                }

                ACTION_REQUEST_PHONE_BOARDS -> {
                    WearCompanionSyncPublisher.resendCachedStateToWear(context)
                    pendingResult?.finish()
                }

                ACTION_ADD_STATION_FROM_WATCH -> {
                    val rawStationId = intent.getStringExtra(EXTRA_STATION_ID).orEmpty().trim()
                    val rawStationName = intent.getStringExtra(EXTRA_STATION_NAME).orEmpty().trim()
                    if (rawStationId.isBlank() && rawStationName.isBlank()) {
                        pendingResult?.finish()
                        return
                    }
                    receiverScope.launch {
                        try {
                            withTimeoutOrNull(ASYNC_BROADCAST_TIMEOUT_MS) {
                                val repo = context.applicationContext.appContainer.transitRepository
                                val currentRegion = repo.selectedRegionFlow.first()
                                val matchedFromQuery = if (rawStationId.startsWith("search:", ignoreCase = true) || rawStationId.isBlank()) {
                                    val q = rawStationName.ifBlank { rawStationId.removePrefix("search:") }
                                    currentRegion.directory?.searchStations(q)?.firstOrNull()
                                        ?: repo.searchStations(q).getOrNull()?.firstOrNull()
                                } else null

                                val stationId = matchedFromQuery?.id
                                    ?: com.androidfung.departureboard.navigation.DeepLinkIntentParser.sanitizeTransitId(rawStationId)
                                    ?: return@withTimeoutOrNull
                                val stationName = matchedFromQuery?.name ?: rawStationName.ifBlank { stationId }
                                val region = TransitRegion.fromStationId(stationId)
                                val fallbackMode = if (stationId.startsWith("910G")) "national-rail" else region.defaultPrimaryModeId
                                val known = matchedFromQuery
                                    ?: region.directory?.getStationById(stationId)
                                    ?: repo.getStationById(stationId)
                                    ?: Station(
                                        id = stationId,
                                        name = stationName,
                                        modes = listOf(fallbackMode),
                                        isFavorite = true
                                    )
                                repo.saveStation(known.copy(isFavorite = true))
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to add station from watch", e)
                        } finally {
                            pendingResult?.finish()
                        }
                    }
                }

                ACTION_REMOVE_STATION_FROM_WATCH -> {
                    val stationId = com.androidfung.departureboard.navigation.DeepLinkIntentParser
                        .sanitizeTransitId(intent.getStringExtra(EXTRA_STATION_ID))
                    if (stationId == null) {
                        pendingResult?.finish()
                        return
                    }
                    receiverScope.launch {
                        try {
                            withTimeoutOrNull(ASYNC_BROADCAST_TIMEOUT_MS) {
                                val repo = context.applicationContext.appContainer.transitRepository
                                repo.removeStation(stationId)
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to remove station from watch", e)
                        } finally {
                            pendingResult?.finish()
                        }
                    }
                }

                ACTION_OPEN_AI_ON_PHONE -> {
                    val queryText = intent.getStringExtra(EXTRA_AI_QUERY_TEXT).orEmpty()
                    val stationId = com.androidfung.departureboard.navigation.DeepLinkIntentParser
                        .sanitizeTransitId(intent.getStringExtra(EXTRA_STATION_ID)).orEmpty()
                    val stationName = intent.getStringExtra(EXTRA_STATION_NAME).orEmpty()
                    val openIntent = Intent(context, MainActivity::class.java).apply {
                        if (stationId.isNotBlank()) {
                            putExtra(MainActivity.EXTRA_STATION_ID, stationId)
                            putExtra(MainActivity.EXTRA_STATION_NAME, stationName)
                        }
                        if (queryText.isNotBlank()) {
                            putExtra(MainActivity.EXTRA_AI_QUERY, queryText)
                            putExtra(MainActivity.EXTRA_AUTO_SUBMIT_AI_QUERY, true)
                        }
                        putExtra(
                            com.androidfung.departureboard.navigation.DeepLinkIntentParser.EXTRA_INTERNAL_TRUST_TOKEN,
                            com.androidfung.departureboard.navigation.DeepLinkIntentParser.INTERNAL_CAPABILITY_TOKEN
                        )
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    runCatching { context.startActivity(openIntent) }
                        .onFailure { Log.w(TAG, "Failed to open AI sheet on phone from watch", it) }
                    pendingResult?.finish()
                }

                ACTION_ASK_AI_FROM_WATCH -> {
                    val stationId = com.androidfung.departureboard.navigation.DeepLinkIntentParser
                        .sanitizeTransitId(intent.getStringExtra(EXTRA_STATION_ID)).orEmpty()
                    if (stationId.isBlank()) {
                        pendingResult?.finish()
                        return
                    }
                    val stationName = intent.getStringExtra(EXTRA_STATION_NAME).orEmpty()
                    val promptType = intent.getStringExtra(EXTRA_AI_PROMPT_TYPE).orEmpty()
                    val queryText = intent.getStringExtra(EXTRA_AI_QUERY_TEXT).orEmpty()
                    receiverScope.launch {
                        try {
                            withTimeoutOrNull(ASYNC_BROADCAST_TIMEOUT_MS) {
                                val repo = context.applicationContext.appContainer.transitRepository
                                val departures = repo.getDepartures(stationId, stationName).getOrDefault(emptyList())
                                val (headline, summary) = synthesizeConcourseTriage(
                                    stationId = stationId,
                                    stationName = stationName,
                                    promptType = promptType,
                                    queryText = queryText,
                                    departures = departures
                                )
                                WearCompanionSyncPublisher.publishAiResponse(
                                    context = context,
                                    stationId = stationId,
                                    headline = headline,
                                    summary = summary,
                                    rawQuery = queryText.ifBlank { "Concourse Triage at $stationName" }
                                )
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to evaluate watch AI query on phone", e)
                        } finally {
                            pendingResult?.finish()
                        }
                    }
                }

                else -> pendingResult?.finish()
            }
        }

        private fun synthesizeConcourseTriage(
            stationId: String,
            stationName: String,
            promptType: String,
            queryText: String,
            departures: List<Departure>
        ): Pair<String, String> {
            val activeDeps = departures.filter { it.status != DepartureStatus.CANCELLED }
            if (activeDeps.isEmpty()) {
                return "No Active Trains" to "No live departures currently listed at $stationName."
            }

            val lowerQuery = queryText.lowercase()
            return when {
                promptType == "DELAYS_AND_BACKLOG" || lowerQuery.contains("delay") || lowerQuery.contains("backlog") -> {
                    val backlogs = PlatformBacklogDetector.detectBacklogs(departures)
                    val delayRepayDep = departures.firstNotNullOfOrNull { dep ->
                        DelayRepayCalculator.evaluate(dep)?.let { dep to it }
                    }
                    val delayedCount = departures.count { it.status == DepartureStatus.DELAYED || it.status == DepartureStatus.CANCELLED }
                    when {
                        delayRepayDep != null -> {
                            val (dep, dr) = delayRepayDep
                            "💸 ${dr.badgeLabel}" to "${dep.destinationName} (${dep.lineName}) qualifies: ${dr.compensationSummary}"
                        }
                        backlogs.isNotEmpty() -> {
                            val b = backlogs.first()
                            val dirStr = b.direction?.let { " $it" }.orEmpty()
                            "⚠️ ${b.lineName}$dirStr Backlog" to "${b.minutesUntilNext}m wait (normal ~${b.typicalHeadwayMinutes}m). Expect crowded platform."
                        }
                        delayedCount > 0 -> {
                            "⚠️ $delayedCount Disrupted" to "$delayedCount service(s) delayed/cancelled at $stationName. Next on-time: ${activeDeps.first().destinationName} (${activeDeps.first().formattedTimeToArrival})."
                        }
                        else -> {
                            "✅ Good Service" to "No platform backlogs or major delays detected at $stationName."
                        }
                    }
                }

                promptType == "LAST_TRAIN" || lowerQuery.contains("last") || lowerQuery.contains("night") -> {
                    val lastTrain = activeDeps.firstOrNull { it.isLastTrain }
                    val nightService = activeDeps.firstOrNull { it.isNightService }
                    when {
                        lastTrain != null -> {
                            "🌙 Last Train Alert" to "Last ${lastTrain.lineName} to ${lastTrain.destinationName} departs in ${lastTrain.formattedTimeToArrival} (${lastTrain.platformName})."
                        }
                        nightService != null -> {
                            "🦉 24hr Night Service" to "${nightService.lineName} is running overnight at $stationName. Next: ${nightService.destinationName} in ${nightService.formattedTimeToArrival}."
                        }
                        else -> {
                            val latest = activeDeps.maxByOrNull { it.timeToStationSeconds } ?: activeDeps.first()
                            "🕒 Regular Service" to "Multiple trains still running. Latest on board: ${latest.destinationName} in ${latest.formattedTimeToArrival}."
                        }
                    }
                }

                else -> {
                    val matchedByVoice = if (lowerQuery.isNotBlank()) {
                        activeDeps.firstOrNull { dep ->
                            lowerQuery.contains(dep.destinationName.lowercase()) ||
                                dep.destinationName.lowercase().split(" ").any { word -> word.length >= 4 && lowerQuery.contains(word) } ||
                                dep.callingPoints.any { cp -> lowerQuery.contains(cp.stationName.lowercase()) }
                        }
                    } else null

                    val best = matchedByVoice
                        ?: activeDeps.firstOrNull { !it.platformName.contains("TBD", ignoreCase = true) }
                        ?: activeDeps.first()
                    val mins = (best.timeToStationSeconds / 60).coerceAtLeast(0)
                    val timeStr = if (mins <= 0) "Due now" else "${mins}m"
                    val platStr = best.platformName.ifBlank { "Plat TBD" }
                    "⚡ Take $platStr ($timeStr)" to "${best.destinationName} (${best.lineName}) is your fastest option at $stationName."
                }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val needsAsync = action == ACTION_ADD_STATION_FROM_WATCH ||
            action == ACTION_REMOVE_STATION_FROM_WATCH ||
            action == ACTION_ASK_AI_FROM_WATCH
        val pendingResult = if (needsAsync) goAsync() else null
        handleCompanionCommand(context, intent, pendingResult)
    }
}
