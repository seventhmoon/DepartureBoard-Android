package com.androidfung.departureboard.data.gtfs

import com.androidfung.departureboard.data.gtfs.db.GtfsCallingPointRecord
import com.androidfung.departureboard.data.gtfs.db.GtfsDao
import com.androidfung.departureboard.data.gtfs.db.GtfsRouteEntity
import com.androidfung.departureboard.data.gtfs.db.GtfsStopEntity
import com.androidfung.departureboard.data.gtfs.db.GtfsStopTimeEntity
import com.androidfung.departureboard.data.gtfs.db.GtfsTripEntity
import com.androidfung.departureboard.data.gtfs.realtime.GtfsRtDepartureSource
import com.androidfung.departureboard.data.gtfs.realtime.GtfsRtFeedMessage
import com.androidfung.departureboard.data.gtfs.realtime.GtfsRtFeedProvider
import com.androidfung.departureboard.data.gtfs.realtime.GtfsRtPosition
import com.androidfung.departureboard.data.gtfs.realtime.GtfsRtStopTimeEvent
import com.androidfung.departureboard.data.gtfs.realtime.GtfsRtStopTimeUpdate
import com.androidfung.departureboard.data.gtfs.realtime.GtfsRtTripDescriptor
import com.androidfung.departureboard.data.gtfs.realtime.GtfsRtTripUpdate
import com.androidfung.departureboard.data.gtfs.realtime.GtfsRtVehicleDescriptor
import com.androidfung.departureboard.data.gtfs.realtime.GtfsRtVehiclePosition
import com.androidfung.departureboard.data.model.GtfsOccupancyStatus
import com.androidfung.departureboard.data.model.GtfsScheduleRelationship
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GtfsRtDepartureSourceTest {

    private class FakeGtfsDao : GtfsDao {
        val stops = mutableMapOf<String, GtfsStopEntity>()
        val routes = mutableMapOf<String, GtfsRouteEntity>()
        val trips = mutableMapOf<String, GtfsTripEntity>()
        val stopTimes = mutableListOf<GtfsStopTimeEntity>()

        override suspend fun insertStops(stops: List<GtfsStopEntity>) {
            stops.forEach { this.stops[it.stopId] = it }
        }
        override suspend fun insertRoutes(routes: List<GtfsRouteEntity>) {
            routes.forEach { this.routes[it.routeId] = it }
        }
        override suspend fun insertTrips(trips: List<GtfsTripEntity>) {
            trips.forEach { this.trips[it.tripId] = it }
        }
        override suspend fun insertStopTimes(stopTimes: List<GtfsStopTimeEntity>) {
            this.stopTimes.addAll(stopTimes)
        }
        override suspend fun getStopById(stopId: String): GtfsStopEntity? = stops[stopId]
        override suspend fun getStopByCode(stopCode: String): GtfsStopEntity? =
            stops.values.firstOrNull { it.stopCode == stopCode }
        override suspend fun getPlatformsForStation(parentStationId: String): List<GtfsStopEntity> =
            stops.values.filter { it.parentStation == parentStationId }
        override suspend fun searchStops(query: String, limit: Int): List<GtfsStopEntity> =
            stops.values.filter { it.stopName.contains(query, ignoreCase = true) }
        override suspend fun getRouteById(routeId: String): GtfsRouteEntity? = routes[routeId]
        override suspend fun getAllRoutes(): List<GtfsRouteEntity> = routes.values.toList()
        override suspend fun getTripById(tripId: String): GtfsTripEntity? = trips[tripId]
        override suspend fun getTripsForRoute(routeId: String): List<GtfsTripEntity> =
            trips.values.filter { it.routeId == routeId }
        override suspend fun getStopTimesForTrip(tripId: String): List<GtfsStopTimeEntity> =
            stopTimes.filter { it.tripId == tripId }.sortedBy { it.stopSequence }
        override suspend fun getCallingPointsForTrip(tripId: String): List<GtfsCallingPointRecord> = emptyList()
        override suspend fun getRoutesServingStop(stopId: String): List<GtfsRouteEntity> = emptyList()
        override suspend fun clearStops() { stops.clear() }
        override suspend fun clearRoutes() { routes.clear() }
        override suspend fun clearTrips() { trips.clear() }
        override suspend fun clearStopTimes() { stopTimes.clear() }
    }

    @Test
    fun fetchDepartures_parsesTripUpdatesAndMatchesPlatformStops() = runBlocking {
        val fakeDao = FakeGtfsDao().apply {
            insertStops(
                listOf(
                    GtfsStopEntity(
                        stopId = "AU_TRAIN_CENTRAL",
                        stopCode = "CTL",
                        stopName = "Central",
                        stopDesc = null,
                        stopLat = -33.88,
                        stopLon = 151.20,
                        zoneId = "1",
                        locationType = 1, // Station
                        parentStation = null,
                        wheelchairBoarding = 1,
                        platformCode = null
                    ),
                    GtfsStopEntity(
                        stopId = "AU_TRAIN_CENTRAL_P4",
                        stopCode = "CTL_P4",
                        stopName = "Central Platform 4",
                        stopDesc = null,
                        stopLat = -33.88,
                        stopLon = 151.20,
                        zoneId = "1",
                        locationType = 0, // Platform
                        parentStation = "AU_TRAIN_CENTRAL",
                        wheelchairBoarding = 1,
                        platformCode = "4"
                    ),
                    GtfsStopEntity(
                        stopId = "AU_TRAIN_BONDI",
                        stopCode = "BND",
                        stopName = "Bondi Junction",
                        stopDesc = null,
                        stopLat = -33.89,
                        stopLon = 151.24,
                        zoneId = "1",
                        locationType = 1,
                        parentStation = null,
                        wheelchairBoarding = 1,
                        platformCode = null
                    )
                )
            )
            insertRoutes(
                listOf(
                    GtfsRouteEntity(
                        routeId = "T4",
                        agencyId = "TfNSW",
                        routeShortName = "T4",
                        routeLongName = "Eastern Suburbs Line",
                        routeType = 2,
                        routeColor = "005AA3",
                        routeTextColor = "FFFFFF"
                    )
                )
            )
        }

        val testEpoch = 1700000000L
        val feedMessage = GtfsRtFeedMessage(
            headerTimestamp = testEpoch,
            tripUpdates = listOf(
                GtfsRtTripUpdate(
                    trip = GtfsRtTripDescriptor(
                        tripId = "TRIP_101",
                        routeId = "T4",
                        directionId = 0
                    ),
                    stopTimeUpdates = listOf(
                        GtfsRtStopTimeUpdate(
                            stopSequence = 1,
                            stopId = "AU_TRAIN_CENTRAL_P4",
                            departure = GtfsRtStopTimeEvent(time = testEpoch + 180) // 3 minutes away
                        ),
                        GtfsRtStopTimeUpdate(
                            stopSequence = 2,
                            stopId = "AU_TRAIN_BONDI",
                            arrival = GtfsRtStopTimeEvent(time = testEpoch + 600)
                        )
                    )
                )
            ),
            vehiclePositions = listOf(
                GtfsRtVehiclePosition(
                    trip = GtfsRtTripDescriptor(tripId = "TRIP_101", routeId = "T4"),
                    vehicle = GtfsRtVehicleDescriptor(id = "SET_42"),
                    position = GtfsRtPosition(latitude = -33.88, longitude = 151.20),
                    occupancyStatus = GtfsOccupancyStatus.MANY_SEATS_AVAILABLE
                )
            )
        )

        val feedProvider = GtfsRtFeedProvider { Result.success(feedMessage) }
        val source = GtfsRtDepartureSource(
            regionPrefix = "AU_",
            feedProvider = feedProvider,
            gtfsDao = fakeDao,
            clock = { testEpoch }
        )

        assertTrue(source.canHandle("AU_TRAIN_CENTRAL", "Central", false))

        val result = source.fetchDepartures("AU_TRAIN_CENTRAL", "Central", false)
        assertTrue(result.isSuccess)

        val departures = result.getOrNull()
        assertNotNull(departures)
        assertEquals(1, departures!!.size)

        val departure = departures.first()
        assertEquals("Central", departure.stationName)
        assertEquals("T4", departure.lineId)
        assertEquals("Platform 4", departure.platformName)
        assertEquals(180, departure.timeToStationSeconds)
        assertEquals("Bondi Junction", departure.destinationName)
        assertEquals(GtfsOccupancyStatus.MANY_SEATS_AVAILABLE, departure.occupancy)
        assertEquals("SET_42", departure.vehicleId)
        assertEquals(GtfsScheduleRelationship.SCHEDULED, departure.scheduleRelationship)
        assertEquals(2, departure.callingPoints.size)
        assertEquals("Central Platform 4", departure.callingPoints[0].stationName)
        assertEquals("Bondi Junction", departure.callingPoints[1].stationName)
    }
}
