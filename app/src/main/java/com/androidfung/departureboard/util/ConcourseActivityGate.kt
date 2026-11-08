package com.androidfung.departureboard.util

import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TransitRegion
import kotlin.math.abs

/**
 * Combines **Station Concourse Geofencing (`<= 350m`)** with **Activity Recognition & Motion Gating**
 * so Concourse Sprint Mode (15s fast platform polling + automatic Watch/Phone station promotion)
 * only activates when the user is actively approaching or walking inside the station concourse:
 *
 * 1. **Active Approach (`APPROACHING_ON_FOOT`)**: User is within 350m and walking/running (`0.4..5.5 m/s`
 *    or hardware `WALKING` / `ON_FOOT` / `RUNNING`, or freshly entered the concourse perimeter)
 *    -> **Activates Concourse Mode** (`15s` poll cadence + `📍 At Concourse` promotion on Watch & Phone).
 * 2. **Stationary Office/Home Dwell Suppression (`STATIONARY_DWELL`)**: User has been stationary (`STILL`
 *    or `< 0.35 m/s` with `< 15m` displacement) at the same station for `>= 3 minutes`
 *    -> **Pauses Concourse Mode** (`60s` normal cadence) so working in an office or flat next to a station
 *    never drains battery.
 * 3. **Vehicle Pass-Through Filter (`PASSING_IN_VEHICLE`)**: User is moving in a vehicle (`IN_VEHICLE`
 *    or GPS speed `> 6.5 m/s`, ~23 km/h)
 *    -> **Suppresses Concourse Board Switching** so riding a train or bus past intermediate stations
 *    does not hijack the departure board.
 * 4. **Target Alighting Stop Geofence (`<= 650m`)**: While tracking a journey in `TrainTrackingService`,
 *    entering a 650m geofence around the user's selected alighting station triggers the
 *    **"Get Ready to Alight"** phone & wrist alert even if the live API feed lags in a tunnel.
 */
object ConcourseActivityGate {

    const val CONCOURSE_RADIUS_METERS = 350.0
    const val ALIGHTING_GEOFENCE_RADIUS_METERS = 650.0
    const val STATIONARY_DWELL_SUPPRESSION_MS = 3 * 60 * 1000L // 3 minutes

    private const val MIN_WALKING_SPEED_MPS = 0.35f
    private const val MIN_VEHICLE_SPEED_MPS = 6.5f // ~23.4 km/h
    private const val STATIONARY_DISPLACEMENT_THRESHOLD_METERS = 15.0

    // Standard Google Play Services DetectedActivity type constants
    const val ACTIVITY_IN_VEHICLE = 0
    const val ACTIVITY_ON_BICYCLE = 1
    const val ACTIVITY_ON_FOOT = 2
    const val ACTIVITY_STILL = 3
    const val ACTIVITY_UNKNOWN = 4
    const val ACTIVITY_RUNNING = 8
    const val ACTIVITY_WALKING = 7

    enum class UserMobilityState {
        APPROACHING_ON_FOOT,
        STATIONARY_DWELL,
        PASSING_IN_VEHICLE,
        OUTSIDE_GEOFENCE
    }

    data class ConcourseProximityDecision(
        val stationId: String?,
        val stationName: String?,
        val distanceMeters: Double?,
        val isInsideConcourseGeofence: Boolean,
        val isConcourseSprintActive: Boolean,
        val mobilityState: UserMobilityState,
        val recommendedPollIntervalMs: Long,
        val concourseBadgeLabel: String?
    )

    @Volatile
    private var latestHardwareActivityType: Int = ACTIVITY_UNKNOWN

    @Volatile
    private var latestHardwareActivityConfidence: Int = 0

    @Volatile
    private var latestHardwareActivityTimestampMs: Long = 0L

    @Volatile
    private var trackedConcourseStationId: String? = null

    @Volatile
    private var stationarySinceTimestampMs: Long? = null

    @Volatile
    private var lastDistanceMeters: Double? = null

