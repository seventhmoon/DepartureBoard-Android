package com.androidfung.departureboard.data.repository.source

import com.androidfung.departureboard.data.db.cache.CachedDepartureEntity
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.hk.HkCitybusEtaResponse
import com.androidfung.departureboard.data.model.hk.HkKmbEtaItem
import com.androidfung.departureboard.data.model.hk.HkKmbStopEtaResponse
import com.androidfung.departureboard.data.model.hk.HkMtrScheduleResponse
import com.androidfung.departureboard.data.model.hk.HkMtrStationScheduleData
import com.androidfung.departureboard.data.model.hk.HkMtrTrainScheduleItem
import com.androidfung.departureboard.data.model.hk.HkStationDirectory
import com.androidfung.departureboard.data.model.hk.topology.HkMtrRoutes
import com.androidfung.departureboard.data.network.hk.HkBusApiService
import com.androidfung.departureboard.data.network.hk.HkMtrApiService
import com.androidfung.departureboard.data.repository.region.gb.direction.DestinationResolver
import com.androidfung.departureboard.data.repository.region.gb.direction.ElizabethLineDirectionResolver
import com.androidfung.departureboard.data.repository.region.gb.direction.OvergroundLineResolver
import com.androidfung.departureboard.data.repository.region.gb.direction.ThameslinkDirectionResolver
import com.androidfung.departureboard.data.repository.region.gb.mapper.NationalRailDepartureMapper
import com.androidfung.departureboard.data.repository.region.gb.mapper.TflDepartureMapper
import com.androidfung.departureboard.data.repository.region.hk.HkBusCallingPointsResolver
import com.androidfung.departureboard.data.repository.region.hk.HkBusDepartureSource
import com.androidfung.departureboard.data.repository.region.hk.HkMtrDepartureSource
import com.androidfung.departureboard.data.topology.LinearCorridorDirectionResolver
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HkDepartureSourcesTest {

    @Test
    fun testHkBusDepartureSource_canHandle() {
        val source = HkBusDepartureSource(
            kmbApiService = FakeHkBusApiService(),
            citybusApiService = FakeHkBusApiService()
        )

        assertTrue(source.canHandle("HK_BUS_KMB_001554", "Cross Harbour Tunnel", false))
        assertTrue(source.canHandle("HK_BUS_001476", "Star Ferry", true))
        assertTrue(source.canHandle("SOMESTOP", "海底隧道收費廣場", false))
    }

    @Test
    fun testHkBusDepartureSource_parsesKmbEtaCorrectly() = runBlocking {
        val now = OffsetDateTime.now()
        val etaTime = now.plusMinutes(4).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

        val fakeApi = FakeHkBusApiService(
            kmbResponse = HkKmbStopEtaResponse(
                type = "ETA",
                data = listOf(
                    HkKmbEtaItem(
                        co = "KMB",
                        route = "1A",
                        destTc = "中秀茂坪",
                        destEn = "Sau Mau Ping",
                        eta = etaTime,
                        rmkTc = "實時班次",
                        etaSeq = 1
                    )
                )
            )
        )

        val source = HkBusDepartureSource(kmbApiService = fakeApi, citybusApiService = fakeApi)
        val result = source.fetchDepartures("HK_BUS_KMB_001476", "Star Ferry TST", true)

        assertTrue(result.isSuccess)
        val departures = result.getOrNull()
        assertNotNull(departures)
        assertEquals(1, departures!!.size)

        val item = departures[0]
        assertEquals("1A", item.lineName)
        assertEquals("Sau Mau Ping (中秀茂坪)", item.destinationName)
        assertEquals(DepartureStatus.ON_TIME, item.status)
        assertEquals("bus", item.modeName)
    }

    @Test
    fun testHkMtrDepartureSource_queriesMultipleLinesAndIncludesCallingPoints() = runBlocking {
        val hkZone = ZoneId.of("Asia/Hong_Kong")
        val nowHk = java.time.LocalDateTime.now(hkZone)
        val departureTimeStr = nowHk.plusMinutes(2).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

        val fakeMtrApi = FakeHkMtrApiService(
            schedules = mapOf(
                "ISL-ADM" to HkMtrScheduleResponse(
                    status = 1,
                    data = mapOf(
                        "ISL-ADM" to HkMtrStationScheduleData(
                            downDepartures = listOf(
                                HkMtrTrainScheduleItem(plat = "1", time = departureTimeStr, dest = "CHW", seq = "1")
                            )
                        )
                    )
                ),
                "TWL-ADM" to HkMtrScheduleResponse(
                    status = 1,
                    data = mapOf(
                        "TWL-ADM" to HkMtrStationScheduleData(
                            upDepartures = listOf(
                                HkMtrTrainScheduleItem(plat = "4", time = departureTimeStr, dest = "TSW", seq = "1")
                            )
                        )
                    )
                )
            )
        )

        val source = HkMtrDepartureSource(apiService = fakeMtrApi)
        assertTrue(source.canHandle("HK_MTR_ISL_ADM", "Admiralty (金鐘)", false))

        val result = source.fetchDepartures("HK_MTR_ISL_ADM", "Admiralty (金鐘)", false)
        assertTrue(result.isSuccess)
        val departures = result.getOrNull()
        assertNotNull(departures)
        assertEquals(2, departures!!.size)

        // Verify calling points are populated
        val islDep = departures.first { it.lineId == "ISL" }
        assertTrue("Calling points must not be empty", islDep.callingPoints.isNotEmpty())
        assertEquals("Admiralty 金鐘", islDep.callingPoints.first().stationName)
        assertTrue(islDep.callingPoints.any { it.stationName.contains("Chai Wan") })
    }

    @Test
    fun testInterchangeStation_hasCompleteLinesList() {
        val admInfo = com.androidfung.departureboard.data.model.hk.HkMtrStationRegistry.getStation("ADM")!!
        val admStation = HkStationDirectory.mtrToStation(admInfo)
        val lineIds = admStation.lines.map { it.id }

        // Admiralty must have 4 lines: ISL, TWL, EAL, SIL
        assertTrue(lineIds.contains("ISL"))
        assertTrue(lineIds.contains("TWL"))
        assertTrue(lineIds.contains("EAL"))
        assertTrue(lineIds.contains("SIL"))

        val cenInfo = com.androidfung.departureboard.data.model.hk.HkMtrStationRegistry.getStation("CEN")!!
        val cenStation = HkStationDirectory.mtrToStation(cenInfo)
        val cenLineIds = cenStation.lines.map { it.id }
        assertTrue(cenLineIds.contains("ISL"))
        assertTrue(cenLineIds.contains("TWL"))
        assertTrue(cenLineIds.contains("TCL"))
    }

    @Test
    fun testCallingPointsResolution() {
        val callingPoints = HkMtrRoutes.resolveCallingPoints("ISL", "CEN", "CHW")
        assertTrue(callingPoints.isNotEmpty())
        assertEquals("Central 中環", callingPoints.first().stationName)
        assertTrue(callingPoints.first().isCurrentStation)
        assertEquals("Chai Wan 柴灣", callingPoints.last().stationName)
        assertTrue(callingPoints.last().isDestination)
    }

    private class FakeHkBusApiService(
        val kmbResponse: HkKmbStopEtaResponse = HkKmbStopEtaResponse(data = emptyList()),
        val citybusResponse: HkCitybusEtaResponse = HkCitybusEtaResponse(data = emptyList())
    ) : HkBusApiService {
        override suspend fun getKmbStopEta(stopId: String): HkKmbStopEtaResponse = kmbResponse
        override suspend fun getCitybusEta(companyId: String, stopId: String, route: String): HkCitybusEtaResponse = citybusResponse
        override suspend fun getKmbRouteStops(route: String, direction: String, serviceType: String): com.androidfung.departureboard.data.model.hk.HkKmbRouteStopResponse {
            return com.androidfung.departureboard.data.model.hk.HkKmbRouteStopResponse(data = emptyList())
        }
        override suspend fun getKmbStopDetail(stopId: String): com.androidfung.departureboard.data.model.hk.HkKmbStopDetailResponse {
            return com.androidfung.departureboard.data.model.hk.HkKmbStopDetailResponse()
        }
    }

    private class FakeHkMtrApiService(
        val schedules: Map<String, HkMtrScheduleResponse> = emptyMap(),
        val defaultSchedule: HkMtrScheduleResponse = HkMtrScheduleResponse()
    ) : HkMtrApiService {
        override suspend fun getSchedule(line: String, station: String, lang: String): HkMtrScheduleResponse {
            return schedules["$line-$station"] ?: defaultSchedule
        }
    }
}
