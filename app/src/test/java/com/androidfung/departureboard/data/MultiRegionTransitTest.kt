package com.androidfung.departureboard.data

import com.androidfung.departureboard.data.model.au.AuDefaultStations
import com.androidfung.departureboard.data.model.au.AuStationDirectory
import com.androidfung.departureboard.data.model.jp.JpDefaultStations
import com.androidfung.departureboard.data.model.jp.JpStationDirectory
import com.androidfung.departureboard.data.model.sg.SgDefaultStations
import com.androidfung.departureboard.data.model.sg.SgStationDirectory
import com.androidfung.departureboard.data.repository.region.au.AuDepartureSource
import com.androidfung.departureboard.data.repository.region.jp.JpDepartureSource
import com.androidfung.departureboard.data.repository.region.sg.SgDepartureSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiRegionTransitTest {

    @Test
    fun testSingaporeMrt_searchAndDepartures() = runBlocking {
        val stations = SgDefaultStations.POPULAR_STATIONS
        assertTrue("SG popular stations should not be empty", stations.isNotEmpty())

        val orchard = stations.firstOrNull { it.name == "Orchard" }
        assertNotNull("Orchard should exist in SG default stations", orchard)
        assertTrue(orchard!!.id.startsWith("SG_MRT_"))

        // Search test
        val searchResults = SgStationDirectory.searchSgStations("Raffles")
        assertTrue("Search for 'Raffles' should return Raffles Place", searchResults.any { it.name.contains("Raffles") })

        val chineseResults = SgStationDirectory.searchSgStations("烏節")
        assertTrue("Search for '烏節' should return Orchard", chineseResults.any { it.name.contains("Orchard") })

        // Departure source test
        val source = SgDepartureSource()
        assertTrue(source.canHandle(orchard.id, orchard.name, false))

        val departures = source.fetchDepartures(orchard.id, orchard.name, false).getOrThrow()
        assertTrue("Departures should be generated for Orchard", departures.isNotEmpty())
        assertEquals("tube", departures[0].modeName)
        assertNotNull(departures[0].lineBadge)
    }

    @Test
    fun testSydneyTransit_searchAndDepartures() = runBlocking {
        val stations = AuDefaultStations.POPULAR_STATIONS
        assertTrue("Sydney popular stations should not be empty", stations.isNotEmpty())

        val central = stations.firstOrNull { it.name == "Central" }
        assertNotNull("Central station should exist in Sydney stations", central)
        assertTrue(central!!.id.startsWith("AU_TRAIN_"))

        // Search test
        val searchResults = AuStationDirectory.searchAuStations("Circular Quay")
        assertTrue("Search for Circular Quay should return Circular Quay", searchResults.any { it.name == "Circular Quay" })

        // Departure source test
        val source = AuDepartureSource()
        assertTrue(source.canHandle(central.id, central.name, false))

        val departures = source.fetchDepartures(central.id, central.name, false).getOrThrow()
        assertTrue("Departures should be generated for Sydney Central", departures.isNotEmpty())
        assertNotNull(departures[0].lineBadge)
    }

    @Test
    fun testTokyoTransit_searchAndDepartures() = runBlocking {
        val stations = JpDefaultStations.POPULAR_STATIONS
        assertTrue("Tokyo popular stations should not be empty", stations.isNotEmpty())

        val tokyoSta = stations.firstOrNull { it.name.contains("Tokyo") }
        assertNotNull("Tokyo station should exist in JP stations", tokyoSta)
        assertTrue(tokyoSta!!.id.startsWith("JP_METRO_"))

        // Search test with Kanji, Hiragana and Romaji
        val kanjiResults = JpStationDirectory.searchJpStations("新宿")
        assertTrue("Search for '新宿' should return Shinjuku", kanjiResults.any { it.name.contains("Shinjuku") })

        val hiraganaResults = JpStationDirectory.searchJpStations("しぶや")
        assertTrue("Search for 'しぶや' should return Shibuya", hiraganaResults.any { it.name.contains("Shibuya") })

        // Departure source test
        val source = JpDepartureSource()
        assertTrue(source.canHandle(tokyoSta.id, tokyoSta.name, false))

        val departures = source.fetchDepartures(tokyoSta.id, tokyoSta.name, false).getOrThrow()
        assertTrue("Departures should be generated for Tokyo station", departures.isNotEmpty())
        assertNotNull(departures[0].lineBadge)
    }
}