    /**
     * Records a hardware Activity Recognition update (if available from `ActivityRecognitionClient`).
     */
    fun recordHardwareActivity(
        activityType: Int,
        confidence: Int = 100,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        if (confidence >= 50) {
            latestHardwareActivityType = activityType
            latestHardwareActivityConfidence = confidence
            latestHardwareActivityTimestampMs = nowMillis
        }
    }

    fun resetStateForTesting() {
        latestHardwareActivityType = ACTIVITY_UNKNOWN
        latestHardwareActivityConfidence = 0
        latestHardwareActivityTimestampMs = 0L
        trackedConcourseStationId = null
        stationarySinceTimestampMs = null
        lastDistanceMeters = null
    }

    /**
     * Evaluates whether the user is actively approaching/inside a station concourse (`<= 350m`)
     * while filtering out `PASSING_IN_VEHICLE` and `STATIONARY_DWELL` (`>= 3 min` still).
     */
    fun evaluateConcourseProximity(
        station: Station?,
        distanceMeters: Double?,
        speedMetersPerSecond: Float? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): ConcourseProximityDecision {
        if (station == null || distanceMeters == null || distanceMeters > CONCOURSE_RADIUS_METERS) {
            trackedConcourseStationId = null
            stationarySinceTimestampMs = null
            lastDistanceMeters = distanceMeters
            return ConcourseProximityDecision(
                stationId = station?.id,
                stationName = station?.name,
                distanceMeters = distanceMeters,
                isInsideConcourseGeofence = false,
                isConcourseSprintActive = false,
                mobilityState = UserMobilityState.OUTSIDE_GEOFENCE,
                recommendedPollIntervalMs = 60_000L,
                concourseBadgeLabel = null
            )
        }

        val hasRecentHardwareActivity = (nowMillis - latestHardwareActivityTimestampMs) <= 120_000L &&
            latestHardwareActivityConfidence >= 50

        // 1. Check Pass-Through in Vehicle (via hardware Activity Recognition or GPS speed > 6.5 m/s)
        val isInVehicle = (hasRecentHardwareActivity &&
            (latestHardwareActivityType == ACTIVITY_IN_VEHICLE || latestHardwareActivityType == ACTIVITY_ON_BICYCLE)) ||
            (speedMetersPerSecond != null && speedMetersPerSecond >= MIN_VEHICLE_SPEED_MPS)

        if (isInVehicle) {
            lastDistanceMeters = distanceMeters
            return ConcourseProximityDecision(
                stationId = station.id,
                stationName = station.name,
                distanceMeters = distanceMeters,
                isInsideConcourseGeofence = true,
                isConcourseSprintActive = false,
                mobilityState = UserMobilityState.PASSING_IN_VEHICLE,
                recommendedPollIntervalMs = 60_000L,
                concourseBadgeLabel = null
            )
        }

        // 2. Check Stationary Dwell Suppression (living/working in a building next to a station)
        val previousStationId = trackedConcourseStationId
        val previousDist = lastDistanceMeters
        val distDelta = if (previousStationId == station.id && previousDist != null) {
            abs(previousDist - distanceMeters)
        } else {
            Double.MAX_VALUE
        }

        val isActivelyWalkingHardware = hasRecentHardwareActivity && (
            latestHardwareActivityType == ACTIVITY_WALKING ||
                latestHardwareActivityType == ACTIVITY_RUNNING ||
                latestHardwareActivityType == ACTIVITY_ON_FOOT
            )
        val isHardwareStill = hasRecentHardwareActivity && latestHardwareActivityType == ACTIVITY_STILL
        val isGpsWalking = speedMetersPerSecond != null && speedMetersPerSecond in MIN_WALKING_SPEED_MPS..MIN_VEHICLE_SPEED_MPS
        val isApproachingByDistance = previousStationId == station.id &&
            previousDist != null &&
            (previousDist - distanceMeters) >= STATIONARY_DISPLACEMENT_THRESHOLD_METERS

        if (previousStationId != station.id) {
            trackedConcourseStationId = station.id
            stationarySinceTimestampMs = if (isActivelyWalkingHardware || isGpsWalking) null else nowMillis
        } else if (isActivelyWalkingHardware || isGpsWalking || isApproachingByDistance) {
            // User is actively walking toward/inside the concourse -> reset dwell timer
            stationarySinceTimestampMs = null
        } else if (isHardwareStill || distDelta < STATIONARY_DISPLACEMENT_THRESHOLD_METERS) {
            if (stationarySinceTimestampMs == null) {
                stationarySinceTimestampMs = nowMillis
            }
        }

        lastDistanceMeters = distanceMeters

        val stationaryStart = stationarySinceTimestampMs
        val dwellDurationMs = if (stationaryStart != null) (nowMillis - stationaryStart) else 0L
        val isStationaryDwellSuppressed = isHardwareStill && dwellDurationMs >= STATIONARY_DWELL_SUPPRESSION_MS ||
            (!isActivelyWalkingHardware && !isGpsWalking && dwellDurationMs >= STATIONARY_DWELL_SUPPRESSION_MS)

        val distInt = distanceMeters.toInt()
        return if (isStationaryDwellSuppressed) {
            ConcourseProximityDecision(
                stationId = station.id,
                stationName = station.name,
                distanceMeters = distanceMeters,
                isInsideConcourseGeofence = true,
                isConcourseSprintActive = false,
                mobilityState = UserMobilityState.STATIONARY_DWELL,
                recommendedPollIntervalMs = 60_000L,
                concourseBadgeLabel = null
            )
        } else {
            ConcourseProximityDecision(
                stationId = station.id,
                stationName = station.name,
                distanceMeters = distanceMeters,
                isInsideConcourseGeofence = true,
                isConcourseSprintActive = true,
                mobilityState = UserMobilityState.APPROACHING_ON_FOOT,
                recommendedPollIntervalMs = 15_000L,
                concourseBadgeLabel = "📍 At Concourse (${distInt}m)"
            )
        }
    }

