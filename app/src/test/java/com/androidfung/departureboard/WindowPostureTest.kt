package com.androidfung.departureboard

import androidx.compose.material3.adaptive.HingeInfo
import androidx.compose.material3.adaptive.Posture
import androidx.compose.ui.geometry.Rect
import com.androidfung.departureboard.ui.theme.WindowPostureInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowPostureTest {

    @Test
    fun `default WindowPostureInfo is not tabletop`() {
        val info = WindowPostureInfo()
        assertFalse(info.isTabletop)
        assertEquals(0f, info.hingeHeightDp.value, 0.01f)
    }

    @Test
    fun `detects tabletop posture from Posture with horizontal hinge`() {
        val horizontalHinge = HingeInfo(
            bounds = Rect(0f, 400f, 800f, 440f),
            isFlat = false,
            isVertical = false,
            isSeparating = true,
            isOccluding = false
        )
        val posture = Posture(isTabletop = true, hingeList = listOf(horizontalHinge))
        assertTrue(posture.isTabletop)
        assertFalse(horizontalHinge.isVertical)
        assertFalse(horizontalHinge.isFlat)
    }
}
