package com.androidfung.departureboard.data.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.i18n.LocalizedNameProvider
import com.androidfung.departureboard.util.LocaleHelper
import java.util.Locale

/**
 * Generic transit service hours status indicating whether a station or line is closed for the night.
 */
@Immutable
data class StationServiceHoursInfo(
    val isClosedForNight: Boolean,
    val lastTrainTime: String? = null,
    val firstTrainTime: String? = null,
    val lineName: String? = null
)

/**
 * GTFS stop_times.txt pickup_type and drop_off_type semantics.
 */
enum class StopActivityType(val gtfsCode: Int) {
    NORMAL(0),            // Regular pickup and drop-off
    NOT_ALLOWED(1),       // No pickup or drop-off available
    CALL_AGENCY(2),       // Must phone agency to arrange pickup/drop-off
    COORDINATE_DRIVER(3), // Must coordinate with driver (request/flag stop)
    ALIGHTING_ONLY(1),    // Set down only
    BOARDING_ONLY(1)      // Pick up only
}

/**
 * Granular wheelchair boarding accessibility for stations and vehicles adhering to GTFS stops.txt wheelchair_boarding.
 */
enum class WheelchairBoardingStatus(val gtfsCode: Int) {
    UNKNOWN(0),
    STEP_FREE_LEVEL_BOARDING(1),  // Level boarding without ramp
    STEP_FREE_WITH_RAMP(1),       // Accessible via lift/ramp, staff boarding ramp required
    NOT_ACCESSIBLE(2);            // Inaccessible / stairs only

    companion object {
        fun fromGtfsCode(code: Int?): WheelchairBoardingStatus = when (code) {
            1 -> STEP_FREE_LEVEL_BOARDING
            2 -> NOT_ACCESSIBLE
            else -> UNKNOWN
        }
    }
}

/**
 * Standard GTFS-RT VehiclePosition / TripUpdate OccupancyStatus.
 */
enum class OccupancyStatus(val gtfsCode: Int) {
    EMPTY(0),
    MANY_SEATS_AVAILABLE(1),
    FEW_SEATS_AVAILABLE(2),
    STANDING_ROOM_ONLY(3),
    CRUSHED_STANDING_ROOM_ONLY(4),
    FULL(5),
    NOT_ACCEPTING_PASSENGERS(6);

    companion object {
        fun fromString(raw: String?): OccupancyStatus? {
            if (raw.isNullOrBlank()) return null
            return when (raw.trim().uppercase().replace("-", "_").replace(" ", "_")) {
                "EMPTY", "0" -> EMPTY
                "MANY_SEATS_AVAILABLE", "SEATS_AVAILABLE", "SEATSAVAILABLE", "SEATS", "1" -> MANY_SEATS_AVAILABLE
                "FEW_SEATS_AVAILABLE", "FEWSEATSAVAILABLE", "LIMITED_SEATS", "2" -> FEW_SEATS_AVAILABLE
                "STANDING_ROOM_ONLY", "STANDING_AVAILABLE", "STANDINGAVAILABLE", "STANDING", "3" -> STANDING_ROOM_ONLY
                "CRUSHED_STANDING_ROOM_ONLY", "CRUSHEDSTANDINGROOMONLY", "VERY_CROWDED", "4" -> CRUSHED_STANDING_ROOM_ONLY
                "FULL", "5" -> FULL
                "NOT_ACCEPTING_PASSENGERS", "NOTACCEPTINGPASSENGERS", "6" -> NOT_ACCEPTING_PASSENGERS
                else -> entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
            }
        }
    }
}

/**
 * Standard GTFS-RT TripUpdate.StopTimeUpdate.ScheduleRelationship for individual calling points.
 */
enum class StopScheduleRelationship(val gtfsCode: Int) {
    SCHEDULED(0),   // Vehicle stops according to schedule
    SKIPPED(1),     // Stop is cancelled/bypassed on this trip
    NO_DATA(2),     // No real-time prediction available for this stop
    UNSCHEDULED(3)  // Additional stop added dynamically to the trip
}

