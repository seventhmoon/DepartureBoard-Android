package com.androidfung.departureboard.util

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.StationServiceHoursInfo
import com.androidfung.departureboard.data.model.TransitRegion
import com.androidfung.departureboard.data.model.hk.HkMtrLine
import com.androidfung.departureboard.data.model.hk.parseHkMtrStationId
import java.time.DayOfWeek
import java.time.ZonedDateTime

/**
 * Multi-region calculator for "Last Train Home" countdowns, overnight service closures,
 * and Night Tube / Night Bus service detection across GB, HK, SG, AU, and JP.
 */
object LastTrainServiceCalculator {

    /**
     * Warning window in minutes before the final train of the night (90 minutes).
     */
    const val APPROACHING_WINDOW_MINUTES = 90

    private val GB_NIGHT_TUBE_LINES = setOf(
        "central",
        "jubilee",
        "northern",
        "piccadilly",
        "victoria",
        "windrush",
        "london-overground"
    )

    private val NIGHT_BUS_REGEX = Regex("""^(?:N|NA)\d+[A-Z]?$""", RegexOption.IGNORE_CASE)

    private val HK_LINE_HOURS = mapOf(
        "TKL" to ("01:05" to "06:00"),
        "ISL" to ("00:55" to "05:55"),
        "TWL" to ("00:50" to "06:00"),
        "KTL" to ("00:50" to "06:00"),
        "EAL" to ("00:40" to "05:30"),
        "TML" to ("00:45" to "05:45"),
        "TCL" to ("00:50" to "06:00"),
        "AEL" to ("00:48" to "05:50"),
        "SIL" to ("01:05" to "06:00"),
        "DRL" to ("00:40" to "06:15")
    )

    /**
     * Evaluates service hours, approaching last-train countdown, or active night service
     * for [stationId] in its regional timezone.
     */
    fun evaluateServiceHours(
        stationId: String,
        lineId: String? = null,
        departures: List<Departure> = emptyList(),
        nowOverride: ZonedDateTime? = null
    ): StationServiceHoursInfo? {
        val region = TransitRegion.fromStationId(stationId)
        val now = nowOverride ?: ZonedDateTime.now(region.timeZone)
        val candidateLineId = lineId ?: departures.firstOrNull()?.lineId

        val (defaultLastTrain, defaultFirstTrain, lineDisplayName) = resolveScheduleTemplate(
            region = region,
            stationId = stationId,
            lineId = candidateLineId,
            now = now
        )

        val isWeekendNight = isWeekendServiceNight(now)
        val isNightTubeLine = region == TransitRegion.GB &&
            isWeekendNight &&
            candidateLineId != null &&
            GB_NIGHT_TUBE_LINES.contains(candidateLineId.lowercase())

        val hasNightBus = departures.any { isNightBusRoute(it.lineId) || isNightBusRoute(it.lineName) }
        val isNightHours = now.hour in 0..4 || now.hour == 23

        if ((isNightTubeLine || hasNightBus) && isNightHours) {
            return StationServiceHoursInfo(
                isClosedForNight = false,
                lastTrainTime = defaultLastTrain,
                firstTrainTime = defaultFirstTrain,
                lineName = lineDisplayName,
                minutesUntilLastTrain = null,
                isApproachingLastTrain = false,
                isNightServiceActive = true
            )
        }

        val nowNorm = normalizeServiceMinutes(now.hour, now.minute)
        val lastNorm = parseNormalizedMinutes(defaultLastTrain) ?: return null
        val firstNorm = parseNormalizedMinutes(defaultFirstTrain) ?: (5 * 60 + 30)

        // 1. Check if currently in the overnight closed window (after lastTrain and before firstTrain)
        val isOvernightClosedWindow = nowNorm > lastNorm && nowNorm < firstNorm + 24 * 60 && now.hour in 0..5
        if (isOvernightClosedWindow && departures.isEmpty()) {
            return StationServiceHoursInfo(
                isClosedForNight = true,
                lastTrainTime = defaultLastTrain,
                firstTrainTime = defaultFirstTrain,
                lineName = lineDisplayName,
                minutesUntilLastTrain = null,
                isApproachingLastTrain = false,
                isNightServiceActive = false
            )
        }

        // 2. Check if within the "Last Train Home" approaching countdown window (<= 90 mins before last train)
        val diffToLastTrain = lastNorm - nowNorm
        if (diffToLastTrain in 1..APPROACHING_WINDOW_MINUTES) {
            // If we have live departures, refine the last train time/countdown if a later departure exists
            val latestLiveDeparture = departures
                .filter { it.status != DepartureStatus.CANCELLED && !isNightBusRoute(it.lineId) }
                .maxByOrNull { it.timeToStationSeconds }

            val effectiveMinutes = if (latestLiveDeparture != null && diffToLastTrain <= 35) {
                (latestLiveDeparture.timeToStationSeconds / 60).coerceAtLeast(1)
            } else {
                diffToLastTrain
            }
            val effectiveLastClock = if (latestLiveDeparture != null && diffToLastTrain <= 35) {
                latestLiveDeparture.formattedActualClockTime ?: defaultLastTrain
            } else {
                defaultLastTrain
            }

            return StationServiceHoursInfo(
                isClosedForNight = false,
                lastTrainTime = effectiveLastClock,
                firstTrainTime = defaultFirstTrain,
                lineName = lineDisplayName,
                minutesUntilLastTrain = effectiveMinutes,
                isApproachingLastTrain = true,
                isNightServiceActive = false
            )
        }

        return null
    }

