package com.androidfung.departureboard.data

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.model.HkMtrLineRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HkMtrLineRegistryTest {

    @Test
    fun testLineRegistry_containsAllMtrLinesWithOfficialColors() {
        val isl = HkMtrLineRegistry.getLine("ISL")
        assertNotNull(isl)
        assertEquals("Island Line", isl!!.nameEn)
        assertEquals("港島綫", isl.nameTc)
        assertEquals(Color(0xFF007DC5), isl.color)

        val twl = HkMtrLineRegistry.getLine("TWL")
        assertNotNull(twl)
        assertEquals("Tsuen Wan Line", twl!!.nameEn)
        assertEquals(Color(0xFFE2231A), twl.color)

        val eal = HkMtrLineRegistry.getLine("EAL")
        assertNotNull(eal)
        assertEquals(Color(0xFF53B7E8), eal!!.color)
    }

    @Test
    fun testLineRegistry_createsValidBadge() {
        val badge = HkMtrLineRegistry.getLineBadge("ISL")
        assertEquals("ISL", badge.lineId)
        assertEquals("ISL", badge.displayName)
        assertEquals(Color(0xFF007DC5), badge.backgroundColor)
        assertEquals(Color.White, badge.textColor)

        val fallbackBadge = HkMtrLineRegistry.getLineBadge("UNKNOWN")
        assertEquals("UNKNOWN", fallbackBadge.lineId)
    }
}
