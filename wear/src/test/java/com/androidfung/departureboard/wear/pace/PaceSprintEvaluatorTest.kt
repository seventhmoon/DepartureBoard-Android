package com.androidfung.departureboard.wear.pace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaceSprintEvaluatorTest {

    @Test
    fun evaluate_adaptsCatchabilityToObservedSpeed() {
        val walking = PaceSprintEvaluator.evaluate(
            remainingSeconds = 120,
            observedSpeedMps = 1.35,
            concourseDistanceMeters = 160.0
        )
        assertEquals(CommuterPaceMode.WALKING, walking.paceMode)
        assertEquals(SprintCatchability.UNLIKELY, walking.catchability)
        assertTrue(walking.isLiveTelemetry)

        val sprinting = PaceSprintEvaluator.evaluate(
            remainingSeconds = 120,
            observedSpeedMps = 2.8,
            concourseDistanceMeters = 160.0
        )
        assertEquals(CommuterPaceMode.SPRINTING, sprinting.paceMode)
        assertEquals(SprintCatchability.MAKEABLE, sprinting.catchability)
    }

    @Test
    fun evaluate_nullTelemetryUsesDefaultWalkBaseline() {
        val fallback = PaceSprintEvaluator.evaluate(remainingSeconds = 200, observedSpeedMps = null)
        assertFalse(fallback.isLiveTelemetry)
        assertEquals(PaceSprintEvaluator.DEFAULT_WALK_SPEED_MPS, fallback.speedMetersPerSec, 0.01)
        assertEquals(SprintCatchability.MAKEABLE, fallback.catchability)
    }

    @Test
    fun speedFromStepsPerMinute_scalesWithCadence() {
        assertEquals(0.0, PaceSprintEvaluator.speedFromStepsPerMinute(0.0), 0.01)
        assertTrue(PaceSprintEvaluator.speedFromStepsPerMinute(160.0) > 2.5)
        assertTrue(PaceSprintEvaluator.speedFromStepsPerMinute(100.0) in 1.0..1.5)
    }
}
