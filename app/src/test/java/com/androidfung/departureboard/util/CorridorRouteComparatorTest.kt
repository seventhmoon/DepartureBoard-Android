package com.androidfung.departureboard.util

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.model.CallingPoint
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.LineBadgeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CorridorRouteComparatorTest {

    private fun createDeparture(
        id: String,
        lineName: String,
        timeToStationSeconds: Int,
        callingPointNames: List<String>
    ): Departure {
        return Departure(
            id = id,
            stationId = "KGX",
            stationName = "London King's Cross",
            lineId = "rail",
            lineName = lineName,
            platformName = "Platform 9",
            destinationName = callingPointNames.lastOrNull() ?: "Cambridge",
            towards = "Cambridge",
            timeToStationSeconds = timeToStationSeconds,
            expectedArrivalIso = null,
            currentLocation = null,
            modeName = "national-rail",
            lineBadge = LineBadgeInfo(lineId = "rail", displayName = lineName, backgroundColor = Color.Blue, textColor = Color.White),
            callingPoints = callingPointNames.map { CallingPoint(stationName = it) }
        )
    }

    @Test
    fun `detects express vs local stopper on corridor`() {
        val stopper = createDeparture(
            id = "d1",
            lineName = "Great Northern",
            timeToStationSeconds = 2 * 60,
            callingPointNames = listOf("Finsbury Park", "Potters Bar", "Hatfield", "Welwyn Garden City", "Stevenage", "Hitchin", "Royston", "Cambridge")
        )
        val express = createDeparture(
            id = "d2",
            lineName = "Thameslink Express",
            timeToStationSeconds = 8 * 60,
            callingPointNames = listOf("Stevenage", "Cambridge")
        )

        val items = CorridorRouteComparator.evaluateCorridorDepartures(
            departures = listOf(stopper, express),
            targetStationName = "Cambridge"
        )

        assertEquals(2, items.size)
        val expressItem = items.first { it.departure.id == "d2" }
        val stopperItem = items.first { it.departure.id == "d1" }

        assertEquals(CorridorServiceType.EXPRESS, expressItem.serviceType)
        assertEquals(CorridorServiceType.ALL_STATIONS, stopperItem.serviceType)
    }

    @Test
    fun `detects faster train overtake recommendation`() {
        val stopper = createDeparture(
            id = "d1",
            lineName = "Local Stopper",
            timeToStationSeconds = 2 * 60, // leaves in 2m, 8 stops = 26m total
            callingPointNames = listOf("S1", "S2", "S3", "S4", "S5", "S6", "S7", "Cambridge")
        )
        val express = createDeparture(
            id = "d2",
            lineName = "Fast Express",
            timeToStationSeconds = 6 * 60, // leaves in 6m, 2 stops = 12m total
            callingPointNames = listOf("S1", "Cambridge")
        )

        val items = CorridorRouteComparator.evaluateCorridorDepartures(
            departures = listOf(stopper, express),
            targetStationName = "Cambridge"
        )

        val alert = CorridorRouteComparator.detectFasterOvertake(items)
        assertNotNull(alert)
        assertEquals("d1", alert!!.stopperDeparture.id)
        assertEquals("d2", alert.expressDeparture.id)
        assertEquals(4, alert.waitExtraMinutes)
        assertEquals(14, alert.minutesSavedAtDestination)
    }

    @Test
    fun `excludes isCurrentStation origin from stop count and uses HH_mm arrival clock time`() {
        val depWithOriginAndClock = Departure(
            id = "d_clock",
            stationId = "KGX",
            stationName = "London King's Cross",
            lineId = "rail",
            lineName = "LNER",
            platformName = "Platform 4",
            destinationName = "Edinburgh",
            towards = "Edinburgh",
            timeToStationSeconds = 5 * 60,
            expectedArrivalIso = null,
            currentLocation = null,
            modeName = "national-rail",
            lineBadge = LineBadgeInfo(lineId = "rail", displayName = "LNER", backgroundColor = Color.Red, textColor = Color.White),
            callingPoints = listOf(
                CallingPoint(stationName = "London King's Cross", scheduledTime = "14:05", isCurrentStation = true),
                CallingPoint(stationName = "Stevenage", scheduledTime = "14:28", estimatedTime = "14:30"),
                CallingPoint(stationName = "Peterborough", scheduledTime = "14:52", estimatedTime = "14:55")
            )
        )

        val items = CorridorRouteComparator.evaluateCorridorDepartures(
            departures = listOf(depWithOriginAndClock),
            targetStationName = "Peterborough",
            nowLocalTime = java.time.LocalTime.of(14, 0)
        )

        assertEquals(1, items.size)
        val item = items.first()
        // Origin at index 0 is excluded -> Stevenage (1), Peterborough (2) = 2 stops
        assertEquals(2, item.stopsCountToDestination)
        assertEquals("14:55", item.targetArrivalTime)
        assertEquals(55, item.estimatedTravelMinutes)
        assertEquals(CorridorServiceType.EXPRESS, item.serviceType)
    }

    @Test
    fun `pre-midnight clock time evaluated just after midnight does not wrap to 23 hours`() {
        val departedBeforeMidnight = Departure(
            id = "d_midnight",
            stationId = "KGX",
            stationName = "London King's Cross",
            lineId = "rail",
            lineName = "LNER",
            platformName = "Platform 4",
            destinationName = "Peterborough",
            towards = "Peterborough",
            timeToStationSeconds = 2 * 60,
            expectedArrivalIso = null,
            currentLocation = null,
            modeName = "national-rail",
            lineBadge = LineBadgeInfo(lineId = "rail", displayName = "LNER", backgroundColor = Color.Red, textColor = Color.White),
            callingPoints = listOf(
                CallingPoint(stationName = "London King's Cross", scheduledTime = "23:45", isCurrentStation = true),
                CallingPoint(stationName = "Stevenage", scheduledTime = "23:52"),
                CallingPoint(stationName = "Peterborough", scheduledTime = "23:55")
            )
        )

        val items = CorridorRouteComparator.evaluateCorridorDepartures(
            departures = listOf(departedBeforeMidnight),
            targetStationName = "Peterborough",
            nowLocalTime = java.time.LocalTime.of(0, 5)
        )

        assertEquals(1, items.size)
        // Must fall back to heuristic estimate (~8 mins: 2m wait + 2*3m stops), NOT ~1430 minutes!
        assertTrue((items.first().estimatedTravelMinutes ?: 999) < 30)
    }
}
