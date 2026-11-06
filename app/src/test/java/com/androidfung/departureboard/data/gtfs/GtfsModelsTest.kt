package com.androidfung.departureboard.data.gtfs

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.gtfs.model.GtfsRoute
import com.androidfung.departureboard.data.gtfs.model.GtfsStop
import com.androidfung.departureboard.data.model.GtfsLocationType
import com.androidfung.departureboard.data.model.GtfsOccupancyStatus
import com.androidfung.departureboard.data.model.GtfsScheduleRelationship
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.model.WheelchairAccessibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GtfsModelsTest {

    @Test
    fun gtfsStop_mapsToDomainStation_withLocationTypeAndPlatform() {
        val gtfsStop = GtfsStop(
            stopId = "AU_TRAIN_CENTRAL_P1",
            stopCode = "CTL1",
            stopName = "Central Platform 1",
            stopLat = -33.883,
            stopLon = 151.206,
            zoneId = "1",
            locationType = 0,
            parentStation = "AU_TRAIN_CENTRAL",
            wheelchairBoarding = 1,
            platformCode = "1"
        )

        val station = gtfsStop.toStation(modes = listOf("rail"))

        assertEquals("AU_TRAIN_CENTRAL_P1", station.id)
        assertEquals("Central Platform 1", station.name)
        assertEquals(GtfsLocationType.STOP_OR_PLATFORM, station.locationType)
        assertEquals("AU_TRAIN_CENTRAL", station.parentStationId)
        assertEquals("1", station.platformCode)
        assertEquals(WheelchairAccessibility.ACCESSIBLE, station.wheelchairBoarding)
        assertEquals(-33.883, station.lat!!, 0.001)
        assertEquals(151.206, station.lon!!, 0.001)
    }

    @Test
    fun gtfsRoute_mapsToDomainLineBadgeInfo_withColorAndMode() {
        val gtfsRoute = GtfsRoute(
            routeId = "T4",
            agencyId = "TfNSW",
            routeShortName = "T4",
            routeLongName = "Eastern Suburbs & Illawarra Line",
            routeType = 2, // Rail
            routeColor = "005AA3",
            routeTextColor = "FFFFFF"
        )

        assertEquals(TransitMode.RAIL, gtfsRoute.transitMode)
        val badge = gtfsRoute.toLineBadgeInfo()

        assertEquals("T4", badge.displayName)
        assertEquals(TransitMode.RAIL, badge.mode)
        assertEquals(Color(0x00, 0x5A, 0xA3), badge.backgroundColor)
        assertEquals(Color(0xFF, 0xFF, 0xFF), badge.textColor)
        assertEquals(10, badge.statusSeverity)
    }

    @Test
    fun gtfsOccupancyStatus_parsesStandardStrings() {
        assertEquals(
            GtfsOccupancyStatus.MANY_SEATS_AVAILABLE,
            GtfsOccupancyStatus.fromString("MANY_SEATS_AVAILABLE")
        )
        assertEquals(
            GtfsOccupancyStatus.STANDING_ROOM_ONLY,
            GtfsOccupancyStatus.fromString("standing-room-only")
        )
        assertEquals(
            GtfsOccupancyStatus.FULL,
            GtfsOccupancyStatus.fromString("full")
        )
        assertNull(GtfsOccupancyStatus.fromString(null))
        assertNull(GtfsOccupancyStatus.fromString("UNKNOWN_VALUE"))
    }

    @Test
    fun gtfsScheduleRelationship_parsesOrDefaultsToScheduled() {
        assertEquals(
            GtfsScheduleRelationship.CANCELLED,
            GtfsScheduleRelationship.fromString("CANCELLED")
        )
        assertEquals(
            GtfsScheduleRelationship.SKIPPED,
            GtfsScheduleRelationship.fromString("skipped")
        )
        assertEquals(
            GtfsScheduleRelationship.SCHEDULED,
            GtfsScheduleRelationship.fromString(null)
        )
        assertEquals(
            GtfsScheduleRelationship.SCHEDULED,
            GtfsScheduleRelationship.fromString("non_existent")
        )
    }
}
