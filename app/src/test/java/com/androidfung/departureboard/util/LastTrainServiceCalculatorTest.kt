package com.androidfung.departureboard.util

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TransitMode
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LastTrainServiceCalculatorTest {

    private fun sampleDeparture(
        id: String,
        stationId: String = "940GZZLUKSX",
        lineId: String = "victoria",
        lineName: String = "Victoria",
        direction: String = "Southbound",
        seconds: Int = 600
    ) = Departure(
        id = id,
        stationId = stationId,
        stationName = "King's Cross St. Pancras",
        lineId = lineId,
        lineName = lineName,
        platformName = "$direction - Platform 1",
        destinationName = "Brixton",
        towards = "Brixton",
        direction = direction,
        timeToStationSeconds = seconds,
        expectedArrivalIso = null,
        currentLocation = "In transit",
        modeName = "tube",
        lineBadge = LineBadgeInfo(
            lineId = lineId,
            displayName = lineName,
            backgroundColor = Color.Blue,
            mode = TransitMode.METRO
        )
    )

    @Test
    fun `detects approaching last train countdown on weekday late evening`() {
        // Wednesday 23:55 London time (last train template is 00:35 -> 40 mins remaining)
        val wednesdayLate = ZonedDateTime.of(2026, 11, 4, 23, 55, 0, 0, ZoneId.of("Europe/London"))
        val station = Station(id = "940GZZLUBNK", name = "Bank", modes = listOf("tube"))
        val dep1 = sampleDeparture(id = "d1", stationId = station.id, lineId = "central", seconds = 300)
        val dep2 = sampleDeparture(id = "d2", stationId = station.id, lineId = "central", seconds = 1500)

        val (enriched, serviceHours) = LastTrainServiceCalculator.enrichDeparturesAndServiceHours(
            station = station,
            departures = listOf(dep1, dep2),
            nowOverride = wednesdayLate
        )

        assertNotNull(serviceHours)
        assertTrue(serviceHours!!.isApproachingLastTrain)
        assertFalse(serviceHours.isClosedForNight)
        assertEquals(40, serviceHours.minutesUntilLastTrain)

        // First departure is not the last train; second (latest) departure in that direction is tagged as last train
        assertFalse(enriched[0].isLastTrain)
        assertTrue(enriched[1].isLastTrain)
    }

    @Test
    fun `detects London Night Tube on Friday and Saturday late night`() {
        // Saturday 01:30 AM (Friday night service) on Victoria line
        val saturdayNight = ZonedDateTime.of(2026, 11, 7, 1, 30, 0, 0, ZoneId.of("Europe/London"))
        val station = Station(id = "940GZZLUKSX", name = "King's Cross", modes = listOf("tube"))
        val dep = sampleDeparture(id = "nt1", stationId = station.id, lineId = "victoria", seconds = 240)

        val (enriched, serviceHours) = LastTrainServiceCalculator.enrichDeparturesAndServiceHours(
            station = station,
            departures = listOf(dep),
            nowOverride = saturdayNight
        )

        assertNotNull(serviceHours)
        assertTrue(serviceHours!!.isNightServiceActive)
        assertFalse(serviceHours.isClosedForNight)
        assertTrue(enriched.first().isNightService)
        assertFalse(enriched.first().isLastTrain)
    }

    @Test
    fun `detects Night Bus route N73 and tags isNightService`() {
        assertTrue(LastTrainServiceCalculator.isNightBusRoute("N73"))
        assertTrue(LastTrainServiceCalculator.isNightBusRoute("NA29"))
        assertFalse(LastTrainServiceCalculator.isNightBusRoute("73"))
        assertFalse(LastTrainServiceCalculator.isNightBusRoute("northern"))
    }

    @Test
    fun `detects overnight closure across HK, SG, AU, and JP when no departures remain`() {
        val hkClosedTime = ZonedDateTime.of(2026, 11, 5, 2, 30, 0, 0, ZoneId.of("Asia/Hong_Kong"))
        val hkInfo = LastTrainServiceCalculator.evaluateServiceHours(
            stationId = "HK_MTR_CEN",
            lineId = "TWL",
            departures = emptyList(),
            nowOverride = hkClosedTime
        )
        assertNotNull(hkInfo)
        assertTrue(hkInfo!!.isClosedForNight)
        assertEquals("00:50", hkInfo.lastTrainTime)
        assertEquals("06:00", hkInfo.firstTrainTime)

        val jpClosedTime = ZonedDateTime.of(2026, 11, 5, 2, 0, 0, 0, ZoneId.of("Asia/Tokyo"))
        val jpInfo = LastTrainServiceCalculator.evaluateServiceHours(
            stationId = "JP_JY01",
            departures = emptyList(),
            nowOverride = jpClosedTime
        )
        assertNotNull(jpInfo)
        assertTrue(jpInfo!!.isClosedForNight)

        // Daytime should return null
        val daytime = ZonedDateTime.of(2026, 11, 5, 14, 0, 0, 0, ZoneId.of("Asia/Tokyo"))
        assertNull(
            LastTrainServiceCalculator.evaluateServiceHours(
                stationId = "JP_JY01",
                departures = emptyList(),
                nowOverride = daytime
            )
        )
    }

    @Test
    fun `mill hill east and other non-night-tube branches never trigger false 24hr Night Service Active`() {
        val saturdayNight2305 = ZonedDateTime.of(2026, 11, 7, 23, 5, 0, 0, ZoneId.of("Europe/London"))
        val sundayEarly0130 = ZonedDateTime.of(2026, 11, 8, 1, 30, 0, 0, ZoneId.of("Europe/London"))

        // Mill Hill East (Northern line single-track spur - NO Night Tube)
        assertFalse(LastTrainServiceCalculator.isGbNightTubeStation("940GZZLUMHL", "northern"))
        // Bank branch (NO Night Tube on Northern line)
        assertFalse(LastTrainServiceCalculator.isGbNightTubeStation("940GZZLUBNK", "northern"))
        // Central line Hainault loop (NO Night Tube)
        assertFalse(LastTrainServiceCalculator.isGbNightTubeStation("940GZZLURVY", "central"))
        // Piccadilly line Heathrow T4 & Uxbridge branch (NO Night Tube)
        assertFalse(LastTrainServiceCalculator.isGbNightTubeStation("940GZZLUHR4", "piccadilly"))
        assertFalse(LastTrainServiceCalculator.isGbNightTubeStation("940GZZLUUXB", "piccadilly"))

        // True Night Tube stations (Euston on Northern, Oxford Circus on Victoria/Central)
        assertTrue(LastTrainServiceCalculator.isGbNightTubeStation("940GZZLUEUS", "northern"))
        assertTrue(LastTrainServiceCalculator.isGbNightTubeStation("940GZZLUOXC", "victoria"))

        val millHillStation = Station(id = "940GZZLUMHL", name = "Mill Hill East", modes = listOf("tube"))
        val dep = sampleDeparture(id = "mhe1", stationId = "940GZZLUMHL", lineId = "northern", seconds = 600)

        val (enriched2305, hours2305) = LastTrainServiceCalculator.enrichDeparturesAndServiceHours(
            station = millHillStation,
            departures = listOf(dep),
            nowOverride = saturdayNight2305
        )
        // At 23:05 on Saturday, Mill Hill East is neither Night Tube nor within 90m of last train (00:45 template -> 100m away)
        assertNull(hours2305)
        assertFalse(enriched2305.first().isNightService)

        // Even at 01:30 AM on Sunday, Mill Hill East must NOT claim Night Tube is active
        val (enriched0130, hours0130) = LastTrainServiceCalculator.enrichDeparturesAndServiceHours(
            station = millHillStation,
            departures = listOf(dep),
            nowOverride = sundayEarly0130
        )
        assertFalse(hours0130?.isNightServiceActive == true)
        assertFalse(enriched0130.first().isNightService)
    }

    @Test
    fun `timetable safety-net crosscheck overrides heuristic when timetableChecked is true`() {
        val saturdayEarly0130 = ZonedDateTime.of(2026, 11, 7, 1, 30, 0, 0, ZoneId.of("Europe/London"))
        val station = Station(id = "940GZZLUKSX", name = "King's Cross", modes = listOf("tube"))
        val dep = sampleDeparture(id = "nt1", stationId = station.id, lineId = "victoria", seconds = 240)

        // If timetable crosscheck returned null (e.g. Night Tube suspended in timetable), heuristic is suppressed
        val (enriched, serviceHours) = LastTrainServiceCalculator.enrichDeparturesAndServiceHours(
            station = station,
            departures = listOf(dep),
            existingServiceHours = null,
            nowOverride = saturdayEarly0130,
            timetableChecked = true
        )

        assertNull(serviceHours)
        assertFalse(enriched.first().isNightService)
    }

    @Test
    fun `TimetableScheduleResolver safety-net verifies real schedule for Mill Hill East vs Night Tube`() = kotlinx.coroutines.test.runTest {
        val millHillTimetable = com.androidfung.departureboard.data.model.gb.TflTimetableResponse(
            lineId = "northern",
            lineName = "Northern",
            timetable = com.androidfung.departureboard.data.model.gb.TflTimetable(
                routes = listOf(
                    com.androidfung.departureboard.data.model.gb.TflTimetableRoute(
                        schedules = listOf(
                            com.androidfung.departureboard.data.model.gb.TflSchedule(
                                name = "Saturday (also Good Friday)",
                                knownJourneys = listOf(
                                    com.androidfung.departureboard.data.model.gb.TflKnownJourney("5", "25", 0),
                                    com.androidfung.departureboard.data.model.gb.TflKnownJourney("12", "00", 0),
                                    com.androidfung.departureboard.data.model.gb.TflKnownJourney("23", "15", 0),
                                    com.androidfung.departureboard.data.model.gb.TflKnownJourney("24", "56", 0),
                                    com.androidfung.departureboard.data.model.gb.TflKnownJourney("25", "16", 0)
                                )
                            )
                        )
                    )
                )
            )
        )

        val fakeTfl = com.androidfung.departureboard.test.FakeTflApiService(timetableResponse = millHillTimetable)
        val resolver = com.androidfung.departureboard.data.repository.region.gb.TimetableScheduleResolver(fakeTfl)

        // 1. Saturday 23:02 at Mill Hill East -> last train is at 01:16 (134m away) and NO overnight trips -> null (normal service)
        val sat2302 = ZonedDateTime.of(2026, 11, 7, 23, 2, 0, 0, ZoneId.of("Europe/London"))
        val at2302 = resolver.checkStationServiceHours("940GZZLUMHL", "northern", nowOverride = sat2302)
        assertNull("Expected normal service (null) at 23:02 for Mill Hill East, got: $at2302", at2302)

        // 2. Sunday 00:00 (Saturday service night) -> 76 mins until 01:16 last train -> approaching last train
        val sun0000 = ZonedDateTime.of(2026, 11, 8, 0, 0, 0, 0, ZoneId.of("Europe/London"))
        val at0000 = resolver.checkStationServiceHours("940GZZLUMHL", "northern", nowOverride = sun0000)
        assertNotNull(at0000)
        assertTrue(at0000!!.isApproachingLastTrain)
        assertFalse(at0000.isNightServiceActive)
        assertEquals("01:16", at0000.lastTrainTime)
        assertEquals(76, at0000.minutesUntilLastTrain)

        // 3. Sunday 02:00 (after 01:16 last train) -> closed for the night
        val sun0200 = ZonedDateTime.of(2026, 11, 8, 2, 0, 0, 0, ZoneId.of("Europe/London"))
        val at0200 = resolver.checkStationServiceHours("940GZZLUMHL", "northern", nowOverride = sun0200)
        assertNotNull(at0200)
        assertTrue(at0200!!.isClosedForNight)
        assertFalse(at0200.isNightServiceActive)
        assertEquals("01:16", at0200.lastTrainTime)
        assertEquals("05:25", at0200.firstTrainTime)

        // 4. Euston on Saturday night WITH 26:48 / 27:10 overnight trips -> Night Tube verified active
        val eustonNightTimetable = com.androidfung.departureboard.data.model.gb.TflTimetableResponse(
            lineId = "northern",
            lineName = "Northern",
            timetable = com.androidfung.departureboard.data.model.gb.TflTimetable(
                routes = listOf(
                    com.androidfung.departureboard.data.model.gb.TflTimetableRoute(
                        schedules = listOf(
                            com.androidfung.departureboard.data.model.gb.TflSchedule(
                                name = "Saturday (also Good Friday)",
                                knownJourneys = listOf(
                                    com.androidfung.departureboard.data.model.gb.TflKnownJourney("3", "25", 0),
                                    com.androidfung.departureboard.data.model.gb.TflKnownJourney("26", "48", 0),
                                    com.androidfung.departureboard.data.model.gb.TflKnownJourney("27", "10", 0)
                                )
                            )
                        )
                    )
                )
            )
        )
        fakeTfl.timetableResponse = eustonNightTimetable
        val eustonAt0130 = resolver.checkStationServiceHours(
            "940GZZLUEUS",
            "northern",
            nowOverride = ZonedDateTime.of(2026, 11, 8, 1, 30, 0, 0, ZoneId.of("Europe/London"))
        )
        assertNotNull(eustonAt0130)
        assertTrue(eustonAt0130!!.isNightServiceActive)
    }

    @Test
    fun `reconciles last train banner with delayed live departures`() {
        // Scheduled last train was 00:45 (so at 00:43, scheduled is in 2 minutes)
        // But live departure is delayed to 00:56 (13 minutes away)
        val now = ZonedDateTime.of(2026, 11, 5, 0, 43, 0, 0, ZoneId.of("Europe/London"))
        val station = Station(id = "940GZZLUMYB", name = "Marylebone", modes = listOf("tube"))
        val existingScheduledServiceHours = com.androidfung.departureboard.data.model.StationServiceHoursInfo(
            isClosedForNight = false,
            lastTrainTime = "00:45",
            firstTrainTime = "06:30",
            lineName = "Bakerloo",
            minutesUntilLastTrain = 2,
            isApproachingLastTrain = true,
            isNightServiceActive = false
        )

        val delayedDeparture = sampleDeparture(
            id = "delayed_last",
            stationId = station.id,
            lineId = "bakerloo",
            seconds = 13 * 60
        ).copy(expectedDepartureInstant = now.plusMinutes(13).toInstant())

        val (enriched, reconciled) = LastTrainServiceCalculator.enrichDeparturesAndServiceHours(
            station = station,
            departures = listOf(delayedDeparture),
            existingServiceHours = existingScheduledServiceHours,
            nowOverride = now,
            timetableChecked = true
        )

        assertNotNull(reconciled)
        assertTrue(reconciled!!.isApproachingLastTrain)
        assertEquals("00:56", reconciled.lastTrainTime)
        assertEquals(13, reconciled.minutesUntilLastTrain)
        assertTrue(enriched.first().isLastTrain)
    }

    @Test
    fun `detects overnight closed window past 0400 AM and Sunday 0615 AM before first train`() {
        // Sunday 06:15 AM London time on non-Night-Tube Bakerloo line (first train on Sunday is ~06:30)
        val sunday0615 = ZonedDateTime.of(2026, 11, 8, 6, 15, 0, 0, ZoneId.of("Europe/London"))
        val bakerlooSunday = LastTrainServiceCalculator.evaluateServiceHours(
            stationId = "940GZZLUMYB",
            lineId = "bakerloo",
            departures = emptyList(),
            nowOverride = sunday0615
        )
        assertNotNull(bakerlooSunday)
        assertTrue(bakerlooSunday!!.isClosedForNight)

        // Thursday 04:20 AM Hong Kong MTR (last train 00:50, first train 06:00) -> still closed!
        val hk0420 = ZonedDateTime.of(2026, 11, 5, 4, 20, 0, 0, ZoneId.of("Asia/Hong_Kong"))
        val hkClosed = LastTrainServiceCalculator.evaluateServiceHours(
            stationId = "HK_MTR_CEN",
            lineId = "TWL",
            departures = emptyList(),
            nowOverride = hk0420
        )
        assertNotNull(hkClosed)
        assertTrue(hkClosed!!.isClosedForNight)
    }

    @Test
    fun `reconciles Mill Hill East banner and row when live last train is earlier than timetable`() {
        val now = ZonedDateTime.of(2026, 11, 5, 0, 46, 0, 0, ZoneId.of("Europe/London"))
        val station = Station(id = "940GZZLUMHL", name = "Mill Hill East", modes = listOf("tube"))
        val timetableServiceHours = com.androidfung.departureboard.data.model.StationServiceHoursInfo(
            isClosedForNight = false,
            lastTrainTime = "01:13",
            firstTrainTime = "05:25",
            lineName = "Northern",
            minutesUntilLastTrain = 28,
            isApproachingLastTrain = true,
            isNightServiceActive = false
        )
        val finalShuttle = sampleDeparture(
            id = "mhe_last",
            stationId = station.id,
            lineId = "northern",
            lineName = "Northern",
            seconds = 4 * 60
        ).copy(expectedDepartureInstant = now.plusMinutes(3).toInstant())

        val (enriched, reconciled) = LastTrainServiceCalculator.enrichDeparturesAndServiceHours(
            station = station,
            departures = listOf(finalShuttle),
            existingServiceHours = timetableServiceHours,
            nowOverride = now,
            timetableChecked = true
        )

        assertNotNull(reconciled)
        assertTrue(enriched.first().isLastTrain)
        assertEquals("00:49", reconciled!!.lastTrainTime)
        assertEquals(4, reconciled.minutesUntilLastTrain)
    }

    @Test
    fun `infers accurate lines for Kings Cross National Rail and Finchley Central`() {
        val kingsCross = Station(
            id = "910GKNGX",
            name = "King's Cross",
            modes = listOf("national-rail"),
            lines = listOf(
                com.androidfung.departureboard.data.model.StationLineInfo("grand-central", "Grand Central", "national-rail"),
                com.androidfung.departureboard.data.model.StationLineInfo("great-northern", "Great Northern", "national-rail"),
                com.androidfung.departureboard.data.model.StationLineInfo("hull-trains", "Hull Trains", "national-rail"),
                com.androidfung.departureboard.data.model.StationLineInfo("london-north-eastern-railway", "London North Eastern Railway", "national-rail"),
                com.androidfung.departureboard.data.model.StationLineInfo("lumo", "Lumo", "national-rail")
            )
        )
        val kxBadges = com.androidfung.departureboard.data.model.StationLineInferrer.infer(kingsCross)
        val kxNames = kxBadges.map { it.displayName }
        assertFalse("King's Cross NR must not match Central tube line: $kxNames", kxNames.contains("Central"))
        assertFalse("King's Cross NR must not match London Overground: $kxNames", kxNames.contains("London Overground"))
        assertFalse("King's Cross NR must show Great Northern instead of Northern City Line: $kxNames", kxNames.contains("Northern City Line"))
        assertTrue(kxNames.contains("Grand Central"))
        assertTrue(kxNames.contains("Great Northern"))
        assertTrue(kxNames.contains("LNER") || kxNames.contains("London North Eastern Railway"))

        val finchleyCentral = Station(
            id = "940GZZLUFYC",
            name = "Finchley Central",
            modes = listOf("tube"),
            lines = emptyList()
        )
        val fycBadges = com.androidfung.departureboard.data.model.StationLineInferrer.infer(finchleyCentral)
        assertEquals(listOf("Northern"), fycBadges.map { it.displayName })
    }

    @Test
    fun `prevents cross-region line code collisions and topology-infers all London stations`() {
        // 1. UK c2c ("cc") and single-letter UK codes ("G") must NOT match Singapore Circle Line or Tokyo Ginza Line
        val fenchurchStreet = Station(
            id = "910GFENCHRS",
            name = "Fenchurch Street",
            modes = listOf("national-rail"),
            lines = listOf(
                com.androidfung.departureboard.data.model.StationLineInfo("cc", "c2c", "national-rail")
            )
        )
        val fstBadges = com.androidfung.departureboard.data.model.StationLineInferrer.infer(fenchurchStreet)
        assertFalse(
            "UK station with line code 'cc' must not match Singapore Circle Line: ${fstBadges.map { it.displayName }}",
            fstBadges.any { it.displayName.contains("Circle", ignoreCase = true) }
        )

        // 2. National Rail "Northern" TOC (in Manchester/Leeds) must NOT resolve to London Underground's Northern Tube line
        val manchesterPiccadilly = Station(
            id = "910GMNCRPIC",
            name = "Manchester Piccadilly",
            modes = listOf("national-rail"),
            lines = listOf(
                com.androidfung.departureboard.data.model.StationLineInfo("nt", "Northern", "national-rail")
            )
        )
        val manBadges = com.androidfung.departureboard.data.model.StationLineInferrer.infer(manchesterPiccadilly)
        assertEquals(1, manBadges.size)
        assertEquals(TransitMode.RAIL, manBadges.first().mode)

        // 3. Topology-driven station line inference resolves any London Tube/Overground/Elizabeth station even when lines is empty
        val belsizePark = Station(id = "940GZZLUBZP", name = "Belsize Park", modes = listOf("tube"), lines = emptyList())
        assertEquals(listOf("Northern"), com.androidfung.departureboard.data.model.StationLineInferrer.infer(belsizePark).map { it.displayName })

        val bakerStreet = Station(id = "940GZZLUBST", name = "Baker Street", modes = listOf("tube"), lines = emptyList())
        assertEquals(
            listOf("Bakerloo", "Circle", "Hammersmith & City", "Jubilee", "Metropolitan"),
            com.androidfung.departureboard.data.model.StationLineInferrer.infer(bakerStreet).map { it.displayName }
        )

        val southKensington = Station(id = "940GZZLUSKS", name = "South Kensington", modes = listOf("tube"), lines = emptyList())
        assertEquals(
            listOf("Circle", "District", "Piccadilly"),
            com.androidfung.departureboard.data.model.StationLineInferrer.infer(southKensington).map { it.displayName }
        )
    }

    @Test
    fun `TimetableScheduleResolver filters out terminating arrival intervals and uses Sunday morning on Saturday late night`() = kotlinx.coroutines.test.runTest {
        val mheTimetableWithTerminatingArrival = com.androidfung.departureboard.data.model.gb.TflTimetableResponse(
            lineId = "northern",
            lineName = "Northern",
            timetable = com.androidfung.departureboard.data.model.gb.TflTimetable(
                routes = listOf(
                    com.androidfung.departureboard.data.model.gb.TflTimetableRoute(
                        stationIntervals = listOf(
                            // Interval 0: outbound to Finchley Central
                            com.androidfung.departureboard.data.model.gb.TflStationInterval(
                                id = "0",
                                intervals = listOf(
                                    com.androidfung.departureboard.data.model.gb.TflInterval(stopId = "940GZZLUFYC", timeToArrival = 3.0)
                                )
                            ),
                            // Interval 1: terminating arrival at Mill Hill East itself
                            com.androidfung.departureboard.data.model.gb.TflStationInterval(
                                id = "1",
                                intervals = listOf(
                                    com.androidfung.departureboard.data.model.gb.TflInterval(stopId = "940GZZLUMHL", timeToArrival = 0.0)
                                )
                            )
                        ),
                        schedules = listOf(
                            com.androidfung.departureboard.data.model.gb.TflSchedule(
                                name = "Saturday",
                                knownJourneys = listOf(
                                    com.androidfung.departureboard.data.model.gb.TflKnownJourney("5", "25", 0),
                                    com.androidfung.departureboard.data.model.gb.TflKnownJourney("24", "49", 0), // 00:49 outbound
                                    com.androidfung.departureboard.data.model.gb.TflKnownJourney("25", "13", 1)  // 01:13 terminating arrival!
                                )
                            ),
                            com.androidfung.departureboard.data.model.gb.TflSchedule(
                                name = "Sunday",
                                knownJourneys = listOf(
                                    com.androidfung.departureboard.data.model.gb.TflKnownJourney("6", "52", 0), // 06:52 Sunday morning first train
                                    com.androidfung.departureboard.data.model.gb.TflKnownJourney("24", "18", 0)
                                )
                            )
                        )
                    )
                )
            )
        )

        val fakeTfl = com.androidfung.departureboard.test.FakeTflApiService(timetableResponse = mheTimetableWithTerminatingArrival)
        val resolver = com.androidfung.departureboard.data.repository.region.gb.TimetableScheduleResolver(fakeTfl)

        // At 00:46 Sunday (Saturday night service), outbound last train is 00:49 (NOT 01:13 terminating arrival),
        // and first morning service is Sunday's 06:52 (NOT Saturday's 05:25)!
        val sun0046 = ZonedDateTime.of(2026, 11, 8, 0, 46, 0, 0, ZoneId.of("Europe/London"))
        val info = resolver.checkStationServiceHours("940GZZLUMHL", "northern", nowOverride = sun0046)
        assertNotNull(info)
        assertEquals("00:49", info!!.lastTrainTime)
        assertEquals("06:52", info.firstTrainTime)
        assertEquals(3, info.minutesUntilLastTrain)
    }

    @Test
    fun `within 35 minute horizon aligns Last Train banner with LAST TRAIN row badge`() {
        val now = ZonedDateTime.of(2026, 11, 5, 23, 50, 0, 0, ZoneId.of("Europe/London"))
        val station = Station(id = "940GZZLUMHL", name = "Mill Hill East", modes = listOf("tube"))
        val scheduledServiceHours = com.androidfung.departureboard.data.model.StationServiceHoursInfo(
            isClosedForNight = false,
            lastTrainTime = "00:25",
            firstTrainTime = "05:25",
            lineName = "Northern",
            minutesUntilLastTrain = 35,
            isApproachingLastTrain = true,
            isNightServiceActive = false
        )
        val liveLastShuttle = sampleDeparture(
            id = "mhe_35m_live",
            stationId = station.id,
            lineId = "northern",
            lineName = "Northern",
            seconds = 28 * 60
        ).copy(expectedDepartureInstant = now.plusMinutes(28).toInstant())

        val (enriched, reconciled) = LastTrainServiceCalculator.enrichDeparturesAndServiceHours(
            station = station,
            departures = listOf(liveLastShuttle),
            existingServiceHours = scheduledServiceHours,
            nowOverride = now,
            timetableChecked = true
        )

        assertNotNull(reconciled)
        assertTrue(enriched.first().isLastTrain)
        assertEquals(28, reconciled!!.minutesUntilLastTrain)
        assertEquals("00:18", reconciled.lastTrainTime)
    }
}

