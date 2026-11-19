package com.androidfung.departureboard.wear.sync

import com.androidfung.departureboard.wear.model.WearTrackedJourneyState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearCompanionStateRepositoryTest {

    @Test
    fun tickTrackedJourneySecond_advancesSyncedAtEpochMs_toPreventDoubleCountdown() {
        val field = WearCompanionStateRepository::class.java.getDeclaredField("_trackedJourneyFlow")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(WearCompanionStateRepository) as MutableStateFlow<WearTrackedJourneyState?>

        val initialEpoch = 1_000_000L
        flow.value = WearTrackedJourneyState(
            departureId = "dep_1",
            stationId = "940GZZLUOXC",
            stationName = "Oxford Circus",
            destinationName = "Brixton",
            lineName = "Victoria",
            platformCode = "4",
            isPlatformConfirmed = true,
            remainingSeconds = 180,
            currentLocation = "Warren Street",
            stopsAway = 2,
            targetAlightStopName = null,
            isCancelled = false,
            syncedAtEpochMs = initialEpoch
        )

        try {
            WearCompanionStateRepository.tickTrackedJourneySecond()
            val updated = flow.value!!
            assertEquals(179, updated.remainingSeconds)
            assertTrue(
                "syncedAtEpochMs must update on tick so UI wall-clock delta does not double-subtract",
                updated.syncedAtEpochMs > initialEpoch
            )
        } finally {
            flow.value = null
        }
    }
}
