package com.androidfung.departureboard.data.model

import com.androidfung.departureboard.data.repository.TflStopPointUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AtcoAreaRegistryTest {

    @Test
    fun testEnumResolution() {
        val manchester = AtcoArea.fromAtcoCode("1800SB04611")
        assertNotNull(manchester)
        assertEquals(AtcoArea.GREATER_MANCHESTER, manchester)
        assertEquals("180", manchester?.code)
        assertEquals("Greater Manchester", manchester?.displayName)
        assertEquals(AtcoRegion.ENGLAND_NORTH_WEST, manchester?.region)
        assertTrue(manchester?.isRegionalBusAuthority == true)

        val rail = AtcoArea.fromAtcoCode("910GSTPX")
        assertEquals(AtcoArea.NATIONAL_RAIL, rail)
        assertEquals(AtcoRegion.NATIONAL, rail?.region)
        assertTrue(rail?.isModalInfrastructure == true)
        assertFalse(rail?.isRegionalBusAuthority == true)

        val peopleMover = AtcoArea.fromAtcoCode("945000001")
        assertEquals(AtcoArea.PEOPLE_MOVER, peopleMover)

        assertNull(AtcoArea.fromAtcoCode("invalid"))
    }

    @Test
    fun testAreaNameResolution() {
        assertEquals("Greater London", AtcoAreaRegistry.getAreaName("490000185A"))
        assertEquals("Greater Manchester", AtcoAreaRegistry.getAreaName("1800SB04611"))
        assertEquals("West Midlands", AtcoAreaRegistry.getAreaName("43000213401"))
        assertEquals("Bristol", AtcoAreaRegistry.getAreaName("0100BRP90312"))
        assertEquals("Edinburgh", AtcoAreaRegistry.getAreaName("6200201230"))
        assertEquals("National Rail", AtcoAreaRegistry.getAreaName("910GSTPX"))
        assertEquals("Automated People Mover", AtcoAreaRegistry.getAreaName("945000001"))
    }

    @Test
    fun testIsRegionalBusStop() {
        assertTrue(AtcoAreaRegistry.isRegionalBusStop("1800SB04611")) // Greater Manchester
        assertTrue(AtcoAreaRegistry.isRegionalBusStop("21002345"))   // Hertfordshire
        assertTrue(AtcoAreaRegistry.isRegionalBusStop("43001234"))   // West Midlands
        assertTrue(AtcoAreaRegistry.isRegionalBusStop("0100BRP90312")) // Bristol
        assertTrue(AtcoAreaRegistry.isRegionalBusStop("BODS_12345")) // Explicit BODS query

        assertFalse(AtcoAreaRegistry.isRegionalBusStop("490000185A")) // London bus
        assertFalse(AtcoAreaRegistry.isRegionalBusStop("910GSTPX"))   // Rail
        assertFalse(AtcoAreaRegistry.isRegionalBusStop("940GZZLUKSX")) // Metro
    }

    @Test
    fun testIsLondonBusStop() {
        assertTrue(AtcoAreaRegistry.isLondonBusStop("490000185A"))
        assertFalse(AtcoAreaRegistry.isLondonBusStop("1800SB04611"))
    }

    @Test
    fun testTflStopPointUtilsIsBusStopWithRegionalPrefixes() {
        // Manchester bus stop
        assertTrue(TflStopPointUtils.isBusStop("1800SB04611", "Piccadilly Gardens"))
        // Birmingham bus stop
        assertTrue(TflStopPointUtils.isBusStop("43001234", "Colmore Row"))
        // London bus stop
        assertTrue(TflStopPointUtils.isBusStop("490000185A", "Mill Hill East"))
        // Rail station should not be identified as bus stop
        assertFalse(TflStopPointUtils.isBusStop("910GSTPX", "St Pancras International"))
    }

    @Test
    fun testExtractBusStopLetter() {
        // Valid London StopPoint ATCO code with 1-letter stop flag
        assertEquals("A", TflStopPointUtils.extractBusStopLetter("490000185A"))
        // Valid London StopPoint ATCO code with 2-letter stop flag
        assertEquals("SB", TflStopPointUtils.extractBusStopLetter("490013323SB"))
        // StopArea (490G) should not yield a bus stop letter
        assertNull(TflStopPointUtils.extractBusStopLetter("490G000773"))
        // Regional ATCO code (e.g. Manchester 1800SB...) should not yield a letter under TfL stop-letter logic
        assertNull(TflStopPointUtils.extractBusStopLetter("1800SB04611"))
    }
}
