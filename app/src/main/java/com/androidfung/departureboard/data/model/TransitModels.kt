package com.androidfung.departureboard.data.model

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

/**
 * Clean domain representation of a transit station or stop.
 */
@Serializable
data class Station(
    val id: String,
    val name: String,
    val modes: List<String> = emptyList(),
    val zone: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val isFavorite: Boolean = false
) {
    /**
     * User-facing display name that includes bus stop letter (e.g. "Euston (Stop D)")
     * if the station is an individual bus stop stand and not already indicated.
     */
    val displayName: String
        get() {
            val isBusOnly = modes.contains("bus") && modes.none { it in listOf("tube", "overground", "elizabeth-line", "national-rail", "dlr") }
            if (!isBusOnly) return name

            val match = Regex("^490\\d+([A-Za-z0-9]+)$").find(id)
            if (match != null) {
                val letter = match.groupValues[1].uppercase()
                if (!name.contains(Regex("\\bStop\\s+$letter\\b", RegexOption.IGNORE_CASE)) && !name.contains("(")) {
                    return "$name (Stop $letter)"
                }
            }
            return name
        }
}

/**
 * Clean domain representation of a live departure prediction.
 */
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
    val lineBadge: LineBadgeInfo
) {
    /**
     * Human-friendly formatted arrival string, e.g. "Due", "1 min", "4 mins".
     */
    val formattedTimeToArrival: String
        get() {
            val minutes = timeToStationSeconds / 60
            return when {
                timeToStationSeconds <= 30 -> "Due"
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
data class LineBadgeInfo(
    val lineId: String,
    val displayName: String,
    val backgroundColor: Color,
    val textColor: Color = Color.White,
    val mode: TransitMode = TransitMode.OTHER,
    val statusSeverity: Int = 10, // 10 = Good Service
    val statusDescription: String? = null,
    val disruptionReason: String? = null,
    val lineCode: String = ""
) {
    val isDisrupted: Boolean get() = statusSeverity < 10
}

/**
 * Supported transit modes.
 */
enum class TransitMode(val id: String, val displayName: String) {
    TUBE("tube", "Underground"),
    BUS("bus", "Bus"),
    ELIZABETH_LINE("elizabeth-line", "Elizabeth line"),
    OVERGROUND("overground", "London Overground"),
    DLR("dlr", "DLR"),
    NATIONAL_RAIL("national-rail", "National Rail"),
    CABLE_CAR("cable-car", "IFS Cloud Cable Car"),
    TRAM("tram", "Tram"),
    OTHER("other", "Transit");

    companion object {
        fun fromModeString(modeStr: String?): TransitMode {
            if (modeStr == null) return OTHER
            val normalized = modeStr.lowercase().trim()
            return entries.firstOrNull { it.id == normalized } ?: OTHER
        }
    }
}
