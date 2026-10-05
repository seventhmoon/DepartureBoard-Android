package com.androidfung.departureboard.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsRailway
import androidx.compose.material.icons.rounded.DirectionsSubway
import androidx.compose.material.icons.rounded.Tram
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Shared transit UI helpers for icon resolution and line equivalence checks across components.
 */
object TransitIconHelper {

    /**
     * Resolves an appropriate Material ImageVector for a given transit mode string.
     */
    fun getTransitIconForMode(mode: String?): ImageVector {
        if (mode == null) return Icons.Rounded.DirectionsSubway
        return when (mode.lowercase().trim()) {
            "tube", "underground" -> Icons.Rounded.DirectionsSubway
            "bus" -> Icons.Rounded.DirectionsBus
            "national-rail", "overground", "elizabeth-line" -> Icons.Rounded.DirectionsRailway
            "dlr", "tram" -> Icons.Rounded.Tram
            else -> Icons.Rounded.DirectionsSubway
        }
    }

    /**
     * Matches line IDs permissively across TfL naming conventions.
     * e.g., "elizabeth" vs "elizabeth-line", "hammersmith-city" vs "hammersmith" or "h&c".
     */
    fun isSameLine(lineId1: String?, lineId2: String?): Boolean {
        if (lineId1.isNullOrBlank() || lineId2.isNullOrBlank()) return false
        val id1 = lineId1.lowercase().trim()
        val id2 = lineId2.lowercase().trim()
        if (id1 == id2) return true

        // Elizabeth line aliases
        if ((id1 == "elizabeth" || id1 == "elizabeth-line" || id1 == "xr") && 
            (id2 == "elizabeth" || id2 == "elizabeth-line" || id2 == "xr")) return true
        // Lioness & London Overground aliases for Watford DC line / Euston
        if ((id1 == "lioness" || id1 == "overground" || id1 == "nr-lo") &&
            (id2 == "lioness" || id2 == "overground" || id2 == "nr-lo")) return true
        // Hammersmith & City aliases
        if ((id1.contains("hammersmith") || id1 == "h&c") && (id2.contains("hammersmith") || id2 == "h&c")) return true
        // Waterloo & City aliases
        if ((id1.contains("waterloo") || id1 == "w&c") && (id2.contains("waterloo") || id2 == "w&c")) return true

        return false
    }
}
