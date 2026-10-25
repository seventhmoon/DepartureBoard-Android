package com.androidfung.departureboard.data

import com.androidfung.departureboard.data.model.HkDefaultStations
import com.androidfung.departureboard.data.model.TransitRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitRegionTest {

    @Test
    fun testTransitRegion_fromId() {
        assertEquals(TransitRegion.GB, TransitRegion.fromId("GB"))
        assertEquals(TransitRegion.GB, TransitRegion.fromId("gb"))
        assertEquals(TransitRegion.GB, TransitRegion.fromId("UK"))
        assertEquals(TransitRegion.GB, TransitRegion.fromId("uk"))
        assertEquals(TransitRegion.GB, TransitRegion.fromId("LON"))
        assertEquals(TransitRegion.HK, TransitRegion.fromId("HK"))
        assertEquals(TransitRegion.HK, TransitRegion.fromId("hk"))
        assertEquals(TransitRegion.HK, TransitRegion.fromId("HKG"))
        assertEquals(TransitRegion.GB, TransitRegion.fromId("INVALID"))
        assertEquals(TransitRegion.GB, TransitRegion.fromId(null))
    }

    @Test
    fun testTransitRegion_fromStationId() {
        assertEquals(TransitRegion.HK, TransitRegion.fromStationId("HK_MTR_ISL_CEN"))
        assertEquals(TransitRegion.HK, TransitRegion.fromStationId("HK_BUS_KMB_001554"))
        assertEquals(TransitRegion.GB, TransitRegion.fromStationId("940GZZLUOXC"))
        assertEquals(TransitRegion.GB, TransitRegion.fromStationId("910GKNGX"))
        assertEquals(TransitRegion.GB, TransitRegion.fromStationId(null))
    }

    @Test
    fun testHkDefaultStations_containsBusesAndMtr() {
        val stations = HkDefaultStations.POPULAR_STATIONS
        assertTrue(stations.isNotEmpty())

        val busStation = stations.firstOrNull { it.modes.contains("bus") }
        assertNotNull(busStation)
        assertTrue(busStation!!.id.startsWith("HK_BUS_"))

        val mtrStation = stations.firstOrNull { it.modes.contains("tube") }
        assertNotNull(mtrStation)
        assertTrue(mtrStation!!.id.startsWith("HK_MTR_"))

        val fallbackBus = HkDefaultStations.getFallbackDepartures(busStation.id, busStation.name)
        assertTrue(fallbackBus.isNotEmpty())
        assertEquals("bus", fallbackBus[0].modeName)

        val fallbackMtr = HkDefaultStations.getFallbackDepartures(mtrStation.id, mtrStation.name)
        assertTrue(fallbackMtr.isNotEmpty())
        assertEquals("tube", fallbackMtr[0].modeName)
    }
}
