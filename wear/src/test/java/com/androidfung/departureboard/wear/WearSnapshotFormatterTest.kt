package com.androidfung.departureboard.wear

import com.androidfung.departureboard.wear.haptic.WearConcourseHapticAnnouncer
import com.androidfung.departureboard.wear.model.WearAiPromptType
import com.androidfung.departureboard.wear.model.WearDepartureItem
import com.androidfung.departureboard.wear.model.WearSnapshotFormatter
import com.androidfung.departureboard.wear.sync.WearCompanionStateRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WearSnapshotFormatterTest {

    @Test
    fun `buildComplicationSnapshot formats concise wrist countdown and platform`() {
        val boards = WearSnapshotFormatter.defaultConcourseBoards()
        val victoriaBoard = boards.first { it.stationCrs == "VIC" }

        val snapshot = WearSnapshotFormatter.buildComplicationSnapshot(victoriaBoard)
        assertEquals("BRX • 1m • P4", snapshot.shortText)
        assertEquals("VIC → BRX", snapshot.titleText)
        assertTrue(snapshot.rangedProgressPercent >= 90)
    }

    @Test
    fun `evaluatePlatformTransition detects TBD to confirmed platform announcement`() {
        val tbdDep = WearDepartureItem(
            id = "wat_1",
            lineName = "South Western Railway",
            destination = "Guildford",
            destinationCrs = "GLD",
            platformCode = "TBD",
            isPlatformConfirmed = false,
            countdownMinutes = 4,
            scheduledTime = "18:15"
        )
        val announcedDep = tbdDep.copy(
            platformCode = "11",
            isPlatformConfirmed = true
        )

        val event = WearConcourseHapticAnnouncer.evaluatePlatformTransition(
            previous = tbdDep,
            current = announcedDep,
            isWatched = true
        )
        assertNotNull(event)
        assertEquals(WearConcourseHapticAnnouncer.WristAlertType.PLATFORM_ANNOUNCED, event!!.type)
        assertEquals("Plat 11 Announced!", event.headline)
    }

    @Test
    fun `evaluatePlatformTransition ignores unwatched departures`() {
        val tbdDep = WearDepartureItem(
            id = "wat_1",
            lineName = "South Western Railway",
            destination = "Guildford",
            destinationCrs = "GLD",
            platformCode = "TBD",
            isPlatformConfirmed = false,
            countdownMinutes = 4,
            scheduledTime = "18:15"
        )
        val announcedDep = tbdDep.copy(
            platformCode = "11",
            isPlatformConfirmed = true
        )

        val event = WearConcourseHapticAnnouncer.evaluatePlatformTransition(
            previous = tbdDep,
            current = announcedDep,
            isWatched = false
        )
        assertNull(event)
    }

    @Test
    fun `WearCompanionStateRepository mirrors phone tracked journey and triggers 1-stop alight alert`() {
        WearCompanionStateRepository.onReceiveStopTrackedDeparture()
        WearCompanionStateRepository.clearWristAlert()

        val initialJson = """
            {
                "departureId": "dep_101",
                "stationId": "940GZZLUWLO",
                "stationName": "Waterloo",
                "destinationName": "Edgware Road",
                "lineName": "Bakerloo",
                "platformCode": "TBD",
                "isPlatformConfirmed": false,
                "remainingSeconds": 240,
                "currentLocation": "At Lambeth North",
                "stopsAway": 2,
                "targetAlightStopName": "Oxford Circus",
                "isCancelled": false,
                "lineColorArgb": 4289946373
            }
        """.trimIndent()

        WearCompanionStateRepository.onReceiveTrackedDepartureJson(null, initialJson)
        val tracked1 = WearCompanionStateRepository.trackedJourneyFlow.value
        assertNotNull(tracked1)
        assertEquals("Edgware Road", tracked1!!.destinationName)
        assertTrue(tracked1.isPlatformTbd)

        // Update 1: Platform Announced on Phone -> triggers PLATFORM_ANNOUNCED on Watch
        val platAnnouncedJson = """
            {
                "departureId": "dep_101",
                "stationId": "940GZZLUWLO",
                "stationName": "Waterloo",
                "destinationName": "Edgware Road",
                "lineName": "Bakerloo",
                "platformCode": "3",
                "isPlatformConfirmed": true,
                "remainingSeconds": 180,
                "currentLocation": "Approaching Waterloo",
                "stopsAway": 2,
                "targetAlightStopName": "Oxford Circus",
                "isCancelled": false,
                "lineColorArgb": 4289946373
            }
        """.trimIndent()

        WearCompanionStateRepository.onReceiveTrackedDepartureJson(null, platAnnouncedJson)
        val alert1 = WearCompanionStateRepository.latestWristAlertFlow.value
        assertNotNull(alert1)
        assertEquals(WearConcourseHapticAnnouncer.WristAlertType.PLATFORM_ANNOUNCED, alert1!!.type)
        assertEquals("Plat 3 Announced!", alert1.headline)

        // Update 2: 1 stop away from Oxford Circus -> triggers ALIGHT_NEXT_STOP on Watch
        val oneStopAwayJson = """
            {
                "departureId": "dep_101",
                "stationId": "940GZZLUWLO",
                "stationName": "Waterloo",
                "destinationName": "Edgware Road",
                "lineName": "Bakerloo",
                "platformCode": "3",
                "isPlatformConfirmed": true,
                "remainingSeconds": 60,
                "currentLocation": "At Piccadilly Circus",
                "stopsAway": 1,
                "targetAlightStopName": "Oxford Circus",
                "isCancelled": false,
                "lineColorArgb": 4289946373
            }
        """.trimIndent()

        WearCompanionStateRepository.onReceiveTrackedDepartureJson(null, oneStopAwayJson)
        val alert2 = WearCompanionStateRepository.latestWristAlertFlow.value
        assertNotNull(alert2)
        assertEquals(WearConcourseHapticAnnouncer.WristAlertType.ALIGHT_NEXT_STOP, alert2!!.type)
        assertEquals("Get Ready to Alight", alert2.headline)
    }

    @Test
    fun `addStationFromWatch and removeStationFromWatch update active watch station list`() {
        val initialCount = WearCompanionStateRepository.stationBoardsFlow.value.size
        val paddington = WearSnapshotFormatter.stationCatalog().first { it.stationCrs == "PAD" }

        WearCompanionStateRepository.addStationFromWatch(null, paddington)
        val afterAdd = WearCompanionStateRepository.stationBoardsFlow.value
        assertEquals(initialCount + 1, afterAdd.size)
        assertTrue(afterAdd.any { it.stationCrs == "PAD" })

        WearCompanionStateRepository.removeStationFromWatch(null, paddington.stationId)
        val afterRemove = WearCompanionStateRepository.stationBoardsFlow.value
        assertEquals(initialCount, afterRemove.size)
    }

    @Test
    fun `askAiConcourseTriage evaluates local triage and updates when phone AI responds`() {
        val waterloo = WearSnapshotFormatter.defaultConcourseBoards().first { it.stationCrs == "WAT" }

        val localResp = WearCompanionStateRepository.askAiConcourseTriage(
            context = null,
            station = waterloo,
            promptType = WearAiPromptType.DELAYS_AND_BACKLOG
        )
        assertTrue(localResp.headline.contains("Delay Repay"))
        assertTrue(localResp.summary.contains("Basingstoke"))

        val phoneJson = """
            {
                "stationId": "910GWATRLMN",
                "headline": "💸 Delay Repay (+17m)",
                "summary": "Basingstoke (SWR) qualifies for 25% single fare refund.",
                "rawQuery": "Are there delays at London Waterloo?",
                "isFromPhone": true
            }
        """.trimIndent()
        WearCompanionStateRepository.onReceiveAiResponseJson(phoneJson)
        val syncedResp = WearCompanionStateRepository.latestAiResponseFlow.value
        assertNotNull(syncedResp)
        assertTrue(syncedResp!!.isFromPhone)
        assertTrue(syncedResp.summary.contains("25%"))
    }

    @Test
    fun `compactPlatformCode and compactStationName shorten verbose TfL strings for watch layout`() {
        assertEquals("6", WearSnapshotFormatter.compactPlatformCode("6 • Eastbound"))
        assertEquals("6", WearSnapshotFormatter.compactPlatformCode("Eastbound - Platform 6"))
        assertEquals("14A", WearSnapshotFormatter.compactPlatformCode("Platform 14a"))
        assertEquals("EB", WearSnapshotFormatter.compactPlatformCode("Eastbound"))
        assertEquals("King's Cross", WearSnapshotFormatter.compactStationName("King's Cross St. Pancras Underground Station"))
    }
}
