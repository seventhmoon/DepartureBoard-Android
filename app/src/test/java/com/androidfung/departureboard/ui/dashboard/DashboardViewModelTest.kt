package com.androidfung.departureboard.ui.dashboard

import android.app.Application
import com.androidfung.departureboard.billing.BillingDataSource
import com.androidfung.departureboard.billing.SubscriptionTier
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

        override suspend fun getCachedDepartures(stationId: String): List<Departure> = emptyList()

        override suspend fun getLineStatuses(): Map<String, com.androidfung.departureboard.data.model.TflLineStatusItem> {
            return emptyMap()
        }

        override suspend fun getCallingPoints(departure: Departure): List<com.androidfung.departureboard.data.model.CallingPoint> {
            return emptyList()
        }

        override suspend fun saveStation(station: Station) {
            savedStations.add(station)
            stationsFlow.value += station
        }

        override suspend fun reorderStations(stations: List<Station>) {
            stationsFlow.value = stations
        }

        override suspend fun removeStation(stationId: String) {
            removedStationIds.add(stationId)
            stationsFlow.value = stationsFlow.value.filterNot { it.id == stationId }
        }

        override suspend fun setRecentStationId(stationId: String) {}

        override suspend fun getNearbyStationsFromApi(lat: Double, lon: Double, radiusMeters: Int): List<Station> {
            return emptyList()
        }
    }

    private class FakeBillingRepository(
        isProInitial: Boolean = false
    ) : BillingDataSource {
        val proFlow = MutableStateFlow(isProInitial)
        override val isProFlow: Flow<Boolean> = proFlow
        override val availableProducts: kotlinx.coroutines.flow.StateFlow<List<com.android.billingclient.api.ProductDetails>> =
            MutableStateFlow(emptyList())
        override val billingError: kotlinx.coroutines.flow.StateFlow<String?> = MutableStateFlow(null)

        override fun queryAvailableProducts() {}
        override fun refreshPurchases() {}
        override fun launchPurchaseFlow(activity: android.app.Activity, productDetails: com.android.billingclient.api.ProductDetails, basePlanId: String?): Boolean = true
        override suspend fun tryConsumeAiQuery(): Boolean = true
        override suspend fun getRemainingAiQueries(): Int = 100
        override fun setDebugPro(enabled: Boolean) {
            proFlow.value = enabled
        }
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
        val fakeBilling = FakeBillingRepository(isProInitial = false)
        val viewModel = DashboardViewModel(FakeApplication(), fakeRepo, fakeBilling)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isInitialLoading)
        assertEquals(2, state.stationCards.size)
        assertEquals("Oxford Circus", state.stationCards[0].station.name)
        assertTrue(state.stationCards[0].departures.isNotEmpty())
    }

    @Test
    fun testAddStation_freeTierLimitTriggersPaywall() = runTest {
        // Initial has FREE_MAX_STATIONS (Free tier max)
        val initialStations = DefaultStations.POPULAR_STATIONS.take(SubscriptionTier.FREE_MAX_STATIONS)
        val fakeRepo = FakeTransitRepository(initialStations)
        val fakeBilling = FakeBillingRepository(isProInitial = false)
        val viewModel = DashboardViewModel(FakeApplication(), fakeRepo, fakeBilling)

        advanceUntilIdle()

        val newStation = DefaultStations.POPULAR_STATIONS[SubscriptionTier.FREE_MAX_STATIONS]
        viewModel.addStation(newStation)
        advanceUntilIdle()

        // Should NOT be added to saved stations, and paywall prompt must be set
        assertEquals(SubscriptionTier.FREE_MAX_STATIONS, viewModel.uiState.value.stationCards.size)
        assertNotNull(viewModel.uiState.value.paywallPromptReason)
        assertTrue(viewModel.uiState.value.paywallPromptReason!!.contains("Free plan includes up to ${SubscriptionTier.FREE_MAX_STATIONS}"))
    }

    @Test
    fun testAddStation_proTierAllowsUpToProMaxStations() = runTest {
        // Initial has 2 stations, and user is PRO
        val initialStations = DefaultStations.POPULAR_STATIONS.take(2)
        val fakeRepo = FakeTransitRepository(initialStations)
        val fakeBilling = FakeBillingRepository(isProInitial = true)
        val viewModel = DashboardViewModel(FakeApplication(), fakeRepo, fakeBilling)

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isPro)

        val newStation = DefaultStations.POPULAR_STATIONS[2]
        viewModel.addStation(newStation)
        advanceUntilIdle()

        // Station is added successfully
        assertTrue(fakeRepo.savedStations.any { it.id == newStation.id })
        assertEquals(3, viewModel.uiState.value.stationCards.size)
        assertTrue(viewModel.uiState.value.userMessage?.contains("Added") == true)
    }

    @Test
    fun testAddStation_proTierCapsAtProMaxStations() = runTest {
        // Generate list of stations up to PRO_MAX_STATIONS
        val maxStations = (1..SubscriptionTier.PRO_MAX_STATIONS).map { i ->
            Station(id = "ST_$i", name = "Station $i")
        }
        val fakeRepo = FakeTransitRepository(maxStations)
        val fakeBilling = FakeBillingRepository(isProInitial = true)
        val viewModel = DashboardViewModel(FakeApplication(), fakeRepo, fakeBilling)

        advanceUntilIdle()
        assertEquals(SubscriptionTier.PRO_MAX_STATIONS, viewModel.uiState.value.stationCards.size)

        // Attempt to add one more over the limit
        val extraStation = Station(id = "ST_EXTRA", name = "Station Extra")
        viewModel.addStation(extraStation)
        advanceUntilIdle()

        // Capped at PRO_MAX_STATIONS to protect API rate limits and battery
        assertEquals(SubscriptionTier.PRO_MAX_STATIONS, viewModel.uiState.value.stationCards.size)
        assertTrue(viewModel.uiState.value.userMessage?.contains("Maximum limit of ${SubscriptionTier.PRO_MAX_STATIONS}") == true)
    }

    @Test
    fun testDismissPaywallClearsReason() = runTest {
        val fakeRepo = FakeTransitRepository()
        val fakeBilling = FakeBillingRepository(isProInitial = false)
        val viewModel = DashboardViewModel(FakeApplication(), fakeRepo, fakeBilling)

        viewModel.showPaywall("Upgrade reason")
        assertEquals("Upgrade reason", viewModel.uiState.value.paywallPromptReason)

        viewModel.dismissPaywall()
        assertEquals(null, viewModel.uiState.value.paywallPromptReason)
    }

    @Test
    fun testRemoveStationUpdatesStateAndCallsRepository() = runTest {
        val fakeRepo = FakeTransitRepository()
        val fakeBilling = FakeBillingRepository()
        val viewModel = DashboardViewModel(FakeApplication(), fakeRepo, fakeBilling)

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
        val fakeBilling = FakeBillingRepository()
        val viewModel = DashboardViewModel(FakeApplication(), fakeRepo, fakeBilling)

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
        val fakeBilling = FakeBillingRepository()
        val viewModel = DashboardViewModel(FakeApplication(), fakeRepo, fakeBilling)

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
        val fakeBilling = FakeBillingRepository()
        val viewModel = DashboardViewModel(FakeApplication(), fakeRepo, fakeBilling)

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
        val fakeBilling = FakeBillingRepository()
        val viewModel = DashboardViewModel(FakeApplication(), fakeRepo, fakeBilling)

        val results = viewModel.searchStations("Oxford")
        assertTrue(results.any { it.name == "Oxford Circus" })
    }

    @Test
    fun testCountdownTickerDecrementsDepartureTimes() = runTest {
        val fakeRepo = FakeTransitRepository()
        val fakeBilling = FakeBillingRepository()
        val viewModel = DashboardViewModel(FakeApplication(), fakeRepo, fakeBilling)

        advanceUntilIdle()

        val initialSeconds = viewModel.uiState.value.stationCards.first().departures.first().timeToStationSeconds
        viewModel.startCountdownTicker()
        testDispatcher.scheduler.advanceTimeBy(1001L)

        val updatedSeconds = viewModel.uiState.value.stationCards.first().departures.first().timeToStationSeconds
        assertEquals(initialSeconds - 1, updatedSeconds)
        viewModel.stopCountdownTicker()
    }

    @Test
    fun testOpenStationDetailLoadsDeparturesEvenWhenNotInCards() = runTest {
        val fakeRepo = FakeTransitRepository()
        val fakeBilling = FakeBillingRepository()
        val viewModel = DashboardViewModel(FakeApplication(), fakeRepo, fakeBilling)

        val externalStation = Station(id = "EXTERNAL_ST", name = "Stratford")
        viewModel.openStationDetail(externalStation)

        assertEquals("EXTERNAL_ST", viewModel.uiState.value.selectedDetailStation?.id)

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDetailLoading)
        assertTrue(viewModel.uiState.value.detailDepartures.isNotEmpty())

        viewModel.closeStationDetail()
        assertEquals(null, viewModel.uiState.value.selectedDetailStation)
        assertTrue(viewModel.uiState.value.detailDepartures.isEmpty())
    }
}
