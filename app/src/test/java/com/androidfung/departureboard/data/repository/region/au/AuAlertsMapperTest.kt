package com.androidfung.departureboard.data.repository.region.au

import com.androidfung.departureboard.data.model.AlertCause
import com.androidfung.departureboard.data.model.AlertEffect
import com.androidfung.departureboard.data.model.AlertSeverity
import com.androidfung.departureboard.data.network.au.AuTfnswAddInfoResponse
import com.androidfung.departureboard.data.network.au.AuTfnswInfoItem
import com.androidfung.departureboard.data.network.au.AuTfnswInfoLink
import com.androidfung.departureboard.data.network.au.AuTfnswInfos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuAlertsMapperTest {

    @Test
    fun `mapAddInfoToAlerts maps trackwork alert and strips html`() {
        val response = AuTfnswAddInfoResponse(
            infos = AuTfnswInfos(
                current = listOf(
                    AuTfnswInfoItem(
                        id = "6002558",
                        priority = "high",
                        publicationStart = "2026-11-14T08:00:00Z",
                        publicationEnd = "2026-11-14T18:00:00Z",
                        title = "Trackwork",
                        subtitle = "T1 Western Line trackwork — buses replace trains",
                        description = "<p>Buses replace <strong>T1</strong> trains between Penrith and Blacktown.</p>",
                        infoLinks = listOf(AuTfnswInfoLink(url = "https://transportnsw.info/alerts"))
                    )
                )
            )
        )

        val alerts = AuRegionalTransitProvider.mapAddInfoToAlerts(response)
        assertEquals(1, alerts.size)
        val alert = alerts.first()
        assertEquals("au_tp_6002558", alert.id)
        assertEquals(AlertSeverity.SEVERE, alert.severity)
        assertEquals(AlertCause.MAINTENANCE, alert.cause)
        assertEquals(AlertEffect.MODIFIED_SERVICE, alert.effect)
        assertEquals("Buses replace T1 trains between Penrith and Blacktown.", alert.descriptionText)
        assertEquals("https://transportnsw.info/alerts", alert.url)
        assertTrue(alert.appliesToOperator("TfNSW"))
    }
}