    /**
     * Evaluates whether the user has entered the 650m alighting geofence around their selected
     * `targetAlightStopName` while riding a tracked train/bus (serving as a GPS safety net when
     * underground or when live vehicle feeds lag).
     */
    fun isInsideTargetAlightGeofence(
        userLat: Double,
        userLon: Double,
        originStationId: String,
        targetAlightStopName: String?,
        candidateStations: List<Station> = emptyList()
    ): Boolean {
        val targetName = targetAlightStopName?.trim()?.takeIf { it.isNotBlank() } ?: return false
        val cleanTarget = cleanStopName(targetName)

        val region = TransitRegion.fromStationId(originStationId)
        val directoryMatches = region.directory?.searchStations(targetName).orEmpty()
        val allCandidates = (candidateStations + directoryMatches + region.defaultStations).distinctBy { it.id }

        val matchedStation = allCandidates.firstOrNull { st ->
            !st.id.equals(originStationId, ignoreCase = true) &&
                st.lat != null && st.lon != null &&
                (st.name.equals(targetName, ignoreCase = true) ||
                    cleanStopName(st.name).equals(cleanTarget, ignoreCase = true) ||
                    st.name.contains(cleanTarget, ignoreCase = true))
        } ?: return false

        val stLat = matchedStation.lat ?: return false
        val stLon = matchedStation.lon ?: return false
        val distanceMeters = LocationHelper.calculateDistanceMeters(
            userLat = userLat,
            userLon = userLon,
            stationLat = stLat,
            stationLon = stLon
        )
        return distanceMeters <= ALIGHTING_GEOFENCE_RADIUS_METERS
    }

    private fun cleanStopName(raw: String): String = raw
        .replace("Underground Station", "", ignoreCase = true)
        .replace("Rail Station", "", ignoreCase = true)
        .replace("Station", "", ignoreCase = true)
        .replace("London ", "", ignoreCase = true)
        .trim()
}