/**
 * Standard GTFS-RT VehiclePosition.VehicleStopStatus indicating vehicle progress relative to a stop.
 */
enum class VehicleStopStatus(val gtfsCode: Int) {
    INCOMING_AT(0),   // Vehicle is about to arrive at the stop
    STOPPED_AT(1),    // Vehicle is standing at the stop / platform
    IN_TRANSIT_TO(2); // Vehicle has departed previous stop and is in transit

    companion object {
        fun fromLocationAndTime(currentLocation: String?, timeToStationSeconds: Int): VehicleStopStatus {
            val loc = currentLocation?.trim()?.lowercase() ?: ""
            return when {
                loc.contains("at platform") || loc.contains("at stop") || loc.startsWith("at ") || timeToStationSeconds <= 30 -> STOPPED_AT
                loc.contains("approaching") || loc.contains("arriving") || timeToStationSeconds <= 90 -> INCOMING_AT
                else -> IN_TRANSIT_TO
            }
        }
    }
}

/**
 * Headway / frequency based transit interval adhering to GTFS frequencies.txt.
 */
@Immutable
data class ServiceFrequency(
    val headwaySeconds: Int,
    val exactTimes: Boolean = false
) {
    val displayHeadway: String
        get() = when {
            headwaySeconds <= 120 -> "Every 2 mins"
            headwaySeconds <= 180 -> "Every 2–3 mins"
            headwaySeconds <= 300 -> "Every 3–5 mins"
            headwaySeconds <= 600 -> "Every 5–10 mins"
            else -> "Every ${headwaySeconds / 60} mins"
        }
}

/**
 * Disentangled journey destination representation (GTFS trips.txt trip_headsign vs destination stop).
 */
@Immutable
data class JourneyDestination(
    val terminusName: String,
    val tripHeadsign: String? = null,
    val via: String? = null,
    val terminusStationId: String? = null
) {
    /**
     * Terminus stop name without any embedded "via ..." corridor suffix.
     */
    val displayDestination: String
        get() = terminusName.substringBefore(" via ").trim().ifBlank {
            tripHeadsign?.substringBefore(" via ")?.trim() ?: terminusName
        }

    /**
     * Clean corridor routing subtitle (e.g. "via Bank", "via London Bridge"),
     * only when non-redundant and not duplicating the terminus name.
     */
    val displayViaSubtitle: String?
        get() {
            val candidateVia = when {
                !via.isNullOrBlank() -> {
                    val raw = via.trim()
                    if (raw.startsWith("via ", ignoreCase = true)) raw.substring(4).trim() else raw
                }
                tripHeadsign != null && tripHeadsign.contains(" via ", ignoreCase = true) ->
                    tripHeadsign.substringAfter(" via ").trim()
                terminusName.contains(" via ", ignoreCase = true) ->
                    terminusName.substringAfter(" via ").trim()
                else -> null
            } ?: return null

            if (candidateVia.isBlank()) return null

            val cleanTerminus = displayDestination.lowercase().trim()
            val cleanVia = candidateVia.lowercase().trim()

            // If via is identical to destination (e.g. terminus "Mill Hill East" and via "Mill Hill East"),
            // or if it simply repeats the destination station, it's not a corridor route.
            if (cleanVia == cleanTerminus || cleanVia.startsWith("$cleanTerminus ") || cleanVia.endsWith(" $cleanTerminus")) {
                return null
            }

            return "via $candidateVia"
        }
}

/**
 * Inter-platform / inter-route transfer rules adhering to GTFS transfers.txt.
 */
enum class TransferType(val gtfsCode: Int) {
    RECOMMENDED(0),      // Recommended transfer point
    TIMED_TRANSFER(1),   // Timed/guaranteed transfer connection
    MIN_TIME(2),         // Minimum walking transfer time required
    IN_SEAT(3),          // In-seat transfer (vehicle continues as another route)
    NOT_POSSIBLE(4)      // Transfer forbidden/impossible
}

