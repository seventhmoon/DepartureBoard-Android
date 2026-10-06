package com.androidfung.departureboard.data.repository

import com.androidfung.departureboard.data.model.CallingPoint
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.NrCallingPoint
import com.androidfung.departureboard.data.model.NrCallingPointList
import com.androidfung.departureboard.data.model.NrLocation
import com.androidfung.departureboard.data.model.NrTrainService
import com.androidfung.departureboard.data.model.TransitMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainCallingPointsTest {

    @Test
    fun nationalRailService_mapsSubsequentCallingPointsCorrectly() {
        val service = NrTrainService(
            serviceId = "srv123",
            std = "14:15",
            etd = "On time",
            operator = "LNER",
            operatorCode = "GR",
            destination = listOf(NrLocation(locationName = "Edinburgh")),
            subsequentCallingPoints = listOf(
                NrCallingPointList(
                    callingPoint = listOf(
                        NrCallingPoint(locationName = "Peterborough", st = "15:02", et = "On time"),
                        NrCallingPoint(locationName = "York", st = "16:10", et = "16:12"),
                        NrCallingPoint(locationName = "Newcastle", st = "17:05", et = "On time"),
                        NrCallingPoint(locationName = "Edinburgh", st = "18:40", et = "On time")
                    )
                )
            )
        )

        val departure = NationalRailDepartureMapper.mapToDeparture(
            service = service,
            stationId = "KGX",
            stationName = "London King's Cross"
        )

        assertEquals("London King's Cross", departure.stationName)
        assertEquals("Edinburgh", departure.destinationName)
        assertEquals(4, departure.callingPoints.size)
        assertEquals("Peterborough", departure.callingPoints[0].stationName)
        assertEquals("15:02", departure.callingPoints[0].scheduledTime)
        assertTrue(departure.callingPoints.last().isDestination)
    }

    @Test
    fun departureModel_holdsCurrentLocationAndCallingPoints() {
        val departure = Departure(
            id = "test1",
            stationId = "940GZZLUVIC",
            stationName = "Victoria",
            lineId = "victoria",
            lineName = "Victoria",
            platformName = "Platform 1",
            destinationName = "Walthamstow Central",
            towards = "Walthamstow Central",
            timeToStationSeconds = 120,
            expectedArrivalIso = null,
            currentLocation = "Between Pimlico and Victoria",
            modeName = "tube",
            lineBadge = LineBadgeInfo("victoria", "Victoria", androidx.compose.ui.graphics.Color.Blue, mode = TransitMode.TUBE),
            callingPoints = listOf(
                CallingPoint("Victoria", isCurrentStation = true),
                CallingPoint("Green Park"),
                CallingPoint("Oxford Circus"),
                CallingPoint("Warren Street"),
                CallingPoint("King's Cross St. Pancras"),
                CallingPoint("Walthamstow Central", isDestination = true)
            )
        )

        assertEquals("Between Pimlico and Victoria", departure.currentLocation)
        assertEquals(6, departure.callingPoints.size)
        assertTrue(departure.callingPoints.first().isCurrentStation)
        assertTrue(departure.callingPoints.last().isDestination)
    }
}
