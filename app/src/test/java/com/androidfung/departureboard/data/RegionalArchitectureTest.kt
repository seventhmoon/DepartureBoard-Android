package com.androidfung.departureboard.data

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.StationLineInferrer
import com.androidfung.departureboard.data.model.StationLineInfo
import com.androidfung.departureboard.data.model.StationSearchRanker
import com.androidfung.departureboard.data.model.TransitLineRegistry
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.model.TransitRegion
import com.androidfung.departureboard.data.model.au.AuStationDirectory
import com.androidfung.departureboard.data.model.au.AuSydneyLine
import com.androidfung.departureboard.data.model.gb.GbTransitLine
import com.androidfung.departureboard.data.model.gb.TflLineColors
import com.androidfung.departureboard.data.model.gb.topology.GbTransitRoutes
import com.androidfung.departureboard.data.model.hk.HkMtrLine
import com.androidfung.departureboard.data.model.hk.topology.HkMtrRoutes
import com.androidfung.departureboard.data.model.jp.JpStationDirectory
import com.androidfung.departureboard.data.model.jp.JpTokyoLine
import com.androidfung.departureboard.data.model.jp.topology.JpTokyoRoutes
import com.androidfung.departureboard.data.model.parseHkMtrStationId
import com.androidfung.departureboard.data.model.sg.SgMrtLine
import com.androidfung.departureboard.data.model.sg.SgStationDirectory
import com.androidfung.departureboard.data.model.sg.topology.SgMrtRoutes
import com.androidfung.departureboard.data.repository.region.au.AuDepartureSource
import com.androidfung.departureboard.data.repository.region.au.AuRegionalTransitProvider
import com.androidfung.departureboard.data.repository.region.hk.HkBusDepartureSource
import com.androidfung.departureboard.data.repository.region.hk.HkMtrDepartureSource
import com.androidfung.departureboard.data.repository.region.hk.HkRegionalTransitProvider
import com.androidfung.departureboard.data.repository.region.jp.JpCallingPointsResolver
import com.androidfung.departureboard.data.repository.region.jp.JpDepartureSource
import com.androidfung.departureboard.data.repository.region.jp.JpRegionalTransitProvider
import com.androidfung.departureboard.data.repository.region.sg.SgCallingPointsResolver
import com.androidfung.departureboard.data.repository.region.sg.SgDepartureSource
import com.androidfung.departureboard.data.repository.region.sg.SgRegionalTransitProvider
import com.androidfung.departureboard.util.CoalescingTimedCache
import com.androidfung.departureboard.util.LocationHelper
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
    fun `HkMtrRoutes resolves Lok Ma Chau and Lo Wu branches without cross-contamination`() {
        val lmcCallingPoints = HkMtrRoutes.resolveCallingPoints(
            lineCode = "EAL",
            originSta = "SHS",
            destStaOrName = "LMC",
            departureEpochSeconds = 1_700_000_000L
        )
        assertEquals(2, lmcCallingPoints.size)
        assertTrue(lmcCallingPoints[0].stationName.contains("Sheung Shui"))
        assertTrue(lmcCallingPoints[1].stationName.contains("Lok Ma Chau"))
        assertFalse(lmcCallingPoints.any { it.stationName.contains("Lo Wu") })

        val lowCallingPoints = HkMtrRoutes.resolveCallingPoints(
            lineCode = "EAL",
            originSta = "SHS",
            destStaOrName = "LOW",
            departureEpochSeconds = 1_700_000_000L
        )
        assertEquals(2, lowCallingPoints.size)
        assertTrue(lowCallingPoints[0].stationName.contains("Sheung Shui"))
        assertTrue(lowCallingPoints[1].stationName.contains("Lo Wu"))
        assertFalse(lowCallingPoints.any { it.stationName.contains("Lok Ma Chau") })
    }

    @Test
    fun `HkMtrRoutes resolves Tseung Kwan O line branches correctly`() {
        val poaCallingPoints = HkMtrRoutes.resolveCallingPoints(
            lineCode = "TKL",
            originSta = "TKO",
            destStaOrName = "POA",
            departureEpochSeconds = 1_700_000_000L
        )
        val poaNames = poaCallingPoints.map { it.stationName }
        assertTrue(poaNames.any { it.contains("Hang Hau") })
        assertTrue(poaNames.any { it.contains("Po Lam") })
        assertFalse(poaNames.any { it.contains("LOHAS") })

        val lhpCallingPoints = HkMtrRoutes.resolveCallingPoints(
            lineCode = "TKL",
            originSta = "TKO",
            destStaOrName = "LHP",
            departureEpochSeconds = 1_700_000_000L
        )
        val lhpNames = lhpCallingPoints.map { it.stationName }
        assertEquals(2, lhpCallingPoints.size)
        assertTrue(lhpNames.any { it.contains("Tseung Kwan O") })
        assertTrue(lhpNames.any { it.contains("LOHAS") })
        assertFalse(lhpNames.any { it.contains("Po Lam") })
        assertFalse(lhpNames.any { it.contains("Hang Hau") })
    }

    @Test
    fun `RegionalTransitProviders claim ownership of their respective station IDs`() = runTest {
        val hkProvider = HkRegionalTransitProvider()
        assertTrue(hkProvider.ownsStationId("HK_MTR_ISL_CEN"))
        assertTrue(hkProvider.ownsStationId("HK_BUS_KMB_001406"))
        // 3-letter UK National Rail CRS & Tyne and Wear Metro codes must NOT be hijacked by HK MTR
        assertFalse(hkProvider.ownsStationId("CEN"))
        assertFalse(hkProvider.ownsStationId("PRE"))
        assertFalse(hkProvider.ownsStationId("UNI"))
        assertFalse(hkProvider.ownsStationId("KET"))
        assertFalse(hkProvider.ownsStationId("SUN"))
        assertFalse(hkProvider.ownsStationId("AIR"))
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

    @Test
    fun `TransitRegion provides centralized defaultStations, lineRegistry, directory, and hints`() {
        for (region in TransitRegion.entries) {
            assertTrue(region.defaultStations.isNotEmpty())
            assertNotNull(region.lineRegistry)
            assertTrue(region.searchNotFoundHintRes != 0)
            assertTrue(region.mapsSearchRegionSuffix.isNotBlank())
            assertTrue(region.lineStatusTitleRes != 0)
            assertTrue(region.lineStatusDescRes != 0)
            if (region == TransitRegion.GB) {
                assertNull(region.directory)
            } else {
                assertNotNull(region.directory)
            }
        }
    }

    @Test
    fun `Regional departure sources reject false positive substring matches`() {
        val jpSource = JpDepartureSource()
        assertFalse(jpSource.canHandle("940GZZLUMTR", "Metro Station", false))
        assertFalse(jpSource.canHandle("BUS_123", "Tokyo Bus", true))
        assertTrue(jpSource.canHandle("JP_METRO_M08", "Tokyo Station", false))

        val sgSource = SgDepartureSource()
        assertFalse(sgSource.canHandle("PARIS_METRO", "Paris MRT", false))
        assertFalse(sgSource.canHandle("BJ_SUBWAY", "北京地鐵", false))
        assertTrue(sgSource.canHandle("SG_MRT_EW13_NS25", "City Hall", false))

        val auSource = AuDepartureSource()
        assertFalse(auSource.canHandle("UK_123", "Sydney Gardens London", false))
        assertFalse(auSource.canHandle("US_NSW", "NSW USA", false))
        assertTrue(auSource.canHandle("AU_TRAIN_CENTRAL", "Central", false))

        val hkMtrSource = HkMtrDepartureSource()
        assertFalse(hkMtrSource.canHandle("SG_MRT_1", "MRT Station", false))
        assertTrue(hkMtrSource.canHandle("HK_MTR_ISL_CEN", "Central", false))
        assertTrue(hkMtrSource.canHandle("ADM", "Admiralty", false))

        val hkBusSource = HkBusDepartureSource()
        assertFalse(hkBusSource.canHandle("UK_BUS_1", "London Victoria Coach Station", true))
        assertTrue(hkBusSource.canHandle("001406", "Cross Harbour Tunnel BBI", true))
        assertTrue(hkBusSource.canHandle("HK_BUS_KMB_001406", "Cross Harbour Tunnel", true))
    }

    @Test
    fun `LocationHelper findNearestStation with coordinates works without Location object`() {
        val st1 = Station(id = "1", name = "One", modes = listOf("tube"), zone = null, lat = 51.500, lon = -0.120)
        val st2 = Station(id = "2", name = "Two", modes = listOf("tube"), zone = null, lat = 51.510, lon = -0.130)

        val nearest = LocationHelper.findNearestStation(51.501, -0.121, listOf(st1, st2))
        assertNotNull(nearest)
        assertEquals("1", nearest!!.first.id)
    }

    @Test
    fun `StationLineInferrer infers badges accurately across different regional lines`() {
        val sgStation = Station(
            id = "SG_MRT_NS1",
            name = "Jurong East",
            modes = listOf("mrt"),
            zone = null,
            lines = listOf(StationLineInfo("NSL", "North South Line", "tube"))
        )
        val sgBadges = StationLineInferrer.infer(sgStation)
        assertTrue(sgBadges.any { it.lineId == "NSL" && it.lineCode == "NS" })

        val jpStation = Station(
            id = "JP_METRO_G01",
            name = "Shibuya",
            modes = listOf("metro"),
            zone = null,
            lines = listOf(StationLineInfo("G", "Ginza Line", "tube"))
        )
        val jpBadges = StationLineInferrer.infer(jpStation)
        assertTrue(jpBadges.any { it.lineCode == "G" })
    }
}
