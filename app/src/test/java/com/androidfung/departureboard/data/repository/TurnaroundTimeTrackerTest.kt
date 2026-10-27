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

    @Test
    fun millHillEast_terminatingTrain_resolvesOutboundDestination_shiftsExpectedArrivalAndSetsSouthbound() {
        val terminatingAtMhe = TflArrivalPrediction(
            id = "mhe_inbound_1",
            naptanId = "940GZZLUMHL",
            stationName = "Mill Hill East Underground Station",
            lineId = "northern",
            lineName = "Northern",
            platformName = "Northbound - Platform 1",
            direction = "outbound",
            destinationNaptanId = "940GZZLUMHL",
            destinationName = "Mill Hill East Underground Station",
            towards = "Mill Hill East",
            timeToStation = 90,
            expectedArrival = "2026-10-26T09:40:00Z",
            currentLocation = "Between Finchley Central and Mill Hill East",
            vehicleId = "301"
        )

        val departure = TflDepartureMapper.mapToDeparture(
            item = terminatingAtMhe,
            stationId = "940GZZLUMHL",
            stationName = "Mill Hill East"
        )

        // Outbound destination and towards must not remain "Mill Hill East"
        assertEquals("Finchley Central", departure.destinationName)
        assertEquals("Finchley Central", departure.towards)
        // Outbound destination NaPTAN ID must be Finchley Central (940GZZLUFYC), never Mill Hill East (940GZZLUMHL)
        assertEquals("940GZZLUFYC", departure.destinationStationId)
        // Mill Hill East only departs Southbound; TfL's "Northbound - Platform 1" must be corrected to Southbound
        assertEquals("Southbound", departure.direction)
        assertEquals("Plat 1 • Southbound", departure.platformName)
        // 90s inbound arrival + 150s Mill Hill East stepping-back dwell = 240s
        assertEquals(240, departure.timeToStationSeconds)
        // expectedArrivalIso must also be shifted by 150s so clock time and countdown agree
        assertEquals("2026-10-26T09:42:30Z", departure.expectedArrivalIso)
    }

    @Test
    fun tflDepartureSource_millHillEast_withReturnTripInLineArrivals_preservesTurnaroundBufferAndUsesOutboundDestination() = kotlinx.coroutines.test.runTest {
        val fakeApi = com.androidfung.departureboard.test.FakeTflApiService(
            arrivalsResult = listOf(
                TflArrivalPrediction(
                    id = "mhe_arr_1",
                    vehicleId = "305",
                    naptanId = "940GZZLUMHL",
                    stationName = "Mill Hill East Underground Station",
                    lineId = "northern",
                    lineName = "Northern",
                    platformName = "Northbound - Platform 1",
                    destinationNaptanId = "940GZZLUMHL",
                    destinationName = "Mill Hill East Underground Station",
                    towards = "Mill Hill East",
                    timeToStation = 60,
                    expectedArrival = "2026-10-26T10:00:00Z",
                    modeName = "tube"
                )
            ),
            lineArrivalsResult = listOf(
                TflArrivalPrediction(
                    id = "fyc_dep_1",
                    vehicleId = "305",
                    naptanId = "940GZZLUFYC",
                    stationName = "Finchley Central Underground Station",
                    lineId = "northern",
                    lineName = "Northern",
                    destinationNaptanId = "940GZZBPSUST",
                    destinationName = "Battersea Power Station Underground Station",
                    towards = "Battersea Power Station via Charing Cross",
                    timeToStation = 390,
                    modeName = "tube"
                )
            )
        )

        val source = com.androidfung.departureboard.data.repository.source.gb.TflDepartureSource(fakeApi)
        val result = source.fetchDepartures("940GZZLUMHL", "Mill Hill East", isBusStop = false)
        val departures = result.getOrThrow()

        assertEquals(1, departures.size)
        val dep = departures.first()
        assertEquals("Battersea Power Station", dep.destinationName)
        assertEquals("940GZZBPSUST", dep.destinationStationId)
        assertEquals("Southbound", dep.direction)
        assertEquals("Plat 1 • Southbound", dep.platformName)
        // Must still include the 150s turnaround dwell at Mill Hill East (60 + 150 = 210s)
        assertEquals(210, dep.timeToStationSeconds)
        assertEquals("2026-10-26T10:02:30Z", dep.expectedArrivalIso)
    }

    @Test
    fun deduplicate_prefersOutboundPredictionOverTerminatingPredictionForSameVehicle() {
        val inboundTerminating = TflArrivalPrediction(
            id = "inbound_1",
            vehicleId = "310",
            naptanId = "940GZZLUMHL",
            stationName = "Mill Hill East Underground Station",
            lineId = "northern",
            destinationNaptanId = "940GZZLUMHL",
            destinationName = "Mill Hill East Underground Station",
            timeToStation = 45
        )
        val outboundDeparting = TflArrivalPrediction(
            id = "outbound_1",
            vehicleId = "310",
            naptanId = "940GZZLUMHL",
            stationName = "Mill Hill East Underground Station",
            lineId = "northern",
            destinationNaptanId = "940GZZLUFYC",
            destinationName = "Finchley Central Underground Station",
            timeToStation = 195
        )

        val deduped = TflDepartureMapper.deduplicate(
            rawArrivals = listOf(inboundTerminating, outboundDeparting),
            stationId = "940GZZLUMHL",
            stationName = "Mill Hill East"
        )

        assertEquals(1, deduped.size)
        assertEquals("940GZZLUFYC", deduped.first().destinationNaptanId)
        assertEquals(195, deduped.first().timeToStation)
    }
}
