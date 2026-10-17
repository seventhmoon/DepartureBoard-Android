package com.androidfung.departureboard.data

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.repository.DepartureQueryEngine
import com.androidfung.departureboard.data.repository.source.DepartureSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DepartureQueryEngineTest {

    private fun createDeparture(
        id: String,
        lineId: String,
        lineName: String,
        destinationName: String,
        timeToStationSeconds: Int,
        mode: TransitMode = TransitMode.TUBE
    ): Departure {
        return Departure(
            id = id,
            stationId = "TEST_STN",
            stationName = "Test Station",
            lineId = lineId,
            lineName = lineName,
            platformName = "Platform 1",
            destinationName = destinationName,
            towards = destinationName,
            timeToStationSeconds = timeToStationSeconds,
            expectedArrivalIso = null,
            currentLocation = null,
            modeName = mode.id,
            lineBadge = LineBadgeInfo(
                lineId = lineId,
                displayName = lineName,
                backgroundColor = Color.Black,
                textColor = Color.White,
                mode = mode
            )
        )
    }

    @Test
    fun testQueriesMatchingSourcesInParallelAndMergesSorted() = runTest {
        val source1 = object : DepartureSource {
            override fun canHandle(stationId: String, stationName: String, isBusStop: Boolean): Boolean = true
            override suspend fun fetchDepartures(
                stationId: String,
                stationName: String,
                isBusStop: Boolean
            ): Result<List<Departure>> {
                return Result.success(
                    listOf(
                        createDeparture("1", "victoria", "Victoria", "Brixton", 120),
                        createDeparture("2", "victoria", "Victoria", "Walthamstow", 300)
                    )
                )
            }
        }

        val source2 = object : DepartureSource {
            override fun canHandle(stationId: String, stationName: String, isBusStop: Boolean): Boolean = true
            override suspend fun fetchDepartures(
                stationId: String,
                stationName: String,
                isBusStop: Boolean
            ): Result<List<Departure>> {
                return Result.success(
                    listOf(
                        createDeparture("3", "national-rail", "GTR", "Brighton", 60, TransitMode.NATIONAL_RAIL)
                    )
                )
            }
        }

        val engine = DepartureQueryEngine(listOf(source1, source2))
        val result = engine.queryDepartures("STATION_1", "Test Station")

        assertTrue(result.isSuccess)
        val departures = result.getOrNull()!!
        assertEquals(3, departures.size)
        // Verify sorted by timeToStationSeconds ascending: 60s, 120s, 300s
        assertEquals("3", departures[0].id)
        assertEquals("1", departures[1].id)
        assertEquals("2", departures[2].id)
    }

    @Test
    fun testSkipsSourcesThatCannotHandle() = runTest {
        var source2Called = false
        val source1 = object : DepartureSource {
            override fun canHandle(stationId: String, stationName: String, isBusStop: Boolean): Boolean = true
            override suspend fun fetchDepartures(
                stationId: String,
                stationName: String,
                isBusStop: Boolean
            ): Result<List<Departure>> = Result.success(listOf(createDeparture("1", "victoria", "Victoria", "Brixton", 60)))
        }

        val source2 = object : DepartureSource {
            override fun canHandle(stationId: String, stationName: String, isBusStop: Boolean): Boolean = false
            override suspend fun fetchDepartures(
                stationId: String,
                stationName: String,
                isBusStop: Boolean
            ): Result<List<Departure>> {
                source2Called = true
                return Result.success(listOf(createDeparture("2", "piccadilly", "Piccadilly", "Cockfosters", 30)))
            }
        }

        val engine = DepartureQueryEngine(listOf(source1, source2))
        val result = engine.queryDepartures("STATION_1", "Test Station")

        assertTrue(result.isSuccess)
        assertEquals(false, source2Called)
        assertEquals(1, result.getOrNull()!!.size)
    }

    @Test
    fun testStPancrasKingsCrossFilteringApplied() = runTest {
        val source = object : DepartureSource {
            override fun canHandle(stationId: String, stationName: String, isBusStop: Boolean): Boolean = true
            override suspend fun fetchDepartures(
                stationId: String,
                stationName: String,
                isBusStop: Boolean
            ): Result<List<Departure>> {
                return Result.success(
                    listOf(
                        createDeparture("1", "northern", "Northern", "Morden", 60, TransitMode.TUBE),
                        createDeparture("2", "emr", "East Midlands Railway", "Sheffield", 120, TransitMode.NATIONAL_RAIL)
                    )
                )
            }
        }

        val engine = DepartureQueryEngine(listOf(source))
        // St Pancras International (NR)
        val result = engine.queryDepartures("910GSTPX", "St Pancras International")

        assertTrue(result.isSuccess)
        val departures = result.getOrNull()!!
        assertEquals(1, departures.size)
        assertEquals("emr", departures[0].lineBadge.lineId)
    }
}
