package com.androidfung.departureboard.data.resolver

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.model.CallingPoint
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.TransitMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallingPointsEngineTest {

    private fun createDeparture(
        id: String = "dep_1",
        lineId: String = "bods-123",
        stationId: String = "station_1",
        callingPoints: List<CallingPoint> = emptyList()
    ): Departure {
        return Departure(
            id = id,
            stationId = stationId,
            stationName = "Current Station",
            lineId = lineId,
            lineName = "Test Line",
            destinationName = "Terminus",
            towards = "Terminus",
            platformName = "1",
            timeToStationSeconds = 120,
            expectedArrivalIso = null,
            currentLocation = null,
            modeName = TransitMode.BUS.id,
            lineBadge = LineBadgeInfo(
                lineId = lineId,
                displayName = "Test Line",
                backgroundColor = Color.Black,
                textColor = Color.White,
                mode = TransitMode.BUS
            ),
            callingPoints = callingPoints
        )
    }

    @Test
    fun testResolveCallingPoints_returnsExistingPointsDirectly() = runBlocking {
        val existing = listOf(
            CallingPoint("Station A", isCurrentStation = true),
            CallingPoint("Station B", isDestination = true)
        )
        val dep = createDeparture(callingPoints = existing)

        val resolver = object : CallingPointsResolver {
            override fun canHandle(departure: Departure) = true
            override suspend fun resolveCallingPoints(departure: Departure) = throw IllegalStateException("Should not be called")
        }

        val engine = CallingPointsEngine(listOf(resolver))
        val result = engine.resolveCallingPoints(dep)

        assertEquals(2, result.size)
        assertEquals("Station A", result[0].stationName)
    }

    @Test
    fun testResolveCallingPoints_delegatesToMatchingResolver() = runBlocking {
        val dep = createDeparture(lineId = "HK_BUS_1A", stationId = "HK_BUS_1A_STOP")

        val hkResolver = object : CallingPointsResolver {
            override fun canHandle(departure: Departure) = departure.lineId.startsWith("HK_BUS_")
            override suspend fun resolveCallingPoints(departure: Departure) = listOf(
                CallingPoint("Stop 1", isCurrentStation = true),
                CallingPoint("Stop 2", isDestination = true)
            )
        }

        val gbResolver = object : CallingPointsResolver {
            override fun canHandle(departure: Departure) = departure.lineId.startsWith("bods-")
            override suspend fun resolveCallingPoints(departure: Departure) = emptyList<CallingPoint>()
        }

        val engine = CallingPointsEngine(listOf(gbResolver, hkResolver))
        val result = engine.resolveCallingPoints(dep)

        assertEquals(2, result.size)
        assertEquals("Stop 1", result[0].stationName)
        assertTrue(result[0].isCurrentStation)
        assertEquals("Stop 2", result[1].stationName)
        assertTrue(result[1].isDestination)
    }

    @Test
    fun testResolveCallingPoints_noMatchingResolver_returnsEmpty() = runBlocking {
        val dep = createDeparture(lineId = "unknown_line")
        val engine = CallingPointsEngine(emptyList())
        val result = engine.resolveCallingPoints(dep)
        assertTrue(result.isEmpty())
    }
}
