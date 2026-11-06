package com.androidfung.departureboard.data.gtfs

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GtfsDatabaseTest {

    private lateinit var fakeDao: GtfsDao
    private lateinit var importer: GtfsStaticScheduleImporter

    @Before
    fun setUp() {
        val stopsList = mutableListOf<GtfsStopEntity>()
        val routesList = mutableListOf<GtfsRouteEntity>()
        val tripsList = mutableListOf<GtfsTripEntity>()
        val stopTimesList = mutableListOf<GtfsStopTimeEntity>()

        fakeDao = object : GtfsDao {
            override suspend fun getStopById(stopId: String): GtfsStopEntity? =
                stopsList.firstOrNull { it.stopId.equals(stopId, ignoreCase = true) }

            override suspend fun searchStopsByName(query: String, limit: Int): List<GtfsStopEntity> =
                stopsList.filter { it.stopName.contains(query, ignoreCase = true) }.take(limit)

            override suspend fun getRouteById(routeId: String): GtfsRouteEntity? =
                routesList.firstOrNull { it.routeId.equals(routeId, ignoreCase = true) }

            override suspend fun getTripById(tripId: String): GtfsTripEntity? =
                tripsList.firstOrNull { it.tripId.equals(tripId, ignoreCase = true) }

            override suspend fun getCallingPointsForTrip(tripId: String): List<GtfsStopTimeEntity> =
                stopTimesList.filter { it.tripId == tripId }.sortedBy { it.stopSequence }

            override suspend fun getUpcomingStopTimes(
                stopId: String,
                afterTimeSeconds: Int,
                limit: Int
            ): List<GtfsStopTimeEntity> =
                stopTimesList
                    .filter { it.stopId == stopId && it.departureTimeSeconds >= afterTimeSeconds }
                    .sortedBy { it.departureTimeSeconds }
                    .take(limit)

            override suspend fun insertStops(stops: List<GtfsStopEntity>) {
                stopsList.addAll(stops)
            }

            override suspend fun insertRoutes(routes: List<GtfsRouteEntity>) {
                routesList.addAll(routes)
            }

            override suspend fun insertTrips(trips: List<GtfsTripEntity>) {
                tripsList.addAll(trips)
            }

            override suspend fun insertStopTimes(stopTimes: List<GtfsStopTimeEntity>) {
                stopTimesList.addAll(stopTimes)
            }

            override suspend fun clearStops() { stopsList.clear() }
            override suspend fun clearRoutes() { routesList.clear() }
            override suspend fun clearTrips() { tripsList.clear() }
            override suspend fun clearStopTimes() { stopTimesList.clear() }
        }

        importer = GtfsStaticScheduleImporter(fakeDao)
    }

    @Test
    fun testGtfsStaticScheduleImporter_importsAndQueriesSchedules() = runTest {
        val stops = listOf(
            GtfsStopEntity(stopId = "STOP_CENTRAL", stopName = "Central Station", stopLat = -33.883, stopLon = 151.206),
            GtfsStopEntity(stopId = "STOP_TOWNHALL", stopName = "Town Hall Station", stopLat = -33.873, stopLon = 151.206),
            GtfsStopEntity(stopId = "STOP_WYNYARD", stopName = "Wynyard Station", stopLat = -33.865, stopLon = 151.205)
        )

        val routes = listOf(
            GtfsRouteEntity(
                routeId = "ROUTE_T1",
                routeShortName = "T1",
                routeLongName = "North Shore & Western Line",
                routeType = 2,
                routeColor = "FDB813",
                routeTextColor = "000000"
            )
        )

        val trips = listOf(
            GtfsTripEntity(
                tripId = "TRIP_101",
                routeId = "ROUTE_T1",
                serviceId = "WD_REGULAR",
                tripHeadsign = "Berowra via Gordon",
                directionId = 0
            )
        )

        val stopTimes = listOf(
            GtfsStopTimeEntity(tripId = "TRIP_101", stopId = "STOP_CENTRAL", stopSequence = 1, arrivalTimeSeconds = 28800, departureTimeSeconds = 28860),
            GtfsStopTimeEntity(tripId = "TRIP_101", stopId = "STOP_TOWNHALL", stopSequence = 2, arrivalTimeSeconds = 28980, departureTimeSeconds = 29040),
            GtfsStopTimeEntity(tripId = "TRIP_101", stopId = "STOP_WYNYARD", stopSequence = 3, arrivalTimeSeconds = 29160, departureTimeSeconds = 29220)
        )

        importer.importScheduleBatch(
            stops = stops,
            routes = routes,
            trips = trips,
            stopTimes = stopTimes,
            clearExisting = true
        )

        // 1. Verify Stop Querying
        val queriedStop = fakeDao.getStopById("STOP_CENTRAL")
        assertNotNull(queriedStop)
        assertEquals("Central Station", queriedStop?.stopName)

        val searchResults = fakeDao.searchStopsByName("Town")
        assertEquals(1, searchResults.size)
        assertEquals("STOP_TOWNHALL", searchResults[0].stopId)

        // 2. Verify Route and Trip Querying
        val route = fakeDao.getRouteById("ROUTE_T1")
        assertNotNull(route)
        assertEquals("T1", route?.routeShortName)

        val trip = fakeDao.getTripById("TRIP_101")
        assertNotNull(trip)
        assertEquals("Berowra via Gordon", trip?.tripHeadsign)

        // 3. Verify Calling Points in Trip Order
        val callingPoints = fakeDao.getCallingPointsForTrip("TRIP_101")
        assertEquals(3, callingPoints.size)
        assertEquals("STOP_CENTRAL", callingPoints[0].stopId)
        assertEquals(1, callingPoints[0].stopSequence)
        assertEquals("STOP_TOWNHALL", callingPoints[1].stopId)
        assertEquals(2, callingPoints[1].stopSequence)
        assertEquals("STOP_WYNYARD", callingPoints[2].stopId)
        assertEquals(3, callingPoints[2].stopSequence)

        // 4. Verify Upcoming Stop Times After Timestamp
        val upcoming = fakeDao.getUpcomingStopTimes(
            stopId = "STOP_TOWNHALL",
            afterTimeSeconds = 28900,
            limit = 5
        )
        assertEquals(1, upcoming.size)
        assertEquals("TRIP_101", upcoming[0].tripId)
        assertEquals(29040, upcoming[0].departureTimeSeconds)

        // No upcoming departures if query time is after departure
        val pastUpcoming = fakeDao.getUpcomingStopTimes(
            stopId = "STOP_TOWNHALL",
            afterTimeSeconds = 30000,
            limit = 5
        )
        assertTrue(pastUpcoming.isEmpty())
    }
}
