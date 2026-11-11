package com.androidfung.departureboard.roadmap

import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.androidfung.departureboard.ai.A2UiBlock
import com.androidfung.departureboard.ai.AiExecutionTier
import com.androidfung.departureboard.ai.AiModelType
import com.androidfung.departureboard.ai.AiQueryRouter
import com.androidfung.departureboard.ai.TransitAiAssistant
import com.androidfung.departureboard.data.db.cache.HistoricalPlatformObservationEntity
import com.androidfung.departureboard.data.db.cache.PlatformPredictionDao
import com.androidfung.departureboard.data.gtfs.GtfsCalendarDateEntity
import com.androidfung.departureboard.data.gtfs.GtfsCalendarEntity
import com.androidfung.departureboard.data.gtfs.GtfsDao
import com.androidfung.departureboard.data.gtfs.GtfsRouteEntity
import com.androidfung.departureboard.data.gtfs.GtfsScheduleResolver
import com.androidfung.departureboard.data.gtfs.GtfsStopEntity
import com.androidfung.departureboard.data.gtfs.GtfsStopTimeEntity
import com.androidfung.departureboard.data.gtfs.GtfsTripEntity
import com.androidfung.departureboard.data.gtfs.GtfsTunnelSeedHelper
import com.androidfung.departureboard.data.model.CallingPoint
import com.androidfung.departureboard.data.model.CoachInfo
import com.androidfung.departureboard.data.model.DataFreshness
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.PlatformInfo
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TrainFormation
import com.androidfung.departureboard.data.model.TrainInfo
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.model.au.AuDefaultStations
import com.androidfung.departureboard.data.model.au.AuStationDirectory
import com.androidfung.departureboard.data.model.gb.TflAdditionalProperty
import com.androidfung.departureboard.data.model.gb.TflBikePointPlace
import com.androidfung.departureboard.data.model.gb.TflBikePointPlacesResponse
import com.androidfung.departureboard.data.model.gb.TflLineStatusDetail
import com.androidfung.departureboard.data.model.gb.TflLineStatusItem
import com.androidfung.departureboard.data.model.gb.TflStopPointDisruption
import com.androidfung.departureboard.data.model.hk.HkLightRailPlatform
import com.androidfung.departureboard.data.model.hk.HkLightRailRouteArrival
import com.androidfung.departureboard.data.model.hk.HkLightRailScheduleResponse
import com.androidfung.departureboard.data.model.jp.JpDefaultStations
import com.androidfung.departureboard.data.model.jp.JpStationDirectory
import com.androidfung.departureboard.data.model.sg.SgStationDirectory
import com.androidfung.departureboard.data.network.hk.HkLightRailApiService
import com.androidfung.departureboard.data.repository.TransitRepositoryImpl
import com.androidfung.departureboard.data.repository.region.gb.GbRegionalTransitProvider
import com.androidfung.departureboard.data.repository.region.gb.TimetableScheduleResolver
import com.androidfung.departureboard.data.repository.region.gb.search.GbStationSearchEngine
import com.androidfung.departureboard.data.repository.region.hk.HkLightRailDepartureSource
import com.androidfung.departureboard.data.repository.region.sg.SgDepartureSource
import com.androidfung.departureboard.service.TrainTrackingService
import com.androidfung.departureboard.test.FakeBodsApiService
import com.androidfung.departureboard.test.FakeNrApiService
import com.androidfung.departureboard.test.FakeStationPreferencesDataSource
import com.androidfung.departureboard.test.FakeTflApiService
import com.androidfung.departureboard.test.FakeTransitRepository
import com.androidfung.departureboard.ui.dashboard.StationCardUiModel
import com.androidfung.departureboard.util.CarriageExitAdvisor
import com.androidfung.departureboard.util.PlatformHistoryPredictor
import com.androidfung.departureboard.util.TwinConcourseEvaluator
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoadmapThemesVerificationTest {

    private class InMemoryPlatformPredictionDao : PlatformPredictionDao() {
        private val rows = mutableListOf<HistoricalPlatformObservationEntity>()

        override suspend fun getObservationsInWindow(
            stationId: String,
            operatorId: String,
            destinationKey: String,
            minBucket: Int,
            maxBucket: Int
        ): List<HistoricalPlatformObservationEntity> = rows.filter {
            it.stationId == stationId &&
                it.operatorId == operatorId &&
                it.destinationKey == destinationKey &&
                it.scheduledMinuteBucket in minBucket..maxBucket
        }

        override suspend fun getObservationsForRoute(
            stationId: String,
            operatorId: String,
            destinationKey: String
        ): List<HistoricalPlatformObservationEntity> = rows.filter {
            it.stationId == stationId &&
                it.operatorId == operatorId &&
                it.destinationKey == destinationKey
        }

        override suspend fun getSingleObservation(
            stationId: String,
            operatorId: String,
            destinationKey: String,
            bucket: Int,
            platformCode: String
        ): HistoricalPlatformObservationEntity? = rows.firstOrNull {
            it.stationId == stationId &&
                it.operatorId == operatorId &&
                it.destinationKey == destinationKey &&
                it.scheduledMinuteBucket == bucket &&
                it.platformCode == platformCode
        }

        override suspend fun upsert(entity: HistoricalPlatformObservationEntity) {
            rows.removeAll {
                it.stationId == entity.stationId &&
                    it.operatorId == entity.operatorId &&
                    it.destinationKey == entity.destinationKey &&
                    it.scheduledMinuteBucket == entity.scheduledMinuteBucket &&
                    it.platformCode == entity.platformCode
            }
            rows.add(entity)
        }

        override suspend fun pruneStaleObservations(thresholdMillis: Long) {
            rows.removeAll { it.lastObservedEpochMillis < thresholdMillis }
        }
    }

    private fun sampleDeparture(
        id: String = "dep_1",
        stationId: String = "HUB_PAD",
        stationName: String = "London Paddington",
        lineId: String = "gwr",
        lineName: String = "Great Western Railway",
        destinationName: String = "Bristol Temple Meads",
        platformName: String = "Platform TBD",
        platformInfo: PlatformInfo? = PlatformInfo(code = "TBD", isConfirmed = false),
        timeToStationSeconds: Int = 480,
        scheduledInstant: Instant? = Instant.parse("2026-11-11T08:30:00Z"),
        expectedInstant: Instant? = Instant.parse("2026-11-11T08:30:00Z"),
        status: DepartureStatus = DepartureStatus.ON_TIME,
        modeName: String = "national-rail"
    ): Departure = Departure(
        id = id,
        stationId = stationId,
        stationName = stationName,
        lineId = lineId,
        lineName = lineName,
        platformName = platformName,
        destinationName = destinationName,
        towards = null,
        timeToStationSeconds = timeToStationSeconds,
        expectedArrivalIso = expectedInstant?.toString(),
        currentLocation = "At Reading",
        modeName = modeName,
        lineBadge = LineBadgeInfo(lineId, lineName, Color(0xFF0A493E)),
        status = status,
        platform = platformInfo,
        scheduledDepartureInstant = scheduledInstant,
        expectedDepartureInstant = expectedInstant
    )

    // --- Theme 1.1: Historical Platform Predictor ---
    @Test
    fun theme1_platformHistoryPredictor_learnsConfirmedPlatformsAndPreservesUnconfirmedTbdFlag() = runTest {
        val dao = InMemoryPlatformPredictionDao()
        val predictor = PlatformHistoryPredictor(dao)

        val confirmedDay1 = sampleDeparture(
            id = "service_day1",
            platformName = "Platform 4",
            platformInfo = PlatformInfo(code = "4", isConfirmed = true)
        )
        val confirmedDay2 = sampleDeparture(
            id = "service_day2",
            platformName = "Platform 4",
            platformInfo = PlatformInfo(code = "4", isConfirmed = true)
        )

        // Duplicate poll of service_day1 should only record once
        predictor.processLiveDepartures("HUB_PAD", listOf(confirmedDay1))
        predictor.processLiveDepartures("HUB_PAD", listOf(confirmedDay1))
        predictor.processLiveDepartures("HUB_PAD", listOf(confirmedDay2))

        val tbdToday = sampleDeparture(
            id = "service_today_tbd",
            platformName = "Platform TBD",
            platformInfo = PlatformInfo(code = "TBD", isConfirmed = false)
        )

        val enriched = predictor.processLiveDepartures("HUB_PAD", listOf(tbdToday)).single()
        assertEquals("Platform TBD", enriched.platformName)
        assertFalse("Must keep isConfirmed=false so live confirmation still triggers haptic alert", enriched.resolvedPlatform.isConfirmed)
        assertTrue(enriched.resolvedPlatform.isHistoricalExpected)
        assertEquals("4", enriched.resolvedPlatform.code)
        assertEquals(100, enriched.resolvedPlatform.expectedConfidencePercent)
    }

    // --- Theme 1.2: Underground Tunnel Mode (Offline GTFS Fallback) ---
    @Test
    fun theme1_undergroundTunnelMode_fallsBackToOfflineGtfsScheduleWithFreshnessTag() = runTest {
        val stopsList = mutableListOf<GtfsStopEntity>()
        val routesList = mutableListOf<GtfsRouteEntity>()
        val tripsList = mutableListOf<GtfsTripEntity>()
        val stopTimesList = mutableListOf<GtfsStopTimeEntity>()
        val calendarsList = mutableListOf<GtfsCalendarEntity>()
        val calendarDatesList = mutableListOf<GtfsCalendarDateEntity>()

        val fakeGtfsDao = object : GtfsDao {
            override suspend fun getStopById(stopId: String) = stopsList.firstOrNull { it.stopId.equals(stopId, ignoreCase = true) }
            override suspend fun getStopsByIds(stopIds: List<String>) = stopsList.filter { s -> stopIds.any { it.equals(s.stopId, ignoreCase = true) } }
            override suspend fun getChildStops(parentStationId: String) = stopsList.filter { it.parentStation == parentStationId }
            override suspend fun searchStopsByName(query: String, limit: Int) = stopsList.filter { it.stopName.contains(query, ignoreCase = true) }.take(limit)
            override suspend fun getRouteById(routeId: String) = routesList.firstOrNull { it.routeId.equals(routeId, ignoreCase = true) }
            override suspend fun getTripById(tripId: String) = tripsList.firstOrNull { it.tripId.equals(tripId, ignoreCase = true) }
            override suspend fun getCallingPointsForTrip(tripId: String) = stopTimesList.filter { it.tripId == tripId }.sortedBy { it.stopSequence }
            override suspend fun getUpcomingStopTimes(stopId: String, afterTimeSeconds: Int, limit: Int) =
                stopTimesList.filter { it.stopId == stopId && it.departureTimeSeconds >= afterTimeSeconds }.sortedBy { it.departureTimeSeconds }.take(limit)
            override suspend fun getUpcomingStopTimesForServices(stopIds: List<String>, serviceIds: List<String>, afterTimeSeconds: Int, limit: Int): List<GtfsStopTimeEntity> {
                val tripIds = tripsList.filter { it.serviceId in serviceIds }.map { it.tripId }.toSet()
                return stopTimesList.filter { it.stopId in stopIds && it.tripId in tripIds && it.departureTimeSeconds >= afterTimeSeconds }
                    .sortedBy { it.departureTimeSeconds }
                    .take(limit)
            }
            override suspend fun getAllCalendars() = calendarsList.toList()
            override suspend fun getCalendarDatesForDate(date: String) = calendarDatesList.filter { it.date == date }
            override suspend fun insertStops(stops: List<GtfsStopEntity>) { stopsList.addAll(stops) }
            override suspend fun insertRoutes(routes: List<GtfsRouteEntity>) { routesList.addAll(routes) }
            override suspend fun insertTrips(trips: List<GtfsTripEntity>) { tripsList.addAll(trips) }
            override suspend fun insertStopTimes(stopTimes: List<GtfsStopTimeEntity>) { stopTimesList.addAll(stopTimes) }
            override suspend fun insertCalendars(calendars: List<GtfsCalendarEntity>) { calendarsList.addAll(calendars) }
            override suspend fun insertCalendarDates(calendarDates: List<GtfsCalendarDateEntity>) { calendarDatesList.addAll(calendarDates) }
            override suspend fun clearStops() { stopsList.clear() }
            override suspend fun clearRoutes() { routesList.clear() }
            override suspend fun clearTrips() { tripsList.clear() }
            override suspend fun clearStopTimes() { stopTimesList.clear() }
            override suspend fun clearCalendars() { calendarsList.clear() }
            override suspend fun clearCalendarDates() { calendarDatesList.clear() }
        }

        GtfsTunnelSeedHelper.ensureCoreMetroSeeded(fakeGtfsDao)
        assertTrue(fakeGtfsDao.getAllCalendars().isNotEmpty())

        val offlineTfl = FakeTflApiService(shouldThrow = true)
        val resolver = GtfsScheduleResolver(fakeGtfsDao)
        val repo = TransitRepositoryImpl(
            apiService = offlineTfl,
            nrApiService = FakeNrApiService(shouldThrow = true),
            bodsApiService = FakeBodsApiService(),
            dataStore = FakeStationPreferencesDataSource(),
            gtfsDao = fakeGtfsDao,
            gtfsScheduleResolver = resolver
        )

        val result = repo.getDepartures("940GZZLUOXC", "Oxford Circus")
        assertTrue("Expected offline GTFS Tunnel Mode fallback to succeed", result.isSuccess)
        val deps = result.getOrThrow()
        assertTrue(deps.isNotEmpty())
        assertTrue(deps.all { it.dataFreshness == DataFreshness.OFFLINE_SCHEDULED })
    }

    // --- Theme 1.3: Walk-Adjusted Twin Concourse Race ---
    @Test
    fun theme1_twinConcourseEvaluator_deductsWalkTimeAndRejectsUncatchableTrain() {
        val stationA = Station(id = "940GZZLUKSX", name = "King's Cross", modes = listOf("tube"))
        val stationB = Station(id = "HUBSTP", name = "St Pancras International", modes = listOf("national-rail"))

        // Hub A is 400m away (~361s walk + gate buffer), train leaves in 120s -> TOO_CLOSE!
        val cardA = StationCardUiModel(
            station = stationA,
            departures = listOf(sampleDeparture(id = "ksx_1", stationId = stationA.id, timeToStationSeconds = 120)),
            isLoading = false,
            distanceMeters = 400.0
        )
        // Hub B is 120m away (~151s walk + gate buffer), train leaves in 300s -> MAKEABLE!
        val cardB = StationCardUiModel(
            station = stationB,
            departures = listOf(sampleDeparture(id = "stp_1", stationId = stationB.id, timeToStationSeconds = 300)),
            isLoading = false,
            distanceMeters = 120.0
        )

        val comparison = TwinConcourseEvaluator.compareHubs(cardA, cardB)
        assertEquals(TwinConcourseEvaluator.CatchabilityStatus.TOO_CLOSE, comparison.evaluatedA.single().catchability)
        assertEquals(TwinConcourseEvaluator.CatchabilityStatus.MAKEABLE, comparison.evaluatedB.single().catchability)
        assertEquals("Should pick catchable Hub B over uncatchable Hub A", stationB.id, comparison.winnerCard?.station?.id)
    }

    // --- Theme 2: Multi-Region Live Feeds, Coordinates & Shūden Guard ---
    @Test
    fun theme2_multiRegionFeedsAndCoordinates_hkLightRail_sgLta_auWaratah_jpShuden_gbSantander() = runTest {
        // 1. Coordinates populated across SG, AU, JP directories
        assertTrue(SgStationDirectory.ALL_STATIONS.all { val s = it.toStation(); s.lat != null && s.lon != null })
        assertTrue(AuStationDirectory.ALL_STATIONS.all { val s = it.toStation(); s.lat != null && s.lon != null })
        assertTrue(JpStationDirectory.ALL_STATIONS.all { val s = it.toStation(); s.lat != null && s.lon != null })

        // 2. Hong Kong Light Rail key-free live parser
        val fakeLrtApi = object : HkLightRailApiService {
            override suspend fun getLightRailSchedule(stationId: String) = HkLightRailScheduleResponse(
                status = 1,
                platformList = listOf(
                    HkLightRailPlatform(
                        platformId = 2,
                        routeList = listOf(
                            HkLightRailRouteArrival(
                                routeNo = "610",
                                trainLength = 2,
                                destEn = "Yuen Long",
                                destCh = "元朗",
                                timeEn = "3 mins"
                            )
                        )
                    )
                )
            )
        }
        val lrtSource = HkLightRailDepartureSource(fakeLrtApi)
        assertTrue(lrtSource.canHandle("HK_LRT_100", "Tuen Mun Ferry Pier", false))
        val lrtDeps = lrtSource.fetchDepartures("HK_LRT_100", "Tuen Mun Ferry Pier", false).getOrThrow()
        assertEquals(1, lrtDeps.size)
        assertEquals("Light Rail 610", lrtDeps.single().lineName)
        assertEquals(TransitMode.TRAM.id, lrtDeps.single().modeName)
        assertEquals(2, lrtDeps.single().formation?.coachCount)

        // 3. Singapore LTA DataMall load & deck mapping
        assertEquals("MANY_SEATS_AVAILABLE", SgDepartureSource.mapLtaLoadToOccupancy("SEA"))
        assertEquals("STANDING_ROOM_ONLY", SgDepartureSource.mapLtaLoadToOccupancy("SDA"))
        assertEquals("FULL", SgDepartureSource.mapLtaLoadToOccupancy("LSD"))
        assertEquals("Double-Decker", SgDepartureSource.mapDeckTypeLabel("DD"))

        // 4. Sydney 8-car Waratah vs 6-car Metro formation
        val waratah = AuDefaultStations.buildWaratahFormation(isMetro = false)
        val sydneyMetro = AuDefaultStations.buildWaratahFormation(isMetro = true)
        assertEquals(8, waratah.coachCount)
        assertTrue(waratah.coaches.first().isQuietCoach == true)
        assertEquals(6, sydneyMetro.coachCount)

        // 5. Tokyo Shūden (23:30–00:45 JST) window
        val tokyoZone = ZoneId.of("Asia/Tokyo")
        val shudenTime = ZonedDateTime.of(2026, 11, 11, 23, 45, 0, 0, tokyoZone)
        val middayTime = ZonedDateTime.of(2026, 11, 11, 13, 15, 0, 0, tokyoZone)
        assertTrue(JpDefaultStations.isShudenWindow(shudenTime))
        assertFalse(JpDefaultStations.isShudenWindow(middayTime))
        val shudenDeps = JpDefaultStations.getFallbackDepartures("JP_METRO_M08_JY17", "Shinjuku", shudenTime.toInstant())
        assertTrue("Expected at least one departure marked isLastTrain during Shūden window", shudenDeps.any { it.isLastTrain })

        // 6. GB Santander Cycles & StopPoint step-free lift outage parsing
        val fakeTfl = FakeTflApiService(
            bikePointsResult = TflBikePointPlacesResponse(
                places = listOf(
                    TflBikePointPlace(
                        id = "BikePoints_101",
                        commonName = "Soho Square, Soho",
                        distance = 140.0,
                        additionalProperties = listOf(
                            TflAdditionalProperty(key = "NbBikes", value = "9"),
                            TflAdditionalProperty(key = "NbEBikes", value = "3"),
                            TflAdditionalProperty(key = "NbEmptyDocks", value = "12")
                        )
                    )
                )
            ),
            stopPointDisruptionsResult = listOf(
                TflStopPointDisruption(
                    atcoCode = "940GZZLUOXC",
                    commonName = "Oxford Circus",
                    type = "Information",
                    description = "No step-free access due to faulty lift between concourse and Victoria line."
                )
            )
        )
        val gbProvider = GbRegionalTransitProvider(
            apiService = fakeTfl,
            nrApiService = FakeNrApiService(),
            bodsApiService = FakeBodsApiService(),
            stationSearchEngine = GbStationSearchEngine(fakeTfl, FakeBodsApiService(), null),
            timetableScheduleResolver = TimetableScheduleResolver(fakeTfl)
        )
        val enrichedOxford = gbProvider.getStationById("940GZZLUOXC")
        assertNotNull(enrichedOxford)
        assertEquals(1, enrichedOxford!!.micromobilityHubs.size)
        assertEquals(9, enrichedOxford.micromobilityHubs.single().totalBikesAvailable)
        assertEquals(3, enrichedOxford.micromobilityHubs.single().totalEbikesAvailable)

        val stepFreeAlerts = gbProvider.getStationDisruptionAlerts("940GZZLUOXC")
        assertEquals(1, stepFreeAlerts.size)
        assertTrue(stepFreeAlerts.single().descriptionText!!.contains("faulty lift"))
    }

    // --- Theme 3: Interactive Live Updates Notification Actions & Alight Stop Cycling ---
    @Test
    fun theme3_trainTrackingService_cyclesAlightStopAndAttachesTrampolineCompliantActions() {
        val points = listOf(
            CallingPoint(stationName = "Oxford Circus", isCurrentStation = true),
            CallingPoint(stationName = "Green Park"),
            CallingPoint(stationName = "Victoria"),
            CallingPoint(stationName = "Brixton", isDestination = true)
        )

        val first = TrainTrackingService.computeNextAlightStop(points, null)
        assertEquals("Green Park", first)
        val second = TrainTrackingService.computeNextAlightStop(points, first)
        assertEquals("Victoria", second)
        val third = TrainTrackingService.computeNextAlightStop(points, second)
        assertEquals("Brixton", third)
        val wrapped = TrainTrackingService.computeNextAlightStop(points, third)
        assertNull("After terminus, cycling wraps cleanly to null", wrapped)

        TrainTrackingService.setTargetAlightStop("Victoria")
        try {
            val service = Robolectric.setupService(TrainTrackingService::class.java)
            val notification = service.buildLiveNotification(
                departureId = "dep_vic_99",
                stationId = "940GZZLUOXC",
                stationName = "Oxford Circus",
                destination = "Brixton",
                lineName = "Victoria",
                platform = "Platform 4",
                timeToStationSeconds = 240,
                currentLocation = "At Warren Street",
                isBus = false,
                modeName = "tube",
                lineColorArgb = Color(0xFF0098D4).toArgb(),
                coachCount = 8,
                currentStopsAway = 2,
                baselineStops = 4,
                baselineSeconds = 300
            )

            val actions = notification.actions.orEmpty()
            assertEquals("Expected +1 Stop, Share ETA, and Stop Tracking actions", 3, actions.size)
            val sharePendingIntent = actions[1].actionIntent
            val shareShadow = shadowOf(sharePendingIntent)
            assertEquals(Intent.ACTION_CHOOSER, shareShadow.savedIntent.action)
            val innerSend = shareShadow.savedIntent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            assertEquals(Intent.ACTION_SEND, innerSend?.action)
        } finally {
            TrainTrackingService.setTargetAlightStop(null)
        }
    }

    // --- Theme 4: Precision Wayfinding ("Best Carriage to Board") ---
    @Test
    fun theme4_carriageExitAdvisor_alignsBufferTerminiInterchangesAndCrowding() {
        val eightCar = TrainFormation(
            coachCount = 8,
            coaches = (1..8).map { CoachInfo(coachNumber = "$it", occupancyStatus = "FEW_SEATS_AVAILABLE") }
        )

        val paddingtonRec = CarriageExitAdvisor.recommend(eightCar, "London Paddington")
        assertNotNull(paddingtonRec)
        assertEquals(CarriageExitAdvisor.ExitPosition.FRONT, paddingtonRec!!.position)
        assertEquals(setOf("1", "2"), paddingtonRec.recommendedCoachLabels)

        val admiraltyRec = CarriageExitAdvisor.recommend(eightCar, "Admiralty (金鐘)")
        assertNotNull(admiraltyRec)
        assertEquals(CarriageExitAdvisor.ExitPosition.MIDDLE, admiraltyRec!!.position)
        assertEquals(setOf("4", "5"), admiraltyRec.recommendedCoachLabels)

        // Fallback to live carriage weight sensors for unknown station
        val mixedOccupancy = TrainFormation(
            coachCount = 6,
            coaches = listOf(
                CoachInfo(coachNumber = "1", occupancyStatus = "FULL"),
                CoachInfo(coachNumber = "2", occupancyStatus = "STANDING_ROOM_ONLY"),
                CoachInfo(coachNumber = "3", occupancyStatus = "STANDING_ROOM_ONLY"),
                CoachInfo(coachNumber = "4", occupancyStatus = "FEW_SEATS_AVAILABLE"),
                CoachInfo(coachNumber = "5", occupancyStatus = "EMPTY"),
                CoachInfo(coachNumber = "6", occupancyStatus = "MANY_SEATS_AVAILABLE")
            )
        )
        val sensorRec = CarriageExitAdvisor.recommend(mixedOccupancy, "Suburban Halt")
        assertNotNull(sensorRec)
        assertEquals(setOf("5", "6"), sensorRec!!.recommendedCoachLabels)
    }

    // --- Theme 5: Zero-Download System AICore & Interactive A2UI Blocks ---
    @Test
    fun theme5_systemAiCoreRoutingAndInteractiveDisruptionAndDelayRepayBlocks() = runTest {
        val appContext = RuntimeEnvironment.getApplication()
        val routerWithAiCore = AiQueryRouter(
            context = appContext,
            dynamicAiProvider = null,
            isSystemAiCoreAvailable = { true }
        )
        assertEquals(
            AiExecutionTier.SYSTEM_AICORE,
            routerWithAiCore.routeQuery("Explain why the train is delayed", null, AiModelType.LOCAL)
        )

        val oxford = Station(id = "940GZZLUOXC", name = "Oxford Circus", modes = listOf("tube"))
        val unaffectedVictoria = sampleDeparture(
            id = "vic_1",
            stationId = oxford.id,
            stationName = oxford.name,
            lineId = "victoria",
            lineName = "Victoria",
            destinationName = "Brixton",
            platformName = "Platform 3",
            platformInfo = PlatformInfo(code = "3", isConfirmed = true),
            modeName = "tube"
        )
        val delayedGwr = sampleDeparture(
            id = "gwr_late",
            stationId = "HUB_PAD",
            stationName = "London Paddington",
            lineId = "gwr",
            lineName = "Great Western Railway",
            destinationName = "Reading",
            platformName = "Platform 5",
            platformInfo = PlatformInfo(code = "5", isConfirmed = true),
            scheduledInstant = Instant.parse("2026-11-11T17:00:00Z"),
            expectedInstant = Instant.parse("2026-11-11T17:28:00Z"), // +28 min late -> Delay Repay eligible!
            status = DepartureStatus.DELAYED,
            modeName = "national-rail"
        )

        val repo = FakeTransitRepository(
            departuresResult = { stationId, _ ->
                if (stationId == oxford.id) Result.success(listOf(unaffectedVictoria))
                else Result.success(listOf(delayedGwr))
            },
            lineStatuses = mapOf(
                "central" to TflLineStatusItem(
                    id = "central",
                    name = "Central line",
                    modeName = "tube",
                    lineStatuses = listOf(
                        TflLineStatusDetail(
                            statusSeverity = 4,
                            statusSeverityDescription = "Severe Delays",
                            reason = "Signal failure at Holborn"
                        )
                    )
                )
            )
        )

        val assistant = TransitAiAssistant(repo)

        // 1. Disrupted status query emits AlternativeRouteCard with unaffected Victoria departure
        val statusRes = assistant.answerQuery("Central line status", savedStations = listOf(oxford))
        val altCard = statusRes.a2uiResponse?.blocks?.filterIsInstance<A2UiBlock.AlternativeRouteCard>()?.singleOrNull()
        assertNotNull("Expected AlternativeRouteCard when Central line has Severe Delays", altCard)
        assertEquals("Central line", altCard!!.disruptedLineName)
        assertEquals(1, altCard.alternativeDepartures.size)
        assertEquals("Victoria", altCard.alternativeDepartures.single().lineName)

        // 2. Delay Repay query emits DelayRepayClaimBlock with GWR claim URL
        val paddington = Station(id = "HUB_PAD", name = "London Paddington", modes = listOf("national-rail"))
        val repayRes = assistant.answerQuery("Can I claim Delay Repay compensation?", savedStations = listOf(paddington))
        val claimBlock = repayRes.a2uiResponse?.blocks?.filterIsInstance<A2UiBlock.DelayRepayClaimBlock>()?.singleOrNull()
        assertNotNull("Expected DelayRepayClaimBlock for +28m GWR delay", claimBlock)
        assertEquals(28, claimBlock!!.delayMinutes)
        assertTrue(claimBlock.claimUrl.contains("gwr.com"))
    }
}
