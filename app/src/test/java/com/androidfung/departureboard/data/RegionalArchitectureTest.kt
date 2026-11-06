package com.androidfung.departureboard.data

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.StationSearchRanker
import com.androidfung.departureboard.data.model.TransitLineRegistry
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.model.au.AuStationDirectory
import com.androidfung.departureboard.data.model.au.AuSydneyLine
import com.androidfung.departureboard.data.model.gb.GbTransitLine
import com.androidfung.departureboard.data.model.gb.TflLineColors
import com.androidfung.departureboard.data.model.gb.topology.GbTransitRoutes
import com.androidfung.departureboard.data.model.hk.HkMtrLine
import com.androidfung.departureboard.data.model.hk.topology.HkMtrRoutes
import com.androidfung.departureboard.data.model.isBus
import com.androidfung.departureboard.data.model.jp.JpStationDirectory
import com.androidfung.departureboard.data.model.jp.JpTokyoLine
import com.androidfung.departureboard.data.model.jp.topology.JpTokyoRoutes
import com.androidfung.departureboard.data.model.parseHkMtrStationId
import com.androidfung.departureboard.data.model.primaryTransitMode
import com.androidfung.departureboard.data.model.sg.SgMrtLine
import com.androidfung.departureboard.data.model.sg.SgStationDirectory
import com.androidfung.departureboard.data.model.sg.topology.SgMrtRoutes
import com.androidfung.departureboard.data.model.transitMode
import com.androidfung.departureboard.data.repository.region.au.AuRegionalTransitProvider
import com.androidfung.departureboard.data.repository.region.hk.HkRegionalTransitProvider
import com.androidfung.departureboard.data.repository.region.jp.JpCallingPointsResolver
import com.androidfung.departureboard.data.repository.region.jp.JpRegionalTransitProvider
import com.androidfung.departureboard.data.repository.region.sg.SgCallingPointsResolver
import com.androidfung.departureboard.data.repository.region.sg.SgRegionalTransitProvider
import com.androidfung.departureboard.util.CoalescingTimedCache
import com.androidfung.departureboard.util.runSuspendCatching
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionalArchitectureTest {

    @Test(expected = CancellationException::class)
    fun `runSuspendCatching rethrows CancellationException`() {
        runSuspendCatching {
            throw CancellationException("Coroutine cancelled")
        }
    }

    @Test
    fun `runSuspendCatching wraps non-cancellation exceptions in Result failure`() {
        val result = runSuspendCatching<String> {
            throw IllegalStateException("Network error")
        }
        assertTrue(result.isFailure)
        assertEquals("Network error", result.exceptionOrNull()?.message)
    }

    @Test
    fun `CoalescingTimedCache coalesces concurrent in-flight requests and respects TTL`() = runTest {
        var nowMs = 1_000L
        val loaderInvocations = AtomicInteger(0)
        val cache = CoalescingTimedCache<String, String>(
            ttlMillis = 5_000L,
            clockMillis = { nowMs }
        )

        val deferreds = (1..5).map {
            async {
                cache.getOrPutCoalesced("KEY_1", this@runTest) {
                    delay(50)
                    "VALUE_${loaderInvocations.incrementAndGet()}"
                }
            }
        }
        val results = deferreds.awaitAll()
        assertEquals(listOf("VALUE_1", "VALUE_1", "VALUE_1", "VALUE_1", "VALUE_1"), results)
        assertEquals(1, loaderInvocations.get())

        // Within TTL (t = 3000ms) -> serves cached without calling loader
        nowMs = 3_000L
        val cachedVal = cache.getOrPutCoalesced("KEY_1", this) {
            "VALUE_${loaderInvocations.incrementAndGet()}"
        }
        assertEquals("VALUE_1", cachedVal)
        assertEquals(1, loaderInvocations.get())

        // Past TTL (t = 7000ms) -> refreshes via loader
        nowMs = 7_000L
        val refreshedVal = cache.getOrPutCoalesced("KEY_1", this) {
            "VALUE_${loaderInvocations.incrementAndGet()}"
        }
        assertEquals("VALUE_2", refreshedVal)
        assertEquals(2, loaderInvocations.get())
    }

    @Test
    fun `parseHkMtrStationId extracts lineCode and stationCode across formats`() {
        val fullRef = "HK_MTR_ISL_CEN".parseHkMtrStationId()
        assertEquals("ISL", fullRef.lineCode)
        assertEquals("CEN", fullRef.stationCode)

        val stationOnlyRef = "HK_MTR_ADM".parseHkMtrStationId()
        assertNull(stationOnlyRef.lineCode)
        assertEquals("ADM", stationOnlyRef.stationCode)

        val rawRef = "tkl_tik".parseHkMtrStationId()
        assertEquals("TKL", rawRef.lineCode)
        assertEquals("TIK", rawRef.stationCode)
    }

    @Test
    fun `Station and Departure mode extensions resolve TransitMode accurately`() {
        val busStation = Station(id = "4900001", name = "Oxford Circus", modes = listOf("bus"))
        assertEquals(TransitMode.BUS, busStation.primaryTransitMode)

        val mtrStation = Station(id = "HK_MTR_ISL_CEN", name = "Central", modes = listOf("mtr"))
        assertEquals(TransitMode.METRO, mtrStation.primaryTransitMode)

        val busDeparture = Departure(
            id = "1",
            stationId = "4900001",
            stationName = "Stop A",
            lineId = "73",
            lineName = "73",
            platformName = "Stop A",
            destinationName = "Victoria",
            towards = null,
            direction = "inbound",
            timeToStationSeconds = 60,
            expectedArrivalIso = null,
            currentLocation = null,
            modeName = "bus",
            lineBadge = TflLineColors.getLineBadge("73", "73", "bus")
        )
        assertTrue(busDeparture.isBus)
        assertEquals(TransitMode.BUS, busDeparture.transitMode)
    }

    @Test
    fun `TransitLineRegistry resolves line badges across GB, HK, SG, AU, and JP`() {
        val registries: List<TransitLineRegistry> = listOf(
            GbTransitLine,
            HkMtrLine,
            SgMrtLine,
            AuSydneyLine,
            JpTokyoLine
        )
        assertEquals(5, registries.size)

        val gbTubeBadge = GbTransitLine.getLineBadge("northern")
        assertEquals("northern", gbTubeBadge.lineId)
        assertEquals("NOR", gbTubeBadge.lineCode)

        val gbOvergroundBadge = GbTransitLine.getLineBadge("windrush")
        assertEquals("windrush", gbOvergroundBadge.lineId)
        assertEquals(TransitMode.RAIL, gbOvergroundBadge.mode)

        val hkBadge = HkMtrLine.getLineBadge("TWL")
        assertEquals("TWL", hkBadge.lineCode)
        assertEquals(TransitMode.METRO, hkBadge.mode)

        val sgBadge = SgMrtLine.getLineBadge("EWL")
        assertEquals("EWL", sgBadge.lineId)
        assertEquals("EW", sgBadge.lineCode)

        val auBadge = AuSydneyLine.getLineBadge("M1")
        assertEquals("M1", auBadge.lineId)
        assertEquals("M", auBadge.lineCode)

        val jpBadge = JpTokyoLine.getLineBadge("JY")
        assertEquals("JY", jpBadge.lineCode)
    }

    @Test
    fun `BaseStationDirectory provides O(1) getStationById and ranked search across SG, AU, and JP`() {
        val sgStation = SgStationDirectory.getStationById("SG_MRT_EW13_NS25")
        assertNotNull(sgStation)
        assertTrue(sgStation!!.name.contains("City Hall"))

        val auStation = AuStationDirectory.getStationById("AU_TRAIN_CENTRAL")
        assertNotNull(auStation)
        assertTrue(auStation!!.name.contains("Central"))

        val jpStation = JpStationDirectory.getStationById("JP_METRO_M08_JY17")
        assertNotNull(jpStation)
        assertTrue(jpStation!!.name.contains("Shinjuku"))

        val ranked = StationSearchRanker.rank(
            listOf(
                Station(id = "1", name = "Bank Underground Station (Stop A)", modes = listOf("bus")),
                Station(id = "2", name = "Bank", modes = listOf("tube", "dlr"))
            ),
            "Bank"
        )
        assertEquals("2", ranked.first().id)
    }

    @Test
    fun `JpTokyoRoutes Yamanote loop line slices circular shortest path across Tokyo terminus`() {
        val resolver = JpCallingPointsResolver()
        // Yurakucho is index 29, Tokyo is index 0, Kanda is index 1 on the 30-station Yamanote loop.
        // Shortest circular path is Yurakucho -> Tokyo -> Kanda (3 calling points), not 29 stops backwards.
        val callingPoints = resolver.resolveCallingPointsForTrip(
            lineCode = "JY",
            originStationIdentifier = "Yurakucho",
            destStationIdentifier = "Kanda",
            departureEpochSeconds = 1_700_000_000L
        )
        assertEquals(listOf("Yurakucho", "Tokyo", "Kanda"), callingPoints.map { it.stationName })
        assertTrue(callingPoints.first().isCurrentStation)
        assertTrue(callingPoints.last().isDestination)
    }

    @Test
    fun `HkMtrRoutes resolves Racecourse branching spur via unified LinearRouteCallingPointsResolver`() {
        val callingPoints = HkMtrRoutes.resolveCallingPoints(
            lineCode = "EAL",
            originSta = "SHT",
            destStaOrName = "RAC",
            departureEpochSeconds = 1_700_000_000L
        )
        assertEquals(2, callingPoints.size)
        assertTrue(callingPoints[0].stationName.contains("Sha Tin"))
        assertTrue(callingPoints[1].stationName.contains("Racecourse"))
        assertEquals("HK_MTR_EAL_RAC", callingPoints[1].stationId)
    }

    @Test
    fun `RegionalTransitProviders claim ownership of their respective station IDs`() = runTest {
        val hkProvider = HkRegionalTransitProvider()
        assertTrue(hkProvider.ownsStationId("HK_MTR_ISL_CEN"))
        assertTrue(hkProvider.ownsStationId("CEN"))
        assertFalse(hkProvider.ownsStationId("SG_MRT_EW13_NS25"))

        assertTrue(SgRegionalTransitProvider.ownsStationId("SG_MRT_EW13_NS25"))
        assertTrue(AuRegionalTransitProvider.ownsStationId("AU_TRAIN_CENTRAL"))
        assertTrue(JpRegionalTransitProvider.ownsStationId("JP_METRO_M17_JY01"))

        assertNotNull(hkProvider.getStationById("HK_MTR_ISL_CEN"))
        assertNotNull(SgRegionalTransitProvider.getStationById("SG_MRT_EW13_NS25"))
        assertNotNull(AuRegionalTransitProvider.getStationById("AU_TRAIN_CENTRAL"))
        assertNotNull(JpRegionalTransitProvider.getStationById("JP_METRO_M17_JY01"))
    }

    @Test
    fun `GbTransitRoutes models London Circle line as spiral linear route not a continuous loop`() {
        assertFalse(GbTransitRoutes.loopLineCodes.contains("CIRCLE"))
        assertTrue(GbTransitRoutes.loopLineCodes.contains("METRO-YELLOW"))

        val circleDep = Departure(
            id = "cir_1",
            stationId = "940GZZLUKSX",
            stationName = "King's Cross St. Pancras",
            lineId = "circle",
            lineName = "Circle",
            platformName = "Eastbound - Platform 1",
            destinationName = "Edgware Road",
            towards = "Edgware Road via Victoria",
            direction = "inbound",
            timeToStationSeconds = 120,
            expectedArrivalIso = null,
            currentLocation = null,
            modeName = "tube",
            lineBadge = GbTransitLine.CIRCLE.toLineBadge()
        )

        val callingPoints = GbTransitRoutes.resolveLocalCallingPoints(circleDep)
        assertEquals("King's Cross St. Pancras", callingPoints.first().stationName)
        assertEquals("Edgware Road", callingPoints.last().stationName)
        assertTrue(callingPoints.any { it.stationName == "Victoria" })
        assertTrue(callingPoints.any { it.stationName == "High Street Kensington" })
    }

    @Test
    fun `SgMrtRoutes and JpTokyoRoutes resolve station codes and canonical stationIds`() {
        // Singapore MRT stores station codes (NS1..NS28, EW1..EW33, CG1..CG2)
        assertTrue(SgMrtRoutes.lineSequences["NSL"]!!.contains("NS25"))
        assertEquals("City Hall", SgMrtRoutes.resolveStationName("NS25"))

        val sgResolver = SgCallingPointsResolver()
        val changiPoints = sgResolver.resolveCallingPointsForTrip(
            lineCode = "EWL",
            originStationIdentifier = "EW4",
            destStationIdentifier = "CG2"
        )
        assertEquals(listOf("Tanah Merah", "Expo", "Changi Airport"), changiPoints.map { it.stationName })
        assertEquals("SG_MRT_CG2", changiPoints.last().stationId)

        // Tokyo stores station numbering codes (JY01..JY30, G01..G19, M01..M25)
        assertTrue(JpTokyoRoutes.lineSequences["JY"]!!.contains("JY01"))
        assertEquals("Tokyo", JpTokyoRoutes.resolveStationName("JY01"))
    }
}
