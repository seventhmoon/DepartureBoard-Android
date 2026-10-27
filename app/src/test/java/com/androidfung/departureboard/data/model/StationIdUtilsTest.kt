package com.androidfung.departureboard.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StationIdUtilsTest {

    @Test
    fun testIsHkTransitId() {
        assertTrue("HK_MTR_CEN".isHkTransitId)
        assertTrue("hk_bus_104".isHkTransitId)
        assertFalse("940GZZLUVIC".isHkTransitId)
        assertFalse("bods-123".isHkTransitId)
    }

    @Test
    fun testIsHkMtrStationId() {
        assertTrue("HK_MTR_ADM".isHkMtrStationId)
        assertTrue("hk_mtr_central".isHkMtrStationId)
        assertFalse("HK_BUS_KMB_104".isHkMtrStationId)
        assertFalse("910GCLPHMJN".isHkMtrStationId)
    }

    @Test
    fun testIsHkBusStationId() {
        assertTrue("HK_BUS_104".isHkBusStationId)
        assertTrue("HK_KMB_ST01".isHkBusStationId)
        assertTrue("HK_CTB_00102".isHkBusStationId)
        assertFalse("HK_MTR_CEN".isHkBusStationId)
    }

    @Test
    fun testIsHkBusRouteId() {
        assertTrue("HK_BUS_104".isHkBusRouteId)
        assertTrue("KMB_104".isHkBusRouteId)
        assertTrue("CTB_969".isHkBusRouteId)
        assertFalse("bods-123".isHkBusRouteId)
        assertFalse("victoria".isHkBusRouteId)
    }

    @Test
    fun testIsBodsTransitId() {
        assertTrue("bods-123".isBodsTransitId)
        assertTrue("BODS_FEED".isBodsTransitId)
        assertFalse("940GZZLUVIC".isBodsTransitId)
    }

    @Test
    fun testExtractRawHkBusStopId() {
        assertEquals("001423", extractRawHkBusStopId("HK_BUS_KMB_001423"))
        assertEquals("002511", extractRawHkBusStopId("HK_BUS_CTB_002511"))
        assertEquals("9988", extractRawHkBusStopId("HK_BUS_9988"))
        assertEquals("ST01", extractRawHkBusStopId("HK_KMB_ST01"))
        assertEquals("CT01", extractRawHkBusStopId("HK_CTB_CT01"))
        assertEquals("940GZZLUVIC", extractRawHkBusStopId("940GZZLUVIC"))
    }

    @Test
    fun testStationExtensions() {
        val mtrStation = Station(id = "HK_MTR_CEN", name = "Central")
        val busStation = Station(id = "HK_BUS_104", name = "Cross Harbour Tunnel")
        val railStation = Station(id = "910GCLPHMJN", name = "Clapham Junction")

        assertTrue(mtrStation.isHkMtr)
        assertTrue(mtrStation.isHkStation)
        assertFalse(mtrStation.isHkBus)

        assertTrue(busStation.isHkBus)
        assertTrue(busStation.isHkStation)
        assertFalse(busStation.isHkMtr)

        assertTrue(railStation.isNationalRail)
        assertFalse(railStation.isHkStation)
    }
}
