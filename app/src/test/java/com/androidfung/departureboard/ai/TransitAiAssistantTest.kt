package com.androidfung.departureboard.ai

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.gb.GbDefaultStations
import com.androidfung.departureboard.data.model.gb.TflLineStatusItem
import com.androidfung.departureboard.test.FakeTransitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies the natural-language routing in [TransitAiAssistant]: status/disruption
 * intents, named-line queries, bus route queries, and from/to routing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransitAiAssistantTest {

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
        val repo = FakeTransitRepository(
            departuresResult = { _, _ -> Result.success(emptyList()) },
            lineStatuses = mapOf(
                "central" to TflLineStatusItem(
                    id = "central",
                    name = "Central line",
                    modeName = "tube",
                    lineStatuses = listOf(
                        com.androidfung.departureboard.data.model.gb.TflLineStatusDetail(
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
        val repo = FakeTransitRepository(
            departuresResult = { _, _ -> Result.success(emptyList()) },
            lineStatuses = mapOf(
                "elizabeth-line" to TflLineStatusItem(
                    id = "elizabeth-line",
                    name = "Elizabeth line",
                    modeName = "elizabeth-line",
                    lineStatuses = listOf(
                        com.androidfung.departureboard.data.model.gb.TflLineStatusDetail(
                            // TfL real-world scale: 10 = good service, 3 = part suspended, 6 = severe delays, 9 = minor delays
                            statusSeverity = 3,
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
        val repo = FakeTransitRepository(
            departuresResult = { _, _ -> Result.success(emptyList()) },
            lineStatuses = emptyMap()
        )
        val assistant = TransitAiAssistant(repo)
        val result = assistant.answerQuery("Any delays?", savedStations = listOf(oxfordCircus))
        assertTrue(
            "Expected unavailable-feed answer but got: ${result.answer}",
            result.answer.contains("couldn't check", ignoreCase = true)
        )
    }

    @Test
    fun `named line query filters departures to that line`() = runTest {
        val testDepartures = listOf(
            departure("bakerloo", "Bakerloo", "Harrow & Wealdstone", seconds = 600),
            departure("central", "Central", "Epping", seconds = 120),
            departure("victoria", "Victoria", "Walthamstow Central", seconds = 90)
        )
        val repo = FakeTransitRepository(
            departuresResult = { stationId, _ -> Result.success(testDepartures.filter { it.stationId == stationId }) }
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
        val testDepartures = listOf(
            departure("221", "221", "Clapham Junction", seconds = 120, mode = "bus"),
            departure("73", "73", "King's Cross", seconds = 300, mode = "bus")
        )
        val repo = FakeTransitRepository(
            departuresResult = { stationId, _ -> Result.success(testDepartures.filter { it.stationId == stationId }) }
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
        val repo = FakeTransitRepository(
            departuresResult = { _, _ -> Result.success(emptyList()) }
        )
        val assistant = TransitAiAssistant(repo)
        val result = assistant.answerQuery("what is the weather", savedStations = listOf(oxfordCircus))
        assertTrue(
            "Expected fallback answer but got: ${result.answer}",
            result.answer.contains("couldn't find", ignoreCase = true)
        )
    }

    @Test
    fun `near me query dynamically resolves closest station using LocationDataSource`() = runTest {
        val kingsCross = GbDefaultStations.POPULAR_STATIONS.first { it.name.contains("King's Cross", ignoreCase = true) }
        val testDepartures = listOf(
            departure("victoria", "Victoria", "Brixton", seconds = 60, stationId = kingsCross.id, stationName = kingsCross.name)
        )
        val repo = FakeTransitRepository(
            departuresResult = { stationId, _ -> Result.success(testDepartures.filter { it.stationId == stationId }) },
            nearbyStationsResult = { _, _, _ -> listOf(kingsCross) }
        )

        val fakeLocationDataSource = object : com.androidfung.departureboard.util.LocationDataSource {
            override suspend fun getCurrentLocation(): android.location.Location? = null
            override suspend fun getCurrentCoordinates(): com.androidfung.departureboard.util.DeviceCoordinates =
                com.androidfung.departureboard.util.DeviceCoordinates(
                    latitude = kingsCross.lat ?: 51.5308,
                    longitude = kingsCross.lon ?: -0.1238
                )
        }

        val assistant = TransitAiAssistant(
            repository = repo,
            context = null,
            locationDataSource = fakeLocationDataSource
        )

        val result = assistant.answerQuery(
            query = "next departure near me",
            savedStations = listOf(oxfordCircus), // Saved station is Oxford Circus, but user is at King's Cross
            nearestStation = null
        )

        assertTrue(
            "Expected answer to reference King's Cross as nearest station, but got: ${result.answer}",
            result.answer.contains("King's Cross", ignoreCase = true)
        )
        assertEquals(kingsCross.id, result.matchedStation?.id)
    }

    @Test
    fun `near me query when location is unavailable returns clear guidance instead of guessing saved station`() = runTest {
        val repo = FakeTransitRepository()
        val emptyLocationDataSource = object : com.androidfung.departureboard.util.LocationDataSource {
            override suspend fun getCurrentLocation(): android.location.Location? = null
            override suspend fun getCurrentCoordinates(): com.androidfung.departureboard.util.DeviceCoordinates? = null
        }

        val assistant = TransitAiAssistant(
            repository = repo,
            context = null,
            locationDataSource = emptyLocationDataSource
        )

        val result = assistant.answerQuery(
            query = "next departure near me",
            savedStations = listOf(oxfordCircus),
            nearestStation = null
        )

        assertTrue(
            "Expected location unavailable guidance but got: ${result.answer}",
            result.answer.contains("couldn't detect your current location", ignoreCase = true) ||
                result.answer.contains("location permissions", ignoreCase = true)
        )
    }

    @Test
    fun `should I run query returns urgency calculation and platform advice`() = runTest {
        val tightDeparture = departure("central", "Central", "Epping", seconds = 60)
        val repo = FakeTransitRepository(
            departuresResult = { _, _ -> Result.success(listOf(tightDeparture)) }
        )
        val assistant = TransitAiAssistant(repo)
        val result = assistant.answerQuery("Should I run for the next train?", savedStations = listOf(oxfordCircus))

        assertTrue(
            "Expected 'Too tight' advice for 60s departure but got: ${result.answer}",
            result.answer.contains("Too tight", ignoreCase = true) || result.answer.contains("Don't run", ignoreCase = true)
        )
        assertTrue(
            "Expected platform reference in advice but got: ${result.answer}",
            result.answer.contains("Platform 3", ignoreCase = true) || result.answer.contains("3")
        )
    }

    @Test
    fun `last train query identifies final departure and station closure notice`() = runTest {
        val lastDeparture = departure("bakerloo", "Bakerloo", "Elephant & Castle", seconds = 2400).copy(
            isLastTrain = true
        )
        val repo = FakeTransitRepository(
            departuresResult = { _, _ -> Result.success(listOf(lastDeparture)) }
        )
        val assistant = TransitAiAssistant(repo)
        val result = assistant.answerQuery("When is the last train tonight?", savedStations = listOf(oxfordCircus))

        assertTrue(
            "Expected final departure notice but got: ${result.answer}",
            result.answer.contains("final", ignoreCase = true) || result.answer.contains("last", ignoreCase = true)
        )
        assertTrue(
            "Expected Elephant & Castle destination in answer but got: ${result.answer}",
            result.answer.contains("Elephant & Castle", ignoreCase = true)
        )
    }

    @Test
    fun `formation query provides platform positioning advice`() = runTest {
        val formationDeparture = departure("victoria", "Victoria", "Brixton", seconds = 300)
        val repo = FakeTransitRepository(
            departuresResult = { _, _ -> Result.success(listOf(formationDeparture)) }
        )
        val assistant = TransitAiAssistant(repo)
        val result = assistant.answerQuery("Where should I stand on the platform?", savedStations = listOf(oxfordCircus))

        assertTrue(
            "Expected platform boarding guidance but got: '${result.answer}'",
            result.answer.contains("boarding", ignoreCase = true) || result.answer.contains("middle", ignoreCase = true)
        )
    }

    @Test
    fun `disrupted status query includes decoded reason and concourse alternatives`() = runTest {
        val repo = FakeTransitRepository(
            departuresResult = { _, _ -> Result.success(emptyList()) },
            lineStatuses = mapOf(
                "northern" to TflLineStatusItem(
                    id = "northern",
                    name = "Northern line",
                    modeName = "tube",
                    lineStatuses = listOf(
                        com.androidfung.departureboard.data.model.gb.TflLineStatusDetail(
                            statusSeverity = 4,
                            statusSeverityDescription = "Severe Delays",
                            reason = "Signal failure at Camden Town"
                        )
                    )
                )
            )
        )
        val assistant = TransitAiAssistant(repo)
        val result = assistant.answerQuery("Northern line status", savedStations = listOf(oxfordCircus))

        assertTrue(
            "Expected severe delays in answer but got: ${result.answer}",
            result.answer.contains("severe delays", ignoreCase = true)
        )
        assertTrue(
            "Expected concourse advice or decoded reason in answer but got: ${result.answer}",
            result.answer.contains("Victoria", ignoreCase = true) || result.answer.contains("signalling", ignoreCase = true)
        )
    }
}
