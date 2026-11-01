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
        assertEquals(TransitRegion.SG, TransitRegion.fromId("SG"))
        assertEquals(TransitRegion.SG, TransitRegion.fromId("sg"))
        assertEquals(TransitRegion.SG, TransitRegion.fromId("SIN"))
        assertEquals(TransitRegion.AU, TransitRegion.fromId("AU"))
        assertEquals(TransitRegion.AU, TransitRegion.fromId("syd"))
        assertEquals(TransitRegion.AU, TransitRegion.fromId("NSW"))
        assertEquals(TransitRegion.JP, TransitRegion.fromId("JP"))
        assertEquals(TransitRegion.JP, TransitRegion.fromId("jp"))
        assertEquals(TransitRegion.JP, TransitRegion.fromId("TYO"))
        assertEquals(TransitRegion.GB, TransitRegion.fromId("INVALID"))
        assertEquals(TransitRegion.GB, TransitRegion.fromId(null))
    }

    @Test
    fun testTransitRegion_fromStationId() {
        assertEquals(TransitRegion.HK, TransitRegion.fromStationId("HK_MTR_ISL_CEN"))
        assertEquals(TransitRegion.HK, TransitRegion.fromStationId("HK_BUS_KMB_001554"))
        assertEquals(TransitRegion.SG, TransitRegion.fromStationId("SG_MRT_NS22_TE14"))
        assertEquals(TransitRegion.AU, TransitRegion.fromStationId("AU_TRAIN_CENTRAL"))
        assertEquals(TransitRegion.JP, TransitRegion.fromStationId("JP_METRO_M17_JY01"))
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

        val mtrStation = stations.firstOrNull { it.modes.contains("tube") || it.modes.contains("mtr") }
        assertNotNull(mtrStation)
        assertTrue(mtrStation!!.id.startsWith("HK_MTR_"))

        val fallbackBus = HkDefaultStations.getFallbackDepartures(busStation.id, busStation.name)
        assertTrue(fallbackBus.isNotEmpty())
        assertEquals("bus", fallbackBus[0].modeName)

        val fallbackMtr = HkDefaultStations.getFallbackDepartures(mtrStation.id, mtrStation.name)
        assertTrue(fallbackMtr.isNotEmpty())
        assertTrue(fallbackMtr[0].modeName == "tube" || fallbackMtr[0].modeName == "mtr")
    }

    @Test
    fun testTransitRegion_supportedModesAreTypedAndRegionSpecific() {
        val gbModes = TransitRegion.GB.supportedModes
        assertEquals("All", gbModes.first().displayName)
        org.junit.Assert.assertNull(gbModes.first().mode)
        assertTrue(gbModes.any { it.displayName == "Tube" && it.mode == com.androidfung.departureboard.data.model.TransitMode.TUBE })
        assertTrue(gbModes.any { it.displayName == "Elizabeth line" })

        val hkModes = TransitRegion.HK.supportedModes
        assertEquals("All", hkModes.first().displayName)
        assertTrue(hkModes.any { it.displayName == "MTR" && it.mode == com.androidfung.departureboard.data.model.TransitMode.TUBE })
        assertTrue(hkModes.any { it.displayName == "Bus" && it.mode == com.androidfung.departureboard.data.model.TransitMode.BUS })
        org.junit.Assert.assertFalse(hkModes.any { it.displayName == "Tube" })
        org.junit.Assert.assertFalse(hkModes.any { it.displayName == "Elizabeth line" })
    }
}
