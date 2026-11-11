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
}
