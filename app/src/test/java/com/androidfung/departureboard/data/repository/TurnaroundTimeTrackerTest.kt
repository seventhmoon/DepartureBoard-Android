package com.androidfung.departureboard.data.repository

import com.androidfung.departureboard.data.model.TflArrivalPrediction
import org.junit.Assert.assertEquals
import org.junit.Test

class TurnaroundTimeTrackerTest {

    @Test
    fun defaultPrior_returnsStandardDwellForVictoriaAndNorthern() {
        // Walthamstow Central has calibrated WTT stepping-back profile (120s)
        val vicDwell = TurnaroundTimeTracker.getEstimatedTurnaroundSeconds("Walthamstow Central", "victoria")
        assertEquals(120, vicDwell) // 2.0 mins

        // Morden has calibrated WTT stepping-back profile (150s)
        val northernDwell = TurnaroundTimeTracker.getEstimatedTurnaroundSeconds("Morden", "northern")
        assertEquals(150, northernDwell) // 2.5 mins
    }

    @Test
    fun terminusArrival_addsTurnaroundBufferToOutboundCountdown() {
        val terminatingItem = TflArrivalPrediction(
            id = "pred1",
            stationName = "Walthamstow Central Underground Station",
            lineId = "victoria",
            lineName = "Victoria",
            destinationName = "Walthamstow Central Underground Station",
            timeToStation = 60, // train arrives in 1 min
            vehicleId = "240"
        )

        val departure = TflDepartureMapper.mapToDeparture(
            item = terminatingItem,
            stationId = "940GZZLUWWL",
            stationName = "Walthamstow Central"
        )

        // Destination resolved to outbound terminus
        assertEquals("Brixton", departure.destinationName)
        // 60s inbound arrival + 120s WTT stepping-back dwell = 180s outbound departure
        assertEquals(180, departure.timeToStationSeconds)
    }

    @Test
    fun nonTerminatingTrain_doesNotAddBuffer() {
        val throughItem = TflArrivalPrediction(
            id = "pred2",
            stationName = "Oxford Circus Underground Station",
            lineId = "victoria",
            lineName = "Victoria",
            destinationName = "Brixton Underground Station",
            timeToStation = 120,
            vehicleId = "241"
        )

        val departure = TflDepartureMapper.mapToDeparture(
            item = throughItem,
            stationId = "940GZZLUOXC",
            stationName = "Oxford Circus"
        )

        assertEquals("Brixton", departure.destinationName)
        assertEquals(120, departure.timeToStationSeconds)
    }
}
