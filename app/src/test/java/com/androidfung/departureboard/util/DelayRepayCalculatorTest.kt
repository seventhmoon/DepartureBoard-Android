package com.androidfung.departureboard.util

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.model.CallingPoint
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.TrainInfo
import com.androidfung.departureboard.data.model.TransitOperator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DelayRepayCalculatorTest {

    private fun buildDeparture(
        lineId: String = "southeastern",
        lineName: String = "Southeastern",
        modeName: String = "national-rail",
        operatorName: String? = "Southeastern",
        scheduledTime: String? = "18:00",
        estimatedTime: String? = "18:18",
        status: DepartureStatus = DepartureStatus.DELAYED
    ): Departure {
        return Departure(
            id = "test_dep",
            stationId = "910GVICTRIC",
            stationName = "London Victoria",
            lineId = lineId,
            lineName = lineName,
            platformName = "Plat 2",
            destinationName = "Orpington",
            towards = null,
            timeToStationSeconds = 600,
            expectedArrivalIso = null,
            currentLocation = if (estimatedTime != null) "Exp $estimatedTime" else null,
            modeName = modeName,
            lineBadge = LineBadgeInfo(
                lineId = lineId,
                displayName = lineName,
                backgroundColor = Color.Blue
            ),
            status = status,
            train = operatorName?.let { TrainInfo(operator = TransitOperator(id = it.lowercase(), name = it)) },
            callingPoints = if (scheduledTime != null) {
                listOf(
                    CallingPoint(
                        stationName = "London Victoria",
                        scheduledTime = scheduledTime,
                        estimatedTime = estimatedTime,
                        isCurrentStation = true
                    )
                )
            } else emptyList()
        )
    }

    @Test
    fun `National Rail delay under 15 minutes is not eligible for Delay Repay`() {
        val dep = buildDeparture(
            scheduledTime = "18:00",
            estimatedTime = "18:12"
        )
        assertNull(DelayRepayCalculator.evaluate(dep))
    }

    @Test
    fun `National Rail 15 to 29 minute delay triggers DR15 25 percent single fare tier`() {
        val dep = buildDeparture(
            scheduledTime = "18:00",
            estimatedTime = "18:18"
        )
        val result = DelayRepayCalculator.evaluate(dep)
        assertNotNull(result)
        assertEquals(18, result!!.delayMinutes)
        assertEquals(15, result.thresholdMinutes)
        assertEquals("Delay Repay (+18m)", result.badgeLabel)
        assertTrue(result.compensationSummary.contains("25%"))
    }

    @Test
    fun `National Rail 35 minute delay triggers DR30 50 percent single fare tier`() {
        val dep = buildDeparture(
            scheduledTime = "18:00",
            estimatedTime = "18:35"
        )
        val result = DelayRepayCalculator.evaluate(dep)
        assertNotNull(result)
        assertEquals(35, result!!.delayMinutes)
        assertTrue(result.compensationSummary.contains("50%"))
    }

    @Test
    fun `Heathrow Express 15 minute delay triggers 50 percent compensation`() {
        val dep = buildDeparture(
            lineId = "heathrow-express",
            lineName = "Heathrow Express",
            operatorName = "Heathrow Express",
            scheduledTime = "10:00",
            estimatedTime = "10:16"
        )
        val result = DelayRepayCalculator.evaluate(dep)
        assertNotNull(result)
        assertEquals(16, result!!.delayMinutes)
        assertTrue(result.compensationSummary.contains("50%"))
    }

    @Test
    fun `Elizabeth line requires 30 minutes for TfL refund eligibility`() {
        val dep20 = buildDeparture(
            lineId = "elizabeth",
            lineName = "Elizabeth line",
            modeName = "elizabeth-line",
            operatorName = "Elizabeth line",
            scheduledTime = "14:00",
            estimatedTime = "14:20"
        )
        assertNull(DelayRepayCalculator.evaluate(dep20))

        val dep32 = buildDeparture(
            lineId = "elizabeth",
            lineName = "Elizabeth line",
            modeName = "elizabeth-line",
            operatorName = "Elizabeth line",
            scheduledTime = "14:00",
            estimatedTime = "14:32"
        )
        val result32 = DelayRepayCalculator.evaluate(dep32)
        assertNotNull(result32)
        assertEquals(32, result32!!.delayMinutes)
        assertEquals(30, result32.thresholdMinutes)
    }

    @Test
    fun `Midnight rollover schedule 23_50 to 00_12 computes 22 minute delay`() {
        val dep = buildDeparture(
            scheduledTime = "23:50",
            estimatedTime = "00:12"
        )
        val result = DelayRepayCalculator.evaluate(dep)
        assertNotNull(result)
        assertEquals(22, result!!.delayMinutes)
    }
}
