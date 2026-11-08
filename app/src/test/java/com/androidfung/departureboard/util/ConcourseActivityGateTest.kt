package com.androidfung.departureboard.util

import com.androidfung.departureboard.data.model.Station
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConcourseActivityGateTest {

    private val kingsCross = Station(
        id = "940GZZLUKSX",
        name = "King's Cross St. Pancras",
        modes = listOf("tube"),
        lat = 51.5308,
        lon = -0.1238
    )

    private val oxfordCircus = Station(
        id = "940GZZLUOXC",
        name = "Oxford Circus",
        modes = listOf("tube"),
        lat = 51.5152,
        lon = -0.1419
    )

    @Before
    fun setUp() {
        ConcourseActivityGate.resetStateForTesting()
    }

    @Test
    fun `evaluateConcourseProximity activates 15s concourse sprint when approaching on foot within 350m`() {
        ConcourseActivityGate.recordHardwareActivity(
            activityType = ConcourseActivityGate.ACTIVITY_WALKING,
            confidence = 90,
            nowMillis = 1_000L
        )

        val decision = ConcourseActivityGate.evaluateConcourseProximity(
            station = kingsCross,
            distanceMeters = 120.0,
            speedMetersPerSecond = 1.3f,
            nowMillis = 2_000L
        )

        assertTrue(decision.isInsideConcourseGeofence)
        assertTrue(decision.isConcourseSprintActive)
        assertEquals(ConcourseActivityGate.UserMobilityState.APPROACHING_ON_FOOT, decision.mobilityState)
        assertEquals(15_000L, decision.recommendedPollIntervalMs)
        assertEquals("📍 At Concourse (120m)", decision.concourseBadgeLabel)
    }

    @Test
    fun `evaluateConcourseProximity suppresses concourse mode when passing through in vehicle`() {
        ConcourseActivityGate.recordHardwareActivity(
            activityType = ConcourseActivityGate.ACTIVITY_IN_VEHICLE,
            confidence = 85,
            nowMillis = 1_000L
        )

        val decision = ConcourseActivityGate.evaluateConcourseProximity(
            station = kingsCross,
            distanceMeters = 180.0,
            speedMetersPerSecond = 11.5f,
            nowMillis = 2_000L
        )

        assertTrue(decision.isInsideConcourseGeofence)
        assertFalse(decision.isConcourseSprintActive)
        assertEquals(ConcourseActivityGate.UserMobilityState.PASSING_IN_VEHICLE, decision.mobilityState)
        assertEquals(60_000L, decision.recommendedPollIntervalMs)
        assertNull(decision.concourseBadgeLabel)
    }

    @Test
    fun `evaluateConcourseProximity suppresses concourse mode after 3 minutes of stationary dwell`() {
        ConcourseActivityGate.recordHardwareActivity(
            activityType = ConcourseActivityGate.ACTIVITY_STILL,
            confidence = 95,
            nowMillis = 1_000L
        )

        // Initial check at t = 1s inside a nearby office (210m from station): active initially
        val initial = ConcourseActivityGate.evaluateConcourseProximity(
            station = kingsCross,
            distanceMeters = 210.0,
            speedMetersPerSecond = 0.0f,
            nowMillis = 1_000L
        )
        assertTrue(initial.isConcourseSprintActive)

        // After 3m 10s still at the same distance (210m): suppressed to STATIONARY_DWELL (60s poll)
        ConcourseActivityGate.recordHardwareActivity(
            activityType = ConcourseActivityGate.ACTIVITY_STILL,
            confidence = 95,
            nowMillis = 191_000L
        )
        val afterDwell = ConcourseActivityGate.evaluateConcourseProximity(
            station = kingsCross,
            distanceMeters = 208.0,
            speedMetersPerSecond = 0.0f,
            nowMillis = 191_000L
        )
        assertFalse(afterDwell.isConcourseSprintActive)
        assertEquals(ConcourseActivityGate.UserMobilityState.STATIONARY_DWELL, afterDwell.mobilityState)
        assertEquals(60_000L, afterDwell.recommendedPollIntervalMs)
        assertNull(afterDwell.concourseBadgeLabel)

        // User starts walking toward the concourse (208m -> 95m): immediately reactivates!
        ConcourseActivityGate.recordHardwareActivity(
            activityType = ConcourseActivityGate.ACTIVITY_ON_FOOT,
            confidence = 90,
            nowMillis = 210_000L
        )
        val resumedWalking = ConcourseActivityGate.evaluateConcourseProximity(
            station = kingsCross,
            distanceMeters = 95.0,
            speedMetersPerSecond = 1.4f,
            nowMillis = 210_000L
        )
        assertTrue(resumedWalking.isConcourseSprintActive)
        assertEquals(ConcourseActivityGate.UserMobilityState.APPROACHING_ON_FOOT, resumedWalking.mobilityState)
        assertEquals(15_000L, resumedWalking.recommendedPollIntervalMs)
        assertNotNull(resumedWalking.concourseBadgeLabel)
    }

    @Test
    fun `isInsideTargetAlightGeofence triggers within 650m of target alighting stop`() {
        val candidates = listOf(kingsCross, oxfordCircus)

        // Still far away at King's Cross (origin) -> false
        assertFalse(
            ConcourseActivityGate.isInsideTargetAlightGeofence(
                userLat = 51.5308,
                userLon = -0.1238,
                originStationId = kingsCross.id,
                targetAlightStopName = "Oxford Circus",
                candidateStations = candidates
            )
        )

        // Approaching within ~200m of Oxford Circus -> true
        assertTrue(
            ConcourseActivityGate.isInsideTargetAlightGeofence(
                userLat = 51.5160,
                userLon = -0.1415,
                originStationId = kingsCross.id,
                targetAlightStopName = "Oxford Circus Underground Station",
                candidateStations = candidates
            )
        )
    }
}
