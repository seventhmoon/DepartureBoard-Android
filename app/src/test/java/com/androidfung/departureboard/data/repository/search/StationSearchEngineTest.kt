package com.androidfung.departureboard.data.repository.search

import com.androidfung.departureboard.data.model.DefaultStations
import com.androidfung.departureboard.data.model.TflSearchResponse
import com.androidfung.departureboard.data.model.TflStopPointMatch
import com.androidfung.departureboard.test.FakeBodsApiService
import com.androidfung.departureboard.test.FakeTflApiService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StationSearchEngineTest {

    @Test
    fun testSearchStations_blankReturnsPopularStations() = runTest {
        val engine = StationSearchEngine(
            apiService = FakeTflApiService(),
            bodsApiService = FakeBodsApiService()
        )

        val result = engine.searchStations("")
        assertTrue(result.isSuccess)
        assertEquals(DefaultStations.POPULAR_STATIONS, result.getOrNull())
    }

    @Test
    fun testSearchStations_tflApiMatch() = runTest {
        val fakeApi = FakeTflApiService(
            searchResult = TflSearchResponse(
                total = 1,
                matches = listOf(
                    TflStopPointMatch(
                        id = "940GZZLUOXC",
                        name = "Oxford Circus Underground Station",
                        modes = listOf("tube"),
                        zone = "1",
                        lat = 51.5152,
                        lon = -0.1419
                    )
                )
            )
        )

        val engine = StationSearchEngine(
            apiService = fakeApi,
            bodsApiService = FakeBodsApiService()
        )

        val result = engine.searchStations("Oxford")
        assertTrue(result.isSuccess)
        val stations = result.getOrNull()!!
        assertEquals(1, stations.size)
        assertEquals("Oxford Circus", stations[0].name)
    }

    @Test
    fun testSearchStations_errorFallsBackToPopularMatches() = runTest {
        val fakeApi = FakeTflApiService(shouldThrow = true)
        val engine = StationSearchEngine(
            apiService = fakeApi,
            bodsApiService = FakeBodsApiService()
        )

        val result = engine.searchStations("Oxford")
        assertTrue(result.isSuccess)
        val stations = result.getOrNull()!!
        assertTrue(stations.any { it.name == "Oxford Circus" })
    }
}
