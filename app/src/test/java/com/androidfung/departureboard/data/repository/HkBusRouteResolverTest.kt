package com.androidfung.departureboard.data.repository

import com.androidfung.departureboard.data.model.HkKmbRouteStopItem
import com.androidfung.departureboard.data.model.HkKmbRouteStopResponse
import com.androidfung.departureboard.data.model.HkKmbStopDetailItem
import com.androidfung.departureboard.data.model.HkKmbStopDetailResponse
import com.androidfung.departureboard.data.network.HkBusApiService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HkBusRouteResolverTest {

    @Test
    fun testResolveCallingPoints_resolvesDownstreamStops() = runBlocking {
        val fakeApi = object : HkBusApiService {
            override suspend fun getKmbStopEta(stopId: String) = throw UnsupportedOperationException()
            override suspend fun getCitybusEta(companyId: String, stopId: String, route: String) = throw UnsupportedOperationException()

            override suspend fun getKmbRouteStops(route: String, direction: String, serviceType: String): HkKmbRouteStopResponse {
                return HkKmbRouteStopResponse(
                    data = listOf(
                        HkKmbRouteStopItem(route = "1A", stop = "STOP_1", seq = 1),
                        HkKmbRouteStopItem(route = "1A", stop = "STOP_2", seq = 2),
                        HkKmbRouteStopItem(route = "1A", stop = "STOP_3", seq = 3)
                    )
                )
            }

            override suspend fun getKmbStopDetail(stopId: String): HkKmbStopDetailResponse {
                val name = when (stopId) {
                    "STOP_1" -> "Star Ferry 尖沙咀碼頭"
                    "STOP_2" -> "Nathan Road 彌敦道"
                    "STOP_3" -> "Sau Mau Ping 中秀茂坪"
                    else -> "Bus Stop"
                }
                return HkKmbStopDetailResponse(
                    data = HkKmbStopDetailItem(stop = stopId, nameEn = name.substringBefore(" "), nameTc = name.substringAfter(" "))
                )
            }
        }

        val resolver = HkBusRouteResolver(apiService = fakeApi)
        val callingPoints = resolver.resolveCallingPoints("1A", "STOP_2", "outbound")

        assertEquals(2, callingPoints.size)
        assertEquals("Nathan Road 彌敦道", callingPoints[0].stationName)
        assertTrue(callingPoints[0].isCurrentStation)

        assertEquals("Sau Mau Ping 中秀茂坪", callingPoints[1].stationName)
        assertTrue(callingPoints[1].isDestination)
    }
}
