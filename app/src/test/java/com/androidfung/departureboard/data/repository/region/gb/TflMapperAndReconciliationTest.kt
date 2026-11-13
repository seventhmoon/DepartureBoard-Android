package com.androidfung.departureboard.data.repository.region.gb

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.gb.TflArrivalPrediction
import com.androidfung.departureboard.data.repository.region.gb.mapper.TflDepartureMapper
import org.junit.Assert.assertEquals
import org.junit.Test

class TflMapperAndReconciliationTest {

    @Test
    fun `deduplicate does not collapse blank destination predictions into named services`() {
        val raw = listOf(
            TflArrivalPrediction(
                id = "tfl-1",
                lineId = "central",
                lineName = "Central",
                platformName = "Eastbound - Platform 2",
                destinationName = "",
                timeToStation = 60,
                modeName = "tube"
            ),
            TflArrivalPrediction(
                id = "tfl-2",
                lineId = "central",
                lineName = "Central",
                platformName = "Eastbound - Platform 2",
                destinationName = "Epping Underground Station",
                timeToStation = 90,
                modeName = "tube"
            )
        )

        val result = TflDepartureMapper.deduplicate(raw)
        assertEquals(2, result.size)
    }

    @Test
    fun `reconciliation does not drop National Rail departure against blank TfL destination`() {
        val tflBlank = Departure(
            id = "tfl-liz-blank",
            stationId = "910GPADTON",
            stationName = "London Paddington",
            lineId = "elizabeth",
            lineName = "Elizabeth line",
            platformName = "Platform A",
            destinationName = "",
            towards = null,
            timeToStationSeconds = 120,
            expectedArrivalIso = null,
            currentLocation = null,
            modeName = "elizabeth-line",
            lineBadge = LineBadgeInfo("elizabeth", "Elizabeth", Color.Magenta)
        )
        val nrNamed = tflBlank.copy(
            id = "nr-liz-reading",
            destinationName = "Reading"
        )

        val merged = GbFeedReconciliationEngine.mergeAndDeduplicate(
            tflDepartures = listOf(tflBlank),
            nrDepartures = listOf(nrNamed)
        )
        assertEquals(2, merged.size)
    }
}
