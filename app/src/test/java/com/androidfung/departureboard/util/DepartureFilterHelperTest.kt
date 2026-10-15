package com.androidfung.departureboard.util

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.LineBadgeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DepartureFilterHelperTest {

    private fun createDeparture(
        id: String,
        lineId: String,
        lineName: String = lineId,
        platformName: String = "Platform 1",
        direction: String? = null
    ): Departure {
        return Departure(
            id = id,
            stationId = "940GZZLUKSX",
            stationName = "King's Cross",
            lineId = lineId,
            lineName = lineName,
            platformName = platformName,
            destinationName = "High Barnet",
            towards = "High Barnet",
            direction = direction,
            timeToStationSeconds = 120,
            expectedArrivalIso = null,
            currentLocation = "Between stations",
            modeName = "tube",
            lineBadge = LineBadgeInfo(
                lineId = lineId,
                displayName = lineName,
                backgroundColor = Color.Transparent
            )
        )
    }

    @Test
    fun matches_filtersByLineAndDirection() {
        val nbDeparture = createDeparture(
            id = "1",
            lineId = "northern",
            platformName = "Platform 1 - Northbound",
            direction = "Northbound"
        )
        val sbDeparture = createDeparture(
            id = "2",
            lineId = "northern",
            platformName = "Platform 2 - Southbound",
            direction = "Southbound"
        )
        val vicDeparture = createDeparture(
            id = "3",
            lineId = "victoria",
            platformName = "Platform 3 - Northbound",
            direction = "Northbound"
        )

        // Line and direction match
        assertTrue(DepartureFilterHelper.matches(nbDeparture, lineId = "northern", direction = "Northbound"))
        // Direction mismatch
        assertFalse(DepartureFilterHelper.matches(nbDeparture, lineId = "northern", direction = "Southbound"))
        // Line mismatch
        assertFalse(DepartureFilterHelper.matches(vicDeparture, lineId = "northern", direction = "Northbound"))
        // Null filter matches all
        assertTrue(DepartureFilterHelper.matches(sbDeparture, lineId = null, direction = null))
    }

    @Test
    fun getAvailableDirections_extractsAndNormalizesDirections() {
        val departures = listOf(
            createDeparture(id = "1", lineId = "northern", direction = "Inbound"),
            createDeparture(id = "2", lineId = "northern", direction = "Outbound"),
            createDeparture(id = "3", lineId = "piccadilly", platformName = "Platform 3 - Westbound")
        )

        // Northern line Inbound/Outbound mapped to Northbound/Southbound
        val northernDirections = DepartureFilterHelper.getAvailableDirections(departures, lineId = "northern")
        assertEquals(listOf("Northbound", "Southbound"), northernDirections)

        val piccadillyDirections = DepartureFilterHelper.getAvailableDirections(departures, lineId = "piccadilly")
        assertEquals(listOf("Westbound"), piccadillyDirections)
    }
}
