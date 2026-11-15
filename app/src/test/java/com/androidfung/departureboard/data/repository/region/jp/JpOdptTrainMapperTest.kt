package com.androidfung.departureboard.data.repository.region.jp

import com.androidfung.departureboard.data.model.DataFreshness
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.network.jp.JpOdptTrainItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class JpOdptTrainMapperTest {

    @Test
    fun `mapOdptTrainsToDepartures maps approaching, stopped, and upstream trains`() {
        val source = JpDepartureSource()
        val now = Instant.parse("2026-11-14T10:00:00Z")
        val trains = listOf(
            // Stopped at Ginza (G09)
            JpOdptTrainItem(
                sameAs = "odpt.Train:TokyoMetro.Ginza.A101",
                railway = "odpt.Railway:TokyoMetro.Ginza",
                trainNumber = "A101",
                trainType = "odpt.TrainType:TokyoMetro.Local",
                delaySeconds = 0,
                fromStation = "odpt.Station:TokyoMetro.Ginza.Ginza",
                toStation = null,
                railDirection = "odpt.RailDirection:TokyoMetro.Asakusa",
                destinationStations = listOf("odpt.Station:TokyoMetro.Ginza.Asakusa"),
                carComposition = 6
            ),
            // Approaching Ginza from Shimbashi (G08 -> G09) with 120s delay
            JpOdptTrainItem(
                sameAs = "odpt.Train:TokyoMetro.Ginza.B202",
                railway = "odpt.Railway:TokyoMetro.Ginza",
                trainNumber = "B202",
                trainType = "odpt.TrainType:TokyoMetro.Local",
                delaySeconds = 120,
                fromStation = "odpt.Station:TokyoMetro.Ginza.Shimbashi",
                toStation = "odpt.Station:TokyoMetro.Ginza.Ginza",
                railDirection = "odpt.RailDirection:TokyoMetro.Asakusa",
                destinationStations = listOf("odpt.Station:TokyoMetro.Ginza.Asakusa"),
                carComposition = 6
            )
        )

        val departures = source.mapOdptTrainsToDepartures(
            trains = trains,
            stationId = "JP_METRO_G09_M16_H08",
            stationName = "Ginza",
            now = now
        )

        assertEquals(2, departures.size)
        val atPlatform = departures[0]
        assertEquals(0, atPlatform.timeToStationSeconds)
        assertEquals(DepartureStatus.DUE, atPlatform.status)
        assertEquals(DataFreshness.LIVE, atPlatform.dataFreshness)
        assertEquals("At Platform", atPlatform.currentLocation)
        assertEquals(6, atPlatform.train?.formation?.coachCount)

        val approaching = departures[1]
        assertEquals(180, approaching.timeToStationSeconds)
        assertEquals(DepartureStatus.DELAYED, approaching.status)
        assertTrue(approaching.currentLocation?.contains("Shimbashi") == true)
    }
}
