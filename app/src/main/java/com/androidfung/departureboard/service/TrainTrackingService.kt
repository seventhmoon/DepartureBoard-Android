package com.androidfung.departureboard.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import com.androidfung.departureboard.MainActivity
import com.androidfung.departureboard.R
import com.androidfung.departureboard.appContainer
import com.androidfung.departureboard.data.model.CallingPoint
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.TransitLineMatcher
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.ui.designsystem.TransitIconHelper
import com.androidfung.departureboard.util.NotificationHelper
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that tracks an active train / bus departure and posts
 * real-time Live Update Notifications (Android 16 Promoted Ongoing notification support).
 */
class TrainTrackingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var trackingJob: Job? = null
    @Volatile
    private var retainNotificationOnDestroy = false

    companion object {
        private const val TAG = "TrainTrackingService"
        const val CHANNEL_ID = "live_train_tracking_channel"
        const val ALERT_CHANNEL_ID = "train_tracking_alert_channel"
        const val PLATFORM_ALERT_CHANNEL_ID = "platform_change_alert_channel"
        const val ALIGHTING_ALERT_CHANNEL_ID = "alighting_stop_alert_channel"
        const val NOTIFICATION_ID = 2001
        const val ALERT_NOTIFICATION_ID = 2002
        const val PLATFORM_ALERT_NOTIFICATION_ID = 2003
        const val ALIGHTING_ALERT_NOTIFICATION_ID = 2004

        // Precompiled regex patterns to avoid per-notification allocations
        private val PLATFORM_LABEL_REGEX = Regex("""Platform\s+[A-Za-z0-9]+""", RegexOption.IGNORE_CASE)
        private val STOP_LABEL_REGEX = Regex("""Stop\s+[A-Za-z0-9]+""", RegexOption.IGNORE_CASE)

        // Default brand & status colors (ARGB) for ProgressStyle segments
        const val DEFAULT_TRANSIT_BLUE_ARGB = 0xFF1976D2.toInt()
        const val DELAY_AMBER_ARGB = 0xFFF57C00.toInt()
        const val CANCELLED_GREY_ARGB = 0xFF9E9E9E.toInt()

        // Polling intervals in seconds
        private const val POLL_INTERVAL_LONG = 45L
        private const val POLL_INTERVAL_MEDIUM = 30L
        private const val POLL_INTERVAL_SHORT = 15L
        private const val MAX_TRACKING_DURATION_MS = 90 * 60 * 1000L // 90 min max runtime safeguard
        private const val ARRIVAL_GRACE_TIMEOUT_MS = 60_000L // Keep boarding notice visible for 60s on arrival
        private const val FUZZY_MATCH_WINDOW_SECONDS = 90
        private const val SIGNIFICANT_DELAY_JUMP_SECONDS = 180

        const val ACTION_START_TRACKING = "com.androidfung.departureboard.action.START_TRACKING"
        const val ACTION_STOP_TRACKING = "com.androidfung.departureboard.action.STOP_TRACKING"

        const val EXTRA_DEPARTURE_ID = "extra_departure_id"
        const val EXTRA_STATION_ID = "extra_station_id"
        const val EXTRA_STATION_NAME = "extra_station_name"
        const val EXTRA_DESTINATION_NAME = "extra_destination_name"
        const val EXTRA_DESTINATION_ID = "extra_destination_id"
        const val EXTRA_LINE_ID = "extra_line_id"
        const val EXTRA_LINE_NAME = "extra_line_name"
        const val EXTRA_PLATFORM = "extra_platform"
        const val EXTRA_INITIAL_SECONDS = "extra_initial_seconds"
        const val EXTRA_IS_BUS = "extra_is_bus"
        const val EXTRA_INITIAL_LOCATION = "extra_initial_location"
        const val EXTRA_MODE_NAME = "extra_mode_name"
        const val EXTRA_VEHICLE_ID = "extra_vehicle_id"
        const val EXTRA_LINE_COLOR_ARGB = "extra_line_color_argb"
        const val EXTRA_TRAIN_CODE = "extra_train_code"
        const val EXTRA_COACH_COUNT = "extra_coach_count"
        const val EXTRA_DELAY_REASON = "extra_delay_reason"
        const val EXTRA_IS_DELAYED = "extra_is_delayed"
        const val EXTRA_TARGET_ALIGHT_STOP_NAME = "extra_target_alight_stop_name"

        private val _currentlyTrackedDepartureIdFlow = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
        val currentlyTrackedDepartureIdFlow: kotlinx.coroutines.flow.StateFlow<String?> = _currentlyTrackedDepartureIdFlow

        private val _targetAlightStopFlow = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
        val targetAlightStopFlow: kotlinx.coroutines.flow.StateFlow<String?> = _targetAlightStopFlow

        var currentlyTrackedDepartureId: String?
            get() = _currentlyTrackedDepartureIdFlow.value
            private set(value) {
                _currentlyTrackedDepartureIdFlow.value = value
            }

        fun setTargetAlightStop(stopName: String?) {
            _targetAlightStopFlow.value = stopName?.takeIf { it.isNotBlank() }
        }

        fun startTracking(
            context: Context,
            departure: Departure,
            targetAlightStopName: String? = null
        ) {
            if (targetAlightStopName != null) {
                _targetAlightStopFlow.value = targetAlightStopName.takeIf { it.isNotBlank() }
            }
            val isBus = departure.modeName.equals("bus", ignoreCase = true)
            val intent = Intent(context, TrainTrackingService::class.java).apply {
                action = ACTION_START_TRACKING
                putExtra(EXTRA_DEPARTURE_ID, departure.id)
                putExtra(EXTRA_STATION_ID, departure.stationId)
                putExtra(EXTRA_STATION_NAME, departure.stationName)
                putExtra(EXTRA_DESTINATION_NAME, departure.destinationName)
                putExtra(EXTRA_DESTINATION_ID, departure.destinationStationId)
                putExtra(EXTRA_LINE_ID, departure.lineId)
                putExtra(EXTRA_LINE_NAME, departure.lineName)
                putExtra(EXTRA_PLATFORM, departure.platformName)
                putExtra(EXTRA_INITIAL_SECONDS, departure.timeToStationSeconds)
                putExtra(EXTRA_IS_BUS, isBus)
                putExtra(EXTRA_INITIAL_LOCATION, departure.currentLocation)
                putExtra(EXTRA_MODE_NAME, departure.modeName)
                putExtra(EXTRA_VEHICLE_ID, departure.vehicleId)
                putExtra(EXTRA_LINE_COLOR_ARGB, departure.lineBadge.backgroundColor.toArgb())
                putExtra(EXTRA_TRAIN_CODE, departure.displayTrainCode)
                putExtra(EXTRA_COACH_COUNT, departure.formation?.coachCount ?: 0)
                putExtra(EXTRA_DELAY_REASON, departure.delayReason)
                putExtra(EXTRA_IS_DELAYED, departure.status == DepartureStatus.DELAYED)
                putExtra(EXTRA_TARGET_ALIGHT_STOP_NAME, _targetAlightStopFlow.value)
            }
            context.startForegroundService(intent)
        }

        fun stopTracking(context: Context) {
            val intent = Intent(context, TrainTrackingService::class.java).apply {
                action = ACTION_STOP_TRACKING
            }
            context.startService(intent)
        }

        fun getPollIntervalSeconds(remainingSeconds: Int): Long = when {
            remainingSeconds > 300 -> POLL_INTERVAL_LONG
            remainingSeconds > 120 -> POLL_INTERVAL_MEDIUM
            else -> POLL_INTERVAL_SHORT
        }

        /**
         * Resolves the tracked departure from a fresh list of station departures.
         * Falls back to vehicleId or line + destination + time-window matching when
         * regional feeds synthesize volatile per-poll departure IDs.
         */
        internal fun findMatchingDeparture(
            departures: List<Departure>,
            departureId: String,
            lineId: String,
            vehicleId: String?,
            destinationId: String?,
            destinationName: String,
            expectedRemainingSeconds: Int
        ): Departure? {
            if (departures.isEmpty()) return null

            // 1. Exact ID match
            departures.firstOrNull { it.id == departureId }?.let { return it }

            // 2. Vehicle ID match on the same line
            if (!vehicleId.isNullOrBlank()) {
                departures.firstOrNull { dep ->
                    dep.vehicleId.equals(vehicleId, ignoreCase = true) &&
                        (lineId.isBlank() || TransitLineMatcher.isSameLine(dep.lineId, lineId))
                }?.let { return it }
            }

            // 3. Fuzzy match by line + destination within a +/-90s prediction window
            return departures
                .filter { dep ->
                    val sameLine = lineId.isBlank() || TransitLineMatcher.isSameLine(dep.lineId, lineId)
                    val sameDestination = (!destinationId.isNullOrBlank() &&
                        !dep.destinationStationId.isNullOrBlank() &&
                        dep.destinationStationId.equals(destinationId, ignoreCase = true)) ||
                        (destinationName.isNotBlank() &&
                            dep.destinationName.equals(destinationName, ignoreCase = true))
                    val withinWindow = abs(dep.timeToStationSeconds - expectedRemainingSeconds) <= FUZZY_MATCH_WINDOW_SECONDS
                    sameLine && sameDestination && withinWindow
                }
                .minByOrNull { abs(it.timeToStationSeconds - expectedRemainingSeconds) }
        }

        internal fun computeProgress(
            timeToStationSeconds: Int,
            baselineSeconds: Int,
            currentStopsAway: Int?,
            baselineStops: Int?,
            isCancelled: Boolean
        ): Int = when {
            isCancelled -> 0
            timeToStationSeconds <= 30 -> 100
            currentStopsAway != null && baselineStops != null && baselineStops > 0 -> {
                val stopsCompleted = (baselineStops - currentStopsAway).coerceAtLeast(0).toDouble()
                val stopFraction = stopsCompleted / baselineStops.toDouble()
                (5 + (stopFraction * 90.0)).toInt().coerceIn(5, 95)
            }
            else -> {
                val safeBaseline = baselineSeconds.coerceAtLeast(30).toDouble()
                val timeFraction = (1.0 - (timeToStationSeconds.toDouble() / safeBaseline)).coerceIn(0.0, 1.0)
                (timeFraction * 95.0).toInt().coerceIn(5, 95)
            }
        }

        /**
         * Builds calling-point milestone dots along the progress bar (1..100).
         */
        internal fun buildMilestonePoints(baselineStops: Int?): List<NotificationCompat.ProgressStyle.Point> {
            val clampedStops = baselineStops?.coerceIn(1, 12) ?: 1
            if (clampedStops <= 1) {
                return listOf(
                    NotificationCompat.ProgressStyle.Point(1),
                    NotificationCompat.ProgressStyle.Point(100)
                )
            }
            return buildList {
                add(NotificationCompat.ProgressStyle.Point(1))
                for (step in 1 until clampedStops) {
                    val pos = (5 + (step.toDouble() / clampedStops.toDouble()) * 90.0)
                        .toInt()
                        .coerceIn(5, 95)
                    add(NotificationCompat.ProgressStyle.Point(pos))
                }
                add(NotificationCompat.ProgressStyle.Point(100))
            }.distinctBy { it.position }
        }

        /**
         * Builds brand-colored and status-aware segments for Android 16 ProgressStyle.
         */
        internal fun buildProgressSegments(
            lineColorArgb: Int,
            progressVal: Int,
            isDelayed: Boolean,
            isCancelled: Boolean
        ): List<NotificationCompat.ProgressStyle.Segment> {
            val baseColor = if ((lineColorArgb ushr 24) == 0) DEFAULT_TRANSIT_BLUE_ARGB else lineColorArgb
            return when {
                isCancelled -> listOf(
                    NotificationCompat.ProgressStyle.Segment(100).setColor(CANCELLED_GREY_ARGB)
                )
                isDelayed && progressVal in 1..99 -> listOf(
                    NotificationCompat.ProgressStyle.Segment(progressVal).setColor(baseColor),
                    NotificationCompat.ProgressStyle.Segment(100 - progressVal).setColor(DELAY_AMBER_ARGB)
                )
                isDelayed -> listOf(
                    NotificationCompat.ProgressStyle.Segment(100).setColor(DELAY_AMBER_ARGB)
                )
                else -> listOf(
                    NotificationCompat.ProgressStyle.Segment(100).setColor(baseColor)
                )
            }
        }

        /**
         * Formats the secondary notification text with platform, coach count/headcode, and delay reason.
         */
        internal fun formatContentText(
            shortPlatform: String,
            coachCount: Int = 0,
            trainCode: String? = null,
            delayReason: String? = null,
            isCancelled: Boolean = false,
            cancelledDesc: String = ""
        ): String {
            if (isCancelled) return cancelledDesc
            return buildList {
                if (shortPlatform.isNotBlank()) add(shortPlatform)
                if (coachCount > 1) {
                    add("$coachCount coaches")
                } else if (!trainCode.isNullOrBlank()) {
                    add("#$trainCode")
                }
                if (!delayReason.isNullOrBlank()) {
                    add(delayReason.trim())
                }
            }.joinToString(" • ")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Basic permission check - just to ensure we can log if something is wrong
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted, service might not function properly")
            }
        }

        when (intent?.action) {
            ACTION_STOP_TRACKING -> {
                retainNotificationOnDestroy = false
                stopTrackingInternal()
                stopSelf()
            }
            ACTION_START_TRACKING -> {
                retainNotificationOnDestroy = false
                val departureId = intent.getStringExtra(EXTRA_DEPARTURE_ID) ?: return START_NOT_STICKY
                val stationId = intent.getStringExtra(EXTRA_STATION_ID) ?: ""
                val stationName = intent.getStringExtra(EXTRA_STATION_NAME) ?: ""
                val destination = intent.getStringExtra(EXTRA_DESTINATION_NAME) ?: getString(R.string.tracking_default_destination)
                val lineName = intent.getStringExtra(EXTRA_LINE_NAME) ?: getString(R.string.tracking_default_train)
                val platform = intent.getStringExtra(EXTRA_PLATFORM) ?: ""
                val initialSeconds = intent.getIntExtra(EXTRA_INITIAL_SECONDS, 180)
                val lineId = intent.getStringExtra(EXTRA_LINE_ID) ?: ""
                val isBus = intent.getBooleanExtra(EXTRA_IS_BUS, false)
                val initialLocation = intent.getStringExtra(EXTRA_INITIAL_LOCATION)
                val destinationId = intent.getStringExtra(EXTRA_DESTINATION_ID)
                val modeName = intent.getStringExtra(EXTRA_MODE_NAME) ?: if (isBus) "bus" else "tube"
                val vehicleId = intent.getStringExtra(EXTRA_VEHICLE_ID)
                val lineColorArgb = intent.getIntExtra(EXTRA_LINE_COLOR_ARGB, DEFAULT_TRANSIT_BLUE_ARGB)
                val trainCode = intent.getStringExtra(EXTRA_TRAIN_CODE)
                val coachCount = intent.getIntExtra(EXTRA_COACH_COUNT, 0)
                val delayReason = intent.getStringExtra(EXTRA_DELAY_REASON)
                val isDelayed = intent.getBooleanExtra(EXTRA_IS_DELAYED, false)
                val targetAlightStop = intent.getStringExtra(EXTRA_TARGET_ALIGHT_STOP_NAME)

                if (!targetAlightStop.isNullOrBlank()) {
                    _targetAlightStopFlow.value = targetAlightStop
                }

                currentlyTrackedDepartureId = departureId

                // Base baseline (B to S): Initial total seconds captured at moment user clicked Track
                val baselineSeconds = initialSeconds.coerceAtLeast(30)

                val notification = buildLiveNotification(
                    departureId = departureId,
                    stationId = stationId,
                    stationName = stationName,
                    destination = destination,
                    lineName = lineName,
                    platform = platform,
                    timeToStationSeconds = initialSeconds,
                    currentLocation = initialLocation,
                    isBus = isBus,
                    modeName = modeName,
                    lineColorArgb = lineColorArgb,
                    coachCount = coachCount,
                    trainCode = trainCode,
                    delayReason = delayReason,
                    isDelayed = isDelayed,
                    currentStopsAway = null,
                    baselineStops = null,
                    baselineSeconds = baselineSeconds
                )

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        val hasLocationPermission =
                            checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                                checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        val fgsType = if (hasLocationPermission) {
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                        } else {
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                        }
                        ServiceCompat.startForeground(
                            this,
                            NOTIFICATION_ID,
                            notification,
                            fgsType
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start foreground service", e)
                    stopSelf()
                    return START_NOT_STICKY
                }

                // Immediately push the newly tracked train to the Wear OS companion watch at t=0ms
                // so the watch launches its full-screen Concourse Sprint HUD without waiting for the first API poll.
                com.androidfung.departureboard.wear.WearCompanionSyncPublisher.publishTrackedDeparture(
                    context = applicationContext,
                    departureId = departureId,
                    stationId = stationId,
                    stationName = stationName,
                    destinationName = destination,
                    lineName = lineName,
                    platform = platform,
                    remainingSeconds = initialSeconds,
                    currentLocation = initialLocation,
                    stopsAway = null,
                    targetAlightStopName = _targetAlightStopFlow.value,
                    isCancelled = false,
                    lineColorArgb = lineColorArgb
                )

                startTrackingLoop(
                    departureId = departureId,
                    stationId = stationId,
                    stationName = stationName,
                    destination = destination,
                    destinationId = destinationId,
                    lineId = lineId,
                    lineName = lineName,
                    platform = platform,
                    initialSeconds = initialSeconds,
                    isBus = isBus,
                    modeName = modeName,
                    vehicleId = vehicleId,
                    lineColorArgb = lineColorArgb,
                    initialTrainCode = trainCode,
                    initialCoachCount = coachCount,
                    initialDelayReason = delayReason,
                    initialIsDelayed = isDelayed,
                    baselineSeconds = baselineSeconds
                )
            }
        }
        return START_NOT_STICKY
    }

    private fun startTrackingLoop(
        departureId: String,
        stationId: String,
        stationName: String,
        destination: String,
        destinationId: String?,
        lineId: String,
        lineName: String,
        platform: String,
        initialSeconds: Int,
        isBus: Boolean,
        modeName: String,
        vehicleId: String?,
        lineColorArgb: Int,
        initialTrainCode: String?,
        initialCoachCount: Int,
        initialDelayReason: String?,
        initialIsDelayed: Boolean,
        baselineSeconds: Int
    ) {
        val previousJob = trackingJob
        trackingJob = serviceScope.launch {
            try {
                previousJob?.cancelAndJoin()
            } catch (_: CancellationException) {
            }

            val repository = applicationContext.appContainer.transitRepository
            var activeDepartureId = departureId
            var activeVehicleId = vehicleId
            var remainingSeconds = initialSeconds
            var currentDestination = destination
            var currentPlatform = platform
            var currentTrainCode = initialTrainCode
            var currentCoachCount = initialCoachCount
            var currentDelayReason = initialDelayReason
            var currentIsDelayed = initialIsDelayed
            var hasPostedDelayAlert = initialIsDelayed
            var hasPostedAlightAlert = false
            var lastAlertedTargetStop: String? = null
            var baselineStops: Int? = null
            var missingCount = 0
            var isServiceCancelled = false
            var cachedCallingPoints: List<CallingPoint>? = null
            val startTrackingTimeMillis = System.currentTimeMillis()

            while (isActive && remainingSeconds > 0 && (System.currentTimeMillis() - startTrackingTimeMillis) < MAX_TRACKING_DURATION_MS) {
                var liveLocation: String? = null
                var isCancelled = false

                try {
                    val res = repository.getDepartures(stationId, stationName)
                    if (res.isSuccess) {
                        val matches = res.getOrDefault(emptyList())
                        val elapsedEstimate = getPollIntervalSeconds(remainingSeconds).toInt()
                        val expectedRemaining = (remainingSeconds - elapsedEstimate).coerceAtLeast(0)
                        val target = findMatchingDeparture(
                            departures = matches,
                            departureId = activeDepartureId,
                            lineId = lineId,
                            vehicleId = activeVehicleId,
                            destinationId = destinationId,
                            destinationName = currentDestination,
                            expectedRemainingSeconds = expectedRemaining
                        )
                        if (target != null) {
                            missingCount = 0
                            val previousRemainingSeconds = remainingSeconds
                            val wasDelayed = currentIsDelayed

                            if (target.id != activeDepartureId) {
                                activeDepartureId = target.id
                                currentlyTrackedDepartureId = target.id
                            }
                            if (!target.vehicleId.isNullOrBlank()) {
                                activeVehicleId = target.vehicleId
                            }
                            remainingSeconds = target.timeToStationSeconds
                            liveLocation = target.currentLocation
                            currentTrainCode = target.displayTrainCode ?: currentTrainCode
                            currentCoachCount = target.formation?.coachCount ?: currentCoachCount
                            currentDelayReason = target.delayReason ?: currentDelayReason
                            currentIsDelayed = target.status == DepartureStatus.DELAYED

                            // 1. Check for explicit cancellation
                            if (target.status == DepartureStatus.CANCELLED ||
                                target.currentLocation.equals("Cancelled", ignoreCase = true)
                            ) {
                                isCancelled = true
                            }

                            // 2. Check for destination change
                            val destinationChanged = if (!destinationId.isNullOrBlank() && !target.destinationStationId.isNullOrBlank()) {
                                !target.destinationStationId.equals(destinationId, ignoreCase = true)
                            } else {
                                target.destinationName.isNotBlank() && !target.destinationName.equals(currentDestination, ignoreCase = true)
                            }

                            if (!isCancelled && destinationChanged && target.destinationName.isNotBlank()) {
                                val oldDest = currentDestination
                                currentDestination = target.destinationName
                                if (NotificationHelper.isDisruptionAlertsEnabled(applicationContext)) {
                                    postDisruptionAlert(
                                        title = getString(R.string.tracking_destination_changed_title),
                                        message = getString(R.string.tracking_destination_changed_message, lineName, oldDest, currentDestination),
                                        stationId = stationId,
                                        stationName = stationName,
                                        departureId = activeDepartureId,
                                        isBus = isBus,
                                        channelId = ALERT_CHANNEL_ID,
                                        notificationId = ALERT_NOTIFICATION_ID
                                    )
                                }
                            }

                            // 3. Check for platform change or first announcement from TBD
                            val isOldTbd = currentPlatform.isBlank() || currentPlatform.contains("TBD", ignoreCase = true)
                            val isNewTbd = target.platformName.contains("TBD", ignoreCase = true)
                            if (!isCancelled && target.platformName.isNotBlank() &&
                                !target.platformName.equals(currentPlatform, ignoreCase = true)
                            ) {
                                val oldPlatform = currentPlatform
                                currentPlatform = target.platformName
                                if (NotificationHelper.isPlatformAlertsEnabled(applicationContext)) {
                                    val alertTitle = if (isOldTbd && !isNewTbd) {
                                        getString(R.string.tracking_platform_announced_title)
                                    } else {
                                        getString(R.string.tracking_platform_changed_title)
                                    }
                                    val alertMsg = if (isOldTbd && !isNewTbd) {
                                        getString(R.string.tracking_platform_announced_message, lineName, currentDestination, currentPlatform)
                                    } else {
                                        getString(R.string.tracking_platform_changed_message, lineName, currentDestination, oldPlatform, currentPlatform)
                                    }
                                    triggerConcourseHapticPulse()
                                    postDisruptionAlert(
                                        title = alertTitle,
                                        message = alertMsg,
                                        stationId = stationId,
                                        stationName = stationName,
                                        departureId = activeDepartureId,
                                        isBus = isBus,
                                        channelId = PLATFORM_ALERT_CHANNEL_ID,
                                        notificationId = PLATFORM_ALERT_NOTIFICATION_ID
                                    )
                                }
                            }

                            // 4. Check for significant new delay
                            val delayJumpSeconds = remainingSeconds - previousRemainingSeconds
                            if (!isCancelled && !hasPostedDelayAlert &&
                                ((currentIsDelayed && !wasDelayed) ||
                                    (delayJumpSeconds >= SIGNIFICANT_DELAY_JUMP_SECONDS && remainingSeconds >= SIGNIFICANT_DELAY_JUMP_SECONDS))
                            ) {
                                hasPostedDelayAlert = true
                                if (NotificationHelper.isDisruptionAlertsEnabled(applicationContext)) {
                                    val mins = (remainingSeconds / 60).coerceAtLeast(1)
                                    postDisruptionAlert(
                                        title = getString(R.string.tracking_service_delayed_title),
                                        message = getString(R.string.tracking_service_delayed_message, lineName, currentDestination, mins),
                                        stationId = stationId,
                                        stationName = stationName,
                                        departureId = activeDepartureId,
                                        isBus = isBus,
                                        channelId = ALERT_CHANNEL_ID,
                                        notificationId = ALERT_NOTIFICATION_ID
                                    )
                                }
                            }

                            // 5. Auto-record Delay Repay receipt when tracked train qualifies (>=15m delay)
                            if (!isBus) {
                                com.androidfung.departureboard.util.DelayRepayCalculator.evaluate(target, cachedCallingPoints ?: target.callingPoints)?.let { dr ->
                                    com.androidfung.departureboard.util.DelayRepayReceiptLog.recordReceipt(
                                        context = applicationContext,
                                        departureId = activeDepartureId,
                                        stationName = stationName,
                                        destinationName = currentDestination,
                                        lineName = lineName,
                                        trainCode = currentTrainCode,
                                        delayMinutes = dr.delayMinutes,
                                        isCancelled = false,
                                        badgeLabel = dr.badgeLabel,
                                        compensationSummary = dr.compensationSummary
                                    )
                                }
                            }
                        } else {
                            missingCount++
                            val elapsedSeconds = getPollIntervalSeconds(remainingSeconds).toInt()
                            remainingSeconds = (remainingSeconds - elapsedSeconds).coerceAtLeast(0)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "API call failed for departure tracking", e)
                    val elapsedSeconds = getPollIntervalSeconds(remainingSeconds).toInt()
                    remainingSeconds = (remainingSeconds - elapsedSeconds).coerceAtLeast(0)
                }

                if (isCancelled) {
                    isServiceCancelled = true
                    if (!isBus) {
                        com.androidfung.departureboard.util.DelayRepayReceiptLog.recordReceipt(
                            context = applicationContext,
                            departureId = activeDepartureId,
                            stationName = stationName,
                            destinationName = currentDestination,
                            lineName = lineName,
                            trainCode = currentTrainCode,
                            delayMinutes = 30,
                            isCancelled = true,
                            badgeLabel = "Cancelled • Refund Eligible",
                            compensationSummary = "Service cancelled ($lineName to $currentDestination). Eligible for full unused ticket refund or Delay Repay on next train."
                        )
                    }
                    if (NotificationHelper.isDisruptionAlertsEnabled(applicationContext)) {
                        postDisruptionAlert(
                            title = getString(R.string.tracking_service_cancelled_title),
                            message = getString(R.string.tracking_service_cancelled_message, lineName, currentDestination),
                            stationId = stationId,
                            stationName = stationName,
                            departureId = activeDepartureId,
                            isBus = isBus,
                            channelId = ALERT_CHANNEL_ID,
                            notificationId = ALERT_NOTIFICATION_ID
                        )
                    }
                    val cancelledNotification = buildLiveNotification(
                        departureId = activeDepartureId,
                        stationId = stationId,
                        stationName = stationName,
                        destination = currentDestination,
                        lineName = lineName,
                        platform = currentPlatform,
                        timeToStationSeconds = remainingSeconds,
                        currentLocation = getString(R.string.status_cancelled),
                        isBus = isBus,
                        modeName = modeName,
                        lineColorArgb = lineColorArgb,
                        coachCount = currentCoachCount,
                        trainCode = currentTrainCode,
                        delayReason = currentDelayReason,
                        isDelayed = currentIsDelayed,
                        currentStopsAway = null,
                        baselineStops = null,
                        baselineSeconds = baselineSeconds,
                        isCancelled = true
                    )
                    try {
                        val nm = getSystemService<NotificationManager>()
                        nm?.notify(NOTIFICATION_ID, cancelledNotification)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to update notification for cancellation", e)
                    }
                    com.androidfung.departureboard.wear.WearCompanionSyncPublisher.publishTrackedDeparture(
                        context = applicationContext,
                        departureId = activeDepartureId,
                        stationId = stationId,
                        stationName = stationName,
                        destinationName = currentDestination,
                        lineName = lineName,
                        platform = currentPlatform,
                        remainingSeconds = remainingSeconds,
                        currentLocation = getString(R.string.status_cancelled),
                        stopsAway = null,
                        targetAlightStopName = _targetAlightStopFlow.value,
                        isCancelled = true,
                        lineColorArgb = lineColorArgb
                    )
                    break
                }

                // Calculate stops away using cached calling points to prevent repeated API calls
                val stopsAway = try {
                    if (liveLocation != null && lineId.isNotBlank()) {
                        if (cachedCallingPoints == null) {
                            val sampleDeparture = Departure(
                                id = activeDepartureId,
                                stationId = stationId,
                                stationName = stationName,
                                lineId = lineId,
                                lineName = lineName,
                                platformName = currentPlatform,
                                destinationName = currentDestination,
                                towards = null,
                                timeToStationSeconds = remainingSeconds,
                                expectedArrivalIso = null,
                                currentLocation = liveLocation,
                                modeName = modeName,
                                lineBadge = LineBadgeInfo(
                                    lineId = lineId,
                                    displayName = lineName,
                                    backgroundColor = androidx.compose.ui.graphics.Color.Transparent
                                )
                            )
                            cachedCallingPoints = repository.getCallingPoints(sampleDeparture)
                        }

                        val points = cachedCallingPoints
                        val curIdx = points.indexOfFirst { cp ->
                            liveLocation.contains(cp.stationName, ignoreCase = true) ||
                            cp.stationName.contains(liveLocation, ignoreCase = true)
                        }
                        val targetAlightStop = _targetAlightStopFlow.value
                        val alightIdx = if (!targetAlightStop.isNullOrBlank()) {
                            points.indexOfFirst { cp ->
                                cp.stationName.equals(targetAlightStop, ignoreCase = true) ||
                                    cp.resolveDisplayName().equals(targetAlightStop, ignoreCase = true)
                            }
                        } else -1

                        if (curIdx != -1 && alightIdx > curIdx) {
                            val stopsToAlight = alightIdx - curIdx
                            if (stopsToAlight == 1 && (!hasPostedAlightAlert || lastAlertedTargetStop != targetAlightStop)) {
                                hasPostedAlightAlert = true
                                lastAlertedTargetStop = targetAlightStop
                                if (NotificationHelper.isAlightingAlertsEnabled(applicationContext)) {
                                    triggerConcourseHapticPulse()
                                    postDisruptionAlert(
                                        title = "Get Ready to Alight",
                                        message = "Next stop is $targetAlightStop on $lineName",
                                        stationId = stationId,
                                        stationName = stationName,
                                        departureId = activeDepartureId,
                                        isBus = isBus,
                                        channelId = ALIGHTING_ALERT_CHANNEL_ID,
                                        notificationId = ALIGHTING_ALERT_NOTIFICATION_ID
                                    )
                                }
                            }
                        }

                        val userStationIdx = points.indexOfFirst { it.isCurrentStation }
                        val targetStopIdx = if (userStationIdx != -1 && (curIdx == -1 || curIdx < userStationIdx)) {
                            userStationIdx
                        } else if (alightIdx != -1) {
                            alightIdx
                        } else {
                            userStationIdx
                        }

                        if (curIdx != -1 && targetStopIdx != -1 && targetStopIdx > curIdx) {
                            val computedStops = targetStopIdx - curIdx
                            val currentBase = baselineStops
                            if (currentBase == null || computedStops > currentBase) {
                                baselineStops = computedStops
                            }
                            computedStops
                        } else null
                    } else null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to calculate stops away", e)
                    null
                }

                // GPS Geofence Safety Net for Target Alighting Stop (works even when underground/tunnel feed lags)
                val activeTargetAlight = _targetAlightStopFlow.value
                if (!activeTargetAlight.isNullOrBlank() && (!hasPostedAlightAlert || lastAlertedTargetStop != activeTargetAlight)) {
                    com.androidfung.departureboard.util.runSuspendCatching {
                        val userLoc = com.androidfung.departureboard.util.LocationHelper.getCurrentLocation(applicationContext)
                        if (userLoc != null && com.androidfung.departureboard.util.ConcourseActivityGate.isInsideTargetAlightGeofence(
                                userLat = userLoc.latitude,
                                userLon = userLoc.longitude,
                                originStationId = stationId,
                                targetAlightStopName = activeTargetAlight
                            )
                        ) {
                            hasPostedAlightAlert = true
                            lastAlertedTargetStop = activeTargetAlight
                            if (NotificationHelper.isAlightingAlertsEnabled(applicationContext)) {
                                triggerConcourseHapticPulse()
                                postDisruptionAlert(
                                    title = "Get Ready to Alight",
                                    message = "Approaching $activeTargetAlight on $lineName",
                                    stationId = stationId,
                                    stationName = stationName,
                                    departureId = activeDepartureId,
                                    isBus = isBus,
                                    channelId = ALIGHTING_ALERT_CHANNEL_ID,
                                    notificationId = ALIGHTING_ALERT_NOTIFICATION_ID
                                )
                            }
                        }
                    }
                }

                val hasReachedStation = remainingSeconds <= 0
                val updatedNotification = buildLiveNotification(
                    departureId = activeDepartureId,
                    stationId = stationId,
                    stationName = stationName,
                    destination = currentDestination,
                    lineName = lineName,
                    platform = currentPlatform,
                    timeToStationSeconds = remainingSeconds,
                    currentLocation = liveLocation,
                    isBus = isBus,
                    modeName = modeName,
                    lineColorArgb = lineColorArgb,
                    coachCount = currentCoachCount,
                    trainCode = currentTrainCode,
                    delayReason = currentDelayReason,
                    isDelayed = currentIsDelayed,
                    currentStopsAway = if (hasReachedStation) 0 else stopsAway,
                    baselineStops = baselineStops,
                    baselineSeconds = baselineSeconds,
                    isLiveUnverified = missingCount >= 2,
                    isOngoing = !hasReachedStation,
                    timeoutAfterMs = if (hasReachedStation) ARRIVAL_GRACE_TIMEOUT_MS else 0L
                )

                try {
                    val nm = getSystemService<NotificationManager>()
                    nm?.notify(NOTIFICATION_ID, updatedNotification)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update notification", e)
                }

                com.androidfung.departureboard.wear.WearCompanionSyncPublisher.publishTrackedDeparture(
                    context = applicationContext,
                    departureId = activeDepartureId,
                    stationId = stationId,
                    stationName = stationName,
                    destinationName = currentDestination,
                    lineName = lineName,
                    platform = currentPlatform,
                    remainingSeconds = remainingSeconds,
                    currentLocation = liveLocation,
                    stopsAway = if (hasReachedStation) 0 else stopsAway,
                    targetAlightStopName = _targetAlightStopFlow.value,
                    isCancelled = false,
                    lineColorArgb = lineColorArgb
                )

                if (hasReachedStation) {
                    break
                }

                val pollIntervalSeconds = getPollIntervalSeconds(remainingSeconds)
                delay((pollIntervalSeconds * 1000L).milliseconds)
            }

            if (isServiceCancelled || remainingSeconds <= 0) {
                // Release foreground status so service can terminate, while retaining the
                // cancellation notice or 60-second arrival boarding card on the lock screen.
                retainNotificationOnDestroy = true
                currentlyTrackedDepartureId = null
                _targetAlightStopFlow.value = null
                if (!isServiceCancelled) {
                    com.androidfung.departureboard.wear.WearCompanionSyncPublisher.publishTrackingStopped(applicationContext)
                }
                try {
                    ServiceCompat.stopForeground(this@TrainTrackingService, ServiceCompat.STOP_FOREGROUND_DETACH)
                } catch (e: Exception) {
                    Log.e(TAG, "Error detaching foreground status", e)
                }
                stopSelf()
            } else {
                retainNotificationOnDestroy = false
                stopTrackingInternal()
                stopSelf()
            }
        }
    }

    private fun postDisruptionAlert(
        title: String,
        message: String,
        stationId: String = "",
        stationName: String = "",
        departureId: String = "",
        isBus: Boolean = false,
        channelId: String = ALERT_CHANNEL_ID,
        notificationId: Int = ALERT_NOTIFICATION_ID
    ) {
        try {
            val openAppIntent = Intent(this, MainActivity::class.java).apply {
                if (stationId.isNotBlank()) {
                    putExtra(MainActivity.EXTRA_STATION_ID, stationId)
                    putExtra(MainActivity.EXTRA_STATION_NAME, stationName)
                }
                if (departureId.isNotBlank()) {
                    putExtra(MainActivity.EXTRA_DEPARTURE_ID, departureId)
                }
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                this,
                notificationId,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val smallIconRes = if (isBus) R.drawable.ic_bus_alert else R.drawable.ic_railway_alert

            val alertNotification = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(smallIconRes)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(openAppPendingIntent)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .extend(NotificationCompat.WearableExtender())
                .build()

            val nm = getSystemService<NotificationManager>()
            nm?.notify(notificationId, alertNotification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post disruption alert", e)
        }
    }

    private fun stopTrackingInternal() {
        try {
            trackingJob?.cancel()
            trackingJob = null
            currentlyTrackedDepartureId = null
            _targetAlightStopFlow.value = null
            com.androidfung.departureboard.wear.WearCompanionSyncPublisher.publishTrackingStopped(applicationContext)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            val nm = getSystemService<NotificationManager>()
            nm?.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping tracking", e)
        }
    }

    /**
     * Fires a distinct double-pulse haptic vibration for station concourse platform announcements
     * ("TBD" -> "Plat X") and 1-stop "Get Ready to Alight" alerts.
     */
    private fun triggerConcourseHapticPulse() {
        try {
            val pattern = longArrayOf(0L, 180L, 90L, 260L)
            val effect = android.os.VibrationEffect.createWaveform(pattern, -1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService<android.os.VibratorManager>()
                vm?.defaultVibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService<android.os.Vibrator>()
                vibrator?.vibrate(effect)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to trigger concourse haptic pulse", e)
        }
    }

    internal fun buildLiveNotification(
        departureId: String = "",
        stationId: String = "",
        stationName: String = "",
        destination: String,
        lineName: String,
        platform: String,
        timeToStationSeconds: Int,
        currentLocation: String?,
        isBus: Boolean = false,
        modeName: String = if (isBus) "bus" else "tube",
        lineColorArgb: Int = DEFAULT_TRANSIT_BLUE_ARGB,
        coachCount: Int = 0,
        trainCode: String? = null,
        delayReason: String? = null,
        isDelayed: Boolean = false,
        currentStopsAway: Int? = null,
        baselineStops: Int? = null,
        baselineSeconds: Int = 180,
        isCancelled: Boolean = false,
        isLiveUnverified: Boolean = false,
        isOngoing: Boolean = !isCancelled,
        timeoutAfterMs: Long = 0L,
        statusChipOverride: String? = null
    ): android.app.Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            if (stationId.isNotBlank()) {
                putExtra(MainActivity.EXTRA_STATION_ID, stationId)
                putExtra(MainActivity.EXTRA_STATION_NAME, stationName)
            }
            if (departureId.isNotBlank()) {
                putExtra(MainActivity.EXTRA_DEPARTURE_ID, departureId)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, TrainTrackingService::class.java).apply {
            action = ACTION_STOP_TRACKING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val targetTimestamp = System.currentTimeMillis() + (timeToStationSeconds.coerceAtLeast(0) * 1000L)

        val progressVal = computeProgress(
            timeToStationSeconds = timeToStationSeconds,
            baselineSeconds = baselineSeconds,
            currentStopsAway = currentStopsAway,
            baselineStops = baselineStops,
            isCancelled = isCancelled
        )

        val milestonePoints = buildMilestonePoints(baselineStops)
        val progressSegments = buildProgressSegments(
            lineColorArgb = lineColorArgb,
            progressVal = progressVal,
            isDelayed = isDelayed,
            isCancelled = isCancelled
        )

        val stopsCount = currentStopsAway ?: when {
            timeToStationSeconds <= 30 -> 0
            timeToStationSeconds <= 120 -> 1
            timeToStationSeconds <= 240 -> 2
            timeToStationSeconds <= 360 -> 3
            timeToStationSeconds <= 480 -> 4
            else -> (timeToStationSeconds / 120).coerceAtMost(10)
        }

        val shortPlatform = when {
            platform.contains("Platform", ignoreCase = true) -> {
                PLATFORM_LABEL_REGEX.find(platform)?.value ?: platform
            }
            platform.contains("Stop", ignoreCase = true) -> {
                STOP_LABEL_REGEX.find(platform)?.value ?: platform
            }
            platform.isNotBlank() -> platform
            else -> if (isBus) getString(R.string.tracking_bus_stop_default) else getString(R.string.tracking_platform_default)
        }

        val headerSubText = getString(R.string.tracking_subtext_to, lineName, destination)

        val minutesAway = (timeToStationSeconds / 60).coerceAtLeast(0)
        val contentTitle = when {
            isCancelled -> getString(R.string.tracking_service_cancelled_title)
            timeToStationSeconds <= 30 -> getString(R.string.tracking_at_platform_boarding)
            timeToStationSeconds <= 90 -> getString(R.string.status_approaching)
            isLiveUnverified && currentLocation.isNullOrBlank() ->
                getString(R.string.tracking_unverified_live_status, minutesAway.coerceAtLeast(1))
            stopsCount == 1 -> getString(R.string.tracking_stops_away_one)
            stopsCount > 1 -> getString(R.string.tracking_stops_away_other, stopsCount)
            !currentLocation.isNullOrBlank() -> currentLocation
            else -> getString(R.string.status_in_transit)
        }

        val contentText = formatContentText(
            shortPlatform = shortPlatform,
            coachCount = coachCount,
            trainCode = trainCode,
            delayReason = delayReason,
            isCancelled = isCancelled,
            cancelledDesc = getString(R.string.tracking_service_cancelled_desc)
        )

        val resolvedMode = if (isBus && modeName.isBlank()) {
            TransitMode.BUS
        } else {
            TransitMode.fromModeString(modeName)
        }
        val trackerIconRes = TransitIconHelper.getProgressTrackerIconRes(resolvedMode)
        val trackerIcon = androidx.core.graphics.drawable.IconCompat.createWithResource(this, trackerIconRes)

        val progressStyle = NotificationCompat.ProgressStyle()
            .setStyledByProgress(!isCancelled)
            .setProgressSegments(progressSegments)
            .setProgressPoints(milestonePoints)
            .setProgressTrackerIcon(trackerIcon)
            .setProgress(progressVal)

        val shortChipText = statusChipOverride ?: when {
            isCancelled -> getString(R.string.status_cancelled)
            timeToStationSeconds <= 30 -> getString(R.string.status_due)
            timeToStationSeconds <= 60 -> getString(R.string.tracking_chip_min, 1)
            else -> getString(R.string.tracking_chip_min, minutesAway)
        }

        val actionButtonTitle = if (isCancelled || !isOngoing) {
            getString(R.string.tracking_action_dismiss)
        } else {
            getString(R.string.tracking_action_stop)
        }

        val smallIconRes = TransitIconHelper.getTransitModeIconRes(TransitMode.fromModeString(modeName))
        val resolvedAccentColor = if ((lineColorArgb ushr 24) == 0) DEFAULT_TRANSIT_BLUE_ARGB else lineColorArgb
        val canPostPromoted = NotificationHelper.canPostPromotedNotifications(this)
        val useFallbackChronometer = !canPostPromoted && !isCancelled && isOngoing && timeToStationSeconds > 30

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(smallIconRes)
            .setColor(resolvedAccentColor)
            .setSubText(headerSubText)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setStyle(progressStyle)
            .setOngoing(isOngoing && !isCancelled)
            .setAutoCancel(!isOngoing || isCancelled)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, actionButtonTitle, stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setWhen(targetTimestamp)
            .setShowWhen(!isCancelled)
            .setUsesChronometer(useFallbackChronometer)
            .setChronometerCountDown(useFallbackChronometer)

        if (timeoutAfterMs > 0L) {
            builder.setTimeoutAfter(timeoutAfterMs)
        }

        builder.setRequestPromotedOngoing(isOngoing && !isCancelled)
        builder.setShortCriticalText(shortChipText)

        return builder.build()
    }

    private fun createNotificationChannel() {
        NotificationHelper.ensureAllNotificationChannels(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            serviceScope.cancel()
            trackingJob?.cancel()
            trackingJob = null
            if (!retainNotificationOnDestroy) {
                stopTrackingInternal()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        }
    }
}
