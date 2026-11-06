package com.androidfung.departureboard.data.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Clean domain representation of a transit station or stop.
 */
@Immutable
data class Station(
    val id: String,
    val name: String,
    val modes: List<String> = emptyList(),
    val zone: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val isFavorite: Boolean = false,
    val lines: List<StationLineInfo> = emptyList()
) {
    val isBusOnly: Boolean get() = TransitMode.isBusOnly(modes)

    /**
     * Clean, valid TfL fare zone description (e.g. "1", "2/3"), or null if not applicable (e.g. bus stops or "NA").
     */
    val displayZone: String?
        get() {
            val z = zone?.trim() ?: return null
            if (z.isBlank() || z.equals("NA", ignoreCase = true) || z.equals("N/A", ignoreCase = true) || z.equals("null", ignoreCase = true)) {
                return null
            }
            return z
        }

    /**
     * User-facing display name that includes bus stop letter (e.g. "Euston (Stop D)")
     * if the station is an individual bus stop stand and not already indicated.
     */
    val displayName: String
        get() {
            if (!isBusOnly) return name

            val letter = com.androidfung.departureboard.data.repository.region.gb.TflStopPointUtils.extractBusStopLetter(id)
            if (letter != null) {
                if (!name.contains(Regex("\\bStop\\s+$letter\\b", RegexOption.IGNORE_CASE)) && !name.contains("(")) {
                    return "$name (Stop $letter)"
                }
            }
            return name
        }
}

/**
 * Domain representation of a transit line serving a station.
 */
@Immutable
data class StationLineInfo(
    val id: String,
    val name: String,
    val mode: String? = null
)

/**
 * An individual calling point or stop on a train/bus journey.
 */
@Immutable
data class CallingPoint(
    val stationName: String,
    val scheduledTime: String? = null,
    val estimatedTime: String? = null,
    val isCurrentStation: Boolean = false,
    val isDestination: Boolean = false,
    val stationId: String? = null,
    val modes: List<String> = emptyList()
)

/**
 * Structured platform or berth representation adhering to GTFS stops.txt platform_code.
 */
@Immutable
data class PlatformInfo(
    val code: String,
    val isConfirmed: Boolean = true,
    val directionHint: String? = null
) {
    /**
     * Formats user-facing label (e.g. "Platform 4", "Stop B", "Plat 2 (Unconfirmed)").
     */
    fun formatDisplay(includeDirection: Boolean = false): String {
        val base = if (code.matches(Regex("""^\d+[A-Za-z]?$"""))) "Platform $code" else code
        val confirmedSuffix = if (!isConfirmed) " (TBD)" else ""
        return if (includeDirection && !directionHint.isNullOrBlank()) {
            "$directionHint • $base$confirmedSuffix"
        } else {
            "$base$confirmedSuffix"
        }
    }
}

/**
 * Rolling stock carriage / coach information.
 */
@Immutable
data class CoachInfo(
    val coachNumber: String? = null,
    val coachClass: String? = null,
    val occupancyStatus: String? = null,
    val isWheelchairAccessible: Boolean? = null,
    val hasBicycleRack: Boolean? = null,
    val hasToilet: Boolean? = null,
    val isQuietCoach: Boolean? = null
)

/**
 * Train composition / formation details (e.g. 8 coaches, carriage layout).
 */
@Immutable
data class TrainFormation(
    val coachCount: Int,
    val coaches: List<CoachInfo> = emptyList()
) {
    val displayCoachCount: String
        get() = when (coachCount) {
            1 -> "1 coach"
            else -> "$coachCount coaches"
        }
}

/**
 * Detailed telemetry for train, rail, or vehicle services (GTFS trips / Real-time TripUpdate).
 */
@Immutable
data class TrainInfo(
    val serviceId: String? = null,
    val headcode: String? = null,
    val operator: TransitOperator? = null,
    val formation: TrainFormation? = null,
    val cancelReason: String? = null,
    val delayReason: String? = null,
    val isWheelchairAccessible: Boolean? = null
)

