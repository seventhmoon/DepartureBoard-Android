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
            "tube", "underground", "mtr" -> Icons.Rounded.DirectionsSubway
            "bus" -> Icons.Rounded.DirectionsBus
            "national-rail", "overground", "elizabeth-line" -> Icons.Rounded.DirectionsRailway
            "dlr", "tram" -> Icons.Rounded.Tram
            else -> Icons.Rounded.DirectionsSubway
        }
    }

    /**
     * Matches line IDs permissively across TfL naming conventions.
     * Delegates to [com.androidfung.departureboard.data.model.TransitLineMatcher.isSameLine].
     */
    fun isSameLine(lineId1: String?, lineId2: String?): Boolean {
        return com.androidfung.departureboard.data.model.TransitLineMatcher.isSameLine(lineId1, lineId2)
    }
}
