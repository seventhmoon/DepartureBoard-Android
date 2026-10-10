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
import com.androidfung.departureboard.MainActivity
import com.androidfung.departureboard.R
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.repository.TransitRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

    companion object {
        private const val TAG = "TrainTrackingService"
        const val CHANNEL_ID = "live_train_tracking_channel"
        const val NOTIFICATION_ID = 2001

        // Polling intervals in seconds
        private const val POLL_INTERVAL_LONG = 45L
        private const val POLL_INTERVAL_MEDIUM = 30L
        private const val POLL_INTERVAL_SHORT = 15L

        const val ACTION_START_TRACKING = "com.androidfung.departureboard.action.START_TRACKING"
        const val ACTION_STOP_TRACKING = "com.androidfung.departureboard.action.STOP_TRACKING"

        const val EXTRA_DEPARTURE_ID = "extra_departure_id"
        const val EXTRA_STATION_ID = "extra_station_id"
        const val EXTRA_STATION_NAME = "extra_station_name"
        const val EXTRA_DESTINATION_NAME = "extra_destination_name"
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
                putExtra(EXTRA_LINE_ID, departure.lineId)
                putExtra(EXTRA_LINE_NAME, departure.lineName)
                putExtra(EXTRA_PLATFORM, departure.platformName)
                putExtra(EXTRA_INITIAL_SECONDS, departure.timeToStationSeconds)
                putExtra(EXTRA_IS_BUS, isBus)
                putExtra(EXTRA_INITIAL_LOCATION, departure.currentLocation)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopTracking(context: Context) {
            val intent = Intent(context, TrainTrackingService::class.java).apply {
                action = ACTION_STOP_TRACKING
            }
            context.startService(intent)
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
                val destination = intent.getStringExtra(EXTRA_DESTINATION_NAME) ?: "Destination"
                val lineName = intent.getStringExtra(EXTRA_LINE_NAME) ?: "Train"
                val platform = intent.getStringExtra(EXTRA_PLATFORM) ?: ""
                val initialSeconds = intent.getIntExtra(EXTRA_INITIAL_SECONDS, 180)
                val lineId = intent.getStringExtra(EXTRA_LINE_ID) ?: ""
                val isBus = intent.getBooleanExtra(EXTRA_IS_BUS, false)
                val initialLocation = intent.getStringExtra(EXTRA_INITIAL_LOCATION)

                currentlyTrackedDepartureId = departureId

                // Base baseline (B to S): Initial total seconds captured at moment user clicked Track
                val baselineSeconds = initialSeconds.coerceAtLeast(30)

                val notification = buildLiveNotification(
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
                    // Continue with service operation even if foreground setup fails
                }

                startTrackingLoop(
                    departureId = departureId,
                    stationId = stationId,
                    stationName = stationName,
                    destination = destination,
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
        lineId: String,
        lineName: String,
        platform: String,
        initialSeconds: Int,
        isBus: Boolean,
        baselineSeconds: Int
    ) {
        trackingJob?.cancel()
        trackingJob = serviceScope.launch {
            val repository = TransitRepositoryImpl(applicationContext)
            var remainingSeconds = initialSeconds
            var baselineStops: Int? = null

            while (isActive && remainingSeconds > 0) {
                // Poll live API periodically
                var liveLocation: String? = null
                try {
                    val res = repository.getDepartures(stationId, stationName)
                    if (res.isSuccess) {
                        val matches = res.getOrDefault(emptyList())
                        val target = matches.firstOrNull { it.id == departureId }
                        if (target != null) {
                            remainingSeconds = target.timeToStationSeconds
                            liveLocation = target.currentLocation
                        } else {
                            // If train no longer listed in arrivals, decrement by the elapsed interval
                            val elapsedSeconds = if (remainingSeconds > 300) 45 else if (remainingSeconds > 120) 30 else 15
                            remainingSeconds = (remainingSeconds - elapsedSeconds).coerceAtLeast(0)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "API call failed for departure tracking", e)
                    // Fallback to using last known time and gradually decrement
                    val elapsedSeconds = if (remainingSeconds > 300) 45 else if (remainingSeconds > 120) 30 else 15
                    remainingSeconds = (remainingSeconds - elapsedSeconds).coerceAtLeast(0)
                }

                // Calculate stops away if line route sequence is available
                val stopsAway = try {
                    if (liveLocation != null && lineId.isNotBlank()) {
                        val fakeDeparture = Departure(
                            id = departureId,
                            stationId = stationId,
                            stationName = stationName,
                            lineId = lineId,
                            lineName = lineName,
                            platformName = platform,
                            destinationName = destination,
                            towards = null,
                            timeToStationSeconds = remainingSeconds,
                            expectedArrivalIso = null,
                            currentLocation = liveLocation,
                            modeName = if (isBus) "bus" else "tube",
                            lineBadge = com.androidfung.departureboard.data.model.LineBadgeInfo(
                                lineId = lineId,
                                displayName = lineName,
                                backgroundColor = androidx.compose.ui.graphics.Color.Transparent
                            )
                        )
                        val callingPoints = repository.getCallingPoints(fakeDeparture)
                        val curIdx = callingPoints.indexOfFirst { cp ->
                            liveLocation.contains(cp.stationName, ignoreCase = true) ||
                            cp.stationName.contains(liveLocation, ignoreCase = true)
                        }
                        val userStationIdx = callingPoints.indexOfFirst { it.isCurrentStation }
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
                    destination = destination,
                    lineName = lineName,
                    platform = platform,
                    timeToStationSeconds = remainingSeconds,
                    currentLocation = liveLocation,
                    isBus = isBus,
                    currentStopsAway = stopsAway,
                    baselineStops = baselineStops,
                    baselineSeconds = baselineSeconds
                )

                try {
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIFICATION_ID, updatedNotification)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update notification", e)
                }

                if (remainingSeconds <= 0) {
                    break
                }

                // Adaptive polling interval based on distance/time to station:
                // - > 5 mins (long distance / many stops away): 45s (saves radio/battery & respects TfL CDN)
                // - 2-5 mins (intermediate approach): 30s (matches TfL's 30-second edge cache window)
                // - < 2 mins (final approach / platform arrival): 15s (high responsiveness when arriving)
                val pollIntervalSeconds = when {
                    remainingSeconds > 300 -> POLL_INTERVAL_LONG
                    remainingSeconds > 120 -> POLL_INTERVAL_MEDIUM
                    else -> POLL_INTERVAL_SHORT
                }

                delay(pollIntervalSeconds * 1000L)
            }

            stopTrackingInternal()
            stopSelf()
        }
    }

    private fun stopTrackingInternal() {
        try {
            trackingJob?.cancel()
            trackingJob = null
            currentlyTrackedDepartureId = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping tracking", e)
        }
    }

    private fun buildLiveNotification(
        destination: String,
        lineName: String,
        platform: String,
        timeToStationSeconds: Int,
        currentLocation: String?,
        isBus: Boolean = false,
        currentStopsAway: Int? = null,
        baselineStops: Int? = null,
        baselineSeconds: Int = 180
    ): android.app.Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
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
        val isDue = timeToStationSeconds <= 30

        // Continuous journey track from Base Point (B) to Destination Station (S)
        // 0 = Base Point (when user hit Track), 100 = Arrived at station / Platform
        val progressVal: Int = if (timeToStationSeconds <= 30) {
            100
        } else if (currentStopsAway != null && baselineStops != null && baselineStops > 0) {
            // High-precision station ratio: progress = (stops completed) / (total stops B->S)
            val stopsCompleted = (baselineStops - currentStopsAway).coerceAtLeast(0).toDouble()
            val stopFraction = stopsCompleted / baselineStops.toDouble()
            // Map 0..baselineStops to 5..95%, hitting 100% when at platform
            (5 + (stopFraction * 90.0)).toInt().coerceIn(5, 95)
        } else {
            // Distance/Time ratio fallback: progress = (seconds elapsed) / (initial total seconds B->S)
            val timeFraction = (1.0 - (timeToStationSeconds.toDouble() / baselineSeconds.toDouble())).coerceIn(0.0, 1.0)
            (timeFraction * 95.0).toInt().coerceIn(5, 95)
        }

        // Milestone points simply mark the start (Base Point B) and end (Station S)
        val milestonePoints = listOf(
            NotificationCompat.ProgressStyle.Point(0),
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

        // Clean platform or stop name for D (Content Text)
        val shortPlatform = when {
            platform.contains("Platform", ignoreCase = true) -> {
                Regex("""Platform\s+[A-Za-z0-9]+""", RegexOption.IGNORE_CASE).find(platform)?.value ?: platform
            }
            platform.contains("Stop", ignoreCase = true) -> {
                Regex("""Stop\s+[A-Za-z0-9]+""", RegexOption.IGNORE_CASE).find(platform)?.value ?: platform
            }
            platform.isNotBlank() -> platform
            else -> if (isBus) "Bus stop" else "Platform"
        }

        // A. Header Subtext: Line + destination together (e.g. "221 to Edgware", "Victoria line to Walthamstow Central")
        val headerSubText = "$lineName to $destination"

        // C. Content Title: Primary status / milestone (e.g. "2 stops away", "Approaching", "At platform • Boarding")
        val contentTitle = when {
            timeToStationSeconds <= 30 -> "At platform • Boarding"
            timeToStationSeconds <= 90 -> "Approaching"
            stopsCount == 1 -> "1 stop away"
            stopsCount > 1 -> "$stopsCount stops away"
            !currentLocation.isNullOrBlank() -> currentLocation
            else -> "In transit"
        }

        // D. Content Text: Platform number or stop name
        val contentText = shortPlatform

        val trackerIconRes = if (isBus) R.drawable.ic_tracker_bus else R.drawable.ic_tracker_train
        val trackerIcon = androidx.core.graphics.drawable.IconCompat.createWithResource(this, trackerIconRes)

        val progressStyle = NotificationCompat.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressPoints(milestonePoints)
            .setProgressTrackerIcon(trackerIcon)
            .setProgress(progressVal)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setSubText(headerSubText)                     // A. Header Subtext: Line and destination (e.g. "221 to Edgware")
            .setContentTitle(contentTitle)                 // C. Content Title: e.g. "2 stops away", "Approaching"
            .setContentText(contentText)                   // D. Content Text: Platform number or stop name
            .setStyle(progressStyle)                       // E. Progress Bar: Continuous track with train/bus icon
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Tracking", stopPendingIntent) // F. Action Button
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setWhen(targetTimestamp)                      // B. Header Time: Drives status bar chip countdown
            .setShowWhen(false)                            // Keeps notification card header clean
            .setUsesChronometer(!isDue)
            .setChronometerCountDown(!isDue)

        // Android 16 Live Update Notification Promoted Ongoing request
        builder.setRequestPromotedOngoing(true)

        // Per DAC guidelines:
        // - When in transit (> 30s): setWhen drives the active status chip countdown automatically.
        // - When due / boarding (<= 30s): setShortCriticalText("Due") conveys the critical milestone state.
        if (isDue) {
            builder.setShortCriticalText("Due")
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Live Train Tracking",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Shows live ongoing countdowns for tracked departures"
                    setShowBadge(true)
                }
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.createNotificationChannel(channel)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create notification channel", e)
            }
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
