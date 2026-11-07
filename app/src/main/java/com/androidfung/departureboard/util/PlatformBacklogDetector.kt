package com.androidfung.departureboard.util

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.TransitMode
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/**
 * An alert raised when an expected high-frequency line experiences an abnormal gap,
 * leading to platform crowding and backlog.
 */
data class PlatformBacklogAlert(
    val lineId: String,
    val lineName: String,
    val direction: String?,
    val minutesUntilNext: Int,
    val typicalHeadwayMinutes: Int,
    val message: String
)

/**
 * Transit service period based on time-of-day and day-of-week.
 */
enum class TransitServicePeriod {
    PEAK,
    OFF_PEAK,
    LATE_NIGHT
}

/**
 * Headway profile defining typical intervals for different service periods.
 */
data class HeadwayProfile(
    val peakMinutes: Int,
    val offPeakMinutes: Int,
    val lateNightMinutes: Int = (offPeakMinutes * 1.5).toInt().coerceAtLeast(offPeakMinutes)
) {
    fun forPeriod(period: TransitServicePeriod): Int = when (period) {
        TransitServicePeriod.PEAK -> peakMinutes
        TransitServicePeriod.OFF_PEAK -> offPeakMinutes
        TransitServicePeriod.LATE_NIGHT -> lateNightMinutes
    }
}

/**
 * Evaluates whether transit platforms have an abnormal backlog / delay gap.
 * Dynamically resolves normal headway using:
 * 1. GTFS explicit frequency metadata
 * 2. Scheduled timetable departure instants
 * 3. Consecutive live prediction gaps
 * 4. Preprocessed branch station timetable profiles (from TfL /Line/{id}/Timetable/{stopId})
 * 5. Preprocessed operator & line timetable profiles with Peak / Off-Peak / Late Night awareness
 *    (from TfL Unified API and National Rail Darwin timetable feeds, see scripts/preprocess_headways.py)
 * 6. TransitMode fallbacks (National Rail 15-20m vs Metro 3-5m vs Tram 4-7m)
 */
object PlatformBacklogDetector {

    /**
     * Resolves the transit service period based on time-of-day and day-of-week.
     * Weekdays feature morning (06:30-09:30) and evening (16:00-19:30) peak periods.
     * Weekends operate with off-peak daytime cadences.
     */
    fun resolveServicePeriod(
        time: LocalTime = LocalTime.now(),
        dayOfWeek: DayOfWeek = LocalDate.now().dayOfWeek
    ): TransitServicePeriod {
        // Weekends do not have commuter peak hours
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            val isLateNight = time.isBefore(LocalTime.of(7, 0)) || !time.isBefore(LocalTime.of(23, 0))
            return if (isLateNight) TransitServicePeriod.LATE_NIGHT else TransitServicePeriod.OFF_PEAK
        }

        val isLateNight = time.isBefore(LocalTime.of(6, 0)) || !time.isBefore(LocalTime.of(22, 30))
        if (isLateNight) return TransitServicePeriod.LATE_NIGHT

        val morningPeakStart = LocalTime.of(6, 30)
        val morningPeakEnd = LocalTime.of(9, 30)
        val eveningPeakStart = LocalTime.of(16, 0)
        val eveningPeakEnd = LocalTime.of(19, 30)

        val isMorningPeak = !time.isBefore(morningPeakStart) && time.isBefore(morningPeakEnd)
        val isEveningPeak = !time.isBefore(eveningPeakStart) && time.isBefore(eveningPeakEnd)

