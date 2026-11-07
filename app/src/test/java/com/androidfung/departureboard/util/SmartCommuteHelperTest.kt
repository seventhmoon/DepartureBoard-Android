package com.androidfung.departureboard.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class SmartCommuteHelperTest {

    private data class TestStationItem(val id: String, val tag: CommuteTag)

    @Test
    fun `identifies morning commute period`() {
        val morningTime = LocalTime.of(8, 15)
        assertEquals(CommutePeriod.MORNING, SmartCommuteHelper.getCommutePeriod(morningTime))
    }

    @Test
    fun `identifies evening commute period`() {
        val eveningTime = LocalTime.of(17, 45)
        assertEquals(CommutePeriod.EVENING, SmartCommuteHelper.getCommutePeriod(eveningTime))
    }

    @Test
    fun `identifies off-peak period`() {
        val noonTime = LocalTime.of(13, 0)
        assertEquals(CommutePeriod.OFF_PEAK, SmartCommuteHelper.getCommutePeriod(noonTime))
    }

    @Test
    fun `prioritizes HOME station in morning commute`() {
        val stations = listOf(
            TestStationItem("work_st", CommuteTag.WORK),
            TestStationItem("other_st", CommuteTag.NONE),
            TestStationItem("home_st", CommuteTag.HOME)
        )

        val reordered = SmartCommuteHelper.reorderForCommute(
            items = stations,
            tagSelector = { it.tag },
            period = CommutePeriod.MORNING
        )

        assertEquals("home_st", reordered[0].id)
    }

    @Test
    fun `prioritizes WORK station in evening commute`() {
        val stations = listOf(
            TestStationItem("home_st", CommuteTag.HOME),
            TestStationItem("work_st", CommuteTag.WORK),
            TestStationItem("other_st", CommuteTag.NONE)
        )

        val reordered = SmartCommuteHelper.reorderForCommute(
            items = stations,
            tagSelector = { it.tag },
            period = CommutePeriod.EVENING
        )

        assertEquals("work_st", reordered[0].id)
    }
}
