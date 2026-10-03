package com.androidfung.departureboard.data

import com.androidfung.departureboard.data.datastore.StationPreferencesDataSource
import com.androidfung.departureboard.data.model.DefaultStations
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TflArrivalPrediction
import com.androidfung.departureboard.data.model.TflSearchResponse
import com.androidfung.departureboard.data.model.TflStopPointMatch
import com.androidfung.departureboard.data.network.TflApiService
import com.androidfung.departureboard.data.repository.TransitRepositoryImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class TransitRepositoryTest {

    // Mock API Service for unit testing
    private class FakeTflApiService(
        var searchResult: TflSearchResponse = TflSearchResponse(total = 0, matches = emptyList()),
        var arrivalsResult: List<TflArrivalPrediction> = emptyList(),
        var shouldThrow: Boolean = false
    ) : TflApiService {
        override suspend fun searchStations(query: String, modes: String, maxResults: Int): TflSearchResponse {
            if (shouldThrow) throw IOException("Network error")
            return searchResult
        }

        override suspend fun getArrivals(stationId: String): List<TflArrivalPrediction> {
            if (shouldThrow) throw IOException("Network error")
            return arrivalsResult
        }

        override suspend fun getLineStatuses(modes: String): List<com.androidfung.departureboard.data.model.TflLineStatusItem> {
            return emptyList()
        }

        override suspend fun getStopPointDetail(stopPointId: String): com.androidfung.departureboard.data.model.TflStopPointDetail {
            return com.androidfung.departureboard.data.model.TflStopPointDetail(id = stopPointId)
        }
    }

    // Fake in-memory implementation of StationPreferencesDataSource
    private class FakeStationPreferencesDataSource : StationPreferencesDataSource {
        private val _stations = MutableStateFlow(DefaultStations.POPULAR_STATIONS)
        private val _recentId = MutableStateFlow<String?>(DefaultStations.POPULAR_STATIONS.first().id)

        override val savedStationsFlow: Flow<List<Station>> = _stations.asStateFlow()
        override val recentStationIdFlow: Flow<String?> = _recentId.asStateFlow()

        override suspend fun saveStation(station: Station) {
            val list = _stations.value.toMutableList()
            val idx = list.indexOfFirst { it.id == station.id }
            if (idx >= 0) {
                list[idx] = station
            } else {
                list.add(station)
            }
            _stations.value = list
        }

        override suspend fun saveStations(stations: List<Station>) {
            _stations.value = stations
        }

        override suspend fun removeStation(stationId: String) {
            _stations.value = _stations.value.filterNot { it.id == stationId }
        }

        override suspend fun setRecentStationId(stationId: String) {
            _recentId.value = stationId
        }
    }

    @Test
    fun testSearchStations_successfulApi() = runTest {
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

        val repo = TransitRepositoryImpl(
            apiService = fakeApi,
            dataStore = FakeStationPreferencesDataSource()
        )

        val result = repo.searchStations("Oxford")
        assertTrue(result.isSuccess)
        val stations = result.getOrNull()!!
        assertEquals(1, stations.size)
        assertEquals("Oxford Circus", stations[0].name)
    }

    @Test
    fun testSearchStations_offlineFallback() = runTest {
        val fakeApi = FakeTflApiService(shouldThrow = true)
        val repo = TransitRepositoryImpl(
            apiService = fakeApi,
            dataStore = FakeStationPreferencesDataSource()
        )

        val result = repo.searchStations("Oxford")
        assertTrue(result.isSuccess)
        val stations = result.getOrNull()!!
        assertTrue(stations.any { it.name == "Oxford Circus" })
    }

    @Test
    fun testGetDepartures_successfulApi() = runTest {
        val fakeApi = FakeTflApiService(
            arrivalsResult = listOf(
                TflArrivalPrediction(
                    id = "arr_1",
                    stationName = "Oxford Circus",
                    lineId = "victoria",
                    lineName = "Victoria",
                    platformName = "Northbound - Platform 3",
                    destinationName = "Walthamstow Central",
                    towards = "Walthamstow Central",
                    timeToStation = 80,
                    modeName = "tube"
                )
            )
        )

        val repo = TransitRepositoryImpl(
            apiService = fakeApi,
            dataStore = FakeStationPreferencesDataSource()
        )

        val result = repo.getDepartures("940GZZLUOXC", "Oxford Circus")
        assertTrue(result.isSuccess)
        val departures = result.getOrNull()!!
        assertEquals(1, departures.size)
        assertEquals("Victoria", departures[0].lineName)
        assertEquals("1 min", departures[0].formattedTimeToArrival)
    }

    @Test
    fun testGetDepartures_networkFailureUsesFallback() = runTest {
        val fakeApi = FakeTflApiService(shouldThrow = true)
        val repo = TransitRepositoryImpl(
            apiService = fakeApi,
            dataStore = FakeStationPreferencesDataSource()
        )

        val result = repo.getDepartures("940GZZLUOXC", "Oxford Circus")
        assertTrue(result.isSuccess)
        val departures = result.getOrNull()!!
        assertTrue(departures.isNotEmpty())
        assertEquals("Oxford Circus", departures[0].stationName)
    }

    @Test
    fun testDataStoreSaveAndRemoveStation() = runTest {
        val fakeDataStore = FakeStationPreferencesDataSource()
        val repo = TransitRepositoryImpl(
            apiService = FakeTflApiService(),
            dataStore = fakeDataStore
        )

        val newStation = Station(
            id = "NEW_1",
            name = "Test New Station",
            modes = listOf("bus"),
            isFavorite = true
        )

        repo.saveStation(newStation)
        val savedList = repo.savedStationsFlow.first()
        assertTrue(savedList.any { it.id == "NEW_1" })

        repo.setRecentStationId("NEW_1")
        assertEquals("NEW_1", repo.recentStationIdFlow.first())

        repo.removeStation("NEW_1")
        val updatedList = repo.savedStationsFlow.first()
        assertTrue(updatedList.none { it.id == "NEW_1" })
    }
}
