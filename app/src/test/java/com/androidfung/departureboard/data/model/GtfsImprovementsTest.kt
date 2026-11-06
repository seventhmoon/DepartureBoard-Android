package com.androidfung.departureboard.data.model

import com.androidfung.departureboard.data.db.cache.CachedDepartureEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GtfsImprovementsTest {

    @Test
    fun testStopActivitySemantics() {
        val normalCp = CallingPoint(stationName = "King's Cross", stopActivity = StopActivityType.NORMAL)
        assertEquals(StopActivityType.NORMAL, normalCp.stopActivity)

        val setDownOnlyDep = Departure(
            id = "dep_set_down",
            stationId = "STP",
            stationName = "St Pancras",
            lineId = "thameslink",
            lineName = "Thameslink",
            platformName = "Platform A",
            destinationName = "Brighton",
            towards = null,
            timeToStationSeconds = 120,
            expectedArrivalIso = null,
            currentLocation = null,
            modeName = "rail",
            lineBadge = LineBadgeInfo("tl", "Thameslink", androidx.compose.ui.graphics.Color.Blue),
            stopActivity = StopActivityType.ALIGHTING_ONLY
        )
        assertTrue(setDownOnlyDep.isSetDownOnly)
        assertFalse(setDownOnlyDep.isPickUpOnly)

        val pickUpOnlyDep = setDownOnlyDep.copy(stopActivity = StopActivityType.BOARDING_ONLY)
        assertTrue(pickUpOnlyDep.isPickUpOnly)
        assertFalse(pickUpOnlyDep.isSetDownOnly)
    }

    @Test
    fun testTransitAlertModel() {
        val alert = TransitAlert(
            id = "alert_101",
            cause = AlertCause.TECHNICAL_PROBLEM,
            effect = AlertEffect.SIGNIFICANT_DELAYS,
            severity = AlertSeverity.SEVERE,
            headerText = "Severe delays due to signalling fault",
            descriptionText = "Trains are held between Finsbury Park and King's Cross.",
            informedEntities = listOf(
                InformedEntity(lineId = "piccadilly", stationId = "FPK"),
                InformedEntity(agencyId = "TL")
            )
        )

        assertTrue(alert.appliesToStation("FPK"))
        assertFalse(alert.appliesToStation("KGX"))
        assertTrue(alert.appliesToLine("piccadilly"))
        assertFalse(alert.appliesToLine("northern"))
        assertTrue(alert.appliesToOperator("TL"))
        assertEquals(AlertCause.TECHNICAL_PROBLEM, alert.cause)
        assertEquals(AlertEffect.SIGNIFICANT_DELAYS, alert.effect)
    }

    @Test
    fun testJourneyDestinationDisentanglement() {
        val dest1 = JourneyDestination(
            terminusName = "Morden",
            tripHeadsign = "Morden via Bank",
            via = "Bank",
            terminusStationId = "940GZZLUMDN"
        )
        assertEquals("Morden via Bank", dest1.displayDestination)
        assertEquals("via Bank", dest1.displayViaSubtitle)

        val dest2 = JourneyDestination(
            terminusName = "Heathrow Terminal 4",
            tripHeadsign = null,
            via = null
        )
        assertEquals("Heathrow Terminal 4", dest2.displayDestination)
        assertNull(dest2.displayViaSubtitle)
    }

    @Test
    fun testStationTransfers() {
        val transferTimed = StationTransfer(
            fromPlatform = "1",
            toPlatform = "2",
            type = TransferType.TIMED_TRANSFER,
            minTransferTimeSeconds = 60
        )
        assertEquals(TransferType.TIMED_TRANSFER, transferTimed.type)
        assertEquals("1 min walk", transferTimed.displayTransferTime)

        val transferLong = StationTransfer(
            fromPlatform = "Circle Line Plat 1",
            toPlatform = "Thameslink Plat A",
            type = TransferType.MIN_TIME,
            minTransferTimeSeconds = 300
        )
        assertEquals("5 min walk", transferLong.displayTransferTime)

        val station = Station(
            id = "STP",
            name = "St Pancras",
            transfers = listOf(transferTimed, transferLong)
        )
        assertEquals(2, station.transfers.size)
    }

    @Test
    fun testServiceFrequencyHeadways() {
        val peakMetro = ServiceFrequency(headwaySeconds = 150)
        assertEquals("Every 2–3 mins", peakMetro.displayHeadway)

        val offPeak = ServiceFrequency(headwaySeconds = 600)
        assertEquals("Every 5–10 mins", offPeak.displayHeadway)

        val standard = ServiceFrequency(headwaySeconds = 900)
        assertEquals("Every 15 mins", standard.displayHeadway)
    }

    @Test
    fun testWheelchairBoardingStatus() {
        val stationLevel = Station(
            id = "EAL_CEN",
            name = "Custom House",
            wheelchairStatus = WheelchairBoardingStatus.STEP_FREE_LEVEL_BOARDING
        )
        assertEquals(WheelchairBoardingStatus.STEP_FREE_LEVEL_BOARDING, stationLevel.wheelchairStatus)

        val stationRamp = Station(
            id = "KGX",
            name = "King's Cross",
            wheelchairStatus = WheelchairBoardingStatus.STEP_FREE_WITH_RAMP
        )
        assertEquals(WheelchairBoardingStatus.STEP_FREE_WITH_RAMP, stationRamp.wheelchairStatus)
    }

    @Test
    fun testCachedDeparturePreservesFrequencyAndStopActivity() {
        val original = Departure(
            id = "dep_freq",
            stationId = "HK_MTR_ISL_CEN",
            stationName = "Central",
            lineId = "ISL",
            lineName = "Island Line",
            platformName = "Platform 1",
            destinationName = "Chai Wan",
            towards = "Chai Wan",
            timeToStationSeconds = 0,
            expectedArrivalIso = null,
            currentLocation = "In Transit",
            modeName = "mtr",
            lineBadge = LineBadgeInfo("ISL", "Island Line", androidx.compose.ui.graphics.Color.Blue),
            stopActivity = StopActivityType.ALIGHTING_ONLY,
            frequency = ServiceFrequency(headwaySeconds = 120)
        )

        val entity = CachedDepartureEntity.fromDeparture(original)
        val restored = entity.toDeparture()

        assertEquals(StopActivityType.ALIGHTING_ONLY, restored.stopActivity)
        assertTrue(restored.isSetDownOnly)
        assertNotNull(restored.frequency)
        assertEquals("Every 2 mins", restored.frequency?.displayHeadway)
    }
}
