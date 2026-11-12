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
        "london-overground",
        "overground"
    )

    /**
     * Northern line stations that do NOT receive Night Tube service:
     * - Mill Hill East single-track branch
     * - Bank branch (Angel, Old Street, Moorgate, Bank, London Bridge, Borough, Elephant & Castle)
     * - Battersea Power Station extension (Nine Elms, Battersea Power Station)
     */
    private val NORTHERN_NON_NIGHT_TUBE_STATIONS = setOf(
        "940GZZLUMHL", "MHL", "MHE", // Mill Hill East
        "940GZZLUAGL", "ANG",        // Angel
        "940GZZLUODS", "OLD",        // Old Street
        "940GZZLUMGT", "MGT",        // Moorgate
        "940GZZLUBNK", "HUBBAN", "BNK", // Bank
        "940GZZLULNB", "HUBLBG", "LBG", // London Bridge
        "940GZZLUBOR", "BOR",        // Borough
        "940GZZLUEAC", "940GZZLUEHA", "EAC", // Elephant & Castle
        "940GZZLUNEL", "NEL",        // Nine Elms
        "940GZZBPSUST", "940GZZLUBPS", "BPS" // Battersea Power Station
    )

    /**
     * Central line stations that do NOT receive Night Tube service:
     * - West Ruislip branch west of North Acton (West Acton -> West Ruislip)
     * - Hainault loop via Chigwell (Roding Valley, Chigwell, Grange Hill)
     * - Epping section north of Loughton (Debden, Theydon Bois, Epping)
     */
    private val CENTRAL_NON_NIGHT_TUBE_STATIONS = setOf(
        "940GZZLUWAC", "WAC", // West Acton
        "940GZZLUHGR", "HGR", // Hanger Lane
        "940GZZLUPVL", "PVL", // Perivale
        "940GZZLUGFD", "GFD", // Greenford
        "940GZZLUNHT", "NHT", // Northolt
        "940GZZLUSRP", "SRP", // South Ruislip
        "940GZZLURSG", "RSG", // Ruislip Gardens
        "940GZZLUWRP", "WRP", // West Ruislip
        "940GZZLURVY", "RVY", // Roding Valley
        "940GZZLUCWL", "CWL", // Chigwell
        "940GZZLUGGH", "GGH", // Grange Hill
        "940GZZLUDBD", "DBD", // Debden
        "940GZZLUTHB", "THB", // Theydon Bois
        "940GZZLUEPG", "EPG"  // Epping
    )

    /**
     * Piccadilly line stations that do NOT receive Night Tube service:
     * - Heathrow Terminal 4 loop
     * - Uxbridge branch west of Acton Town (Ealing Common -> Uxbridge)
     */
    private val PICCADILLY_NON_NIGHT_TUBE_STATIONS = setOf(
        "940GZZLUHR4", "HR4", // Heathrow Terminal 4
        "940GZZLUECM", "ECM", // Ealing Common
        "940GZZLUNEN", "NEN", // North Ealing
        "940GZZLUPKR", "PKR", // Park Royal
        "940GZZLUALP", "ALP", // Alperton
        "940GZZLUSUT", "SUT", // Sudbury Town
        "940GZZLUSUH", "SUH", // Sudbury Hill
        "940GZZLUSHH", "SHH", // South Harrow
        "940GZZLURYL", "RYL", // Rayners Lane
        "940GZZLUEAE", "EAE", // Eastcote
        "940GZZLURSM", "RSM", // Ruislip Manor
        "940GZZLURSP", "RSP", // Ruislip
        "940GZZLUICK", "ICK", // Ickenham
        "940GZZLUHGD", "HGD", // Hillingdon
        "940GZZLUUXB", "UXB"  // Uxbridge
    )

    /**
     * London Overground (Windrush line) Night Service ONLY operates between
     * Highbury & Islington and New Cross Gate.
     */
    private val OVERGROUND_NIGHT_SERVICE_STATIONS = setOf(
        "910GHGHI", "940GZZLUHBY", "HBY",    // Highbury & Islington
        "910GCNNB", "CNN",                   // Canonbury
        "910GDALS", "DLJ",                   // Dalston Junction
        "910GHAGG", "HGG",                   // Haggerston
        "910GHOXT", "HOX",                   // Hoxton
        "910GSHRDHST", "SDC",                // Shoreditch High Street
        "910GWCHAPEL", "940GZZLUWPL", "WPL", // Whitechapel
        "910GSHADWEL", "SDE",                // Shadwell
        "910GWAPPING", "WPE",                // Wapping
        "910GRTHERHI", "ROE",                // Rotherhithe
        "910GCANADAW", "940GZZLUCWR", "HUBCAW", "CWR", // Canada Water
        "910GSURREYQ", "SQE",                // Surrey Quays
        "910GNWCRELL", "NXG"                 // New Cross Gate
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
     * Checks whether a specific GB station and line actually receives Friday/Saturday Night Tube
     * or Night Overground service.
     */
    fun isGbNightTubeStation(stationId: String, lineId: String?): Boolean {
        val cleanLine = lineId?.trim()?.lowercase() ?: return false
        if (!GB_NIGHT_TUBE_LINES.contains(cleanLine)) return false

        val upperStation = stationId.trim().uppercase()
        val shortCode = if (upperStation.startsWith("940GZZLU") && upperStation.length >= 11) {
            upperStation.substring(8)
        } else {
            upperStation
        }

        return when (cleanLine) {
            "victoria", "jubilee" -> true
            "northern" -> upperStation !in NORTHERN_NON_NIGHT_TUBE_STATIONS &&
                shortCode !in NORTHERN_NON_NIGHT_TUBE_STATIONS
            "central" -> upperStation !in CENTRAL_NON_NIGHT_TUBE_STATIONS &&
                shortCode !in CENTRAL_NON_NIGHT_TUBE_STATIONS
            "piccadilly" -> upperStation !in PICCADILLY_NON_NIGHT_TUBE_STATIONS &&
                shortCode !in PICCADILLY_NON_NIGHT_TUBE_STATIONS
            "windrush", "london-overground", "overground" ->
                upperStation in OVERGROUND_NIGHT_SERVICE_STATIONS ||
                    shortCode in OVERGROUND_NIGHT_SERVICE_STATIONS
            else -> false
        }
    }

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
        val isNightTubeStationAndLine = region == TransitRegion.GB &&
            isWeekendNight &&
            isGbNightTubeStation(stationId, candidateLineId)

        val hasNightBus = departures.any { isNightBusRoute(it.lineId) || isNightBusRoute(it.lineName) }
        val isNightHours = now.hour in 0..4 || (now.hour == 23 && now.minute >= 30)

        if ((isNightTubeStationAndLine || hasNightBus) && isNightHours) {
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

        // 1. Check if currently in the overnight closed window (after lastTrain and before firstTrain)
        val isOvernightClosedWindow = isInOvernightClosedWindow(
            nowHour = now.hour,
            nowMinute = now.minute,
            lastHhMm = defaultLastTrain,
            firstHhMm = defaultFirstTrain
        )
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
     *
     * @param timetableChecked When true, [existingServiceHours] comes from an authoritative timetable
     *                         safety-net check (where `null` means normal daytime/evening service verified by timetable).
     */
    fun enrichDeparturesAndServiceHours(
        station: Station,
        departures: List<Departure>,
        existingServiceHours: StationServiceHoursInfo? = null,
        nowOverride: ZonedDateTime? = null,
        timetableChecked: Boolean = false
    ): Pair<List<Departure>, StationServiceHoursInfo?> {
        val region = TransitRegion.fromStationId(station.id)
        val now = nowOverride ?: ZonedDateTime.now(region.timeZone)
        val serviceHours = if (timetableChecked) {
            existingServiceHours
        } else {
            existingServiceHours ?: evaluateServiceHours(
                stationId = station.id,
                lineId = station.lines.firstOrNull()?.id ?: departures.firstOrNull()?.lineId,
                departures = departures,
                nowOverride = now
            )
        }

        if (departures.isEmpty()) {
            return emptyList<Departure>() to serviceHours
        }

        val isWeekendNight = isWeekendServiceNight(now)
        val isLateNightServiceWindow = now.hour in 0..4
        val scheduledMinsUntilLast = serviceHours?.minutesUntilLastTrain
        val isApproachingLastTrain = serviceHours?.isApproachingLastTrain == true &&
            (scheduledMinsUntilLast ?: 999) <= 45

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

        val nightServiceAllowedByTimetable = if (timetableChecked) {
            serviceHours?.isNightServiceActive == true
        } else {
            true
        }

        val enriched = departures.map { dep ->
            val nightBus = isNightBusRoute(dep.lineId) || isNightBusRoute(dep.lineName)
            val nightTube = nightServiceAllowedByTimetable &&
                region == TransitRegion.GB &&
                isWeekendNight &&
                isLateNightServiceWindow &&
                isGbNightTubeStation(station.id, dep.lineId)

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

        // Reconcile serviceHours with live departures when within the 35-minute live prediction horizon
        // (matching evaluateServiceHours) or when live tracked trains extend past the scheduled last train,
        // ensuring the Last Train Home banner and the [LAST TRAIN] row badge show the same time & countdown.
        val reconciledServiceHours = if (serviceHours?.isApproachingLastTrain == true && enriched.isNotEmpty()) {
            val latestTrainDep = enriched
                .filter { it.status != DepartureStatus.CANCELLED && !isNightBusRoute(it.lineId) && !it.isNightService }
                .maxByOrNull { it.timeToStationSeconds }

            if (latestTrainDep != null) {
                val liveMinutes = (latestTrainDep.timeToStationSeconds / 60).coerceAtLeast(1)
                val scheduledMins = serviceHours.minutesUntilLastTrain ?: 0

                val shouldAlignWithLive = liveMinutes > scheduledMins || scheduledMins <= 35
                if (shouldAlignWithLive) {
                    val liveClock = latestTrainDep.formattedActualClockTime
                        ?: run {
                            val liveArrivalZdt = now.plusSeconds(latestTrainDep.timeToStationSeconds.toLong())
                            String.format(java.util.Locale.US, "%02d:%02d", liveArrivalZdt.hour, liveArrivalZdt.minute)
                        }
                    serviceHours.copy(
                        lastTrainTime = liveClock,
                        minutesUntilLastTrain = liveMinutes
                    )
                } else {
                    serviceHours
                }
            } else {
                serviceHours
            }
        } else {
            serviceHours
        }

        return enriched to reconciledServiceHours
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
        val raw = parseRawMinutesOfDay(hhMm) ?: return null
        return normalizeServiceMinutes(raw / 60, raw % 60)
    }

    internal fun parseRawMinutesOfDay(hhMm: String?): Int? {
        if (hhMm.isNullOrBlank()) return null
        val parts = hhMm.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    /**
     * Circular 24-hour check determining whether `(nowHour:nowMinute)` falls strictly after `lastHhMm`
     * and strictly before `firstHhMm` (handling both pre-midnight `23:55 -> 05:30` and post-midnight `00:35 -> 06:30`).
     */
    internal fun isInOvernightClosedWindow(
        nowHour: Int,
        nowMinute: Int,
        lastHhMm: String?,
        firstHhMm: String?
    ): Boolean {
        val lastMins = parseRawMinutesOfDay(lastHhMm) ?: return false
        val firstMins = parseRawMinutesOfDay(firstHhMm) ?: (5 * 60 + 30)
        val nowMins = nowHour * 60 + nowMinute
        return if (lastMins > firstMins) {
            // Spans midnight (e.g. last 23:55, first 05:30)
            nowMins > lastMins || nowMins < firstMins
        } else {
            // Both after midnight (e.g. last 00:35, first 06:30)
            nowMins > lastMins && nowMins < firstMins
        }
    }
}