    /**
     * Enriches a list of [Departure] items with [Departure.isLastTrain] and [Departure.isNightService] flags,
     * and returns the station's [StationServiceHoursInfo] if in late-night / approaching-last-train hours.
     */
    fun enrichDeparturesAndServiceHours(
        station: Station,
        departures: List<Departure>,
        existingServiceHours: StationServiceHoursInfo? = null,
        nowOverride: ZonedDateTime? = null
    ): Pair<List<Departure>, StationServiceHoursInfo?> {
        val region = TransitRegion.fromStationId(station.id)
        val now = nowOverride ?: ZonedDateTime.now(region.timeZone)
        val serviceHours = existingServiceHours ?: evaluateServiceHours(
            stationId = station.id,
            lineId = station.lines.firstOrNull()?.id ?: departures.firstOrNull()?.lineId,
            departures = departures,
            nowOverride = now
        )

        if (departures.isEmpty()) {
            return emptyList<Departure>() to serviceHours
        }

        val isWeekendNight = isWeekendServiceNight(now)
        val isLateNightServiceWindow = now.hour in 0..4
        val isApproachingLastTrain = serviceHours?.isApproachingLastTrain == true &&
            (serviceHours.minutesUntilLastTrain ?: 999) <= 45

        // Group non-cancelled departures by line + direction/platform to identify the final train in each direction
        val lastDepartureIds = if (isApproachingLastTrain) {
            departures
                .filter { it.status != DepartureStatus.CANCELLED && !isNightBusRoute(it.lineId) }
                .groupBy { "${it.lineId.lowercase()}_${(it.direction ?: it.platformName).lowercase()}" }
                .mapNotNull { (_, group) ->
                    val latest = group.maxByOrNull { it.timeToStationSeconds }
                    // Tag as last train if we're within 45 mins of last train and this is the furthest prediction in this direction
                    latest?.id
                }
                .toSet()
        } else {
            emptySet()
        }

        val enriched = departures.map { dep ->
            val nightBus = isNightBusRoute(dep.lineId) || isNightBusRoute(dep.lineName)
            val nightTube = region == TransitRegion.GB &&
                isWeekendNight &&
                isLateNightServiceWindow &&
                GB_NIGHT_TUBE_LINES.contains(dep.lineId.lowercase())

            val isNight = dep.isNightService || nightBus || nightTube
            val isLast = dep.isLastTrain || (!isNight && lastDepartureIds.contains(dep.id))

            if (isNight != dep.isNightService || isLast != dep.isLastTrain) {
                dep.copy(
                    isLastTrain = isLast,
                    isNightService = isNight
                )
            } else {
                dep
            }
        }

        return enriched to serviceHours
    }

    fun isNightBusRoute(lineIdOrName: String?): Boolean {
        if (lineIdOrName.isNullOrBlank()) return false
        return NIGHT_BUS_REGEX.matches(lineIdOrName.trim())
    }

    private fun isWeekendServiceNight(now: ZonedDateTime): Boolean {
        // Friday night (Fri >= 22:00 or Sat < 05:00) or Saturday night (Sat >= 22:00 or Sun < 05:00)
        val dow = now.dayOfWeek
        val h = now.hour
        return (dow == DayOfWeek.FRIDAY && h >= 22) ||
            (dow == DayOfWeek.SATURDAY && (h < 5 || h >= 22)) ||
            (dow == DayOfWeek.SUNDAY && h < 5)
    }

    private fun resolveScheduleTemplate(
        region: TransitRegion,
        stationId: String,
        lineId: String?,
        now: ZonedDateTime
    ): Triple<String, String, String?> {
        val isWeekend = isWeekendServiceNight(now)
        return when (region) {
            TransitRegion.HK -> {
                val parsed = stationId.parseHkMtrStationId()
                val code = (lineId ?: parsed.lineCode)?.uppercase()
                val (last, first) = HK_LINE_HOURS[code] ?: ("00:55" to "05:55")
                val name = code?.let { HkMtrLine.getLineName(it) }
                Triple(last, first, name)
            }
            TransitRegion.SG -> {
                val last = if (isWeekend) "00:15" else "23:55"
                Triple(last, "05:30", lineId ?: "SMRT / SBS Transit")
            }
            TransitRegion.AU -> {
                val last = if (isWeekend) "01:15" else "00:45"
                Triple(last, "04:45", lineId ?: "Sydney Trains / Metro")
            }
            TransitRegion.JP -> {
                Triple("00:25", "04:45", lineId ?: "Tokyo Metro / JR East")
            }
            TransitRegion.GB -> {
                val last = if (isWeekend) "00:45" else "00:35"
                val first = if (now.dayOfWeek == DayOfWeek.SUNDAY) "06:30" else "05:20"
                Triple(last, first, lineId)
            }
        }
    }

    /**
     * Normalizes hours/minutes into a continuous service day where 00:00..03:59 are 24:00..27:59.
     */
    internal fun normalizeServiceMinutes(hour: Int, minute: Int): Int {
        return if (hour < 4) (hour + 24) * 60 + minute else hour * 60 + minute
    }

    internal fun parseNormalizedMinutes(hhMm: String?): Int? {
        if (hhMm.isNullOrBlank()) return null
        val parts = hhMm.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return normalizeServiceMinutes(h, m)
    }
}
