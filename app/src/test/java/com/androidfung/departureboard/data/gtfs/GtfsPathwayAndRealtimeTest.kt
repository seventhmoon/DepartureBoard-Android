package com.androidfung.departureboard.data.gtfs

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.OccupancyStatus
import com.androidfung.departureboard.data.model.PlatformInfo
import com.androidfung.departureboard.data.model.TransitMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class GtfsPathwayAndRealtimeTest {

    @Test
    fun resolveInterchangePathway_stepFreeAvoidsStairsAndRoutesViaElevator() = runTest {
        val pathways = listOf(
            GtfsPathwayEntity(
                pathwayId = "stairs_shortcut",
                fromStopId = "PLAT_1",
                toStopId = "PLAT_2",
                pathwayMode = 2, // Stairs
                isBidirectional = 1,
                traversalTime = 20,
                signpostedAs = "Stairs to Platform 2"
            ),
            GtfsPathwayEntity(
                pathwayId = "walk_to_lift",
                fromStopId = "PLAT_1",
                toStopId = "MEZZ",
                pathwayMode = 1, // Walkway
                isBidirectional = 1,
                traversalTime = 25,
                signpostedAs = "Way out & Lift"
            ),
            GtfsPathwayEntity(
                pathwayId = "lift_to_p2",
                fromStopId = "MEZZ",
                toStopId = "PLAT_2",
                pathwayMode = 5, // Elevator
                isBidirectional = 1,
                traversalTime = 35,
                signpostedAs = "Lift to Platform 2"
            )
        )

        val dao = fakeDaoWithPathways(pathways)
        val resolver = GtfsScheduleResolver(dao)

        val fastest = resolver.resolveInterchangePathway("PLAT_1", "PLAT_2", requireStepFree = false)
        assertNotNull(fastest)
        assertEquals(20, fastest!!.totalSeconds)
        assertFalse(fastest.isStepFree)

        val stepFree = resolver.resolveInterchangePathway("PLAT_1", "PLAT_2", requireStepFree = true)
        assertNotNull(stepFree)
        assertEquals(60, stepFree!!.totalSeconds)
        assertTrue(stepFree.isStepFree)
        assertEquals(listOf(1, 5), stepFree.modesUsed)
    }

    @Test
    fun gtfsRealtimeDecoder_decodesTripUpdateVehiclePositionAndAppliesUpdates() {
        val trip1Desc = protoMsg {
            writeString(1, "trip_delayed")
            writeString(5, "route_t1")
            writeVarint(4, 0) // SCHEDULED
        }
        val trip1Update = protoMsg {
            writeBytes(1, trip1Desc)
            writeVarint(5, 240) // 4 minutes delay
        }
        val vp1 = protoMsg {
            writeBytes(1, trip1Desc)
            writeVarint(9, 4) // CRUSHED_STANDING_ROOM_ONLY -> HIGH
        }

        val trip2Desc = protoMsg {
            writeString(1, "trip_cancelled")
            writeVarint(4, 3) // CANCELED
        }
        val trip2Update = protoMsg {
            writeBytes(1, trip2Desc)
        }

        val feedBytes = protoMsg {
            writeBytes(1, protoMsg {
                writeString(1, "2.0")
                writeVarint(3, 1700000000L)
            })
            writeBytes(2, protoMsg {
                writeString(1, "e1")
                writeBytes(3, trip1Update)
            })
            writeBytes(2, protoMsg {
                writeString(1, "e2")
                writeBytes(4, vp1)
            })
            writeBytes(2, protoMsg {
                writeString(1, "e3")
                writeBytes(3, trip2Update)
            })
        }

        val feed = GtfsRealtimeDecoder.decode(feedBytes)
        assertEquals("2.0", feed.gtfsRealtimeVersion)
        assertEquals(3, feed.entities.size)

        val baseDepartures = listOf(
            sampleDeparture("trip_delayed", 300),
            sampleDeparture("trip_cancelled", 600)
        )
        val updated = GtfsRealtimeDecoder.applyRealtimeUpdates(baseDepartures, feed)

        assertEquals(DepartureStatus.DELAYED, updated[0].status)
        assertEquals(540, updated[0].timeToStationSeconds)
        assertEquals(OccupancyStatus.CRUSHED_STANDING_ROOM_ONLY.name, updated[0].occupancyStatus)

        assertEquals(DepartureStatus.CANCELLED, updated[1].status)
        assertNull(updated[1].occupancyStatus)
    }

    private fun sampleDeparture(id: String, seconds: Int) = Departure(
        id = id,
        stationId = "ST1",
        stationName = "Central",
        lineId = "T1",
        lineName = "T1",
        platformName = "1",
        destinationName = "Town Hall",
        towards = null,
        timeToStationSeconds = seconds,
        expectedArrivalIso = null,
        currentLocation = "On time",
        modeName = TransitMode.RAIL.id,
        lineBadge = LineBadgeInfo("T1", "T1", androidx.compose.ui.graphics.Color.Blue),
        platform = PlatformInfo("1"),
        status = DepartureStatus.ON_TIME
    )

    private class ProtoBuilder {
        private val out = ByteArrayOutputStream()
        fun writeVarint(field: Int, value: Long) {
            writeRawVarint((field shl 3).toLong())
            writeRawVarint(value)
        }

        fun writeBytes(field: Int, bytes: ByteArray) {
            writeRawVarint(((field shl 3) or 2).toLong())
            writeRawVarint(bytes.size.toLong())
            out.write(bytes)
        }

        fun writeString(field: Int, value: String) =
            writeBytes(field, value.toByteArray(StandardCharsets.UTF_8))

        private fun writeRawVarint(v: Long) {
            var remaining = v
            while (true) {
                if ((remaining and 0x7FL.inv()) == 0L) {
                    out.write(remaining.toInt())
                    return
                }
                out.write(((remaining.toInt() and 0x7F) or 0x80))
                remaining = remaining ushr 7
            }
        }

        fun toByteArray(): ByteArray = out.toByteArray()
    }

    private fun protoMsg(block: ProtoBuilder.() -> Unit): ByteArray =
        ProtoBuilder().apply(block).toByteArray()

    private fun fakeDaoWithPathways(pathways: List<GtfsPathwayEntity>) = object : GtfsDao {
        override suspend fun getStopById(stopId: String): GtfsStopEntity? = null
        override suspend fun getStopsByIds(stopIds: List<String>) = emptyList<GtfsStopEntity>()
        override suspend fun searchStopsByName(query: String, limit: Int) = emptyList<GtfsStopEntity>()
        override suspend fun getChildStops(parentStationId: String) = emptyList<GtfsStopEntity>()
        override suspend fun getRouteById(routeId: String): GtfsRouteEntity? = null
        override suspend fun getTripById(tripId: String): GtfsTripEntity? = null
        override suspend fun getAllCalendars() = emptyList<GtfsCalendarEntity>()
        override suspend fun getCalendarDatesForDate(date: String) = emptyList<GtfsCalendarDateEntity>()
        override suspend fun getLevelsByIds(levelIds: List<String>) = emptyList<GtfsLevelEntity>()
        override suspend fun getPathwaysForStop(stopId: String) =
            pathways.filter { it.fromStopId == stopId || (it.isBidirectional == 1 && it.toStopId == stopId) }
        override suspend fun getPathwaysForStops(stopIds: List<String>) = pathways
        override suspend fun getCallingPointsForTrip(tripId: String) = emptyList<GtfsStopTimeEntity>()
        override suspend fun getUpcomingStopTimes(stopId: String, afterTimeSeconds: Int, limit: Int) =
            emptyList<GtfsStopTimeEntity>()
        override suspend fun getUpcomingStopTimesForServices(
            stopIds: List<String>,
            serviceIds: List<String>,
            afterTimeSeconds: Int,
            limit: Int
        ) = emptyList<GtfsStopTimeEntity>()
        override suspend fun insertStops(stops: List<GtfsStopEntity>) = Unit
        override suspend fun insertRoutes(routes: List<GtfsRouteEntity>) = Unit
        override suspend fun insertTrips(trips: List<GtfsTripEntity>) = Unit
        override suspend fun insertStopTimes(stopTimes: List<GtfsStopTimeEntity>) = Unit
        override suspend fun insertCalendars(calendars: List<GtfsCalendarEntity>) = Unit
        override suspend fun insertCalendarDates(calendarDates: List<GtfsCalendarDateEntity>) = Unit
        override suspend fun insertLevels(levels: List<GtfsLevelEntity>) = Unit
        override suspend fun insertPathways(pathways: List<GtfsPathwayEntity>) = Unit
        override suspend fun clearStops() = Unit
        override suspend fun clearRoutes() = Unit
        override suspend fun clearTrips() = Unit
        override suspend fun clearStopTimes() = Unit
        override suspend fun clearCalendars() = Unit
        override suspend fun clearCalendarDates() = Unit
        override suspend fun clearLevels() = Unit
        override suspend fun clearPathways() = Unit
    }
}