/**
 * Clean domain representation of a live departure prediction.
 */
@Immutable
data class Departure(
    val id: String,
    val stationId: String,
    val stationName: String,
    val lineId: String,
    val lineName: String,
    val platformName: String,
    val destinationName: String,
    val towards: String?,
    val direction: String? = null,
    val timeToStationSeconds: Int,
    val expectedArrivalIso: String?,
    val currentLocation: String?,
    val modeName: String,
    val lineBadge: LineBadgeInfo,
    val callingPoints: List<CallingPoint> = emptyList(),
    val status: DepartureStatus = DepartureStatus.fromString(currentLocation, timeToStationSeconds),
    val vehicleId: String? = null,
    val destinationStationId: String? = null,
    val isWheelchairAccessible: Boolean? = null,
    val occupancyStatus: String? = null,
    val platform: PlatformInfo? = null,
    val train: TrainInfo? = null,
    val scheduledDepartureInstant: java.time.Instant? = null,
    val expectedDepartureInstant: java.time.Instant? = null
) {
    val resolvedPlatform: PlatformInfo
        get() = platform ?: PlatformInfo(
            code = platformName.ifBlank { "TBD" },
            isConfirmed = !platformName.contains("TBD", ignoreCase = true)
        )

    val operator: TransitOperator? get() = train?.operator
    val formation: TrainFormation? get() = train?.formation
    val cancelReason: String? get() = train?.cancelReason
    val delayReason: String? get() = train?.delayReason

    /**
     * Human-friendly formatted arrival string, e.g. "Due", "1 min", "4 mins".
     */
    val formattedTimeToArrival: String
        get() {
            val minutes = timeToStationSeconds / 60
            return when {
                status == DepartureStatus.DUE -> "Due"
                minutes <= 1 -> "1 min"
                else -> "$minutes mins"
            }
        }

    /**
     * Formats actual expected clock time (e.g. "17:42").
     */
    val formattedActualClockTime: String?
        get() {
            val targetZone = TransitRegion.fromStationId(stationId.ifBlank { id }).timeZone
            val targetInstant = expectedDepartureInstant ?: scheduledDepartureInstant
            if (targetInstant != null) {
                val local = java.time.ZonedDateTime.ofInstant(targetInstant, targetZone)
                return local.format(TIME_FORMATTER)
            }

            if (!expectedArrivalIso.isNullOrBlank()) {
                return try {
                    val instant = java.time.Instant.parse(expectedArrivalIso)
                    val local = java.time.ZonedDateTime.ofInstant(instant, targetZone)
                    local.format(TIME_FORMATTER)
                } catch (_: Exception) {
                    null
                }
            }
            // Fallback: estimate from current time + seconds
            val estimated = java.time.ZonedDateTime.now(targetZone)
                .plusSeconds(timeToStationSeconds.toLong())
            return estimated.format(TIME_FORMATTER)
        }

    companion object {
        private val TIME_FORMATTER = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
    }
}

/**
 * Line branding badge colors & details.
 */
@Immutable
data class LineBadgeInfo(
    val lineId: String,
    val displayName: String,
    val backgroundColor: Color,
    val textColor: Color = Color.White,
    val mode: TransitMode = TransitMode.OTHER,
    val statusSeverity: Int = 10, // TfL scale: 10 = Good Service, < 10 or != 10 = delays / disruptions / closures
    val statusDescription: String? = null,
    val disruptionReason: String? = null,
    val lineCode: String = ""
) {
    val isDisrupted: Boolean get() = statusSeverity != 10

    companion object {
        /**
         * Natural comparator: numeric lines (e.g. bus routes 13, 102, 460) ordered numerically,
         * followed by alphabetical order for named rail/tube lines.
         */
        val NATURAL_COMPARATOR: Comparator<LineBadgeInfo> = Comparator { a, b ->
            val numA = a.displayName.toIntOrNull()
            val numB = b.displayName.toIntOrNull()
            when {
                numA != null && numB != null -> numA.compareTo(numB)
                numA != null -> -1
                numB != null -> 1
                else -> a.displayName.compareTo(b.displayName, ignoreCase = true)
            }
        }
    }
}

