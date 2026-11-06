package com.androidfung.departureboard.ui.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsBoat
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsRailway
import androidx.compose.material.icons.rounded.DirectionsSubway
import androidx.compose.material.icons.rounded.Tram
import androidx.compose.ui.graphics.vector.ImageVector
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TransitLineMatcher
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.model.TransitRegion
import com.androidfung.departureboard.data.model.isBodsTransitId

/**
 * Shared transit UI helpers for icon resolution, mode display formatting,
 * region-aware map queries, and line equivalence checks across components.
 */
object TransitIconHelper {

    /**
     * Resolves an appropriate Material ImageVector for a given strongly-typed [TransitMode].
     */
    fun getTransitIconForMode(mode: TransitMode?): ImageVector = when (mode) {
        TransitMode.METRO -> Icons.Rounded.DirectionsSubway
        TransitMode.RAIL -> Icons.Rounded.DirectionsRailway
        TransitMode.BUS -> Icons.Rounded.DirectionsBus
        TransitMode.TRAM -> Icons.Rounded.Tram
        TransitMode.FERRY -> Icons.Rounded.DirectionsBoat
        TransitMode.CABLE_CAR, TransitMode.FUNICULAR, TransitMode.MONORAIL -> Icons.Rounded.Tram
        TransitMode.OTHER, null -> Icons.Rounded.DirectionsSubway
    }

    /**
     * Resolves an appropriate Material ImageVector for a given transit mode string.
     */
    fun getTransitIconForMode(mode: String?): ImageVector {
        if (mode == null) return Icons.Rounded.DirectionsSubway
        return getTransitIconForMode(TransitMode.fromModeString(mode))
    }

    /**
     * Returns true if the given [Departure] represents a bus service.
     */
    fun isBusService(departure: Departure): Boolean = departure.isBus

    /**
     * Formats raw station mode identifiers (e.g. ["tube", "elizabeth-line", "national-rail"])
     * into clean, human-readable labels ("Underground, Elizabeth line, National Rail").
     */
    fun formatStationModes(modes: List<String>): String {
        if (modes.isEmpty()) return ""
        return modes
            .mapNotNull { rawMode ->
                val normalized = rawMode.trim().lowercase()
                if (normalized.isEmpty()) return@mapNotNull null
                when (normalized) {
                    "tube", "underground" -> "Underground"
                    "elizabeth-line", "elizabeth" -> "Elizabeth line"
                    "national-rail", "rail", "train" -> "National Rail"
                    "overground" -> "Overground"
                    "dlr" -> "DLR"
                    "tram", "light-rail" -> "Tram"
                    "bus" -> "Bus"
                    "mtr" -> "MTR"
                    "mrt" -> "MRT"
                    "lrt" -> "LRT"
                    "metro" -> "Metro"
                    "jr" -> "JR Rail"
                    else -> TransitMode.fromModeString(normalized).displayName
                }
            }
            .distinct()
            .joinToString(", ")
    }

    /**
     * Builds a region-aware search query for opening a [Station] in a map application.
     */
    fun buildStationMapsQuery(station: Station): String {
        val isBusOnly = station.isBusOnly
        val hasStationSuffix = station.name.contains("Station", ignoreCase = true) ||
            station.name.contains("Bus Stop", ignoreCase = true)
        val typeSuffix = when {
            hasStationSuffix -> ""
            isBusOnly -> "Bus Stop"
            else -> "Station"
        }
        val region = TransitRegion.fromStationId(station.id)
        val regionSuffix = when (region) {
            TransitRegion.GB -> {
                if (station.id.startsWith("940GZZNE", ignoreCase = true) ||
                    station.id.startsWith("TW_", ignoreCase = true)
                ) {
                    "Newcastle UK"
                } else if (station.id.isBodsTransitId) {
                    "UK"
                } else {
                    region.mapsSearchRegionSuffix
                }
            }
            else -> region.mapsSearchRegionSuffix
        }
        return listOf(station.displayName.trim(), typeSuffix, regionSuffix)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    /**
     * Matches line IDs permissively across TfL naming conventions.
     * Delegates to [TransitLineMatcher.isSameLine].
     */
    fun isSameLine(lineId1: String?, lineId2: String?): Boolean {
        return TransitLineMatcher.isSameLine(lineId1, lineId2)
    }
}
