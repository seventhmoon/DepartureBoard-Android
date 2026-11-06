package com.androidfung.departureboard.data.gtfs

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Utilities for GTFS static schedule time and calendar date rules.
 *
 * Per GTFS specification, times in `stop_times.txt` are measured relative to noon minus 12h
 * of the service day, and trips running past midnight use hours >= 24 (e.g. `25:15:00` for 01:15 AM).
 */
object GtfsTimeUtils {

    private val GTFS_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.US)
    const val SECONDS_PER_DAY = 86_400

    /**
     * Parses a GTFS time string (`HH:MM:SS` or `HH:MM`, including hours >= 24) into seconds from service day start.
     */
    fun parseTimeToSeconds(gtfsTime: String?): Int? {
        if (gtfsTime.isNullOrBlank()) return null
        val parts = gtfsTime.trim().split(":")
        if (parts.size < 2 || parts.size > 3) return null
        val hours = parts[0].toIntOrNull() ?: return null
        val minutes = parts[1].toIntOrNull() ?: return null
        val seconds = if (parts.size == 3) parts[2].toIntOrNull() ?: return null else 0
        if (hours < 0 || minutes !in 0..59 || seconds !in 0..59) return null
        return hours * 3600 + minutes * 60 + seconds
    }

    /**
     * Formats seconds from service day start into a GTFS `HH:MM:SS` string (preserving hours >= 24).
     */
    fun formatSecondsToGtfsTime(totalSeconds: Int): String {
        val coerced = totalSeconds.coerceAtLeast(0)
        val hours = coerced / 3600
        val minutes = (coerced % 3600) / 60
        val seconds = coerced % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    /**
     * Formats seconds from service day start into a 24-hour wall-clock `HH:mm` display string
     * (wrapping hours >= 24 around midnight, e.g. `25:15:00` -> `"01:15"`).
     */
    fun formatSecondsToClockTime(totalSeconds: Int): String {
        val normalized = ((totalSeconds % SECONDS_PER_DAY) + SECONDS_PER_DAY) % SECONDS_PER_DAY
        val hours = normalized / 3600
        val minutes = (normalized % 3600) / 60
        return String.format(Locale.US, "%02d:%02d", hours, minutes)
    }

    /**
     * Formats a [LocalDate] into GTFS `YYYYMMDD` format.
     */
    fun formatDateToGtfsDate(date: LocalDate): String = date.format(GTFS_DATE_FORMATTER)

    /**
     * Evaluates whether a [GtfsCalendarEntity] is active on the given [date] (before `calendar_dates.txt` exceptions).
     */
    fun isCalendarActiveOnDate(calendar: GtfsCalendarEntity, date: LocalDate): Boolean {
        val dateStr = formatDateToGtfsDate(date)
        if (dateStr < calendar.startDate || dateStr > calendar.endDate) return false
        return when (date.dayOfWeek) {
            DayOfWeek.MONDAY -> calendar.monday == 1
            DayOfWeek.TUESDAY -> calendar.tuesday == 1
            DayOfWeek.WEDNESDAY -> calendar.wednesday == 1
            DayOfWeek.THURSDAY -> calendar.thursday == 1
            DayOfWeek.FRIDAY -> calendar.friday == 1
            DayOfWeek.SATURDAY -> calendar.saturday == 1
            DayOfWeek.SUNDAY -> calendar.sunday == 1
        }
    }
}
