package com.androidfung.departureboard.util

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.ServiceFrequency
import com.androidfung.departureboard.data.model.TrainInfo
import com.androidfung.departureboard.data.model.TransitOperator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

class PlatformBacklogDetectorTest {

    private fun createMetroDeparture(
        id: String,
        lineId: String = "victoria",
        lineName: String = "Victoria",
        timeToStationSeconds: Int,
        headwaySeconds: Int? = null
    ): Departure {
        return Departure(
            id = id,
            stationId = "940GZZLUOXC",
            stationName = "Oxford Circus",
            lineId = lineId,
            lineName = lineName,
            platformName = "Northbound - Platform 4",
            destinationName = "Walthamstow Central",
            towards = "Walthamstow Central",
            direction = "Northbound",
            timeToStationSeconds = timeToStationSeconds,
            expectedArrivalIso = null,
            currentLocation = null,
            modeName = "tube",
            lineBadge = LineBadgeInfo(lineId = lineId, displayName = lineName, backgroundColor = Color.Cyan, textColor = Color.White),
            frequency = headwaySeconds?.let { ServiceFrequency(headwaySeconds = it) }
        )
    }

    private fun createRailDeparture(
        id: String,
        lineId: String,
        lineName: String,
        stationName: String = "London Bridge",
        operatorId: String? = null,
        operatorName: String? = null,
        timeToStationSeconds: Int
    ): Departure {
        return Departure(
            id = id,
            stationId = "910GLNDNBDG",
            stationName = stationName,
            lineId = lineId,
            lineName = lineName,
            platformName = "Platform 1",
            destinationName = "Dartford",
            towards = "Dartford",
            direction = "Outbound",
            timeToStationSeconds = timeToStationSeconds,
            expectedArrivalIso = null,
            currentLocation = null,
            modeName = "rail",
            lineBadge = LineBadgeInfo(lineId = lineId, displayName = lineName, backgroundColor = Color.Blue, textColor = Color.White),
            train = if (operatorId != null || operatorName != null) {
                TrainInfo(
                    operator = TransitOperator(
                        id = operatorId ?: lineId,
                        name = operatorName ?: lineName
                    )
                )
            } else null
        )
    }

    @Test
    fun `detects backlog when next train is 14 minutes away on 2-minute line`() {
        val dep = createMetroDeparture("v1", timeToStationSeconds = 14 * 60)
        val peakTime = LocalTime.of(8, 30)
        val weekday = DayOfWeek.TUESDAY
        val alerts = PlatformBacklogDetector.detectBacklogs(listOf(dep), time = peakTime, dayOfWeek = weekday)

        assertEquals(1, alerts.size)
        val alert = alerts[0]
        assertEquals("victoria", alert.lineId)
        assertEquals(14, alert.minutesUntilNext)
        assertEquals(2, alert.typicalHeadwayMinutes)
    }

