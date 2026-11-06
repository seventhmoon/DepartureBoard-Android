package com.androidfung.departureboard.data.model.gb

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.model.gb.GbTransitLine

/**
 * Official TfL line brand colors & styling definitions according to TfL design standards.
 * Delegates line lookup to [GbTransitLine] while providing fallback styling for buses and National Rail.
 */
object TflLineColors {

    // London Underground Lines
    val Bakerloo = Color(0xFFB36305)
    val Central = Color(0xFFE32017)
    val Circle = Color(0xFFFFD300)
    val District = Color(0xFF00782A)
    val HammersmithAndCity = Color(0xFFF3A9BB)
    val Jubilee = Color(0xFFA0A5A9)
    val Metropolitan = Color(0xFF9B0056)
    val Northern = Color(0xFF000000)
    val Piccadilly = Color(0xFF003688)
    val Victoria = Color(0xFF0098D4)
    val WaterlooAndCity = Color(0xFF95CDBA)

    // Other TfL Rail & Mass Transit Modes
    val ElizabethLine = Color(0xFF6950A1)
    val LondonOverground = Color(0xFFEE7C0E)
    // 6 London Overground lines (2024 TfL standard)
    val OvergroundLiberty = Color(0xFF606667)
    val OvergroundLioness = Color(0xFFEF9600)
    val OvergroundMildmay = Color(0xFF2774AE)
    val OvergroundSuffragette = Color(0xFF5BA763)
    val OvergroundWeaver = Color(0xFF893B67)
    val OvergroundWindrush = Color(0xFFD22730)

    val Dlr = Color(0xFF00A4A7)
    val Tram = Color(0xFF00BD19)
    val CableCar = Color(0xFFE21836)

    // London Buses & General
    val Bus = Color(0xFFDC241F)
    val NationalRail = Color(0xFF1E3561)
    val DefaultTransit = Color(0xFF0019A8) // TfL Corporate Blue

    // Contrasting foreground text colors
    val DarkText = Color(0xFF111111)
    val LightText = Color(0xFFFFFFFF)

    /**
     * Set of standard London Underground line IDs for fast O(1) membership check.
     */
    val TUBE_LINE_IDS: Set<String> = setOf(
        "bakerloo", "central", "circle", "district", "hammersmith-city",
        "jubilee", "metropolitan", "northern", "piccadilly", "victoria",
        "waterloo-city", "tube"
    )

    /**
     * Resolves the badge styling for a given line ID and/or mode name.
     */
    fun getLineBadge(lineId: String?, lineName: String?, modeName: String?): LineBadgeInfo {
        GbTransitLine.resolveBadge(lineId, lineName, modeName)?.let { return it }

        val normalizedId = lineId?.lowercase()?.trim() ?: ""
        val mode = TransitMode.fromModeString(modeName)

        val displayName = when {
            !lineName.isNullOrBlank() -> lineName
            !lineId.isNullOrBlank() -> lineId.replaceFirstChar { it.uppercase() }
            else -> mode.displayName
        }

        return when {
            // Bus
            mode == TransitMode.BUS || normalizedId.toIntOrNull() != null || normalizedId.matches(Regex("[a-z]?[0-9]+[a-z]?")) ->
                LineBadgeInfo(
                    lineId = lineId ?: "bus",
                    displayName = displayName,
                    backgroundColor = Bus,
                    textColor = LightText,
                    mode = TransitMode.BUS,
                    lineCode = displayName
                )

            // National Rail
            mode == TransitMode.RAIL || normalizedId.contains("national-rail") ->
                LineBadgeInfo(
                    lineId = "national-rail",
                    displayName = displayName.ifBlank { "National Rail" },
                    backgroundColor = NationalRail,
                    textColor = LightText,
                    mode = TransitMode.RAIL,
                    lineCode = "NR"
                )

            else ->
                LineBadgeInfo(
                    lineId = lineId ?: "transit",
                    displayName = displayName,
                    backgroundColor = DefaultTransit,
                    textColor = LightText,
                    mode = mode,
                    lineCode = displayName.take(3).uppercase()
                )
        }
    }
}
