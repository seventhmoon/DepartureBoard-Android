package com.androidfung.departureboard.util

import com.androidfung.departureboard.data.model.TransitRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class RegionDetectionHelperTest {

    @Test
    fun testFromCoordinatesHongKong() {
        // Central station in HK (22.2819, 114.1583)
        val region = TransitRegion.fromCoordinates(22.2819, 114.1583)
        assertEquals(TransitRegion.HK, region)
    }

    @Test
    fun testFromCoordinatesTokyo() {
        // Tokyo station (35.6812, 139.7671)
        val region = TransitRegion.fromCoordinates(35.6812, 139.7671)
        assertEquals(TransitRegion.JP, region)
    }

    @Test
    fun testFromCoordinatesLondon() {
        // London King's Cross (51.5308, -0.1238)
        val region = TransitRegion.fromCoordinates(51.5308, -0.1238)
        assertEquals(TransitRegion.GB, region)
    }

    @Test
    fun testFromCoordinatesSydney() {
        // Sydney Central (-33.8830, 151.2065)
        val region = TransitRegion.fromCoordinates(-33.8830, 151.2065)
        assertEquals(TransitRegion.AU, region)
    }

    @Test
    fun testFromCoordinatesSingapore() {
        // Orchard MRT (1.3040, 103.8318)
        val region = TransitRegion.fromCoordinates(1.3040, 103.8318)
        assertEquals(TransitRegion.SG, region)
    }

    @Test
    fun testFromCoordinatesUnsupportedLocation() {
        // New York (40.7128, -74.0060)
        val region = TransitRegion.fromCoordinates(40.7128, -74.0060)
        assertNull(region)
    }

    @Test
    fun testFromCountryCode() {
        assertEquals(TransitRegion.GB, TransitRegion.fromCountryCode("GB"))
        assertEquals(TransitRegion.GB, TransitRegion.fromCountryCode("UK"))
        assertEquals(TransitRegion.HK, TransitRegion.fromCountryCode("hk"))
        assertEquals(TransitRegion.SG, TransitRegion.fromCountryCode("SG"))
        assertEquals(TransitRegion.AU, TransitRegion.fromCountryCode("AU"))
        assertEquals(TransitRegion.JP, TransitRegion.fromCountryCode("jp"))
        assertNull(TransitRegion.fromCountryCode("US"))
        assertNull(TransitRegion.fromCountryCode("FR"))
    }

    @Test
    fun testFromTimeZone() {
        assertEquals(TransitRegion.JP, TransitRegion.fromTimeZone(ZoneId.of("Asia/Tokyo")))
        assertEquals(TransitRegion.HK, TransitRegion.fromTimeZone(ZoneId.of("Asia/Hong_Kong")))
        assertEquals(TransitRegion.SG, TransitRegion.fromTimeZone(ZoneId.of("Asia/Singapore")))
        assertEquals(TransitRegion.AU, TransitRegion.fromTimeZone(ZoneId.of("Australia/Sydney")))
        assertEquals(TransitRegion.GB, TransitRegion.fromTimeZone(ZoneId.of("Europe/London")))
        assertNull(TransitRegion.fromTimeZone(ZoneId.of("America/New_York")))
    }
}
