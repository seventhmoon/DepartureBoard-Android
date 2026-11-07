package com.androidfung.departureboard.util

import java.time.LocalTime

/**
 * Commute period based on time of day.
 */
enum class CommutePeriod {
    MORNING,  // 06:30 - 10:00: Residential -> Office
    EVENING,  // 16:00 - 19:30: Office -> Residential
    OFF_PEAK  // Normal / weekend
}

/**
 * User commute designation for a station.
 */
enum class CommuteTag {
    NONE,
    HOME,
    WORK
}

/**
 * Helper to determine time-of-day commute periods and prioritize stations accordingly.
 */
object SmartCommuteHelper {

    val MORNING_START: LocalTime = LocalTime.of(6, 30)
    val MORNING_END: LocalTime = LocalTime.of(10, 0)
    val EVENING_START: LocalTime = LocalTime.of(16, 0)
    val EVENING_END: LocalTime = LocalTime.of(19, 30)

    fun getCommutePeriod(time: LocalTime = LocalTime.now()): CommutePeriod {
        return when {
            !time.isBefore(MORNING_START) && time.isBefore(MORNING_END) -> CommutePeriod.MORNING
            !time.isBefore(EVENING_START) && time.isBefore(EVENING_END) -> CommutePeriod.EVENING
            else -> CommutePeriod.OFF_PEAK
        }
    }

    /**
     * Reorders stations according to the active commute period and [CommuteTag]s.
     */
    fun <T> reorderForCommute(
        items: List<T>,
        tagSelector: (T) -> CommuteTag,
        period: CommutePeriod = getCommutePeriod()
    ): List<T> {
        if (items.size <= 1 || period == CommutePeriod.OFF_PEAK) return items

        val targetTag = if (period == CommutePeriod.MORNING) CommuteTag.HOME else CommuteTag.WORK
        val (prioritized, remaining) = items.partition { tagSelector(it) == targetTag }

        return prioritized + remaining
    }
}
