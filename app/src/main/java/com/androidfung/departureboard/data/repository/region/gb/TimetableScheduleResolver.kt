package com.androidfung.departureboard.data.repository.region.gb

import com.androidfung.departureboard.data.model.gb.StationServiceHoursInfo
import com.androidfung.departureboard.data.model.gb.TflKnownJourney
import com.androidfung.departureboard.data.model.gb.TflSchedule
import com.androidfung.departureboard.data.model.gb.TflTimetableResponse
import com.androidfung.departureboard.data.model.gb.TflTimetableRoute
import com.androidfung.departureboard.data.network.gb.TflApiService
import com.androidfung.departureboard.data.repository.region.gb.direction.DestinationResolver
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * Collaborator responsible for inspecting timetable schedules to detect service hours,
 * late-night gaps, first morning trains, last night trains, Night Tube verification,
 * and scheduled outbound destinations at terminus and shuttle-branch stations (such as Mill Hill East).
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
        var response = apiService.getLineTimetable(lineId, stationId)
        if (response.timetable == null && !response.disambiguation?.disambiguationOptions.isNullOrEmpty()) {
            for (opt in response.disambiguation.disambiguationOptions) {
                val uri = opt.uri ?: continue
                val toStopId = uri.substringAfterLast("/to/", "").substringBefore("?").trim()
                if (toStopId.isNotEmpty() && !toStopId.equals(stationId, ignoreCase = true)) {
                    try {
                        val disambiguated = apiService.getLineTimetableTo(lineId, stationId, toStopId)
                        if (disambiguated.timetable != null) {
                            response = disambiguated
                            break
                        }
                    } catch (_: Exception) {
                        // Try next disambiguation option
                    }
                }
            }
        }
        if (response.timetable != null) {
            timetableCache[key] = CachedTimetable(response, now)
        }
        return response
    }

    private fun selectSchedulesForDay(
        routes: List<TflTimetableRoute>,
        dayOfWeek: DayOfWeek
    ): List<TflSchedule> {
        val allSchedules = routes.flatMap { it.schedules }
        if (allSchedules.isEmpty()) return emptyList()

        val matched = allSchedules.filter { sched ->
            val name = sched.name.lowercase()
            when (dayOfWeek) {
                DayOfWeek.SATURDAY -> name.contains("saturday") || name.contains("sat")
                DayOfWeek.SUNDAY -> name.contains("sunday") || name.contains("sun")
                DayOfWeek.FRIDAY -> name.contains("friday") && !name.contains("good friday")
                else -> name.contains("monday") || name.contains("mon") || name.contains("weekday")
            }
        }
        return matched.ifEmpty { allSchedules }
    }

    /**
     * Checks scheduled timetables for a station as an authoritative safety-net to detect:
     * 1. Whether 24-hour Night Tube / Night Overground actually operates at this station tonight
     *    (verified by checking for scheduled departures in the 02:00-04:29 AM window),
     * 2. Whether service has concluded for the night and when the first morning train departs, or
     * 3. Whether the station is approaching its final train of the night.
     */
    suspend fun checkStationServiceHours(
        stationId: String,
        lineId: String? = null,
        nowOverride: ZonedDateTime? = null
    ): StationServiceHoursInfo? {
        try {
            // Determine candidate line to check
            val targetLineId = lineId ?: when {
                stationId.startsWith("940GZZLU") -> {
                    val detail = apiService.getStopPointDetail(stationId)
                    detail.lines.firstOrNull { it.modeName.equals("tube", ignoreCase = true) }?.id
                        ?: detail.lineModeGroups.flatMap { it.lineIdentifier }.firstOrNull()
                }
                else -> null
            } ?: return com.androidfung.departureboard.util.LastTrainServiceCalculator.evaluateServiceHours(
                stationId = stationId,
                lineId = lineId,
                nowOverride = nowOverride
            )

            val londonZone = ZoneId.of("Europe/London")
            val nowLondon = nowOverride ?: ZonedDateTime.now(londonZone)

            val timetableResponse = getCachedTimetable(targetLineId, stationId)
            val routes = timetableResponse.timetable?.routes.orEmpty()
            if (routes.isEmpty()) {
                return com.androidfung.departureboard.util.LastTrainServiceCalculator.evaluateServiceHours(
                    stationId = stationId,
                    lineId = targetLineId,
                    nowOverride = nowLondon
                )
            }

            // Between 00:00 and 04:29 AM, overnight service belongs to the previous service day
            val effectiveServiceDay = if (nowLondon.hour < 4 || (nowLondon.hour == 4 && nowLondon.minute < 30)) {
                nowLondon.dayOfWeek.minus(1)
            } else {
                nowLondon.dayOfWeek
            }

            val activeSchedules = selectSchedulesForDay(routes, effectiveServiceDay)
            val journeys = activeSchedules.flatMap { it.knownJourneys }
            if (journeys.isEmpty()) {
                return com.androidfung.departureboard.util.LastTrainServiceCalculator.evaluateServiceHours(
                    stationId = stationId,
                    lineId = targetLineId,
                    nowOverride = nowLondon
                )
            }

            // Timetable Safety-Net Crosscheck for 24hr Night Service:
            // A station only operates Night Tube / 24hr overnight service if its schedule for tonight
            // (or early tomorrow morning on the calendar day) contains journeys in the deep overnight
            // 02:00 - 04:29 AM window (hours 2, 3, or 26, 27, or 04:00-04:29 / 28:00-28:29).
            val nextMorningSchedules = selectSchedulesForDay(routes, effectiveServiceDay.plus(1))
            val combinedOvernightCheckJourneys = journeys + nextMorningSchedules.flatMap { it.knownJourneys }
            val hasOvernightTimetableTrips = combinedOvernightCheckJourneys.any { kj ->
                val rawHour = kj.hour.toIntOrNull() ?: return@any false
                val minute = kj.minute.toIntOrNull() ?: 0
                val clockHour = rawHour % 24
                clockHour in 2..3 || (clockHour == 4 && minute < 30)
            } && com.androidfung.departureboard.util.LastTrainServiceCalculator.isGbNightTubeStation(
                stationId = stationId,
                lineId = targetLineId
            )

            val isNightServiceWindow = nowLondon.hour in 0..4 || (nowLondon.hour == 23 && nowLondon.minute >= 30)
            if (hasOvernightTimetableTrips && isNightServiceWindow) {
                return StationServiceHoursInfo(
                    isClosedForNight = false,
                    lastTrainTime = null,
                    firstTrainTime = null,
                    lineName = timetableResponse.lineName ?: targetLineId,
                    minutesUntilLastTrain = null,
                    isApproachingLastTrain = false,
                    isNightServiceActive = true
                )
            }

            // Sort journeys chronologically by service day (starts ~04:30 AM through ~01:59 AM next day)
            // Normalize journeys: 00:00 - 04:29 are treated as late-night (add 24*60)
            val normalizedTimes = journeys.mapNotNull { kj ->
                val h = kj.hour.toIntOrNull() ?: return@mapNotNull null
                val m = kj.minute.toIntOrNull() ?: return@mapNotNull null
                val normMin = if (h < 4 || (h == 4 && m < 30)) (h + 24) * 60 + m else h * 60 + m
                normMin to kj.formattedTime
            }.sortedBy { it.first }

            val earliestMorning = normalizedTimes.firstOrNull { it.first in (4 * 60 + 30)..(8 * 60) }?.second
            val latestNightPair = normalizedTimes.lastOrNull()
            val latestNight = latestNightPair?.second
            val lastNorm = latestNightPair?.first
                ?: com.androidfung.departureboard.util.LastTrainServiceCalculator.parseNormalizedMinutes(latestNight)

            val nowH = nowLondon.hour
            val nowM = nowLondon.minute
            val nowNorm = if (nowH < 4 || (nowH == 4 && nowM < 30)) (nowH + 24) * 60 + nowM else nowH * 60 + nowM
            val minutesUntilLast = if (lastNorm != null && lastNorm > nowNorm) (lastNorm - nowNorm) else null

            val isClosedNow = com.androidfung.departureboard.util.LastTrainServiceCalculator.isInOvernightClosedWindow(
                nowHour = nowH,
                nowMinute = nowM,
                lastHhMm = latestNight,
                firstHhMm = earliestMorning ?: "05:20"
            )

            return if (isClosedNow) {
                StationServiceHoursInfo(
                    isClosedForNight = true,
                    lastTrainTime = latestNight,
                    firstTrainTime = earliestMorning,
                    lineName = timetableResponse.lineName ?: targetLineId,
                    isNightServiceActive = false
                )
            } else if (minutesUntilLast != null && minutesUntilLast in 1..com.androidfung.departureboard.util.LastTrainServiceCalculator.APPROACHING_WINDOW_MINUTES) {
                StationServiceHoursInfo(
                    isClosedForNight = false,
                    lastTrainTime = latestNight,
                    firstTrainTime = earliestMorning,
                    lineName = timetableResponse.lineName ?: targetLineId,
                    minutesUntilLastTrain = minutesUntilLast,
                    isApproachingLastTrain = true,
                    isNightServiceActive = false
                )
            } else {
                // Authoritative timetable crosscheck confirms regular service is operating normally
                // and last train is > 90 minutes away (and Night Tube is NOT active at this station).
                null
            }
        } catch (_: Exception) {
            return com.androidfung.departureboard.util.LastTrainServiceCalculator.evaluateServiceHours(
                stationId = stationId,
                lineId = lineId,
                nowOverride = nowOverride
            )
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
                        DayOfWeek.SATURDAY -> name.contains("saturday")
                        DayOfWeek.SUNDAY -> name.contains("sunday")
                        DayOfWeek.FRIDAY -> name.contains("friday") && !name.contains("good friday")
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
                ?: com.androidfung.departureboard.data.model.gb.TflStationIds.CANONICAL_NAMES_BY_ID[terminalStopId.uppercase()]
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
