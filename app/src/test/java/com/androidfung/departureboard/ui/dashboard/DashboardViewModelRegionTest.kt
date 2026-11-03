package com.androidfung.departureboard.ui.dashboard

import android.app.Application
import com.androidfung.departureboard.billing.BillingDataSource
import com.androidfung.departureboard.data.model.TransitRegion
import com.androidfung.departureboard.test.FakeTransitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelRegionTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeBillingRepository : BillingDataSource {
        val planFlow = MutableStateFlow(com.androidfung.departureboard.billing.UserPlan.FREE)
        override val userPlanFlow: Flow<com.androidfung.departureboard.billing.UserPlan> = planFlow
        override val isProFlow: Flow<Boolean> = planFlow.map { it.isPaidOrAbove }
        override val availableProducts = MutableStateFlow(emptyList<com.android.billingclient.api.ProductDetails>())
        override val billingError = MutableStateFlow<String?>(null)

        override fun queryAvailableProducts() {}
        override fun refreshPurchases(onResult: ((Boolean, String) -> Unit)?) {
            onResult?.invoke(false, "No purchases")
        }
        override fun launchPurchaseFlow(
            activity: android.app.Activity,
            productDetails: com.android.billingclient.api.ProductDetails,
            basePlanId: String?
        ): Boolean = true
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
    fun testRegionSwitchingToHkAndBackToGb() = runTest {
        val fakeRepo = FakeTransitRepository()
        val fakeBilling = FakeBillingRepository()
        val viewModel = DashboardViewModel(FakeApplication(), fakeRepo, fakeBilling)

        advanceUntilIdle()

        // Initially GB region
        assertEquals(TransitRegion.GB, viewModel.uiState.value.selectedRegion)
        assertEquals(2, viewModel.uiState.value.stationCards.size)
        assertEquals("Oxford Circus", viewModel.uiState.value.stationCards[0].station.name)

        // Switch to HK
        viewModel.setSelectedRegion(TransitRegion.HK)
        advanceUntilIdle()

        assertEquals(TransitRegion.HK, viewModel.uiState.value.selectedRegion)
        assertEquals(TransitRegion.HK, fakeRepo.selectedRegionFlow.let { fakeRepo.selectedRegionStateFlow.value })

        // Switch back to GB
        viewModel.setSelectedRegion(TransitRegion.GB)
        advanceUntilIdle()

        assertEquals(TransitRegion.GB, viewModel.uiState.value.selectedRegion)
    }

    @Test
    fun testHkExperimentalToggle() = runTest {
        val fakeRepo = FakeTransitRepository()
        val fakeBilling = FakeBillingRepository()
        val viewModel = DashboardViewModel(FakeApplication(), fakeRepo, fakeBilling)

        advanceUntilIdle()

        // Enable HK
        viewModel.setHkRegionEnabled(true)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isHkRegionEnabled)

        // Disable HK -> should force fallback to GB if previously HK
        viewModel.setHkRegionEnabled(false)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isHkRegionEnabled)
        assertEquals(TransitRegion.GB, viewModel.uiState.value.selectedRegion)
    }

    @Test
    fun testRecentSearchedStationsAreScopedByRegion() = runTest {
        val fakeRepo = FakeTransitRepository()
        val fakeBilling = FakeBillingRepository()
        val viewModel = DashboardViewModel(FakeApplication(), fakeRepo, fakeBilling)

        advanceUntilIdle()

        // 1. In GB region, add a London station
        val gbStation = com.androidfung.departureboard.data.model.Station(id = "940GZZLUKSX", name = "King's Cross St. Pancras")
        viewModel.addRecentSearchedStation(gbStation)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.recentSearchedStations.size)
        assertEquals("King's Cross St. Pancras", viewModel.uiState.value.recentSearchedStations.first().name)

        // 2. Switch to Japan (JP)
        viewModel.setSelectedRegion(TransitRegion.JP)
        advanceUntilIdle()

        // JP recent searches should be empty initially
        assertTrue("JP recents should be empty", viewModel.uiState.value.recentSearchedStations.isEmpty())

        // 3. In JP region, add Tokyo station
        val jpStation = com.androidfung.departureboard.data.model.Station(id = "JP_METRO_M17", name = "Tokyo")
        viewModel.addRecentSearchedStation(jpStation)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.recentSearchedStations.size)
        assertEquals("Tokyo", viewModel.uiState.value.recentSearchedStations.first().name)

        // 4. Switch back to GB -> GB recents should remain intact
        viewModel.setSelectedRegion(TransitRegion.GB)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.recentSearchedStations.size)
        assertEquals("King's Cross St. Pancras", viewModel.uiState.value.recentSearchedStations.first().name)

        // 5. Clear recent searches in GB -> only GB is cleared, JP remains
        viewModel.clearRecentSearchedStations()
        advanceUntilIdle()

        assertTrue("GB recents should be cleared", viewModel.uiState.value.recentSearchedStations.isEmpty())

        viewModel.setSelectedRegion(TransitRegion.JP)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.recentSearchedStations.size)
        assertEquals("Tokyo", viewModel.uiState.value.recentSearchedStations.first().name)
    }

    @Test
    fun testRecentSearchedStationsAreScopedByRegion() = runTest {
        val fakeRepo = FakeTransitRepository()
        val fakeBilling = FakeBillingRepository()
        val viewModel = DashboardViewModel(FakeApplication(), fakeRepo, fakeBilling)

        advanceUntilIdle()

        // 1. In GB region, add a London station
        val gbStation = com.androidfung.departureboard.data.model.Station(id = "940GZZLUKSX", name = "King's Cross St. Pancras")
        viewModel.addRecentSearchedStation(gbStation)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.recentSearchedStations.size)
        assertEquals("King's Cross St. Pancras", viewModel.uiState.value.recentSearchedStations.first().name)

        // 2. Switch to Japan (JP)
        viewModel.setSelectedRegion(TransitRegion.JP)
        advanceUntilIdle()

        // JP recent searches should be empty initially
        assertTrue("JP recents should be empty", viewModel.uiState.value.recentSearchedStations.isEmpty())

        // 3. In JP region, add Tokyo station
        val jpStation = com.androidfung.departureboard.data.model.Station(id = "JP_METRO_M17", name = "Tokyo")
        viewModel.addRecentSearchedStation(jpStation)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.recentSearchedStations.size)
        assertEquals("Tokyo", viewModel.uiState.value.recentSearchedStations.first().name)

        // 4. Switch back to GB -> GB recents should remain intact
        viewModel.setSelectedRegion(TransitRegion.GB)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.recentSearchedStations.size)
        assertEquals("King's Cross St. Pancras", viewModel.uiState.value.recentSearchedStations.first().name)

        // 5. Clear recent searches in GB -> only GB is cleared, JP remains
        viewModel.clearRecentSearchedStations()
        advanceUntilIdle()

        assertTrue("GB recents should be cleared", viewModel.uiState.value.recentSearchedStations.isEmpty())

        viewModel.setSelectedRegion(TransitRegion.JP)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.recentSearchedStations.size)
        assertEquals("Tokyo", viewModel.uiState.value.recentSearchedStations.first().name)
    }
}
