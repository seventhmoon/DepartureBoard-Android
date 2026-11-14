package com.androidfung.departureboard.core.wearcontract

import org.junit.Assert.assertEquals
import org.junit.Test

class CompactTransitFormatterTest {

    @Test
    fun `compactPlatformCode handles UK, CJK, and directional platforms`() {
        assertEquals("6", CompactTransitFormatter.compactPlatformCode("Platform 6 • Eastbound"))
        assertEquals("12A", CompactTransitFormatter.compactPlatformCode("Plat 12a"))
        assertEquals("3", CompactTransitFormatter.compactPlatformCode("3番線"))
        assertEquals("1", CompactTransitFormatter.compactPlatformCode("1號月台"))
        assertEquals("EB", CompactTransitFormatter.compactPlatformCode("Eastbound"))
        assertEquals("TBD", CompactTransitFormatter.compactPlatformCode(""))
        assertEquals("TBD", CompactTransitFormatter.compactPlatformCode("TBD"))
    }

    @Test
    fun `deriveStationShortCode resolves regional station IDs and multi-script names`() {
        assertEquals("KGX", CompactTransitFormatter.deriveStationShortCode("910GKGX", "London King's Cross"))
        assertEquals("OXC", CompactTransitFormatter.deriveStationShortCode("940GZZLUOXC", "Oxford Circus"))
        assertEquals("ADM", CompactTransitFormatter.deriveStationShortCode("HK_MTR_ISL_ADM", "Admiralty"))
        assertEquals("EW13", CompactTransitFormatter.deriveStationShortCode("SG_MRT_EW13", "City Hall"))
        assertEquals("G01", CompactTransitFormatter.deriveStationShortCode("JP_METRO_G01", "Shibuya"))
        assertEquals("新宿", CompactTransitFormatter.deriveStationShortCode("", "新宿"))
        assertEquals("金鐘", CompactTransitFormatter.deriveStationShortCode("", "金鐘"))
        assertEquals("104", CompactTransitFormatter.deriveStationShortCode("HK_BUS_104", "Cross Harbour"))
        assertEquals("CC1", CompactTransitFormatter.deriveStationShortCode("SG_MRT_CC1", "Dhoby Ghaut"))
        assertEquals("CGD", CompactTransitFormatter.deriveStationShortCode("AU_TRAIN_CGD", "Circular Quay"))
        assertEquals("JY01", CompactTransitFormatter.deriveStationShortCode("JP_RAIL_JY01", "Tokyo"))
    }

    @Test
    fun `compactPlatformCode preserves Stop, Stand, and Bay tokens`() {
        assertEquals("Stop A", CompactTransitFormatter.compactPlatformCode("Stop A"))
        assertEquals("Stand B", CompactTransitFormatter.compactPlatformCode("Stand B"))
        assertEquals("Bay 12", CompactTransitFormatter.compactPlatformCode("Bay 12"))
    }
}
