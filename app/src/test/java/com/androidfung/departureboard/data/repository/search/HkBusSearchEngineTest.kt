package com.androidfung.departureboard.data.repository.search

import com.androidfung.departureboard.data.model.hk.HkKmbRouteStopItem
import com.androidfung.departureboard.data.model.hk.HkKmbRouteStopResponse
import com.androidfung.departureboard.data.model.hk.HkKmbStopDetailItem
import com.androidfung.departureboard.data.model.hk.HkKmbStopDetailResponse
import com.androidfung.departureboard.data.network.hk.HkBusApiService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HkBusSearchEngineTest {

    private val fakeApiService = object : HkBusApiService {
        override suspend fun getKmbStopEta(stopId: String) = throw UnsupportedOperationException()
        override suspend fun getCitybusEta(companyId: String, stopId: String, route: String) = throw UnsupportedOperationException()

        override suspend fun getKmbRouteStops(route: String, direction: String, serviceType: String): HkKmbRouteStopResponse {
            return if (route == "1A" && direction == "outbound") {
                HkKmbRouteStopResponse(
                    data = listOf(
                        HkKmbRouteStopItem(route = "1A", stop = "001476", seq = 1),
                        HkKmbRouteStopItem(route = "1A", stop = "001614", seq = 2)
                    )
                )
            } else {
                HkKmbRouteStopResponse(data = emptyList())
            }
        }

        override suspend fun getKmbStopDetail(stopId: String): HkKmbStopDetailResponse {
            val (en, tc) = when (stopId) {
                "001476" -> "Star Ferry TST" to "尖沙咀天星碼頭"
                "001614" -> "Mong Kok Bank Centre" to "旺角銀行中心"
                else -> "Stop" to "巴士站"
            }
            return HkKmbStopDetailResponse(
                data = HkKmbStopDetailItem(
                    stop = stopId,
                    jsonNameEn = en,
                    jsonNameTc = tc,
                    lat = "22.3",
                    long = "114.1"
                )
            )
        }
    }

    private val engine = HkBusSearchEngine(kmbApiService = fakeApiService)

    @Test
    fun testIsRouteQuery() {
        assertTrue(engine.isRouteQuery("1A"))
        assertTrue(engine.isRouteQuery("104"))
        assertTrue(engine.isRouteQuery("968"))
        assertTrue(engine.isRouteQuery("E21A"))
        assertTrue(engine.isRouteQuery("6"))
        assertTrue(engine.isRouteQuery("B1"))
        assertTrue(engine.isRouteQuery("280X"))

        assertFalse(engine.isRouteQuery(""))
        assertFalse(engine.isRouteQuery("Central"))
        assertFalse(engine.isRouteQuery("Admiralty"))
        assertFalse(engine.isRouteQuery("Hong Kong"))
    }

    @Test
    fun testSearchRouteStops_returnsStopsForRoute() = runBlocking {
        val stops = engine.searchRouteStops("1A")
        assertEquals(2, stops.size)
        assertEquals("HK_BUS_KMB_001476", stops[0].id)
        assertEquals("Star Ferry TST (尖沙咀天星碼頭) (Route 1A)", stops[0].name)
        assertTrue(stops[0].modes.contains("bus"))

        assertEquals("HK_BUS_KMB_001614", stops[1].id)
        assertEquals("Mong Kok Bank Centre (旺角銀行中心) (Route 1A)", stops[1].name)
    }

    @Test
    fun testSearchRouteStops_nonMatchingRouteReturnsEmpty() = runBlocking {
        val stops = engine.searchRouteStops("999X")
        assertTrue(stops.isEmpty())
    }
}