    @Test
    fun `no alert when next train is on time within normal headway`() {
        val dep = createMetroDeparture("v1", timeToStationSeconds = 2 * 60) // 2 mins
        val peakTime = LocalTime.of(8, 30)
        val weekday = DayOfWeek.TUESDAY
        val alerts = PlatformBacklogDetector.detectBacklogs(listOf(dep), time = peakTime, dayOfWeek = weekday)

        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `service period resolution distinguishes peak, off-peak, and late night`() {
        // Tuesday morning peak (08:30)
        assertEquals(
            TransitServicePeriod.PEAK,
            PlatformBacklogDetector.resolveServicePeriod(LocalTime.of(8, 30), DayOfWeek.TUESDAY)
        )
        // Tuesday evening peak (17:30)
        assertEquals(
            TransitServicePeriod.PEAK,
            PlatformBacklogDetector.resolveServicePeriod(LocalTime.of(17, 30), DayOfWeek.TUESDAY)
        )
        // Tuesday midday off-peak (13:00)
        assertEquals(
            TransitServicePeriod.OFF_PEAK,
            PlatformBacklogDetector.resolveServicePeriod(LocalTime.of(13, 0), DayOfWeek.TUESDAY)
        )
        // Tuesday late night (23:30)
        assertEquals(
            TransitServicePeriod.LATE_NIGHT,
            PlatformBacklogDetector.resolveServicePeriod(LocalTime.of(23, 30), DayOfWeek.TUESDAY)
        )
        // Saturday daytime (13:00) -> Off-peak (no commuter peak)
        assertEquals(
            TransitServicePeriod.OFF_PEAK,
            PlatformBacklogDetector.resolveServicePeriod(LocalTime.of(13, 0), DayOfWeek.SATURDAY)
        )
    }

    @Test
    fun `mill hill east branch normal 12-minute wait does NOT trigger false positive alert`() {
        // Mill Hill East: Northern line single-track branch (~4 tph)
        val dep1 = Departure(
            id = "mhe1",
            stationId = "940GZZLUMHL",
            stationName = "Mill Hill East",
            lineId = "northern",
            lineName = "Northern",
            platformName = "Platform 1",
            destinationName = "Kennington",
            towards = "Kennington",
            direction = "Southbound",
            timeToStationSeconds = 12 * 60, // 12 mins away
            expectedArrivalIso = null,
            currentLocation = null,
            modeName = "tube",
            lineBadge = LineBadgeInfo("northern", "Northern", Color.Black)
        )
        val dep2 = dep1.copy(
            id = "mhe2",
            timeToStationSeconds = 27 * 60 // 15m gap after dep1
        )

        val alerts = PlatformBacklogDetector.detectBacklogs(listOf(dep1, dep2))
        assertTrue(
            "Expected no backlog alert for Mill Hill East with normal 12m wait on 15m branch headway, but got: $alerts",
            alerts.isEmpty()
        )
    }

    @Test
    fun `mill hill east branch triggers backlog alert when delay exceeds 28 minutes`() {
        val dep = Departure(
            id = "mhe-delayed",
            stationId = "940GZZLUMHL",
            stationName = "Mill Hill East",
            lineId = "northern",
            lineName = "Northern",
            platformName = "Platform 1",
            destinationName = "Kennington",
            towards = "Kennington",
            direction = "Southbound",
            timeToStationSeconds = 28 * 60, // 28 mins away (missing a full 15m train cycle)
            expectedArrivalIso = null,
            currentLocation = null,
            modeName = "tube",
            lineBadge = LineBadgeInfo("northern", "Northern", Color.Black)
        )

        val offPeakTime = LocalTime.of(12, 0)
        val weekday = DayOfWeek.TUESDAY
        val alerts = PlatformBacklogDetector.detectBacklogs(listOf(dep), time = offPeakTime, dayOfWeek = weekday)
        assertEquals(1, alerts.size)
        val alert = alerts[0]
        assertEquals(15, alert.typicalHeadwayMinutes) // Detected branch off-peak headway
        assertEquals(28, alert.minutesUntilNext)
    }

    @Test
    fun `heathrow express defaults to 15m clockface headway and avoids false positive alerts`() {
        val dep = createRailDeparture(
            id = "hex1",
            lineId = "heathrow-express",
            lineName = "Heathrow Express",
            stationName = "London Paddington",
            operatorName = "Heathrow Express",
            timeToStationSeconds = 18 * 60 // 18 minutes away
        )

        // Heathrow Express runs every 15 minutes.
        // With previous 4m default, 18m would trigger a false alarm (4.5x normal headway).
        val peakTime = LocalTime.of(8, 30)
        val offPeakTime = LocalTime.of(14, 0)
        val weekday = DayOfWeek.TUESDAY

        val peakHeadway = PlatformBacklogDetector.resolveTypicalHeadwayMinutes(dep, listOf(dep), time = peakTime, dayOfWeek = weekday)
        val offPeakHeadway = PlatformBacklogDetector.resolveTypicalHeadwayMinutes(dep, listOf(dep), time = offPeakTime, dayOfWeek = weekday)

        assertEquals(15, peakHeadway)
        assertEquals(15, offPeakHeadway)

        val alerts = PlatformBacklogDetector.detectBacklogs(listOf(dep), time = peakTime, dayOfWeek = weekday)
        assertTrue("Heathrow Express 18m wait should NOT trigger false positive alert on 15m line, got: $alerts", alerts.isEmpty())
    }

    @Test
    fun `heathrow express triggers alert when delay gap reaches 30 minutes`() {
        val dep = createRailDeparture(
            id = "hex-delayed",
            lineId = "heathrow-express",
            lineName = "Heathrow Express",
            stationName = "London Paddington",
            timeToStationSeconds = 30 * 60 // 30 minutes away
        )

        val alerts = PlatformBacklogDetector.detectBacklogs(listOf(dep))
        assertEquals(1, alerts.size)
        val alert = alerts[0]
        assertEquals(15, alert.typicalHeadwayMinutes)
        assertEquals(30, alert.minutesUntilNext)
        assertTrue(alert.message.contains("Normal headway: 15m"))
    }

    @Test
    fun `southeastern resolves peak vs off-peak vs late night headways`() {
        val dep = createRailDeparture(
            id = "se1",
            lineId = "southeastern",
            lineName = "Southeastern",
            operatorId = "se",
            operatorName = "Southeastern",
            timeToStationSeconds = 18 * 60
        )

        val weekday = DayOfWeek.TUESDAY
        val peakHeadway = PlatformBacklogDetector.resolveTypicalHeadwayMinutes(
            dep, listOf(dep), time = LocalTime.of(8, 30), dayOfWeek = weekday
        )
        val offPeakHeadway = PlatformBacklogDetector.resolveTypicalHeadwayMinutes(
            dep, listOf(dep), time = LocalTime.of(14, 0), dayOfWeek = weekday
        )
        val lateNightHeadway = PlatformBacklogDetector.resolveTypicalHeadwayMinutes(
            dep, listOf(dep), time = LocalTime.of(23, 30), dayOfWeek = weekday
        )

        assertEquals(12, peakHeadway)
        assertEquals(20, offPeakHeadway)
        assertEquals(30, lateNightHeadway)

        // In off-peak, 18m wait is completely normal for a 20m service
        val offPeakAlerts = PlatformBacklogDetector.detectBacklogs(
            listOf(dep), time = LocalTime.of(14, 0), dayOfWeek = weekday
        )
        assertTrue("Southeastern 18m wait should NOT trigger alert during off-peak", offPeakAlerts.isEmpty())
    }

    @Test
    fun `unmapped national rail operator falls back to rail profile rather than 4m metro default`() {
        val unmappedRailDep = createRailDeparture(
            id = "unknown1",
            lineId = "somerset-regional-rail",
            lineName = "Somerset Rail",
            timeToStationSeconds = 15 * 60
        )

        val weekday = DayOfWeek.TUESDAY
        val peakHeadway = PlatformBacklogDetector.resolveTypicalHeadwayMinutes(
            unmappedRailDep, listOf(unmappedRailDep), time = LocalTime.of(8, 30), dayOfWeek = weekday
        )
        val offPeakHeadway = PlatformBacklogDetector.resolveTypicalHeadwayMinutes(
            unmappedRailDep, listOf(unmappedRailDep), time = LocalTime.of(14, 0), dayOfWeek = weekday
        )

        // Mode fallback for RAIL is 15m peak / 20m off-peak, NEVER 4m metro default
        assertEquals(15, peakHeadway)
        assertEquals(20, offPeakHeadway)
    }

    @Test
    fun `scheduled departure instants dynamically calculate timetable headway`() {
        val now = Instant.now()
        val dep1 = createMetroDeparture("d1", timeToStationSeconds = 25 * 60).copy(
            scheduledDepartureInstant = now
        )
        val dep2 = createMetroDeparture("d2", timeToStationSeconds = 45 * 60).copy(
            scheduledDepartureInstant = now.plus(Duration.ofMinutes(20))
        )

        val headway = PlatformBacklogDetector.resolveTypicalHeadwayMinutes(dep1, listOf(dep1, dep2))
        assertEquals(20, headway)
    }

    @Test
    fun `preprocessed tfl timetable values match circle, overground, and outer branch realities`() {
        val weekday = DayOfWeek.TUESDAY
        val peak = LocalTime.of(8, 30)
        val offPeak = LocalTime.of(14, 0)

        // Circle and Hammersmith & City lines individually run 6 tph (every 10m) in TfL timetables
        val circleDep = createMetroDeparture("c1", lineId = "circle", lineName = "Circle", timeToStationSeconds = 12 * 60)
        assertEquals(10, PlatformBacklogDetector.resolveTypicalHeadwayMinutes(circleDep, listOf(circleDep), peak, weekday))
        assertTrue(PlatformBacklogDetector.detectBacklogs(listOf(circleDep), peak, weekday).isEmpty())

        // London Overground Liberty line (Romford - Upminster) runs 2 tph (every 30m)
        val libertyDep = createRailDeparture("lib1", lineId = "liberty", lineName = "Liberty", timeToStationSeconds = 25 * 60)
        assertEquals(30, PlatformBacklogDetector.resolveTypicalHeadwayMinutes(libertyDep, listOf(libertyDep), peak, weekday))

        // Central line Hainault loop (Roding Valley) runs 3 tph (every 20m)
        val rodingDep = createMetroDeparture("rv1", lineId = "central", lineName = "Central", timeToStationSeconds = 18 * 60).copy(
            stationId = "940GZZLURVY",
            stationName = "Roding Valley"
        )
        assertEquals(20, PlatformBacklogDetector.resolveTypicalHeadwayMinutes(rodingDep, listOf(rodingDep), offPeak, weekday))
        assertTrue(PlatformBacklogDetector.detectBacklogs(listOf(rodingDep), offPeak, weekday).isEmpty())
    }
}
