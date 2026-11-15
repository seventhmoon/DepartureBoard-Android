package com.androidfung.departureboard.data.repository.region.sg

import com.androidfung.departureboard.data.model.AlertCause
import com.androidfung.departureboard.data.model.AlertEffect
import com.androidfung.departureboard.data.model.AlertSeverity
import com.androidfung.departureboard.data.network.sg.SgLtaAffectedSegment
import com.androidfung.departureboard.data.network.sg.SgLtaAlertMessage
import com.androidfung.departureboard.data.network.sg.SgLtaFacilitiesMaintenanceResponse
import com.androidfung.departureboard.data.network.sg.SgLtaFacilityMaintenance
import com.androidfung.departureboard.data.network.sg.SgLtaTrainServiceAlertValue
import com.androidfung.departureboard.data.network.sg.SgLtaTrainServiceAlertsResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SgAlertsMapperTest {

    @Test
    fun `mapTrainServiceAlerts returns empty when status is normal and no segments`() {
        val response = SgLtaTrainServiceAlertsResponse(
            value = SgLtaTrainServiceAlertValue(status = 1)
        )
        assertTrue(SgRegionalTransitProvider.mapTrainServiceAlerts(response).isEmpty())
    }

    @Test
    fun `mapTrainServiceAlerts maps disrupted segment with shuttle hints and entities`() {
        val response = SgLtaTrainServiceAlertsResponse(
            value = SgLtaTrainServiceAlertValue(
                status = 2,
                affectedSegments = listOf(
                    SgLtaAffectedSegment(
                        line = "EWL",
                        from = "EW1",
                        to = "EW3",
                        freePublicBus = "Yes",
                        freeMrtShuttle = "Yes",
                        mrtShuttleDirection = "Both directions"
                    )
                ),
                messages = listOf(
                    SgLtaAlertMessage(content = "Train fault between Pasir Ris and Tanah Merah.")
                )
            )
        )

        val alerts = SgRegionalTransitProvider.mapTrainServiceAlerts(response)
        assertEquals(1, alerts.size)
        val alert = alerts.first()
        assertEquals(AlertSeverity.SEVERE, alert.severity)
        assertTrue(alert.headerText.contains("EWL"))
        assertTrue(alert.headerText.contains("EW1 – EW3"))
        assertTrue(alert.descriptionText?.contains("Free public bus available") == true)
        assertTrue(alert.descriptionText?.contains("Free MRT shuttle bus available (Both directions)") == true)
        assertTrue(alert.appliesToLine("EWL"))
        assertTrue(alert.appliesToStation("EW1"))
    }

    @Test
    fun `mapFacilitiesMaintenance maps lift outage to accessibility alert`() {
        val response = SgLtaFacilitiesMaintenanceResponse(
            value = listOf(
                SgLtaFacilityMaintenance(
                    stationCode = "NS21",
                    stationName = "Newton",
                    facilityType = "Lift",
                    facilityCode = "L1",
                    location = "Concourse to Platform",
                    status = "Under Maintenance",
                    estimatedCompletionDate = "2026-11-14T18:00:00+08:00"
                )
            )
        )

        val alerts = SgRegionalTransitProvider.mapFacilitiesMaintenance(response)
        assertEquals(1, alerts.size)
        val alert = alerts.first()
        assertEquals(AlertCause.MAINTENANCE, alert.cause)
        assertEquals(AlertEffect.ACCESSIBILITY_ISSUE, alert.effect)
        assertEquals(AlertSeverity.INFO, alert.severity)
        assertTrue(alert.headerText.contains("Newton (NS21)"))
        assertTrue(alert.appliesToStation("NS21"))
    }
}
