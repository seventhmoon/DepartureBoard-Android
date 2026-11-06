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
import com.androidfung.departureboard.data.repository.TransitRepositoryImpl
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
import kotlin.time.Duration.Companion.milliseconds

/**
 * Foreground service that tracks an active train / bus departure and posts
 * real-time Live Update Notifications (Android 16 Promoted Ongoing notification support).
 */
class TrainTrackingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var trackingJob: Job? = null

    companion object {
        private const val TAG = "TrainTrackingService"
        const val CHANNEL_ID = "live_train_tracking_channel"
        const val ALERT_CHANNEL_ID = "train_tracking_alert_channel"
        const val NOTIFICATION_ID = 2001
        const val ALERT_NOTIFICATION_ID = 2002

        // Precompiled regex patterns to avoid per-notification allocations
        private val PLATFORM_LABEL_REGEX = Regex("""Platform\s+[A-Za-z0-9]+""", RegexOption.IGNORE_CASE)
        private val STOP_LABEL_REGEX = Regex("""Stop\s+[A-Za-z0-9]+""", RegexOption.IGNORE_CASE)

        // Polling intervals in seconds
        private const val POLL_INTERVAL_LONG = 45L
        private const val POLL_INTERVAL_MEDIUM = 30L
        private const val POLL_INTERVAL_SHORT = 15L
        private const val MAX_TRACKING_DURATION_MS = 90 * 60 * 1000L // 90 min max runtime safeguard

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

        private val _currentlyTrackedDepartureIdFlow = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
        val currentlyTrackedDepartureIdFlow: kotlinx.coroutines.flow.StateFlow<String?> = _currentlyTrackedDepartureIdFlow

        var currentlyTrackedDepartureId: String?
            get() = _currentlyTrackedDepartureIdFlow.value
            private set(value) {
                _currentlyTrackedDepartureIdFlow.value = value
            }

        fun startTracking(
            context: Context,
            departure: Departure
        ) {
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
                stopTrackingInternal()
                stopSelf()
            }
            ACTION_START_TRACKING -> {
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

                currentlyTrackedDepartureId = departureId

                // Base baseline (B to S): Initial total seconds captured at moment user clicked Track
                val baselineSeconds = initialSeconds.coerceAtLeast(30)

                val notification = buildLiveNotification(
                    stationId = stationId,
                    stationName = stationName,
                    destination = destination,
                    lineName = lineName,
                    platform = platform,
                    timeToStationSeconds = initialSeconds,
                    currentLocation = initialLocation,
                    isBus = isBus,
                    currentStopsAway = null,
                    baselineStops = null,
                    baselineSeconds = baselineSeconds
                )

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        ServiceCompat.startForeground(
                            this,
                            NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start foreground service", e)
                    stopSelf()
                    return START_NOT_STICKY
                }

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
        baselineSeconds: Int
    ) {
        val previousJob = trackingJob
        trackingJob = serviceScope.launch {
            try {
                previousJob?.cancelAndJoin()
            } catch (_: CancellationException) {
            }

            val repository = applicationContext.appContainer.transitRepository
            var remainingSeconds = initialSeconds
            var currentDestination = destination
            var currentPlatform = platform
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
                        val target = matches.firstOrNull { it.id == departureId }
                        if (target != null) {
                            missingCount = 0
                            remainingSeconds = target.timeToStationSeconds
                            liveLocation = target.currentLocation

                            // 1. Check for cancellation
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

                            if (destinationChanged && target.destinationName.isNotBlank()) {
                                val oldDest = currentDestination
                                currentDestination = target.destinationName
                                postDisruptionAlert(
                                    title = getString(R.string.tracking_destination_changed_title),
                                    message = getString(R.string.tracking_destination_changed_message, lineName, oldDest, currentDestination),
                                    stationId = stationId,
                                    stationName = stationName
                                )
                            }

                            // 3. Check for platform change
                            if (target.platformName.isNotBlank() &&
                                currentPlatform.isNotBlank() &&
                                !target.platformName.equals(currentPlatform, ignoreCase = true)
                            ) {
                                val oldPlatform = currentPlatform
                                currentPlatform = target.platformName
                                postDisruptionAlert(
                                    title = getString(R.string.tracking_platform_changed_title),
                                    message = getString(R.string.tracking_platform_changed_message, lineName, currentDestination, oldPlatform, currentPlatform),
                                    stationId = stationId,
                                    stationName = stationName
                                )
                            }
                        } else {
                            missingCount++
                            // If train dropped off the prediction board prematurely
                            if (missingCount >= 2 && remainingSeconds > 120) {
                                isCancelled = true
                            } else {
                                val elapsedSeconds = getPollIntervalSeconds(remainingSeconds).toInt()
                                remainingSeconds = (remainingSeconds - elapsedSeconds).coerceAtLeast(0)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "API call failed for departure tracking", e)
                    val elapsedSeconds = getPollIntervalSeconds(remainingSeconds).toInt()
                    remainingSeconds = (remainingSeconds - elapsedSeconds).coerceAtLeast(0)
                }

                if (isCancelled) {
                    isServiceCancelled = true
                    postDisruptionAlert(
                        title = getString(R.string.tracking_service_cancelled_title),
                        message = getString(R.string.tracking_service_cancelled_message, lineName, currentDestination),
                        stationId = stationId,
                        stationName = stationName
                    )
                    val cancelledNotification = buildLiveNotification(
                        stationId = stationId,
                        stationName = stationName,
                        destination = currentDestination,
                        lineName = lineName,
                        platform = currentPlatform,
                        timeToStationSeconds = remainingSeconds,
                        currentLocation = getString(R.string.status_cancelled),
                        isBus = isBus,
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
                    break
                }

                // Calculate stops away using cached calling points to prevent repeated API calls
                val stopsAway = try {
                    if (liveLocation != null && lineId.isNotBlank()) {
                        if (cachedCallingPoints == null) {
                            val sampleDeparture = Departure(
                                id = departureId,
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
                                modeName = if (isBus) "bus" else "tube",
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
                        val userStationIdx = points.indexOfFirst { it.isCurrentStation }
                        if (curIdx != -1 && userStationIdx != -1 && userStationIdx > curIdx) {
                            val computedStops = userStationIdx - curIdx
                            val currentBase = baselineStops
                            if (currentBase == null || computedStops > currentBase) {
                                baselineStops = computedStops
                            }
                            computedStops
                        } else null
                    } else null
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to calculate stops away", e)
                    null
                }

                val updatedNotification = buildLiveNotification(
                    stationId = stationId,
                    stationName = stationName,
                    destination = currentDestination,
                    lineName = lineName,
                    platform = currentPlatform,
                    timeToStationSeconds = remainingSeconds,
                    currentLocation = liveLocation,
                    isBus = isBus,
                    currentStopsAway = stopsAway,
                    baselineStops = baselineStops,
                    baselineSeconds = baselineSeconds
                )

                try {
                    val nm = getSystemService<NotificationManager>()
                    nm?.notify(NOTIFICATION_ID, updatedNotification)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update notification", e)
                }

                if (remainingSeconds <= 0) {
                    break
                }

                val pollIntervalSeconds = getPollIntervalSeconds(remainingSeconds)
                delay((pollIntervalSeconds * 1000L).milliseconds)
            }

            if (isServiceCancelled) {
                // Release foreground status so service can terminate, but retain non-ongoing cancellation notice
                try {
                    ServiceCompat.stopForeground(this@TrainTrackingService, ServiceCompat.STOP_FOREGROUND_DETACH)
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping foreground status on cancellation", e)
                }
                currentlyTrackedDepartureId = null
                stopSelf()
            } else {
                stopTrackingInternal()
                stopSelf()
            }
        }
    }

    private fun postDisruptionAlert(
        title: String,
        message: String,
        stationId: String = "",
        stationName: String = ""
    ) {
        try {
            val openAppIntent = Intent(this, MainActivity::class.java).apply {
                if (stationId.isNotBlank()) {
                    putExtra(MainActivity.EXTRA_STATION_ID, stationId)
                    putExtra(MainActivity.EXTRA_STATION_NAME, stationName)
                }
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                this,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alertNotification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(openAppPendingIntent)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .build()

            val nm = getSystemService<NotificationManager>()
            nm?.notify(ALERT_NOTIFICATION_ID, alertNotification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post disruption alert", e)
        }
    }

    private fun stopTrackingInternal() {
        try {
            trackingJob?.cancel()
            trackingJob = null
            currentlyTrackedDepartureId = null
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            val nm = getSystemService<NotificationManager>()
            nm?.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping tracking", e)
        }
    }

    private fun buildLiveNotification(
        stationId: String = "",
        stationName: String = "",
        destination: String,
        lineName: String,
        platform: String,
        timeToStationSeconds: Int,
        currentLocation: String?,
        isBus: Boolean = false,
        currentStopsAway: Int? = null,
        baselineStops: Int? = null,
        baselineSeconds: Int = 180,
        isCancelled: Boolean = false,
        statusChipOverride: String? = null
    ): android.app.Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            if (stationId.isNotBlank()) {
                putExtra(MainActivity.EXTRA_STATION_ID, stationId)
                putExtra(MainActivity.EXTRA_STATION_NAME, stationName)
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

        val targetTimestamp = System.currentTimeMillis() + (timeToStationSeconds * 1000L)

        val progressVal: Int = if (isCancelled) {
            0
        } else if (timeToStationSeconds <= 30) {
            100
        } else if (currentStopsAway != null && baselineStops != null && baselineStops > 0) {
            val stopsCompleted = (baselineStops - currentStopsAway).coerceAtLeast(0).toDouble()
            val stopFraction = stopsCompleted / baselineStops.toDouble()
            (5 + (stopFraction * 90.0)).toInt().coerceIn(5, 95)
        } else {
            val timeFraction = (1.0 - (timeToStationSeconds.toDouble() / baselineSeconds.toDouble())).coerceIn(0.0, 1.0)
            (timeFraction * 95.0).toInt().coerceIn(5, 95)
        }

        val milestonePoints = listOf(
            NotificationCompat.ProgressStyle.Point(1),
            NotificationCompat.ProgressStyle.Point(100)
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

        val contentTitle = when {
            isCancelled -> getString(R.string.tracking_service_cancelled_title)
            timeToStationSeconds <= 30 -> getString(R.string.tracking_at_platform_boarding)
            timeToStationSeconds <= 90 -> getString(R.string.status_approaching)
            stopsCount == 1 -> getString(R.string.tracking_stops_away_one)
            stopsCount > 1 -> getString(R.string.tracking_stops_away_other, stopsCount)
            !currentLocation.isNullOrBlank() -> currentLocation
            else -> getString(R.string.status_in_transit)
        }

        val contentText = if (isCancelled) getString(R.string.tracking_service_cancelled_desc) else shortPlatform

        val trackerIconRes = if (isBus) R.drawable.ic_tracker_bus else R.drawable.ic_tracker_train
        val trackerIcon = androidx.core.graphics.drawable.IconCompat.createWithResource(this, trackerIconRes)

        val progressStyle = NotificationCompat.ProgressStyle()
            .setStyledByProgress(!isCancelled)
            .setProgressPoints(milestonePoints)
            .setProgressTrackerIcon(trackerIcon)
            .setProgress(progressVal)

        val minutesAway = (timeToStationSeconds / 60).coerceAtLeast(0)
        val shortChipText = statusChipOverride ?: when {
            isCancelled -> getString(R.string.status_cancelled)
            timeToStationSeconds <= 30 -> getString(R.string.status_due)
            timeToStationSeconds <= 60 -> getString(R.string.tracking_chip_min, 1)
            else -> getString(R.string.tracking_chip_min, minutesAway)
        }

        val actionButtonTitle = if (isCancelled) getString(R.string.tracking_action_dismiss) else getString(R.string.tracking_action_stop)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setSubText(headerSubText)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setStyle(progressStyle)
            .setOngoing(!isCancelled)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, actionButtonTitle, stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setWhen(targetTimestamp)
            .setShowWhen(!isCancelled)
            .setUsesChronometer(false)
            .setChronometerCountDown(false)

        builder.setRequestPromotedOngoing(!isCancelled)
        builder.setShortCriticalText(shortChipText)

        return builder.build()
    }

    private fun createNotificationChannel() {
        try {
            val liveChannel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_live_tracking),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_live_tracking_desc)
                setShowBadge(true)
            }

            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                getString(R.string.notification_channel_alerts),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_alerts_desc)
                enableVibration(true)
                setShowBadge(true)
            }

            val nm = getSystemService<NotificationManager>()
            nm?.createNotificationChannel(liveChannel)
            nm?.createNotificationChannel(alertChannel)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create notification channels", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            serviceScope.cancel()
            stopTrackingInternal()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        }
    }
}
