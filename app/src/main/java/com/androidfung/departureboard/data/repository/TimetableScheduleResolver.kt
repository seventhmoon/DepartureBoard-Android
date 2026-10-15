package com.androidfung.departureboard.data.repository

import com.androidfung.departureboard.data.model.StationServiceHoursInfo
import com.androidfung.departureboard.data.network.TflApiService
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Collaborator responsible for inspecting timetable schedules to detect service hours,
 * late-night gaps, first morning trains, and last night trains.
 */
class TimetableScheduleResolver(
    private val apiService: TflApiService
) {

    /**
     * Checks scheduled timetables for a station to detect if service has concluded for the night
     * and when the first morning train departs.
     */
    suspend fun checkStationServiceHours(
        stationId: String,
        lineId: String? = null
    ): StationServiceHoursInfo? {
        try {
            // Determine candidate line to check
            val targetLineId = lineId ?: when {
                stationId.startsWith("940GZZLU") -> {
                    // Query StopPoint detail to find lines
                    val detail = apiService.getStopPointDetail(stationId)
                    detail.lines.firstOrNull { it.modeName.equals("tube", ignoreCase = true) }?.id
                        ?: detail.lineModeGroups.flatMap { it.lineIdentifier }.firstOrNull()
                }
                else -> null
            } ?: return null

            // Get current London time
            val londonZone = ZoneId.of("Europe/London")
            val nowLondon = ZonedDateTime.now(londonZone)

            val timetableResponse = apiService.getLineTimetable(targetLineId, stationId)
            val schedules = timetableResponse.timetable?.routes?.flatMap { it.schedules } ?: emptyList()

            // Find all known journey departure times
            val journeys = schedules.flatMap { it.knownJourneys }
            if (journeys.isEmpty()) return null

            // Sort journeys chronologically by service day (starts ~04:30 AM through ~01:30 AM next day)
            // Normalize journeys: 00:00 - 04:00 are treated as late-night (add 24*60)
            val normalizedTimes = journeys.map { kj ->
                val h = kj.hour.toIntOrNull() ?: 0
                val m = kj.minute.toIntOrNull() ?: 0
                val normMin = if (h < 4) (h + 24) * 60 + m else h * 60 + m
                normMin to kj.formattedTime
            }.sortedBy { it.first }

            val earliestMorning = normalizedTimes.firstOrNull { it.first in (4 * 60)..(7 * 60) }?.second
            val latestNight = normalizedTimes.lastOrNull()?.second

            // Check if current London time is in late night gap (typically after 00:30 and before 05:00)
            val isLateNight = nowLondon.hour in 0..4 || (nowLondon.hour == 23 && nowLondon.minute >= 45)

            return if (isLateNight) {
                StationServiceHoursInfo(
                    isClosedForNight = true,
                    lastTrainTime = latestNight,
                    firstTrainTime = earliestMorning,
                    lineName = timetableResponse.lineName ?: targetLineId
                )
            } else {
                null
            }
        } catch (_: Exception) {
            return null
        }
    }
}
