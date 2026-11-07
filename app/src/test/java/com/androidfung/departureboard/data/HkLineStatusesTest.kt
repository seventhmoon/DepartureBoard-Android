package com.androidfung.departureboard.data

import com.androidfung.departureboard.data.model.TransitRegion
import com.androidfung.departureboard.data.model.hk.HkMtrLine
import com.androidfung.departureboard.data.model.hk.HkMtrScheduleResponse
import com.androidfung.departureboard.data.network.hk.HkMtrApiService
import com.androidfung.departureboard.data.repository.TransitRepositoryImpl
import com.androidfung.departureboard.data.repository.region.hk.HkRegionalTransitProvider
import com.androidfung.departureboard.test.FakeStationPreferencesDataSource
import com.androidfung.departureboard.test.FakeTflApiService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HkLineStatusesTest {

    private class FakeHkMtrApiService(
        private val isDelay: Boolean = false,
        private val delayMessage: String? = null
    ) : HkMtrApiService {
        override suspend fun getSchedule(line: String, station: String, lang: String): HkMtrScheduleResponse {
            return HkMtrScheduleResponse(
                status = 1,
                message = if (isDelay) delayMessage ?: "Signal failure at Quarry Bay" else "successful",
                sysTime = "2026-03-31 12:00:00",
                isDelay = if (isDelay) "Y" else "N",
                data = emptyMap()
            )
        }
    }

    @Test
    fun testHkRegionalTransitProvider_goodService() = runTest {
        val fakeApi = FakeHkMtrApiService(isDelay = false)
        val provider = HkRegionalTransitProvider(
            mtrApiService = fakeApi
        )

        val statuses = provider.getLineStatuses()
        assertTrue(statuses.isNotEmpty())
        assertEquals(HkMtrLine.entries.size, statuses.size)

        val isl = statuses["isl"]
        assertNotNull(isl)
        assertEquals("Island Line", isl?.name)
        val detail = isl?.lineStatuses?.firstOrNull()
        assertNotNull(detail)
        assertEquals(10, detail?.statusSeverity)
        assertEquals("Good Service", detail?.statusSeverityDescription)

        val alerts = provider.getTransitAlerts()
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun testHkRegionalTransitProvider_delayDisruption() = runTest {
        val fakeApi = FakeHkMtrApiService(isDelay = true, delayMessage = "Train fault near Admiralty")
        val provider = HkRegionalTransitProvider(
            mtrApiService = fakeApi
        )

        val statuses = provider.getLineStatuses()
        val twl = statuses["twl"]
        assertNotNull(twl)
        val detail = twl?.lineStatuses?.firstOrNull()
        assertNotNull(detail)
        assertEquals(9, detail?.statusSeverity)
        assertEquals("Delays", detail?.statusSeverityDescription)
        assertEquals("Train fault near Admiralty", detail?.reason)

        val alerts = provider.getTransitAlerts()
        assertTrue(alerts.isNotEmpty())
        assertTrue(alerts.any { it.headerText.contains("Tsuen Wan Line") })
    }

    @Test
    fun testTransitRepository_delegatesLineStatusesToActiveRegion() = runTest {
        val fakeDataStore = FakeStationPreferencesDataSource()
        fakeDataStore.setSelectedRegion(TransitRegion.HK)

        val fakeMtrApi = FakeHkMtrApiService(isDelay = true, delayMessage = "Overhead line issue")
        val hkProvider = HkRegionalTransitProvider(mtrApiService = fakeMtrApi)

        val repo = TransitRepositoryImpl(
            apiService = FakeTflApiService(),
            dataStore = fakeDataStore,
            customRegionalProviders = listOf(hkProvider)
        )

        val statuses = repo.getLineStatuses()
        val isl = statuses["isl"]
        assertNotNull(isl)
        assertEquals(9, isl?.lineStatuses?.firstOrNull()?.statusSeverity)

        val alerts = repo.getTransitAlerts()
        assertTrue(alerts.isNotEmpty())
    }
}