        return if (isMorningPeak || isEveningPeak) TransitServicePeriod.PEAK else TransitServicePeriod.OFF_PEAK
    }

    /**
     * Mode default headways when operator or line profiles are unavailable.
     */
    val MODE_DEFAULT_HEADWAYS = mapOf(
        TransitMode.METRO to HeadwayProfile(peakMinutes = 3, offPeakMinutes = 5, lateNightMinutes = 8),
        TransitMode.RAIL to HeadwayProfile(peakMinutes = 15, offPeakMinutes = 20, lateNightMinutes = 30),
        TransitMode.TRAM to HeadwayProfile(peakMinutes = 4, offPeakMinutes = 7, lateNightMinutes = 10),
        TransitMode.BUS to HeadwayProfile(peakMinutes = 8, offPeakMinutes = 12, lateNightMinutes = 20),
        TransitMode.OTHER to HeadwayProfile(peakMinutes = 6, offPeakMinutes = 10, lateNightMinutes = 15)
    )

    private val GENERIC_FALLBACK_PROFILE = HeadwayProfile(peakMinutes = 5, offPeakMinutes = 8, lateNightMinutes = 15)

    /**
     * Empirically preprocessed operator and line headway profiles (in minutes) across
     * Peak, Off-Peak, and Late-Night periods, derived from TfL Timetable and National Rail Darwin feeds.
     */
    val LINE_OPERATOR_HEADWAYS = mapOf(
        // Airport express services (preprocessed from Darwin PAD->HXX & LST->SSD)
        "heathrow-express" to HeadwayProfile(peakMinutes = 15, offPeakMinutes = 15, lateNightMinutes = 15),
        "heathrow express" to HeadwayProfile(peakMinutes = 15, offPeakMinutes = 15, lateNightMinutes = 15),
        "hex" to HeadwayProfile(peakMinutes = 15, offPeakMinutes = 15, lateNightMinutes = 15),
        "hx" to HeadwayProfile(peakMinutes = 15, offPeakMinutes = 15, lateNightMinutes = 15),
        "gatwick-express" to HeadwayProfile(peakMinutes = 15, offPeakMinutes = 30, lateNightMinutes = 30),
        "gatwick express" to HeadwayProfile(peakMinutes = 15, offPeakMinutes = 30, lateNightMinutes = 30),
        "gx" to HeadwayProfile(peakMinutes = 15, offPeakMinutes = 30, lateNightMinutes = 30),
        "stansted-express" to HeadwayProfile(peakMinutes = 15, offPeakMinutes = 15, lateNightMinutes = 30),
        "stansted express" to HeadwayProfile(peakMinutes = 15, offPeakMinutes = 15, lateNightMinutes = 30),

        // National Rail & Commuter operators (preprocessed from Darwin LDBWS)
        "southeastern" to HeadwayProfile(peakMinutes = 12, offPeakMinutes = 20, lateNightMinutes = 30),
        "se" to HeadwayProfile(peakMinutes = 12, offPeakMinutes = 20, lateNightMinutes = 30),
        "nr-se" to HeadwayProfile(peakMinutes = 12, offPeakMinutes = 20, lateNightMinutes = 30),
        "southern" to HeadwayProfile(peakMinutes = 12, offPeakMinutes = 20, lateNightMinutes = 30),
        "sn" to HeadwayProfile(peakMinutes = 12, offPeakMinutes = 20, lateNightMinutes = 30),
        "south-western-railway" to HeadwayProfile(peakMinutes = 10, offPeakMinutes = 15, lateNightMinutes = 30),
        "south western railway" to HeadwayProfile(peakMinutes = 10, offPeakMinutes = 15, lateNightMinutes = 30),
        "swr" to HeadwayProfile(peakMinutes = 10, offPeakMinutes = 15, lateNightMinutes = 30),
        "sw" to HeadwayProfile(peakMinutes = 10, offPeakMinutes = 15, lateNightMinutes = 30),
        "great-western-railway" to HeadwayProfile(peakMinutes = 15, offPeakMinutes = 20, lateNightMinutes = 30),
        "great western railway" to HeadwayProfile(peakMinutes = 15, offPeakMinutes = 20, lateNightMinutes = 30),
        "gwr" to HeadwayProfile(peakMinutes = 15, offPeakMinutes = 20, lateNightMinutes = 30),
        "gw" to HeadwayProfile(peakMinutes = 15, offPeakMinutes = 20, lateNightMinutes = 30),
        "greater-anglia" to HeadwayProfile(peakMinutes = 15, offPeakMinutes = 20, lateNightMinutes = 30),
        "greater anglia" to HeadwayProfile(peakMinutes = 15, offPeakMinutes = 20, lateNightMinutes = 30),
        "le" to HeadwayProfile(peakMinutes = 15, offPeakMinutes = 20, lateNightMinutes = 30),
        "thameslink" to HeadwayProfile(peakMinutes = 6, offPeakMinutes = 10, lateNightMinutes = 15),
        "tl" to HeadwayProfile(peakMinutes = 6, offPeakMinutes = 10, lateNightMinutes = 15),
        "great-northern" to HeadwayProfile(peakMinutes = 10, offPeakMinutes = 20, lateNightMinutes = 30),
        "great northern" to HeadwayProfile(peakMinutes = 10, offPeakMinutes = 20, lateNightMinutes = 30),
        "gn" to HeadwayProfile(peakMinutes = 10, offPeakMinutes = 20, lateNightMinutes = 30),
        "c2c" to HeadwayProfile(peakMinutes = 10, offPeakMinutes = 15, lateNightMinutes = 30),
        "cc" to HeadwayProfile(peakMinutes = 10, offPeakMinutes = 15, lateNightMinutes = 30),
        "chiltern" to HeadwayProfile(peakMinutes = 15, offPeakMinutes = 25, lateNightMinutes = 30),
        "chiltern-railways" to HeadwayProfile(peakMinutes = 15, offPeakMinutes = 25, lateNightMinutes = 30),
        "chiltern railways" to HeadwayProfile(peakMinutes = 15, offPeakMinutes = 25, lateNightMinutes = 30),
        "ch" to HeadwayProfile(peakMinutes = 15, offPeakMinutes = 25, lateNightMinutes = 30),
        "london-northwestern-railway" to HeadwayProfile(peakMinutes = 15, offPeakMinutes = 22, lateNightMinutes = 30),
        "london northwestern railway" to HeadwayProfile(peakMinutes = 15, offPeakMinutes = 22, lateNightMinutes = 30),
        "lnr & wmr" to HeadwayProfile(peakMinutes = 15, offPeakMinutes = 22, lateNightMinutes = 30),
        "lnr" to HeadwayProfile(peakMinutes = 15, offPeakMinutes = 22, lateNightMinutes = 30),
        "lm" to HeadwayProfile(peakMinutes = 15, offPeakMinutes = 22, lateNightMinutes = 30),
        "avanti" to HeadwayProfile(peakMinutes = 20, offPeakMinutes = 30, lateNightMinutes = 45),
        "avanti-west-coast" to HeadwayProfile(peakMinutes = 20, offPeakMinutes = 30, lateNightMinutes = 45),
        "avanti west coast" to HeadwayProfile(peakMinutes = 20, offPeakMinutes = 30, lateNightMinutes = 45),
        "vt" to HeadwayProfile(peakMinutes = 20, offPeakMinutes = 30, lateNightMinutes = 45),
        "lner" to HeadwayProfile(peakMinutes = 20, offPeakMinutes = 30, lateNightMinutes = 45),
        "crosscountry" to HeadwayProfile(peakMinutes = 30, offPeakMinutes = 30, lateNightMinutes = 60),
        "cross-country" to HeadwayProfile(peakMinutes = 30, offPeakMinutes = 30, lateNightMinutes = 60),
        "transpennine" to HeadwayProfile(peakMinutes = 20, offPeakMinutes = 30, lateNightMinutes = 45),
        "transpennine-express" to HeadwayProfile(peakMinutes = 20, offPeakMinutes = 30, lateNightMinutes = 45),
        "transpennine express" to HeadwayProfile(peakMinutes = 20, offPeakMinutes = 30, lateNightMinutes = 45),
        "tpe" to HeadwayProfile(peakMinutes = 20, offPeakMinutes = 30, lateNightMinutes = 45),
        "northern-rail" to HeadwayProfile(peakMinutes = 20, offPeakMinutes = 30, lateNightMinutes = 45),
        "scotrail" to HeadwayProfile(peakMinutes = 15, offPeakMinutes = 30, lateNightMinutes = 45),
        "tfw" to HeadwayProfile(peakMinutes = 20, offPeakMinutes = 30, lateNightMinutes = 60),
        "transport-for-wales" to HeadwayProfile(peakMinutes = 20, offPeakMinutes = 30, lateNightMinutes = 60),
        "transport for wales" to HeadwayProfile(peakMinutes = 20, offPeakMinutes = 30, lateNightMinutes = 60),
        "east-midlands-railway" to HeadwayProfile(peakMinutes = 20, offPeakMinutes = 30, lateNightMinutes = 45),
        "east midlands railway" to HeadwayProfile(peakMinutes = 20, offPeakMinutes = 30, lateNightMinutes = 45),
        "emr" to HeadwayProfile(peakMinutes = 20, offPeakMinutes = 30, lateNightMinutes = 45),
        "merseyrail" to HeadwayProfile(peakMinutes = 15, offPeakMinutes = 15, lateNightMinutes = 30),

        // London Overground (6 named lines + generic LO) & Elizabeth line
        "liberty" to HeadwayProfile(peakMinutes = 30, offPeakMinutes = 30, lateNightMinutes = 30),     // Romford-Upminster (2 tph)
        "lioness" to HeadwayProfile(peakMinutes = 15, offPeakMinutes = 15, lateNightMinutes = 20),     // Euston-Watford Jn (4 tph)
        "suffragette" to HeadwayProfile(peakMinutes = 15, offPeakMinutes = 15, lateNightMinutes = 20), // Gospel Oak-Barking Riverside (4 tph)
        "weaver" to HeadwayProfile(peakMinutes = 10, offPeakMinutes = 15, lateNightMinutes = 20),      // Liverpool St-Chingford/Enfield/Cheshunt
        "mildmay" to HeadwayProfile(peakMinutes = 8, offPeakMinutes = 10, lateNightMinutes = 15),      // Stratford-Richmond/Clapham Jn
        "windrush" to HeadwayProfile(peakMinutes = 5, offPeakMinutes = 8, lateNightMinutes = 12),      // Highbury & Islington-New Cross/Croydon
        "london-overground" to HeadwayProfile(peakMinutes = 10, offPeakMinutes = 15, lateNightMinutes = 20),
        "london overground" to HeadwayProfile(peakMinutes = 10, offPeakMinutes = 15, lateNightMinutes = 20),
        "overground" to HeadwayProfile(peakMinutes = 10, offPeakMinutes = 15, lateNightMinutes = 20),
        "lo" to HeadwayProfile(peakMinutes = 10, offPeakMinutes = 15, lateNightMinutes = 20),
        "elizabeth" to HeadwayProfile(peakMinutes = 3, offPeakMinutes = 5, lateNightMinutes = 10),
        "elizabeth-line" to HeadwayProfile(peakMinutes = 3, offPeakMinutes = 5, lateNightMinutes = 10),
        "elizabeth line" to HeadwayProfile(peakMinutes = 3, offPeakMinutes = 5, lateNightMinutes = 10),
        "xr" to HeadwayProfile(peakMinutes = 3, offPeakMinutes = 5, lateNightMinutes = 10),

        // London Underground, DLR & Trams (preprocessed from TfL /Line/{id}/Timetable/{stopId})
        "victoria" to HeadwayProfile(peakMinutes = 2, offPeakMinutes = 2, lateNightMinutes = 4),       // 562 daily trips/dir
        "jubilee" to HeadwayProfile(peakMinutes = 2, offPeakMinutes = 3, lateNightMinutes = 5),        // 460 daily trips/dir
        "central" to HeadwayProfile(peakMinutes = 2, offPeakMinutes = 3, lateNightMinutes = 4),        // 462 daily trips/dir
        "northern" to HeadwayProfile(peakMinutes = 3, offPeakMinutes = 3, lateNightMinutes = 5),       // 410 daily trips/dir
        "piccadilly" to HeadwayProfile(peakMinutes = 3, offPeakMinutes = 3, lateNightMinutes = 5),     // 400 daily trips/dir
        "bakerloo" to HeadwayProfile(peakMinutes = 3, offPeakMinutes = 4, lateNightMinutes = 4),       // 305 daily trips/dir
        "district" to HeadwayProfile(peakMinutes = 2, offPeakMinutes = 3, lateNightMinutes = 5),       // 360 daily trips/dir
        "metropolitan" to HeadwayProfile(peakMinutes = 4, offPeakMinutes = 5, lateNightMinutes = 6),   // 235 daily trips/dir
        "circle" to HeadwayProfile(peakMinutes = 10, offPeakMinutes = 10, lateNightMinutes = 10),      // 113 daily trips/dir (6 tph)
        "hammersmith-city" to HeadwayProfile(peakMinutes = 10, offPeakMinutes = 10, lateNightMinutes = 10), // 117 daily trips/dir (6 tph)
        "hammersmith & city" to HeadwayProfile(peakMinutes = 10, offPeakMinutes = 10, lateNightMinutes = 10),
        "waterloo-city" to HeadwayProfile(peakMinutes = 3, offPeakMinutes = 5, lateNightMinutes = 7),  // 265 daily trips/dir
        "dlr" to HeadwayProfile(peakMinutes = 2, offPeakMinutes = 3, lateNightMinutes = 5),            // 352 daily trips/dir
        "tram" to HeadwayProfile(peakMinutes = 3, offPeakMinutes = 3, lateNightMinutes = 6),           // 349 daily trips/dir

        // Hong Kong MTR lines
        "island" to HeadwayProfile(peakMinutes = 2, offPeakMinutes = 4, lateNightMinutes = 6),
        "tsuen-wan" to HeadwayProfile(peakMinutes = 2, offPeakMinutes = 4, lateNightMinutes = 6),
        "twen-wan" to HeadwayProfile(peakMinutes = 2, offPeakMinutes = 4, lateNightMinutes = 6),
        "kwun-tong" to HeadwayProfile(peakMinutes = 2, offPeakMinutes = 4, lateNightMinutes = 6),
        "east-rail" to HeadwayProfile(peakMinutes = 3, offPeakMinutes = 5, lateNightMinutes = 7),
        "south-island" to HeadwayProfile(peakMinutes = 3, offPeakMinutes = 5, lateNightMinutes = 7),
        "tung-chung" to HeadwayProfile(peakMinutes = 4, offPeakMinutes = 7, lateNightMinutes = 10),
        "tuen-ma" to HeadwayProfile(peakMinutes = 3, offPeakMinutes = 5, lateNightMinutes = 7),
        "airport-express" to HeadwayProfile(peakMinutes = 10, offPeakMinutes = 10, lateNightMinutes = 12)
    )

    /**
     * Empirically preprocessed branch endpoints and single-track spurs (from TfL /Line/{id}/Timetable/{stopId})
     * where frequency is naturally much lower than the high-frequency line trunk.
     */
    val BRANCH_STATION_HEADWAYS = mapOf(
        "940GZZLUMHL" to HeadwayProfile(peakMinutes = 12, offPeakMinutes = 15, lateNightMinutes = 15), // Mill Hill East (86 trips/day)
        "940GZZLUKSG" to HeadwayProfile(peakMinutes = 20, offPeakMinutes = 26, lateNightMinutes = 30), // Kensington (Olympia)
        "940GZZLUKOY" to HeadwayProfile(peakMinutes = 20, offPeakMinutes = 26, lateNightMinutes = 30), // Kensington (Olympia) NaPTAN
        "940GZZLUCSM" to HeadwayProfile(peakMinutes = 30, offPeakMinutes = 30, lateNightMinutes = 30), // Chesham (40 trips/day, 2 tph)
        "940GZZLUAMS" to HeadwayProfile(peakMinutes = 20, offPeakMinutes = 30, lateNightMinutes = 30), // Amersham (51 trips/day, 2-3 tph)
        "940GZZLUWFD" to HeadwayProfile(peakMinutes = 9, offPeakMinutes = 15, lateNightMinutes = 15),  // Watford (103 trips/day)
        "940GZZLUWAF" to HeadwayProfile(peakMinutes = 9, offPeakMinutes = 15, lateNightMinutes = 15),  // Watford NaPTAN
        "940GZZLUHR4" to HeadwayProfile(peakMinutes = 12, offPeakMinutes = 12, lateNightMinutes = 12), // Heathrow Terminal 4 (95 trips/day)
        "940GZZLUHR5" to HeadwayProfile(peakMinutes = 12, offPeakMinutes = 12, lateNightMinutes = 12), // Heathrow Terminal 5 (128 trips/day)
        "940GZZLURVY" to HeadwayProfile(peakMinutes = 20, offPeakMinutes = 20, lateNightMinutes = 22), // Roding Valley (60 trips/day, 3 tph)
        "940GZZLUCWL" to HeadwayProfile(peakMinutes = 20, offPeakMinutes = 20, lateNightMinutes = 22), // Chigwell (60 trips/day, 3 tph)
        "940GZZLUGGH" to HeadwayProfile(peakMinutes = 20, offPeakMinutes = 20, lateNightMinutes = 22)  // Grange Hill (60 trips/day, 3 tph)
    )

    val BRANCH_STATION_HEADWAYS_BY_NAME = mapOf(
        "mill hill east" to HeadwayProfile(peakMinutes = 12, offPeakMinutes = 15, lateNightMinutes = 15),
        "kensington (olympia)" to HeadwayProfile(peakMinutes = 20, offPeakMinutes = 26, lateNightMinutes = 30),
        "kensington olympia" to HeadwayProfile(peakMinutes = 20, offPeakMinutes = 26, lateNightMinutes = 30),
        "chesham" to HeadwayProfile(peakMinutes = 30, offPeakMinutes = 30, lateNightMinutes = 30),
        "amersham" to HeadwayProfile(peakMinutes = 20, offPeakMinutes = 30, lateNightMinutes = 30),
        "watford" to HeadwayProfile(peakMinutes = 9, offPeakMinutes = 15, lateNightMinutes = 15),
        "heathrow terminal 4" to HeadwayProfile(peakMinutes = 12, offPeakMinutes = 12, lateNightMinutes = 12),
        "heathrow terminal 5" to HeadwayProfile(peakMinutes = 12, offPeakMinutes = 12, lateNightMinutes = 12),
        "roding valley" to HeadwayProfile(peakMinutes = 20, offPeakMinutes = 20, lateNightMinutes = 22),
        "chigwell" to HeadwayProfile(peakMinutes = 20, offPeakMinutes = 20, lateNightMinutes = 22),
        "grange hill" to HeadwayProfile(peakMinutes = 20, offPeakMinutes = 20, lateNightMinutes = 22)
    )

    private fun findOperatorOrLineProfile(firstDep: Departure): HeadwayProfile? {
        val candidates = listOfNotNull(
            firstDep.lineId,
            firstDep.lineName,
            firstDep.train?.operator?.id,
            firstDep.train?.operator?.name
        ).map { it.lowercase().trim() }.filter { it.isNotEmpty() }

        // 1. Direct exact match
        for (c in candidates) {
            LINE_OPERATOR_HEADWAYS[c]?.let { return it }
        }

        // 2. Substring matching for longer descriptive names (>= 4 characters)
        for (c in candidates) {
            val matched = LINE_OPERATOR_HEADWAYS.entries.firstOrNull { (k, _) ->
                (k.length >= 4 && c.contains(k)) || (c.length >= 4 && k.contains(c))
            }
            if (matched != null) return matched.value
        }

        return null
    }

    /**
     * Dynamically calculates normal headway from scheduled timetable data,
     * empirical departure spacing, branch overrides, operator profiles, or transit mode defaults.
     */
    fun resolveTypicalHeadwayMinutes(
        firstDep: Departure,
        group: List<Departure>,
        time: LocalTime = LocalTime.now(),
        dayOfWeek: DayOfWeek = LocalDate.now().dayOfWeek
    ): Int {
        // 1. Explicit headway from GTFS or feed frequency metadata
        val explicitFrequency = firstDep.frequency?.headwaySeconds?.let { it / 60 }
        if (explicitFrequency != null && explicitFrequency > 0) return explicitFrequency

        // 2. Scheduled timetable gap calculated from scheduled departure instants
        val scheduledInstants = group.mapNotNull { it.scheduledDepartureInstant }.distinct().sorted()
        if (scheduledInstants.size >= 2) {
            val scheduledGaps = scheduledInstants.zipWithNext { a, b ->
                Duration.between(a, b).toMinutes().toInt()
            }.filter { it in 1..90 }
            if (scheduledGaps.isNotEmpty()) {
                return scheduledGaps.sorted()[scheduledGaps.size / 2]
            }
        }

        // 3. Observed timetable cadence from consecutive departure spacing
        val sortedGroup = group.sortedBy { it.timeToStationSeconds }
        if (sortedGroup.size >= 2) {
            val predictionGaps = sortedGroup.zipWithNext { a, b ->
                (b.timeToStationSeconds - a.timeToStationSeconds) / 60
            }.filter { it in 1..90 }
            if (predictionGaps.isNotEmpty()) {
                return predictionGaps.sorted()[predictionGaps.size / 2]
            }
        }

        val period = resolveServicePeriod(time, dayOfWeek)

        // 4. Known branch station timetable headways (for branch endpoints/spurs when only 1 train is listed)
        val stationKey = firstDep.stationId.uppercase()
        val stationNameLower = firstDep.stationName.lowercase()
        val branchProfile = BRANCH_STATION_HEADWAYS[stationKey]
            ?: BRANCH_STATION_HEADWAYS_BY_NAME.entries.firstOrNull { stationNameLower.contains(it.key) }?.value
        if (branchProfile != null) return branchProfile.forPeriod(period)

        // 5. Operator or line profile (e.g. Heathrow Express, Southeastern, Victoria)
        val operatorOrLineProfile = findOperatorOrLineProfile(firstDep)
        if (operatorOrLineProfile != null) return operatorOrLineProfile.forPeriod(period)

        // 6. Transit mode fallback profile (Rail vs Metro vs Tram vs Bus)
        val transitMode = TransitMode.fromModeString(firstDep.modeName).takeIf { it != TransitMode.OTHER }
            ?: firstDep.lineBadge.mode.takeIf { it != TransitMode.OTHER }
            ?: TransitMode.OTHER
        val modeProfile = MODE_DEFAULT_HEADWAYS[transitMode] ?: GENERIC_FALLBACK_PROFILE
        return modeProfile.forPeriod(period)
    }

    /**
     * Checks a station's departures for platform backlog conditions.
     * Returns a list of active [PlatformBacklogAlert]s.
     */
    fun detectBacklogs(
        departures: List<Departure>,
        time: LocalTime = LocalTime.now(),
        dayOfWeek: DayOfWeek = LocalDate.now().dayOfWeek
    ): List<PlatformBacklogAlert> {
        val nonBusDepartures = departures.filter {
            !it.isBus && it.status != DepartureStatus.CANCELLED
        }
        if (nonBusDepartures.isEmpty()) return emptyList()

        val alerts = mutableListOf<PlatformBacklogAlert>()

        // Group by line and direction / platform
        val grouped = nonBusDepartures.groupBy { dep ->
            val dir = dep.direction ?: dep.towards ?: dep.platformName
            "${dep.lineId.lowercase()}_$dir"
        }

        for ((_, group) in grouped) {
            val firstDep = group.minByOrNull { it.timeToStationSeconds } ?: continue
            val minutesUntilNext = firstDep.timeToStationSeconds / 60

            val typicalHeadway = resolveTypicalHeadwayMinutes(firstDep, group, time, dayOfWeek)

            // Adaptive backlog threshold scaling:
            // - Rapid trunk lines (<= 5 min headway): threshold 2.8x with a 9-minute floor
            // - Medium-frequency lines (6..9 min headway): threshold 2.2x with a 12-minute floor
            // - Lower-frequency branches & commuter rail (>= 10 min headway): threshold 1.8x with a 1.5x headway floor
            val (thresholdMultiplier, minMinutesFloor) = when {
                typicalHeadway <= 5 -> 2.8 to 9
                typicalHeadway <= 9 -> 2.2 to 12
                else -> 1.8 to (typicalHeadway * 1.5).toInt()
            }

            if (minutesUntilNext >= minMinutesFloor && minutesUntilNext >= (typicalHeadway * thresholdMultiplier)) {
                val dirLabel = firstDep.direction ?: firstDep.platformName
                alerts.add(
                    PlatformBacklogAlert(
                        lineId = firstDep.lineId,
                        lineName = firstDep.lineName,
                        direction = dirLabel,
                        minutesUntilNext = minutesUntilNext,
                        typicalHeadwayMinutes = typicalHeadway,
                        message = "Platform backlog alert: Next ${firstDep.lineName} in ${minutesUntilNext}m (Normal headway: ${typicalHeadway}m)"
                    )
                )
            }
        }

        return alerts
    }
}
