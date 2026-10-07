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

            val letter = Regex("^490\\d+([A-Za-z0-9]+)$").find(id)?.groupValues?.getOrNull(1)?.uppercase()
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
    val isDestination: Boolean = false
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
    val status: DepartureStatus = DepartureStatus.fromString(currentLocation, timeToStationSeconds)
) {
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
            if (!expectedArrivalIso.isNullOrBlank()) {
                return try {
                    val instant = java.time.Instant.parse(expectedArrivalIso)
                    val local = java.time.ZonedDateTime.ofInstant(instant, java.time.ZoneId.of("Europe/London"))
                    local.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                } catch (_: Exception) {
                    null
                }
            }
            // Fallback: estimate from current time + seconds
            val estimated = java.time.ZonedDateTime.now(java.time.ZoneId.of("Europe/London"))
                .plusSeconds(timeToStationSeconds.toLong())
            return estimated.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
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
    val statusSeverity: Int = 10, // TfL scale: 10 = Good Service, 20-70 = issues, 80 = planned work
    val statusDescription: String? = null,
    val disruptionReason: String? = null,
    val lineCode: String = ""
) {
    val isDisrupted: Boolean get() = statusSeverity > 10

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
 */
enum class TransitMode(val id: String, val displayName: String) {
    TUBE("tube", "Underground"),
    BUS("bus", "Bus"),
    ELIZABETH_LINE("elizabeth-line", "Elizabeth line"),
    OVERGROUND("overground", "Overground"),
    DLR("dlr", "DLR"),
    NATIONAL_RAIL("national-rail", "National Rail"),
    CABLE_CAR("cable-car", "Cable Car"),
    TRAM("tram", "Tram"),
    OTHER("other", "Transit");

    val isRail: Boolean get() = this in RAIL_MODES

    companion object {
        val RAIL_MODES: Set<TransitMode> = setOf(
            TUBE,
            OVERGROUND,
            ELIZABETH_LINE,
            NATIONAL_RAIL,
            DLR
        )

        fun fromModeString(modeStr: String?): TransitMode {
            if (modeStr == null) return OTHER
            val normalized = modeStr.lowercase().trim()
            return entries.firstOrNull { it.id == normalized } ?: OTHER
        }

        fun fromModes(modes: List<String>): List<TransitMode> =
            modes.map { fromModeString(it) }

        fun isBusOnly(modes: List<String>): Boolean =
            modes.isNotEmpty() && modes.all { it.equals(BUS.id, ignoreCase = true) }
    }
}
