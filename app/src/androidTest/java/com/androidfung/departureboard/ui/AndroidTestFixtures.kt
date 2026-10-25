package com.androidfung.departureboard.ui

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TransitMode

object AndroidTestFixtures {

    fun createTestStation(
        id: String = "940GZZLUOXC",
        name: String = "Oxford Circus"
    ): Station = Station(
        id = id,
        name = name,
        modes = listOf("tube"),
        zone = "1",
        lat = 51.5152,
        lon = -0.1419,
        isFavorite = false,
        lines = emptyList()
    )

    fun createTestDeparture(
        id: String = "dep_test_device",
        stationId: String = "940GZZLUOXC",
        stationName: String = "Oxford Circus",
        lineId: String = "victoria",
        lineName: String = "Victoria",
        destinationName: String = "Brixton",
        status: DepartureStatus = DepartureStatus.DUE
    ): Departure = Departure(
        id = id,
        stationId = stationId,
        stationName = stationName,
        lineId = lineId,
        lineName = lineName,
        platformName = "Platform 3",
        destinationName = destinationName,
        towards = destinationName,
        direction = "inbound",
        timeToStationSeconds = 30,
        expectedArrivalIso = null,
        currentLocation = "Approaching",
        modeName = "tube",
        lineBadge = LineBadgeInfo(
            lineId = lineId,
            displayName = lineName,
            backgroundColor = Color.Cyan,
            mode = TransitMode.TUBE
        ),
        callingPoints = emptyList(),
        status = status,
        vehicleId = null,
        destinationStationId = null
    )
}
