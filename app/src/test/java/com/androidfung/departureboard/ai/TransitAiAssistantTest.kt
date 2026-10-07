package com.androidfung.departureboard.ai

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TflLineStatusItem
import com.androidfung.departureboard.data.repository.TransitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies the natural-language routing in [TransitAiAssistant]: status/disruption
 * intents, named-line queries, bus route queries, and from/to routing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransitAiAssistantTest {

    private class FakeRepository(
        private val departures: List<Departure>,
        private val lineStatuses: Map<String, TflLineStatusItem> = emptyMap()
    ) : TransitRepository {
        override val savedStationsFlow: Flow<List<Station>> = flowOf(emptyList())
        override val recentStationIdFlow: Flow<String?> = flowOf(null)

        override suspend fun searchStations(query: String): Result<List<Station>> = Result.success(emptyList())
        override suspend fun getDepartures(stationId: String, stationName: String): Result<List<Departure>> =
            Result.success(departures.filter { it.stationId == stationId })
        override suspend fun getBatchDepartures(stations: List<Station>): Map<String, Result<List<Departure>>> =
            stations.associate { it.id to getDepartures(it.id, it.name) }
        override fun getDeparturesFlow(stationId: String, stationName: String): Flow<Result<List<Departure>>> =
            flowOf(Result.success(departures.filter { it.stationId == stationId }))
        override suspend fun getCachedDepartures(stationId: String): List<Departure> =
            departures.filter { it.stationId == stationId }
        override suspend fun getLineStatuses(): Map<String, TflLineStatusItem> = lineStatuses
        override suspend fun getCallingPoints(departure: Departure): List<com.androidfung.departureboard.data.model.CallingPoint> = emptyList()
        override suspend fun saveStation(station: Station) {}
        override suspend fun reorderStations(stations: List<Station>) {}
        override suspend fun removeStation(stationId: String) {}
        override suspend fun setRecentStationId(stationId: String) {}
        override suspend fun getNearbyStationsFromApi(lat: Double, lon: Double, radiusMeters: Int): List<Station> = emptyList()
    }

    private val oxfordCircus = Station(
        id = "940GZZLUOXC",
        name = "Oxford Circus",
        modes = listOf("tube"),
        lat = 51.5152,
        lon = -0.1419
    )

    private fun departure(
        lineId: String,
        lineName: String,
        destination: String,
        stationId: String = oxfordCircus.id,
        stationName: String = "Oxford Circus",
        seconds: Int = 300,
        mode: String = "tube"
    ) = Departure(
        id = "$lineId-$destination-$seconds",
        stationId = stationId,
        stationName = stationName,
        lineId = lineId,
        lineName = lineName,
        platformName = "3",
        destinationName = destination,
        towards = null,
        direction = null,
        timeToStationSeconds = seconds,
        expectedArrivalIso = null,
        currentLocation = null,
        modeName = mode,
        lineBadge = LineBadgeInfo(lineId, lineName, Color.Cyan)
    )

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `status query with all lines healthy reports good service`() = runTest {
        val repo = FakeRepository(
            departures = emptyList(),
            lineStatuses = mapOf(
                "central" to TflLineStatusItem(
                    id = "central",
                    name = "Central line",
                    modeName = "tube",
                    lineStatuses = listOf(
                        com.androidfung.departureboard.data.model.TflLineStatusDetail(
                            statusSeverity = 10,
                            statusSeverityDescription = "Good Service"
                        )
                    )
                )
            )
        )
        val assistant = TransitAiAssistant(repo)
        val result = assistant.answerQuery(
            "Any Tube delays or disruptions?",
            savedStations = listOf(oxfordCircus)
        )
        assertTrue(
            "Expected good-service answer but got: ${result.answer}",
            result.answer.contains("good service", ignoreCase = true)
        )
    }

    @Test
    fun `status query reports disrupted line with reason`() = runTest {
        val repo = FakeRepository(
            departures = emptyList(),
            lineStatuses = mapOf(
                "elizabeth-line" to TflLineStatusItem(
                    id = "elizabeth-line",
                    name = "Elizabeth line",
                    modeName = "elizabeth-line",
                    lineStatuses = listOf(
                        com.androidfung.departureboard.data.model.TflLineStatusDetail(
                            // TfL real-world scale: 10 = good service, 60 = part suspended
                            statusSeverity = 60,
                            statusSeverityDescription = "Part Suspended",
                            reason = "Signal failure"
                        )
                    )
                )
            )
        )
        val assistant = TransitAiAssistant(repo)
        val result = assistant.answerQuery("Elizabeth line status", savedStations = listOf(oxfordCircus))
        assertTrue(
            "Expected Elizabeth disruption but got: ${result.answer}",
            result.answer.contains("Elizabeth", ignoreCase = true) &&
                result.answer.contains("part suspended", ignoreCase = true)
        )
    }

    @Test
    fun `status query against unavailable feed says it could not check`() = runTest {
        val repo = FakeRepository(departures = emptyList(), lineStatuses = emptyMap())
        val assistant = TransitAiAssistant(repo)
        val result = assistant.answerQuery("Any delays?", savedStations = listOf(oxfordCircus))
        assertTrue(
            "Expected unavailable-feed answer but got: ${result.answer}",
            result.answer.contains("couldn't check", ignoreCase = true)
        )
    }

    @Test
    fun `named line query filters departures to that line`() = runTest {
        val repo = FakeRepository(
            departures = listOf(
                departure("bakerloo", "Bakerloo", "Harrow & Wealdstone", seconds = 600),
                departure("central", "Central", "Epping", seconds = 120),
                departure("victoria", "Victoria", "Walthamstow Central", seconds = 90)
            )
        )
        val assistant = TransitAiAssistant(repo)
        val result = assistant.answerQuery("Next Central line train", savedStations = listOf(oxfordCircus))
        assertTrue(
            "Expected a Central line answer but got: ${result.answer}",
            result.answer.contains("Central", ignoreCase = true)
        )
        assertTrue(
            "Expected only Central departures matched",
            result.matchedDepartures.isNotEmpty() &&
                result.matchedDepartures.all { it.lineId == "central" }
        )
    }

    @Test
    fun `bus route query still works`() = runTest {
        val repo = FakeRepository(
            departures = listOf(
                departure("221", "221", "Clapham Junction", seconds = 120, mode = "bus"),
                departure("73", "73", "King's Cross", seconds = 300, mode = "bus")
            )
        )
        val assistant = TransitAiAssistant(repo)
        val result = assistant.answerQuery("When is the 221 bus?", savedStations = listOf(oxfordCircus))
        assertTrue(
            "Expected a 221 bus answer but got: ${result.answer}",
            result.answer.contains("221")
        )
    }

    @Test
    fun `unanswerable query falls back to friendly suggestion`() = runTest {
        val repo = FakeRepository(departures = emptyList())
        val assistant = TransitAiAssistant(repo)
        val result = assistant.answerQuery("what is the weather", savedStations = listOf(oxfordCircus))
        assertTrue(
            "Expected fallback answer but got: ${result.answer}",
            result.answer.contains("couldn't find", ignoreCase = true)
        )
    }
}
