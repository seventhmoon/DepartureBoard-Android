package com.androidfung.departureboard.util

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.ServiceFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformBacklogDetectorTest {

    private fun createMetroDeparture(
        id: String,
        lineId: String = "victoria",
        lineName: String = "Victoria",
        timeToStationSeconds: Int,
        headwaySeconds: Int? = null
    ): Departure {
        return Departure(
            id = id,
            stationId = "940GZZLUOXC",
            stationName = "Oxford Circus",
            lineId = lineId,
            lineName = lineName,
            platformName = "Northbound - Platform 4",
            destinationName = "Walthamstow Central",
            towards = "Walthamstow Central",
            direction = "Northbound",
            timeToStationSeconds = timeToStationSeconds,
            expectedArrivalIso = null,
            currentLocation = null,
            modeName = "tube",
            lineBadge = LineBadgeInfo(lineId = lineId, displayName = lineName, backgroundColor = Color.Cyan, textColor = Color.White),
            frequency = headwaySeconds?.let { ServiceFrequency(headwaySeconds = it) }
        )
    }

    @Test
    fun `detects backlog when next train is 14 minutes away on 2-minute line`() {
        val dep = createMetroDeparture("v1", timeToStationSeconds = 14 * 60)
        val alerts = PlatformBacklogDetector.detectBacklogs(listOf(dep))

        assertEquals(1, alerts.size)
        val alert = alerts[0]
        assertEquals("victoria", alert.lineId)
        assertEquals(14, alert.minutesUntilNext)
        assertEquals(2, alert.typicalHeadwayMinutes)
    }

    @Test
    fun `no alert when next train is on time within normal headway`() {
        val dep = createMetroDeparture("v1", timeToStationSeconds = 2 * 60) // 2 mins
        val alerts = PlatformBacklogDetector.detectBacklogs(listOf(dep))

        assertTrue(alerts.isEmpty())
    }
}
