package com.androidfung.departureboard.data.repository.region.gb.mapper

import com.androidfung.departureboard.data.model.gb.NrLocation
import com.androidfung.departureboard.data.model.gb.NrTrainService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NationalRailDepartureMapperTest {

    @Test
    fun testMapToDeparture_mapsTrainFormationOperatorAndPlatformInfo() {
        val service = NrTrainService(
            serviceId = "RID_12345",
            std = "14:30",
            etd = "On time",
            platform = "4",
            operator = "Thameslink",
            operatorCode = "TL",
            length = 8,
            destination = listOf(NrLocation(locationName = "Brighton", crs = "BTN")),
            cancelReason = null,
            delayReason = null
        )

        val departure = NationalRailDepartureMapper.mapToDeparture(
            service = service,
            stationId = "STP",
            stationName = "London St Pancras"
        )

        assertEquals("RID_12345", departure.id)
        assertEquals("Brighton", departure.destinationName)
        assertEquals("Platform 4", departure.platformName)
        assertNotNull(departure.platform)
        assertEquals("4", departure.platform?.code)
        assertTrue(departure.platform?.isConfirmed == true)

        assertNotNull(departure.train)
        assertEquals("Thameslink", departure.train?.operator?.name)
        assertEquals("TL", departure.train?.operator?.id)
        assertEquals(8, departure.train?.formation?.coachCount)
        assertEquals("8 coaches", departure.formation?.displayCoachCount)
        assertNotNull(departure.expectedDepartureInstant)
        assertNotNull(departure.expectedArrivalIso)
    }

    @Test
    fun testMapToDeparture_unconfirmedPlatform_setsIsConfirmedFalse() {
        val service = NrTrainService(
            serviceId = "RID_99999",
            std = "15:00",
            etd = "Delayed",
            platform = null,
            operator = "LNER",
            operatorCode = "GR",
            length = 9,
            destination = listOf(NrLocation(locationName = "Edinburgh", crs = "EDB")),
            cancelReason = null,
            delayReason = "Waiting for a train crew member"
        )

        val departure = NationalRailDepartureMapper.mapToDeparture(
            service = service,
            stationId = "KGX",
            stationName = "London King's Cross"
        )

        assertEquals("Platform TBD", departure.platformName)
        assertNotNull(departure.platform)
        assertEquals("TBD", departure.platform?.code)
        assertFalse(departure.platform?.isConfirmed == true)

        assertNotNull(departure.train)
        assertEquals("Waiting for a train crew member", departure.delayReason)
        assertEquals("9 coaches", departure.formation?.displayCoachCount)
    }

    @Test
    fun testMapToDeparture_cancelledService_mapsCancelReason() {
        val service = NrTrainService(
            serviceId = "RID_CANCELLED",
            std = "16:00",
            etd = "Cancelled",
            isCancelled = true,
            platform = "2",
            operator = "Avanti West Coast",
            operatorCode = "VT",
            length = 11,
            destination = listOf(NrLocation(locationName = "Manchester Piccadilly", crs = "MAN")),
            cancelReason = "Track fault at Watford Junction",
            delayReason = null
        )

        val departure = NationalRailDepartureMapper.mapToDeparture(
            service = service,
            stationId = "EUS",
            stationName = "London Euston"
        )

        assertNotNull(departure.train)
        assertEquals("Track fault at Watford Junction", departure.cancelReason)
        assertEquals("11 coaches", departure.formation?.displayCoachCount)
    }

    @Test
    fun testMapToDeparture_skippedCallingPointAndVia_mapsScheduleRelationshipAndJourneyDestination() {
        val service = NrTrainService(
            serviceId = "RID_SKIPPED_CP",
            std = "17:15",
            etd = "On time",
            platform = "1",
            operator = "Thameslink",
            operatorCode = "TL",
            destination = listOf(
                NrLocation(locationName = "Brighton", crs = "BTN", via = "via London Bridge")
            ),
            subsequentCallingPoints = listOf(
                com.androidfung.departureboard.data.model.gb.NrCallingPointList(
                    callingPoint = listOf(
                        com.androidfung.departureboard.data.model.gb.NrCallingPoint(
                            locationName = "Farringdon",
                            crs = "ZFD",
                            st = "17:20",
                            et = "On time",
                            isCancelled = false
                        ),
                        com.androidfung.departureboard.data.model.gb.NrCallingPoint(
                            locationName = "City Thameslink",
                            crs = "CTK",
                            st = "17:22",
                            et = "Cancelled",
                            isCancelled = true
                        ),
                        com.androidfung.departureboard.data.model.gb.NrCallingPoint(
                            locationName = "Brighton",
                            crs = "BTN",
                            st = "18:30",
                            et = "On time",
                            isCancelled = false
                        )
                    )
                )
            )
        )

        val departure = NationalRailDepartureMapper.mapToDeparture(
            service = service,
            stationId = "STP",
            stationName = "London St Pancras"
        )

        assertEquals(4, departure.callingPoints.size)
        val cityThameslink = departure.callingPoints[2]
        assertEquals("City Thameslink", cityThameslink.stationName)
        assertEquals(com.androidfung.departureboard.data.model.StopScheduleRelationship.SKIPPED, cityThameslink.scheduleRelationship)
        assertTrue(cityThameslink.isCancelled)
        assertFalse(departure.callingPoints[1].isCancelled)

        assertNotNull(departure.journeyDestination)
        assertEquals("Brighton via London Bridge", departure.resolvedDestination.displayDestination)
        assertEquals("via London Bridge", departure.resolvedDestination.displayViaSubtitle)
    }
}
