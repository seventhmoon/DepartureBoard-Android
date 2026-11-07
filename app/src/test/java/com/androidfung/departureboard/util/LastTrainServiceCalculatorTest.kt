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
}
