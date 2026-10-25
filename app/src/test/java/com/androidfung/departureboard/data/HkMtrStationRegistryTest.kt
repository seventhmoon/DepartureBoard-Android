package com.androidfung.departureboard.data

import com.androidfung.departureboard.data.model.HkMtrStationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HkMtrStationRegistryTest {

    @Test
    fun testStationRegistry_returnsValidStationData() {
        val central = HkMtrStationRegistry.getStation("CEN")
        assertNotNull(central)
        assertEquals("Central", central!!.nameEn)
        assertEquals("中環", central.nameTc)
        assertEquals("Central 中環", central.bilingualName)
        assertEquals("Central (中環)", central.displayName)

        val admName = HkMtrStationRegistry.getName("ADM")
        assertEquals("Admiralty 金鐘", admName)
    }

    @Test
    fun testStationRegistry_allMajorLinesCovered() {
        val stations = HkMtrStationRegistry.STATIONS
        assertTrue(stations.containsKey("CEN")) // Island / Tsuen Wan
        assertTrue(stations.containsKey("ADM")) // Island / TWL / East Rail / South Island
        assertTrue(stations.containsKey("MOK")) // Tsuen Wan / Kwun Tong
        assertTrue(stations.containsKey("KOT")) // East Rail / Kwun Tong
        assertTrue(stations.containsKey("TAW")) // East Rail / Tuen Ma
        assertTrue(stations.containsKey("HOK")) // Tung Chung / Airport Express
        assertTrue(stations.containsKey("TKO")) // Tseung Kwan O
    }
}