/**
 * Supported transit modes.
 *
 * Generic physical transit mode definitions following international standards (such as GTFS route types).
 * Regional sub-networks and brands (e.g. Tube for Metro; Overground, Elizabeth line, JR for Rail)
 * are represented as lines or customized via [TransitRegion.getModeDisplayName].
 */
enum class TransitMode(
    val id: String,
    val displayName: String,
    val gtfsRouteType: Int = -1
) {
    METRO("metro", "Metro", 1),              // GTFS 1: Subway, Metro, Underground, MRT, MTR
    RAIL("rail", "Rail", 2),                 // GTFS 2: Rail, National Rail, Commuter Rail, JR, Sydney Trains
    BUS("bus", "Bus", 3),                    // GTFS 3: Bus
    TRAM("tram", "Tram", 0),                 // GTFS 0: Tram, Streetcar, Light rail, DLR, LRT
    FERRY("ferry", "Ferry", 4),              // GTFS 4: Ferry, River Bus
    CABLE_CAR("cable-car", "Cable Car", 6),  // GTFS 6: Aerial lift, suspended cable car
    FUNICULAR("funicular", "Funicular", 7),  // GTFS 7: Funicular railway (e.g. Peak Tram, cliff railways)
    MONORAIL("monorail", "Monorail", 12),    // GTFS 12: Monorail (e.g. Tokyo Monorail, Sentosa Express)
    OTHER("other", "Transit", -1);

    /**
     * Resolves localized or regional name for this mode, falling back to [displayName].
     */
    fun getDisplayName(region: TransitRegion?): String {
        return region?.getModeDisplayName(this) ?: displayName
    }

    val isRail: Boolean get() = this in RAIL_MODES

    companion object {
        // Backward-compatibility aliases for legacy TfL-centric names
        @Deprecated("Use METRO instead", ReplaceWith("TransitMode.METRO"))
        val TUBE: TransitMode get() = METRO

        @Deprecated("Use RAIL instead", ReplaceWith("TransitMode.RAIL"))
        val NATIONAL_RAIL: TransitMode get() = RAIL

        @Deprecated("Use RAIL instead (Elizabeth line is a rail service)", ReplaceWith("TransitMode.RAIL"))
        val ELIZABETH_LINE: TransitMode get() = RAIL

        @Deprecated("Use RAIL instead (Overground is a rail service)", ReplaceWith("TransitMode.RAIL"))
        val OVERGROUND: TransitMode get() = RAIL

        @Deprecated("Use TRAM instead (DLR is light rail)", ReplaceWith("TransitMode.TRAM"))
        val DLR: TransitMode get() = TRAM

        val RAIL_MODES: Set<TransitMode> = setOf(
            METRO,
            RAIL,
            MONORAIL
        )

        fun fromModeString(modeStr: String?): TransitMode {
            if (modeStr == null) return OTHER
            return when (val normalized = modeStr.lowercase().trim()) {
                "metro", "tube", "underground", "mtr", "mrt", "subway" -> METRO
                "bus" -> BUS
                "rail", "national-rail", "train", "overground", "elizabeth", "elizabeth-line", "thameslink", "jr" -> RAIL
                "tram", "light-rail", "lrt", "dlr" -> TRAM
                "ferry", "river-bus", "river-tour", "boat" -> FERRY
                "cable-car", "gondola", "aerial" -> CABLE_CAR
                "funicular" -> FUNICULAR
                "monorail" -> MONORAIL
                else -> entries.firstOrNull { it.id == normalized } ?: OTHER
            }
        }

        fun fromModes(modes: List<String>): List<TransitMode> =
            modes.map { fromModeString(it) }

        fun isBusOnly(modes: List<String>): Boolean =
            modes.isNotEmpty() && modes.all { it.equals(BUS.id, ignoreCase = true) }
    }
}
