package com.androidfung.departureboard.ui.dashboard

import android.app.Application
import com.androidfung.departureboard.data.model.DefaultStations
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.repository.TransitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeTransitRepository(
        initialStations: List<Station> = DefaultStations.POPULAR_STATIONS.take(2)
    ) : TransitRepository {
        val stationsFlow = MutableStateFlow(initialStations)
        val removedStationIds = mutableListOf<String>()
        val savedStations = mutableListOf<Station>()

        override val savedStationsFlow: Flow<List<Station>> = stationsFlow
        override val recentStationIdFlow: Flow<String?> = flowOf("940GZZLUOXC")

        override suspend fun searchStations(query: String): Result<List<Station>> {
            val matches = DefaultStations.POPULAR_STATIONS.filter {
                it.name.contains(query, ignoreCase = true)
            }
            return Result.success(matches)
        }

        override suspend fun getDepartures(stationId: String, stationName: String): Result<List<Departure>> {
            return Result.success(DefaultStations.getFallbackDepartures(stationId, stationName))
        }

        override suspend fun getBatchDepartures(stations: List<Station>): Map<String, Result<List<Departure>>> {
            return stations.associate { it.id to getDepartures(it.id, it.name) }
        }

        override fun getDeparturesFlow(stationId: String, stationName: String): Flow<Result<List<Departure>>> {
            return flowOf(Result.success(DefaultStations.getFallbackDepartures(stationId, stationName)))
        }

        override suspend fun getLineStatuses(): Map<String, com.androidfung.departureboard.data.model.TflLineStatusItem> {
            return emptyMap()
        }

        override suspend fun saveStation(station: Station) {
            savedStations.add(station)
            stationsFlow.value = stationsFlow.value + station
        }

        override suspend fun reorderStations(stations: List<Station>) {
            stationsFlow.value = stations
        }

        override suspend fun removeStation(stationId: String) {
            removedStationIds.add(stationId)
            stationsFlow.value = stationsFlow.value.filterNot { it.id == stationId }
        }

        override suspend fun setRecentStationId(stationId: String) {}
    }

    private class FakeApplication : Application()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialLoadingAndSavedStationsLoaded() = runTest {
        val fakeRepo = FakeTransitRepository()
        val viewModel = DashboardViewModel(FakeApplication(), fakeRepo, enablePeriodicTasks = false)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isInitialLoading)
        assertEquals(2, state.stationCards.size)
        assertEquals("Oxford Circus", state.stationCards[0].station.name)
        assertTrue(state.stationCards[0].departures.isNotEmpty())
    }

    @Test
    fun testAddStationUpdatesRepositoryAndState() = runTest {
        val fakeRepo = FakeTransitRepository()
        val viewModel = DashboardViewModel(FakeApplication(), fakeRepo, enablePeriodicTasks = false)

        advanceUntilIdle()

        val newStation = DefaultStations.POPULAR_STATIONS[2] // Waterloo
        viewModel.addStation(newStation)
        advanceUntilIdle()

        assertTrue(fakeRepo.savedStations.any { it.id == newStation.id })
        assertEquals(3, viewModel.uiState.value.stationCards.size)
        assertTrue(viewModel.uiState.value.userMessage?.contains("Added") == true)
    }

    @Test
    fun testRemoveStationUpdatesStateAndCallsRepository() = runTest {
        val fakeRepo = FakeTransitRepository()
        val viewModel = DashboardViewModel(FakeApplication(), fakeRepo, enablePeriodicTasks = false)

        advanceUntilIdle()

        val stationToRemove = fakeRepo.stationsFlow.value.first()
        viewModel.removeStation(stationToRemove)

        advanceUntilIdle()

        assertTrue(fakeRepo.removedStationIds.contains(stationToRemove.id))
        assertNotNull(viewModel.uiState.value.userMessage)
        assertTrue(viewModel.uiState.value.userMessage?.contains("Removed") == true)
    }

    @Test
    fun testUndoRemoveStationRestoresStation() = runTest {
        val fakeRepo = FakeTransitRepository()
        val viewModel = DashboardViewModel(FakeApplication(), fakeRepo, enablePeriodicTasks = false)

        advanceUntilIdle()

        val stationToRemove = fakeRepo.stationsFlow.value.first()
        viewModel.removeStation(stationToRemove)
        advanceUntilIdle()

        viewModel.undoRemoveStation()
        advanceUntilIdle()

        assertTrue(fakeRepo.savedStations.any { it.id == stationToRemove.id })
        assertTrue(viewModel.uiState.value.userMessage?.contains("Restored") == true)
    }

    @Test
    fun testRefreshDeparturesUpdatesTimestamps() = runTest {
        val fakeRepo = FakeTransitRepository()
        val viewModel = DashboardViewModel(FakeApplication(), fakeRepo, enablePeriodicTasks = false)

        advanceUntilIdle()

        val beforeTime = viewModel.uiState.value.lastUpdatedTimestamp
        viewModel.refreshDepartures(isManualPullToRefresh = true)
        advanceUntilIdle()

        val afterTime = viewModel.uiState.value.lastUpdatedTimestamp
        assertTrue(afterTime >= beforeTime)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun testRefreshStationUpdatesSingleCard() = runTest {
        val fakeRepo = FakeTransitRepository()
        val viewModel = DashboardViewModel(FakeApplication(), fakeRepo, enablePeriodicTasks = false)

        advanceUntilIdle()

        val station = fakeRepo.stationsFlow.value.first()
        viewModel.refreshStation(station)
        advanceUntilIdle()

        val updatedCard = viewModel.uiState.value.stationCards.first { it.station.id == station.id }
        assertFalse(updatedCard.isLoading)
        assertTrue(updatedCard.departures.isNotEmpty())
    }

    @Test
    fun testSearchStationsReturnsMatchingStations() = runTest {
        val fakeRepo = FakeTransitRepository()
        val viewModel = DashboardViewModel(FakeApplication(), fakeRepo, enablePeriodicTasks = false)

        val results = viewModel.searchStations("Oxford")
        assertTrue(results.any { it.name == "Oxford Circus" })
    }

    @Test
    fun testCountdownTickerDecrementsDepartureTimes() = runTest {
        val fakeRepo = FakeTransitRepository()
        val viewModel = DashboardViewModel(FakeApplication(), fakeRepo, enablePeriodicTasks = false)

        advanceUntilIdle()

        val initialSeconds = viewModel.uiState.value.stationCards.first().departures.first().timeToStationSeconds
        viewModel.startCountdownTicker()
        testDispatcher.scheduler.advanceTimeBy(1001L)

        val updatedSeconds = viewModel.uiState.value.stationCards.first().departures.first().timeToStationSeconds
        assertEquals(initialSeconds - 1, updatedSeconds)
        viewModel.stopCountdownTicker()
    }
}