enum class PathwayMode {
    WALKWAY,
    STAIRS,
    ESCALATOR,
    ELEVATOR
}

/**
 * Station transfer connection adhering to GTFS transfers.txt and NeTEx / IFOPT pathway topology.
 */
@Immutable
data class StationTransfer(
    val fromPlatform: String,
    val toPlatform: String,
    val type: TransferType = TransferType.RECOMMENDED,
    val minTransferTimeSeconds: Int = 0,
    val isStepFree: Boolean = true,
    val pathwayModes: List<PathwayMode> = listOf(PathwayMode.WALKWAY),
    val pathwayInstructions: String? = null
) {
    val displayTransferTime: String?
        get() = if (minTransferTimeSeconds > 0) "${minTransferTimeSeconds / 60} min walk" else null
}

/**
 * Micromobility / Bikeshare station telemetry adhering to GBFS (General Bikeshare Feed Specification).
 */
@Immutable
data class MicromobilityHub(
    val id: String,
    val name: String,
    val totalBikesAvailable: Int,
    val totalEbikesAvailable: Int = 0,
    val totalDocksAvailable: Int,
    val operatorName: String? = null,
    val distanceMeters: Int? = null
)

/**
 * Clean domain representation of a transit station or stop.
 */
@Immutable
data class Station(
    val id: String,
    val name: String,
    override val localizedNames: Map<String, String> = emptyMap(),
    val modes: List<String> = emptyList(),
    val zone: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val isFavorite: Boolean = false,
    val lines: List<StationLineInfo> = emptyList(),
    val wheelchairStatus: WheelchairBoardingStatus = WheelchairBoardingStatus.UNKNOWN,
    val transfers: List<StationTransfer> = emptyList(),
    val micromobilityHubs: List<MicromobilityHub> = emptyList()
) : LocalizedNameProvider {
    val isBusOnly: Boolean get() = TransitMode.isBusOnly(modes)

    val primaryTransitMode: TransitMode
        get() = modes.firstNotNullOfOrNull { raw ->
            val norm = raw.trim().lowercase()
            if (norm.isEmpty()) null else TransitMode.fromModeString(norm)
        } ?: TransitMode.METRO

    /**
     * Resolves the display name according to the active locale or bilingual setting.
     */
    fun resolveDisplayName(
        showBilingual: Boolean = false,
        locale: Locale = LocaleHelper.getAppLocale() ?: Locale.getDefault()
    ): String {
        if (localizedNames.isEmpty()) return displayName
        val baseName = if (showBilingual) getBilingualName(parentheses = true) else getLocalizedName(locale)
        if (!isBusOnly) return baseName

        val letter = com.androidfung.departureboard.data.repository.region.gb.TflStopPointUtils.extractBusStopLetter(id)
        if (letter != null) {
            if (!baseName.contains(Regex("\\bStop\\s+$letter\\b", RegexOption.IGNORE_CASE)) && !baseName.contains("(")) {
                return "$baseName (Stop $letter)"
            }
        }
        return baseName
    }

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
    val modes: List<String> = emptyList(),
    override val localizedNames: Map<String, String> = emptyMap(),
    val stopActivity: StopActivityType = StopActivityType.NORMAL,
    val scheduleRelationship: StopScheduleRelationship = StopScheduleRelationship.SCHEDULED
) : LocalizedNameProvider {
    val isCancelled: Boolean
        get() = scheduleRelationship == StopScheduleRelationship.SKIPPED ||
            estimatedTime.equals("Cancelled", ignoreCase = true)

    /**
     * Resolves the calling point display name according to the active locale or bilingual setting.
     */
    fun resolveDisplayName(
        showBilingual: Boolean = false,
        locale: Locale = LocaleHelper.getAppLocale() ?: Locale.getDefault()
    ): String {
        if (localizedNames.isEmpty()) return stationName
        return if (showBilingual) getBilingualName(parentheses = true) else getLocalizedName(locale)
    }
}

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
) {
    val parsedOccupancyStatus: OccupancyStatus?
        get() = OccupancyStatus.fromString(occupancyStatus)
}

