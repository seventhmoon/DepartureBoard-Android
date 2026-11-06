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
        val calendarsList = mutableListOf<GtfsCalendarEntity>()
        val calendarDatesList = mutableListOf<GtfsCalendarDateEntity>()

        fakeDao = object : GtfsDao {
            override suspend fun getStopById(stopId: String): GtfsStopEntity? =
                stopsList.firstOrNull { it.stopId.equals(stopId, ignoreCase = true) }

            override suspend fun getChildStops(parentStationId: String): List<GtfsStopEntity> =
                stopsList.filter { it.parentStation == parentStationId }

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

            override suspend fun getUpcomingStopTimesForServices(
                stopIds: List<String>,
                serviceIds: List<String>,
                afterTimeSeconds: Int,
                limit: Int
            ): List<GtfsStopTimeEntity> {
                val matchingTripIds = tripsList
                    .filter { it.serviceId in serviceIds }
                    .map { it.tripId }
                    .toSet()
                return stopTimesList
                    .filter { it.stopId in stopIds && it.tripId in matchingTripIds && it.departureTimeSeconds >= afterTimeSeconds }
                    .sortedBy { it.departureTimeSeconds }
                    .take(limit)
            }

            override suspend fun getAllCalendars(): List<GtfsCalendarEntity> =
                calendarsList.toList()

            override suspend fun getCalendarDatesForDate(date: String): List<GtfsCalendarDateEntity> =
                calendarDatesList.filter { it.date == date }

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

            override suspend fun insertCalendars(calendars: List<GtfsCalendarEntity>) {
                calendarsList.addAll(calendars)
            }

            override suspend fun insertCalendarDates(calendarDates: List<GtfsCalendarDateEntity>) {
                calendarDatesList.addAll(calendarDates)
            }

            override suspend fun clearStops() { stopsList.clear() }
            override suspend fun clearRoutes() { routesList.clear() }
            override suspend fun clearTrips() { tripsList.clear() }
            override suspend fun clearStopTimes() { stopTimesList.clear() }
            override suspend fun clearCalendars() { calendarsList.clear() }
            override suspend fun clearCalendarDates() { calendarDatesList.clear() }
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

    @Test
    fun testGtfsTimeUtils_handlesStandardAndOverMidnightTimes() {
        assertEquals(28800, GtfsTimeUtils.parseTimeToSeconds("08:00:00"))
        assertEquals(90900, GtfsTimeUtils.parseTimeToSeconds("25:15:00"))
        assertEquals("25:15:00", GtfsTimeUtils.formatSecondsToGtfsTime(90900))
        assertEquals("01:15", GtfsTimeUtils.formatSecondsToClockTime(90900))
        assertEquals("08:00", GtfsTimeUtils.formatSecondsToClockTime(28800))
    }

    @Test
    fun testGtfsScheduleResolver_resolvesCalendarsExceptionsAndOverMidnightDepartures() = runTest {
        val resolver = GtfsScheduleResolver(fakeDao)

        val stops = listOf(
            GtfsStopEntity(
                stopId = "PARENT_KGX",
                stopName = "King's Cross",
                locationType = 1,
                wheelchairBoarding = 1,
                zoneId = "1"
            ),
            GtfsStopEntity(
                stopId = "PLAT_KGX_4",
                stopName = "King's Cross Platform 4",
                locationType = 0,
                parentStation = "PARENT_KGX",
                platformCode = "4",
                wheelchairBoarding = 1
            ),
            GtfsStopEntity(
                stopId = "STOP_FPK",
                stopName = "Finsbury Park",
                locationType = 1
            ),
            GtfsStopEntity(
                stopId = "STOP_CBG",
                stopName = "Cambridge",
                locationType = 1
            )
        )

        val routes = listOf(
            GtfsRouteEntity(
                routeId = "GN_CBG",
                agencyId = "GN",
                routeShortName = "GN",
                routeLongName = "Great Northern",
                routeType = 2,
                routeColor = "#003882",
                routeTextColor = "#FFFFFF"
            )
        )

        val calendars = listOf(
            GtfsCalendarEntity(
                serviceId = "WEEKDAY_SVC",
                monday = 1, tuesday = 1, wednesday = 1, thursday = 1, friday = 1,
                saturday = 0, sunday = 0,
                startDate = "20260101",
                endDate = "20261231"
            )
        )

        // Holiday exception on Friday 2026-12-25 removes WEEKDAY_SVC and adds HOLIDAY_SVC
        val calendarDates = listOf(
            GtfsCalendarDateEntity(serviceId = "WEEKDAY_SVC", date = "20261225", exceptionType = 2),
            GtfsCalendarDateEntity(serviceId = "HOLIDAY_SVC", date = "20261225", exceptionType = 1)
        )

        val trips = listOf(
            // Late-night Friday trip departing at 25:15:00 (01:15 AM Saturday)
            GtfsTripEntity(
                tripId = "TRIP_LATE_FRI",
                routeId = "GN_CBG",
                serviceId = "WEEKDAY_SVC",
                tripHeadsign = "Cambridge Late Night",
                directionId = 0,
                wheelchairAccessible = 1
            ),
            // Regular weekday morning trip at 09:00:00
            GtfsTripEntity(
                tripId = "TRIP_MORNING",
                routeId = "GN_CBG",
                serviceId = "WEEKDAY_SVC",
                tripHeadsign = "Cambridge Express",
                directionId = 0,
                wheelchairAccessible = 1
            )
        )

        val stopTimes = listOf(
            // Late Friday trip (25:15:00 -> 25:25:00 -> 26:05:00)
            GtfsStopTimeEntity(
                tripId = "TRIP_LATE_FRI",
                stopId = "PLAT_KGX_4",
                stopSequence = 1,
                arrivalTimeSeconds = 90900,
                departureTimeSeconds = 90900,
                pickupType = 0,
                dropOffType = 1 // Boarding only at origin
            ),
            GtfsStopTimeEntity(
                tripId = "TRIP_LATE_FRI",
                stopId = "STOP_FPK",
                stopSequence = 2,
                arrivalTimeSeconds = 91500,
                departureTimeSeconds = 91500,
                pickupType = 1, // Alighting only at Finsbury Park
                dropOffType = 0
            ),
            GtfsStopTimeEntity(
                tripId = "TRIP_LATE_FRI",
                stopId = "STOP_CBG",
                stopSequence = 3,
                arrivalTimeSeconds = 93900,
                departureTimeSeconds = 93900
            ),
            // Regular weekday trip (09:00:00 -> 09:45:00)
            GtfsStopTimeEntity(
                tripId = "TRIP_MORNING",
                stopId = "PLAT_KGX_4",
                stopSequence = 1,
                arrivalTimeSeconds = 32400,
                departureTimeSeconds = 32400
            ),
            GtfsStopTimeEntity(
                tripId = "TRIP_MORNING",
                stopId = "STOP_CBG",
                stopSequence = 2,
                arrivalTimeSeconds = 35100,
                departureTimeSeconds = 35100
            )
        )

        importer.importScheduleBatch(
            stops = stops,
            routes = routes,
            trips = trips,
            stopTimes = stopTimes,
            calendars = calendars,
            calendarDates = calendarDates,
            clearExisting = true
        )

        // 1. Check holiday exception resolution on Friday 2026-12-25
        val christmasServices = resolver.getActiveServiceIds(java.time.LocalDate.of(2026, 12, 25))
        assertEquals(setOf("HOLIDAY_SVC"), christmasServices)

        // 2. Check over-midnight resolution at 01:00 AM on Saturday 2026-11-07 (after Friday 2026-11-06 service day)
        val saturday1Am = java.time.ZonedDateTime.of(
            2026, 11, 7, 1, 0, 0, 0,
            java.time.ZoneId.of("Europe/London")
        )
        val nightDepartures = resolver.getScheduledDepartures(
            stopId = "PARENT_KGX",
            now = saturday1Am,
            limit = 5
        )
        assertEquals(1, nightDepartures.size)
        val nightDep = nightDepartures.first()
        assertEquals("Cambridge Late Night", nightDep.destinationName)
        assertEquals("Platform 4", nightDep.platformName)
        assertEquals(900, nightDep.timeToStationSeconds) // 15 minutes until 01:15 AM
        assertEquals("01:15", nightDep.currentLocation)
        assertEquals(com.androidfung.departureboard.data.model.StopActivityType.BOARDING_ONLY, nightDep.stopActivity)
        assertEquals(3, nightDep.callingPoints.size)
        assertEquals(com.androidfung.departureboard.data.model.StopActivityType.ALIGHTING_ONLY, nightDep.callingPoints[1].stopActivity)

        // 3. Check station mapping with wheelchair status and zone
        val mappedStation = resolver.mapStopToStation(stops.first())
        assertEquals("1", mappedStation.zone)
        assertEquals(com.androidfung.departureboard.data.model.WheelchairBoardingStatus.STEP_FREE_LEVEL_BOARDING, mappedStation.wheelchairStatus)
    }
}
