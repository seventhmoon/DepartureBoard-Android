package com.androidfung.departureboard.data.gtfs

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.gtfs.db.GtfsCallingPointRecord
import com.androidfung.departureboard.data.gtfs.db.GtfsDao
import com.androidfung.departureboard.data.gtfs.db.GtfsRouteEntity
import com.androidfung.departureboard.data.gtfs.db.GtfsStopEntity
import com.androidfung.departureboard.data.gtfs.db.GtfsStopTimeEntity
import com.androidfung.departureboard.data.gtfs.db.GtfsTripEntity
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.TransitMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GtfsCallingPointsResolverTest {

    private class FakeGtfsCallingPointsDao(
        val records: List<GtfsCallingPointRecord>
    ) : GtfsDao {
        override suspend fun insertStops(stops: List<GtfsStopEntity>) {}
        override suspend fun insertRoutes(routes: List<GtfsRouteEntity>) {}
        override suspend fun insertTrips(trips: List<GtfsTripEntity>) {}
        override suspend fun insertStopTimes(stopTimes: List<GtfsStopTimeEntity>) {}
        override suspend fun getStopById(stopId: String): GtfsStopEntity? = null
        override suspend fun getStopByCode(stopCode: String): GtfsStopEntity? = null
        override suspend fun getPlatformsForStation(parentStationId: String): List<GtfsStopEntity> = emptyList()
        override suspend fun searchStops(query: String, limit: Int): List<GtfsStopEntity> = emptyList()
        override suspend fun getRouteById(routeId: String): GtfsRouteEntity? = null
        override suspend fun getAllRoutes(): List<GtfsRouteEntity> = emptyList()
        override suspend fun getTripById(tripId: String): GtfsTripEntity? = null
        override suspend fun getTripsForRoute(routeId: String): List<GtfsTripEntity> = emptyList()
        override suspend fun getStopTimesForTrip(tripId: String): List<GtfsStopTimeEntity> = emptyList()
        override suspend fun getCallingPointsForTrip(tripId: String): List<GtfsCallingPointRecord> =
            records.filter { it.tripId == tripId }
        override suspend fun getRoutesServingStop(stopId: String): List<GtfsRouteEntity> = emptyList()
        override suspend fun clearStops() {}
        override suspend fun clearRoutes() {}
        override suspend fun clearTrips() {}
        override suspend fun clearStopTimes() {}
    }

    @Test
    fun resolveCallingPoints_slicesTripFromOriginToDestination() = runBlocking {
        val records = listOf(
            GtfsCallingPointRecord("TRIP_1", 1, "STOP_1", "Central", "10:00:00", "10:01:00", "Bondi", "1"),
            GtfsCallingPointRecord("TRIP_1", 2, "STOP_2", "Town Hall", "10:03:00", "10:04:00", "Bondi", "2"),
            GtfsCallingPointRecord("TRIP_1", 3, "STOP_3", "Martin Place", "10:06:00", "10:07:00", "Bondi", "1"),
            GtfsCallingPointRecord("TRIP_1", 4, "STOP_4", "Kings Cross", "10:09:00", "10:10:00", "Bondi", "1"),
            GtfsCallingPointRecord("TRIP_1", 5, "STOP_5", "Bondi Junction", "10:14:00", "10:15:00", "Bondi", "1")
        )

        val fakeDao = FakeGtfsCallingPointsDao(records)
        val resolver = GtfsCallingPointsResolver(fakeDao)

        val departure = Departure(
            id = "DEP_1",
            stationId = "STOP_2",
            stationName = "Town Hall",
            lineId = "T4",
            lineName = "T4",
            platformName = "Platform 2",
            destinationName = "Bondi Junction",
            towards = "Bondi Junction",
            timeToStationSeconds = 60,
            expectedArrivalIso = null,
            currentLocation = null,
            modeName = "rail",
            lineBadge = LineBadgeInfo("T4", "T4", Color.Blue, mode = TransitMode.RAIL),
            tripId = "TRIP_1"
        )

        assertTrue(resolver.canHandle(departure))

        val callingPoints = resolver.resolveCallingPoints(departure)

        assertEquals(4, callingPoints.size)
        assertEquals("Town Hall", callingPoints[0].stationName)
        assertTrue(callingPoints[0].isCurrentStation)

        assertEquals("Martin Place", callingPoints[1].stationName)
        assertEquals("Kings Cross", callingPoints[2].stationName)

        assertEquals("Bondi Junction", callingPoints[3].stationName)
        assertTrue(callingPoints[3].isDestination)
    }
}
