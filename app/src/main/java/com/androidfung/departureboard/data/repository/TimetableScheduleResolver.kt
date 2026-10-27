package com.androidfung.departureboard.data.repository

import com.androidfung.departureboard.data.model.StationServiceHoursInfo
import com.androidfung.departureboard.data.model.TflKnownJourney
import com.androidfung.departureboard.data.model.TflTimetableResponse
import com.androidfung.departureboard.data.model.TflTimetableRoute
import com.androidfung.departureboard.data.network.TflApiService
import com.androidfung.departureboard.data.repository.direction.DestinationResolver
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * Collaborator responsible for inspecting timetable schedules to detect service hours,
 * late-night gaps, first morning trains, last night trains, and scheduled outbound destinations
 * at terminus and shuttle-branch stations (such as Mill Hill East).
 */
class TimetableScheduleResolver(
    private val apiService: TflApiService
) {

    data class ScheduledDepartureMatch(
        val destinationName: String,
        val destinationNaptanId: String?,
        val scheduledDepartureEpochSeconds: Long? = null
    )

    private data class CachedTimetable(
        val response: TflTimetableResponse,
        val fetchedAtMillis: Long
    )

    private val timetableCache = ConcurrentHashMap<String, CachedTimetable>()

    companion object {
        private const val CACHE_TTL_MILLIS = 60 * 60 * 1000L // 1 hour
    }

    private suspend fun getCachedTimetable(lineId: String, stationId: String): TflTimetableResponse {
        val key = "${lineId.lowercase()}_${stationId.uppercase()}"
        val now = System.currentTimeMillis()
        val cached = timetableCache[key]
        if (cached != null && (now - cached.fetchedAtMillis) < CACHE_TTL_MILLIS) {
            return cached.response
        }
        val response = apiService.getLineTimetable(lineId, stationId)
        if (response.timetable != null) {
            timetableCache[key] = CachedTimetable(response, now)
        }
        return response
    }

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

            val timetableResponse = getCachedTimetable(targetLineId, stationId)
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

    /**
     * Resolves the scheduled outbound destination from the line timetable by matching an expected departure time.
     * Useful when live predictions at a terminus/shuttle station (like Mill Hill East) do not yet have an outbound
     * destination published by TfL.
     */
    suspend fun resolveScheduledDestination(
        stationId: String,
        lineId: String,
        departureEpochSeconds: Long
    ): String? {
        return resolveScheduledDeparture(
            stationId = stationId,
            lineId = lineId,
            departureEpochSeconds = departureEpochSeconds
        )?.destinationName
    }

    /**
     * Resolves the scheduled outbound destination, destination NaPTAN ID, and scheduled departure epoch time
     * from the line timetable by matching the earliest physically possible outbound departure time.
     *
     * @param stationId The station stopPointId (e.g. "940GZZLUMHL")
     * @param lineId Line identifier (e.g. "northern")
     * @param departureEpochSeconds The estimated departure timestamp in epoch seconds (UTC)
     * @param earliestDepartureEpochSeconds Optional floor timestamp (e.g. arrival + minimum stepping-back dwell)
     *                                      so an inbound train never matches a previous departure that already left.
     */
    suspend fun resolveScheduledDeparture(
        stationId: String,
        lineId: String,
        departureEpochSeconds: Long,
        earliestDepartureEpochSeconds: Long? = null
    ): ScheduledDepartureMatch? {
        return try {
            val timetable = getCachedTimetable(lineId, stationId)
            val routes = timetable.timetable?.routes?.takeIf { it.isNotEmpty() } ?: return null

            val londonZone = ZoneId.of("Europe/London")
            val targetZdt = java.time.Instant.ofEpochSecond(departureEpochSeconds).atZone(londonZone)
            val targetDayOfWeek = targetZdt.dayOfWeek
            val targetMinutes = normalizeServiceDayMinutes(targetZdt.hour, targetZdt.minute)

            val earliestMinutes = earliestDepartureEpochSeconds?.let { earliestEpoch ->
                val earliestZdt = java.time.Instant.ofEpochSecond(earliestEpoch).atZone(londonZone)
                normalizeServiceDayMinutes(earliestZdt.hour, earliestZdt.minute)
            }

            data class CandidateJourney(
                val route: TflTimetableRoute,
                val journey: TflKnownJourney,
                val normalizedMinutes: Int
            )

            val candidates = mutableListOf<CandidateJourney>()
            for (route in routes) {
                if (route.schedules.isEmpty()) continue
                val schedule = route.schedules.firstOrNull { sched ->
                    val name = sched.name.lowercase()
                    when (targetDayOfWeek) {
                        java.time.DayOfWeek.SATURDAY -> name.contains("saturday")
                        java.time.DayOfWeek.SUNDAY -> name.contains("sunday")
                        java.time.DayOfWeek.FRIDAY -> name.contains("friday") && !name.contains("good friday")
                        else -> name.contains("monday") || name.contains("mon")
                    }
                } ?: route.schedules.first()

                for (kj in schedule.knownJourneys) {
                    val h = kj.hour.toIntOrNull() ?: continue
                    val m = kj.minute.toIntOrNull() ?: continue
                    candidates.add(CandidateJourney(route, kj, normalizeServiceDayMinutes(h, m)))
                }
            }

            if (candidates.isEmpty()) return null

            // Prefer the earliest upcoming scheduled departure at or after (earliestMinutes - 1 min tolerance)
            // within a 20-minute turnaround window; otherwise fall back to closest within ±15 minutes.
            val matchedCandidate = if (earliestMinutes != null) {
                candidates
                    .filter { it.normalizedMinutes in (earliestMinutes - 1)..(earliestMinutes + 20) }
                    .minByOrNull { kotlin.math.abs(it.normalizedMinutes - targetMinutes) }
                    ?: candidates
                        .filter { kotlin.math.abs(it.normalizedMinutes - targetMinutes) <= 15 }
                        .minByOrNull { kotlin.math.abs(it.normalizedMinutes - targetMinutes) }
            } else {
                candidates
                    .filter { kotlin.math.abs(it.normalizedMinutes - targetMinutes) <= 15 }
                    .minByOrNull { kotlin.math.abs(it.normalizedMinutes - targetMinutes) }
            } ?: return null

            val stationInterval = matchedCandidate.route.stationIntervals.firstOrNull {
                it.id == matchedCandidate.journey.intervalId.toString()
            } ?: return null

            val intervals = stationInterval.intervals
            if (intervals.isEmpty()) return null

            val terminalStopId = intervals.last().stopId
            val terminalStationName = timetable.stations.firstOrNull {
                it.id.equals(terminalStopId, ignoreCase = true)
            }?.name?.let { StationNameFormatter.clean(it) }
                ?: com.androidfung.departureboard.data.model.TflStationIds.CANONICAL_NAMES_BY_ID[terminalStopId.uppercase()]
                ?: return null

            val stops = intervals.map { it.stopId }
            val hasCharingCross = stops.any { it.contains("CHX", ignoreCase = true) }
            val hasBank = stops.any { it.contains("BNK", ignoreCase = true) }

            val resolvedDestinationName = when {
                hasCharingCross && !terminalStationName.contains("via", ignoreCase = true) -> "$terminalStationName via Charing Cross"
                hasBank && !terminalStationName.contains("via", ignoreCase = true) -> "$terminalStationName via Bank"
                else -> terminalStationName
            }

            val scheduledEpochSeconds = computeJourneyEpochSeconds(
                referenceZdt = targetZdt,
                journey = matchedCandidate.journey
            )

            val resolvedNaptanId = terminalStopId.ifBlank {
                DestinationResolver.resolveDestinationNaptanId(resolvedDestinationName, lineId)
            }

            ScheduledDepartureMatch(
                destinationName = resolvedDestinationName,
                destinationNaptanId = resolvedNaptanId,
                scheduledDepartureEpochSeconds = scheduledEpochSeconds
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeServiceDayMinutes(hour: Int, minute: Int): Int {
        return if (hour < 4) (hour + 24) * 60 + minute else hour * 60 + minute
    }

    private fun computeJourneyEpochSeconds(
        referenceZdt: ZonedDateTime,
        journey: TflKnownJourney
    ): Long? {
        val h = journey.hour.toIntOrNull() ?: return null
        val m = journey.minute.toIntOrNull() ?: return null
        val baseDate = if (referenceZdt.hour < 4 && h >= 4) {
            referenceZdt.toLocalDate().minusDays(1)
        } else if (referenceZdt.hour >= 4 && h < 4) {
            referenceZdt.toLocalDate().plusDays(1)
        } else {
            referenceZdt.toLocalDate()
        }
        return baseDate.atTime(h % 24, m).atZone(referenceZdt.zone).toEpochSecond()
    }
}