enum class TrainAssociationType {
    DIVIDES,   // Train splits/divides at this station into multiple portions (Darwin Association)
    JOINS      // Trains join/attach together at this station
}

/**
 * Train portion splitting or joining notice adhering to Darwin association standards.
 */
@Immutable
data class TrainAssociation(
    val type: TrainAssociationType = TrainAssociationType.DIVIDES,
    val junctionStationName: String,
    val coachRange: String? = null,
    val portionDestination: String? = null,
    val notice: String? = null
)

/**
 * Train composition / formation details (e.g. 8 coaches, carriage layout).
 */
@Immutable
data class TrainFormation(
    val coachCount: Int,
    val coaches: List<CoachInfo> = emptyList(),
    val associations: List<TrainAssociation> = emptyList()
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
    val isWheelchairAccessible: Boolean? = null,
    val associations: List<TrainAssociation> = emptyList()
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
    val expectedDepartureInstant: java.time.Instant? = null,
    val stopActivity: StopActivityType = StopActivityType.NORMAL,
    val wheelchairStatus: WheelchairBoardingStatus = if (isWheelchairAccessible == true) WheelchairBoardingStatus.STEP_FREE_LEVEL_BOARDING else WheelchairBoardingStatus.UNKNOWN,
    val frequency: ServiceFrequency? = null,
    val journeyDestination: JourneyDestination? = null
) {
    val resolvedPlatform: PlatformInfo
        get() = platform ?: PlatformInfo(
            code = platformName.ifBlank { "TBD" },
            isConfirmed = !platformName.contains("TBD", ignoreCase = true)
        )

    val resolvedDestination: JourneyDestination
        get() = journeyDestination ?: JourneyDestination(
            terminusName = destinationName,
            tripHeadsign = destinationName,
            via = towards,
            terminusStationId = destinationStationId
        )

    val isSetDownOnly: Boolean
        get() = stopActivity == StopActivityType.ALIGHTING_ONLY

    val isPickUpOnly: Boolean
        get() = stopActivity == StopActivityType.BOARDING_ONLY

    val parsedOccupancyStatus: OccupancyStatus?
        get() = OccupancyStatus.fromString(occupancyStatus)

    val vehicleStopStatus: VehicleStopStatus
        get() = VehicleStopStatus.fromLocationAndTime(currentLocation, timeToStationSeconds)

    val operator: TransitOperator? get() = train?.operator
    val formation: TrainFormation? get() = train?.formation
    val associations: List<TrainAssociation> get() = formation?.associations?.takeIf { it.isNotEmpty() } ?: train?.associations.orEmpty()
    val cancelReason: String? get() = train?.cancelReason
    val delayReason: String? get() = train?.delayReason

    val isBus: Boolean
        get() = modeName.equals(TransitMode.BUS.id, ignoreCase = true) || lineBadge.mode == TransitMode.BUS

    val transitDirection: TransitDirection?
        get() = TransitDirection.fromString(direction)

    val transitMode: TransitMode
        get() = if (modeName.isNotBlank()) TransitMode.fromModeString(modeName) else lineBadge.mode

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

        fun fromGtfsRouteType(routeType: Int): TransitMode = when (routeType) {
            0, in 900..999 -> TRAM
            1, in 400..499 -> METRO
            2, in 100..199 -> RAIL
            3, 11, in 700..799, in 800..899 -> BUS
            4, in 1000..1099 -> FERRY
            5, 6, in 1300..1399 -> CABLE_CAR
            7, in 1400..1499 -> FUNICULAR
            12, 405 -> MONORAIL
            else -> OTHER
        }

        fun isBusOnly(modes: List<String>): Boolean =
            modes.isNotEmpty() && modes.all { it.equals(BUS.id, ignoreCase = true) }
    }
}
