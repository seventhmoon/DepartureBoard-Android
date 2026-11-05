package com.androidfung.departureboard.data

import com.androidfung.departureboard.data.repository.region.gb.TimetableScheduleResolver
import com.androidfung.departureboard.data.repository.region.gb.GbFeedReconciliationEngine
import com.androidfung.departureboard.data.repository.region.gb.GbRegionalTransitProvider
import com.androidfung.departureboard.data.repository.region.gb.search.GbStationSearchEngine
import com.androidfung.departureboard.test.FakeBodsApiService
import com.androidfung.departureboard.test.FakeNrApiService
import com.androidfung.departureboard.test.FakeTflApiService
import com.androidfung.departureboard.test.TestFixtures
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GbRegionalTransitProviderDeparturesTest {

    @Test
    fun testProviderOwnsGbStationIds() = runTest {
        val fakeTfl = FakeTflApiService()
        val fakeNr = FakeNrApiService()
        val fakeBods = FakeBodsApiService()
        val searchEngine = GbStationSearchEngine(fakeTfl, fakeBods)
        val timetableResolver = TimetableScheduleResolver(fakeTfl)

        val provider = GbRegionalTransitProvider(
            apiService = fakeTfl,
            nrApiService = fakeNr,
            bodsApiService = fakeBods,
            stationSearchEngine = searchEngine,
            timetableScheduleResolver = timetableResolver
        )

        // Verify provider owns London/GB stations
        assertTrue(provider.ownsStationId("940GZZLUKSX"))
        assertTrue(provider.ownsStationId("EUS"))
    }

    @Test
    fun testGbFeedReconciliationEngineDeduplicatesTflAndNr() {
        val tflDep = TestFixtures.createTestDeparture(
            id = "1",
            lineId = "elizabeth",
            lineName = "Elizabeth line",
            destinationName = "Shenfield",
            timeToStationSeconds = 120
        )
        val nrDep = TestFixtures.createTestDeparture(
            id = "2",
            lineId = "elizabeth",
            lineName = "Elizabeth line",
            destinationName = "Shenfield",
            timeToStationSeconds = 125
        )

        val merged = GbFeedReconciliationEngine.mergeAndDeduplicate(listOf(tflDep), listOf(nrDep))
        assertEquals(1, merged.size)
        assertEquals("1", merged[0].id)
    }
}
