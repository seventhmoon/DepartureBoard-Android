package com.androidfung.departureboard.test

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.model.CallingPoint
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.StationLineInfo
import com.androidfung.departureboard.data.model.TransitMode

/**
 * Common test fixtures and builder functions for unit tests across DepartureBoard.
 */
object TestFixtures {

    fun createTestStation(
        id: String = "940GZZLUOXC",
        name: String = "Oxford Circus",
        modes: List<String> = listOf("tube"),
        zone: String? = "1",
        lat: Double? = 51.5152,
        lon: Double? = -0.1419,
        isFavorite: Boolean = false,
        lines: List<StationLineInfo> = emptyList()
    ): Station = Station(
        id = id,
        name = name,
        modes = modes,
        zone = zone,
        lat = lat,
        lon = lon,
        isFavorite = isFavorite,
        lines = lines
    )

    fun createTestDeparture(
        id: String = "dep_test_1",
        stationId: String = "940GZZLUOXC",
        stationName: String = "Oxford Circus",
        lineId: String = "victoria",
        lineName: String = "Victoria",
        platformName: String = "Northbound - Platform 3",
        destinationName: String = "Walthamstow Central",
        towards: String? = "Walthamstow Central",
        direction: String? = "inbound",
        timeToStationSeconds: Int = 120,
        expectedArrivalIso: String? = null,
        currentLocation: String? = "Approaching",
        modeName: String = "tube",
        status: DepartureStatus = DepartureStatus.ON_TIME,
        lineBadge: LineBadgeInfo = LineBadgeInfo(
            lineId = lineId,
            displayName = lineName,
            backgroundColor = Color.Cyan,
            mode = TransitMode.TUBE
        ),
        callingPoints: List<CallingPoint> = emptyList(),
        vehicleId: String? = null
    ): Departure = Departure(
        id = id,
        stationId = stationId,
        stationName = stationName,
        lineId = lineId,
        lineName = lineName,
        platformName = platformName,
        destinationName = destinationName,
        towards = towards,
        direction = direction,
        timeToStationSeconds = timeToStationSeconds,
        expectedArrivalIso = expectedArrivalIso,
        currentLocation = currentLocation,
        modeName = modeName,
        lineBadge = lineBadge,
        callingPoints = callingPoints,
        status = status,
        vehicleId = vehicleId
    )
}
