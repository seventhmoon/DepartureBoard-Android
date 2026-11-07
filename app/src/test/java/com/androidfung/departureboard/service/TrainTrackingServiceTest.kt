package com.androidfung.departureboard.service

import android.app.Notification
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.androidfung.departureboard.MainActivity
import com.androidfung.departureboard.R
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.TrainFormation
import com.androidfung.departureboard.data.model.TrainInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrainTrackingServiceTest {

    private fun sampleDeparture(
        id: String = "dep_1",
        lineId: String = "victoria",
        lineName: String = "Victoria",
        destinationName: String = "Brixton",
        destinationStationId: String? = "940GZZLUBXN",
        platformName: String = "Platform 4",
        timeToStationSeconds: Int = 240,
        vehicleId: String? = "241",
        modeName: String = "tube",
        status: DepartureStatus = DepartureStatus.ON_TIME,
        coachCount: Int = 8,
        delayReason: String? = null
    ): Departure {
        return Departure(
            id = id,
            stationId = "940GZZLUOXC",
            stationName = "Oxford Circus",
            lineId = lineId,
            lineName = lineName,
            platformName = platformName,
            destinationName = destinationName,
            towards = null,
            timeToStationSeconds = timeToStationSeconds,
            expectedArrivalIso = null,
            currentLocation = "Between Warren Street and Oxford Circus",
            modeName = modeName,
            lineBadge = LineBadgeInfo(
                lineId = lineId,
                displayName = lineName,
                backgroundColor = Color(0xFF0098D4)
            ),
            status = status,
            vehicleId = vehicleId,
            destinationStationId = destinationStationId,
            train = TrainInfo(
                headcode = "1A23",
                formation = if (coachCount > 0) TrainFormation(coachCount = coachCount) else null,
                delayReason = delayReason
            )
        )
    }

    @Test
    fun findMatchingDeparture_matchesExactIdFirst() {
        val exact = sampleDeparture(id = "exact_id", timeToStationSeconds = 180)
        val other = sampleDeparture(id = "other_id", timeToStationSeconds = 180)

        val matched = TrainTrackingService.findMatchingDeparture(
            departures = listOf(other, exact),
            departureId = "exact_id",
            lineId = "victoria",
            vehicleId = "999",
            destinationId = "940GZZLUBXN",
            destinationName = "Brixton",
            expectedRemainingSeconds = 180
        )

        assertEquals("exact_id", matched?.id)
    }

    @Test
    fun findMatchingDeparture_fallsBackToVehicleIdWhenFeedIdChanges() {
        val shiftedIdDep = sampleDeparture(
            id = "synthesized_id_poll_2",
            vehicleId = "241",
            timeToStationSeconds = 195
        )

        val matched = TrainTrackingService.findMatchingDeparture(
            departures = listOf(shiftedIdDep),
            departureId = "synthesized_id_poll_1",
            lineId = "victoria",
            vehicleId = "241",
            destinationId = "940GZZLUBXN",
            destinationName = "Brixton",
            expectedRemainingSeconds = 210
        )

        assertNotNull(matched)
        assertEquals("synthesized_id_poll_2", matched?.id)
    }

    @Test
    fun findMatchingDeparture_fallsBackToFuzzyTimeWindowWhenNoVehicleId() {
        val candidateClose = sampleDeparture(
            id = "hk_mtr_poll_2_a",
            vehicleId = null,
            timeToStationSeconds = 190
        )
        val candidateFar = sampleDeparture(
            id = "hk_mtr_poll_2_b",
            vehicleId = null,
            timeToStationSeconds = 480
        )

        val matched = TrainTrackingService.findMatchingDeparture(
            departures = listOf(candidateFar, candidateClose),
            departureId = "hk_mtr_poll_1",
            lineId = "victoria",
            vehicleId = null,
            destinationId = "940GZZLUBXN",
            destinationName = "Brixton",
            expectedRemainingSeconds = 210
        )

        assertEquals("hk_mtr_poll_2_a", matched?.id)
    }

    @Test
    fun findMatchingDeparture_returnsNullWhenOutsideFuzzyWindow() {
        val farCandidate = sampleDeparture(
            id = "next_train",
            vehicleId = null,
            timeToStationSeconds = 420
        )

        val matched = TrainTrackingService.findMatchingDeparture(
            departures = listOf(farCandidate),
            departureId = "departed_train",
            lineId = "victoria",
            vehicleId = null,
            destinationId = "940GZZLUBXN",
            destinationName = "Brixton",
            expectedRemainingSeconds = 60
        )

        assertNull(matched)
    }

    @Test
    fun buildMilestonePoints_generatesIntermediateCallingPoints() {
        val points = TrainTrackingService.buildMilestonePoints(baselineStops = 4)
        // Expect start (1), 3 intermediate stops (27, 50, 72), and destination (100)
        assertEquals(5, points.size)
        assertEquals(1, points.first().position)
        assertEquals(100, points.last().position)
    }

    @Test
    fun buildProgressSegments_splitsColorAndAmberWhenDelayed() {
        val victoriaBlue = Color(0xFF0098D4).toArgb()

        val normalSegments = TrainTrackingService.buildProgressSegments(
            lineColorArgb = victoriaBlue,
            progressVal = 50,
            isDelayed = false,
            isCancelled = false
        )
        assertEquals(1, normalSegments.size)
        assertEquals(victoriaBlue, normalSegments[0].color)

        val delayedSegments = TrainTrackingService.buildProgressSegments(
            lineColorArgb = victoriaBlue,
            progressVal = 40,
            isDelayed = true,
            isCancelled = false
        )
        assertEquals(2, delayedSegments.size)
        assertEquals(40, delayedSegments[0].length)
        assertEquals(victoriaBlue, delayedSegments[0].color)
        assertEquals(60, delayedSegments[1].length)
        assertEquals(TrainTrackingService.DELAY_AMBER_ARGB, delayedSegments[1].color)
    }

    @Test
    fun formatContentText_includesPlatformCoachesAndDelayReason() {
        val text = TrainTrackingService.formatContentText(
            shortPlatform = "Platform 4",
            coachCount = 8,
            trainCode = "1A23",
            delayReason = "Signal failure",
            isCancelled = false
        )
        assertEquals("Platform 4 • 8 coaches • Signal failure", text)
    }

    @Test
    fun buildLiveNotification_includesDeepLinkDepartureIdAndMonochromeSmallIcon() {
        val service = Robolectric.setupService(TrainTrackingService::class.java)
        val notification = service.buildLiveNotification(
            departureId = "dep_victoria_1",
            stationId = "940GZZLUOXC",
            stationName = "Oxford Circus",
            destination = "Brixton",
            lineName = "Victoria",
            platform = "Platform 4",
            timeToStationSeconds = 180,
            currentLocation = "At Warren Street",
            isBus = false,
            modeName = "tube",
            lineColorArgb = Color(0xFF0098D4).toArgb(),
            coachCount = 8,
            currentStopsAway = 2,
            baselineStops = 4,
            baselineSeconds = 300
        )

        val pendingIntent = notification.contentIntent
        assertNotNull(pendingIntent)
        val shadowIntent = shadowOf(pendingIntent).savedIntent
        assertEquals("940GZZLUOXC", shadowIntent.getStringExtra(MainActivity.EXTRA_STATION_ID))
        assertEquals("Oxford Circus", shadowIntent.getStringExtra(MainActivity.EXTRA_STATION_NAME))
        assertEquals("dep_victoria_1", shadowIntent.getStringExtra(MainActivity.EXTRA_DEPARTURE_ID))
        assertEquals(R.drawable.ic_subway, notification.smallIcon.resId)
        assertTrue((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0)
    }

    @Test
    fun getProgressTrackerIconRes_resolvesDistinctBadgeForEachTransitMode() {
        val modesToExpectedIcons = listOf(
            "tube" to R.drawable.ic_tracker_subway,
            "national-rail" to R.drawable.ic_tracker_train,
            "bus" to R.drawable.ic_tracker_bus,
            "tram" to R.drawable.ic_tracker_tram,
            "ferry" to R.drawable.ic_tracker_ferry,
            "cable-car" to R.drawable.ic_tracker_cable_car,
            "funicular" to R.drawable.ic_tracker_funicular,
            "monorail" to R.drawable.ic_tracker_monorail
        )

        for ((rawMode, expectedResId) in modesToExpectedIcons) {
            val mode = com.androidfung.departureboard.data.model.TransitMode.fromModeString(rawMode)
            val actualResId = com.androidfung.departureboard.ui.designsystem.TransitIconHelper.getProgressTrackerIconRes(mode)
            assertEquals("Unexpected tracker icon for mode=$rawMode", expectedResId, actualResId)
        }
    }
}
