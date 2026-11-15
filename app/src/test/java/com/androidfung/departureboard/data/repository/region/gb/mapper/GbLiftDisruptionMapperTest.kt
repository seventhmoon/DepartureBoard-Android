package com.androidfung.departureboard.data.repository.region.gb.mapper

import com.androidfung.departureboard.data.model.AlertCause
import com.androidfung.departureboard.data.model.AlertEffect
import com.androidfung.departureboard.data.model.AlertSeverity
import com.androidfung.departureboard.data.model.gb.TflLiftDisruption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GbLiftDisruptionMapperTest {

    @Test
    fun `fromTflLiftDisruption maps step-free lift outage to accessibility alert`() {
        val lift = TflLiftDisruption(
            icsCode = "1000248",
            naptanCode = "940GZZLUWSM",
            stationName = "Westminster",
            outageStartArea = "Ticket Hall",
            outageEndArea = "Jubilee line",
            message = "Lift between Ticket Hall and Jubilee line is out of service."
        )

        val alert = GbTransitAlertMapper.fromTflLiftDisruption(lift)
        requireNotNull(alert)
        assertEquals(AlertCause.MAINTENANCE, alert.cause)
        assertEquals(AlertEffect.ACCESSIBILITY_ISSUE, alert.effect)
        assertEquals(AlertSeverity.INFO, alert.severity)
        assertTrue(alert.headerText.contains("Westminster"))
        assertTrue(alert.headerText.contains("Ticket Hall to Jubilee line"))
        assertTrue(alert.appliesToStation("940GZZLUWSM"))
    }
}
