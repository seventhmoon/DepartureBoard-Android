package com.androidfung.departureboard.ui.dashboard

import android.app.Application
import com.androidfung.departureboard.billing.BillingDataSource
import com.androidfung.departureboard.billing.SubscriptionTier
import com.androidfung.departureboard.data.model.DefaultStations
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.repository.TransitRepository
import com.androidfung.departureboard.test.FakeTransitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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

    private class FakeBillingRepository(
        isProInitial: Boolean = false
    ) : BillingDataSource {
        val planFlow = MutableStateFlow(if (isProInitial) com.androidfung.departureboard.billing.UserPlan.PRO else com.androidfung.departureboard.billing.UserPlan.FREE)
        override val userPlanFlow: Flow<com.androidfung.departureboard.billing.UserPlan> = planFlow
        override val isProFlow: Flow<Boolean> = planFlow.map { it.isPaidOrAbove }
        override val availableProducts: kotlinx.coroutines.flow.StateFlow<List<com.android.billingclient.api.ProductDetails>> =
            MutableStateFlow(emptyList())
        override val billingError: kotlinx.coroutines.flow.StateFlow<String?> = MutableStateFlow(null)

        override fun queryAvailableProducts() {}
        override fun refreshPurchases(onResult: ((Boolean, String) -> Unit)?) {
            onResult?.invoke(false, "No purchases")
        }
        override fun launchPurchaseFlow(activity: android.app.Activity, productDetails: com.android.billingclient.api.ProductDetails, basePlanId: String?): Boolean = true
        override suspend fun tryConsumeAiQuery(): Boolean = true
        override suspend fun getRemainingAiQueries(): Int = 100
        override fun setDebugPro(enabled: Boolean) {
            planFlow.value = if (enabled) com.androidfung.departureboard.billing.UserPlan.PRO else com.androidfung.departureboard.billing.UserPlan.FREE
        }
        override fun setDebugPlan(plan: com.androidfung.departureboard.billing.UserPlan) {
            planFlow.value = plan
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

    @Test
    fun testThemeModeAndDynamicColorPreferences() = runTest {
        val themeModeFlow = MutableStateFlow(com.androidfung.departureboard.ui.theme.AppThemeMode.SYSTEM)
        val dynamicColorFlow = MutableStateFlow(true)
        val repo = object : FakeTransitRepository() {
            override val themeModeFlow: Flow<com.androidfung.departureboard.ui.theme.AppThemeMode> = themeModeFlow
            override val dynamicColorFlow: Flow<Boolean> = dynamicColorFlow

            override suspend fun setThemeMode(mode: com.androidfung.departureboard.ui.theme.AppThemeMode) {
                themeModeFlow.value = mode
            }

            override suspend fun setDynamicColor(enabled: Boolean) {
                dynamicColorFlow.value = enabled
            }
        }
        val viewModel = DashboardViewModel(FakeApplication(), repo, FakeBillingRepository())
        advanceUntilIdle()

        assertEquals(com.androidfung.departureboard.ui.theme.AppThemeMode.SYSTEM, viewModel.uiState.value.themeMode)
        assertTrue(viewModel.uiState.value.isDynamicColor)

        viewModel.setThemeMode(com.androidfung.departureboard.ui.theme.AppThemeMode.DARK)
        viewModel.setDynamicColor(false)
        advanceUntilIdle()

        assertEquals(com.androidfung.departureboard.ui.theme.AppThemeMode.DARK, viewModel.uiState.value.themeMode)
        assertFalse(viewModel.uiState.value.isDynamicColor)
    }

    @Test
    fun testNetworkConnectivityPausesAndResumesPolling() = runTest {
        val fakeRepo = FakeTransitRepository()
        val onlineFlow = MutableStateFlow(true)
        val fakeObserver = object : com.androidfung.departureboard.util.NetworkConnectivityObserver {
            override val isOnline: Flow<Boolean> = onlineFlow
        }
        val viewModel = DashboardViewModel(
            application = FakeApplication(),
            repository = fakeRepo,
            billingRepository = FakeBillingRepository(),
            networkConnectivityObserver = fakeObserver
        )
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isOnline)

        // Go offline
        onlineFlow.value = false
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isOnline)

        // Go back online
        onlineFlow.value = true
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isOnline)
    }
}
