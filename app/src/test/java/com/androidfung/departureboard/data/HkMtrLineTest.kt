package com.androidfung.departureboard.data

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.model.hk.HkMtrLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HkMtrLineTest {

    @Test
    fun testLineRegistry_containsAllMtrLinesWithOfficialColors() {
        val isl = HkMtrLine.getLine("ISL")
        assertNotNull(isl)
        assertEquals("Island Line", isl!!.nameEn)
        assertEquals("港島綫", isl.nameTc)
        assertEquals(Color(0xFF007DC5), isl.color)

        val twl = HkMtrLine.getLine("TWL")
        assertNotNull(twl)
        assertEquals("Tsuen Wan Line", twl!!.nameEn)
        assertEquals(Color(0xFFE2231A), twl.color)

        val eal = HkMtrLine.getLine("EAL")
        assertNotNull(eal)
        assertEquals(Color(0xFF53B7E8), eal!!.color)
    }

    @Test
    fun testLineRegistry_createsValidBadge() {
        val badge = HkMtrLine.getLineBadge("ISL")
        assertEquals("ISL", badge.lineId)
        assertEquals("ISL", badge.displayName)
        assertEquals(Color(0xFF007DC5), badge.backgroundColor)
        assertEquals(Color.White, badge.textColor)

        val fallbackBadge = HkMtrLine.getLineBadge("UNKNOWN")
        assertEquals("UNKNOWN", fallbackBadge.lineId)
    }
}
