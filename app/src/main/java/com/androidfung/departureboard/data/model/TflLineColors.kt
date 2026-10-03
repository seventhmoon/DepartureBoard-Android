package com.androidfung.departureboard.data.model

import androidx.compose.ui.graphics.Color

/**
 * Official TfL line brand colors & styling definitions according to TfL design standards.
 *
 * Official TfL Line Hex Codes:
 * - Bakerloo: #B36305
 * - Central: #E32017
 * - Circle: #FFD300 (Dark text)
 * - District: #00782A
 * - Hammersmith & City: #F3A9BB (Dark text)
 * - Jubilee: #A0A5A9
 * - Metropolitan: #9B0056
 * - Northern: #000000
 * - Piccadilly: #003688
 * - Victoria: #0098D4
 * - Waterloo & City: #95CDBA (Dark text)
 * - Elizabeth line: #6950A1
 * - London Overground: #EE7C0E
 * - DLR (Docklands Light Railway): #00A4A7
 * - London Buses: #DC241F
 * - London Trams: #00BD19
 * - IFS Cloud Cable Car: #E21836
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
     * Resolves the badge styling for a given line ID and/or mode name.
     */
    fun getLineBadge(lineId: String?, lineName: String?, modeName: String?): LineBadgeInfo {
        val normalizedId = lineId?.lowercase()?.trim() ?: ""
        val normalizedName = lineName?.lowercase()?.trim() ?: ""
        val mode = TransitMode.fromModeString(modeName)

        val displayName = when {
            !lineName.isNullOrBlank() -> lineName
            !lineId.isNullOrBlank() -> lineId.replaceFirstChar { it.uppercase() }
            else -> mode.displayName
        }

        return when {
            // Underground Lines
            normalizedId == "bakerloo" || normalizedName.contains("bakerloo") ->
                LineBadgeInfo(lineId = "bakerloo", displayName = "Bakerloo", backgroundColor = Bakerloo, textColor = LightText, mode = TransitMode.TUBE)

            normalizedId == "central" || normalizedName.contains("central") ->
                LineBadgeInfo(lineId = "central", displayName = "Central", backgroundColor = Central, textColor = LightText, mode = TransitMode.TUBE)

            normalizedId == "circle" || normalizedName.contains("circle") ->
                LineBadgeInfo(lineId = "circle", displayName = "Circle", backgroundColor = Circle, textColor = DarkText, mode = TransitMode.TUBE)

            normalizedId == "district" || normalizedName.contains("district") ->
                LineBadgeInfo(lineId = "district", displayName = "District", backgroundColor = District, textColor = LightText, mode = TransitMode.TUBE)

            normalizedId.contains("hammersmith") || normalizedName.contains("hammersmith") ->
                LineBadgeInfo(lineId = "hammersmith-city", displayName = "Hammersmith & City", backgroundColor = HammersmithAndCity, textColor = DarkText, mode = TransitMode.TUBE)

            normalizedId == "jubilee" || normalizedName.contains("jubilee") ->
                LineBadgeInfo(lineId = "jubilee", displayName = "Jubilee", backgroundColor = Jubilee, textColor = LightText, mode = TransitMode.TUBE)

            normalizedId == "metropolitan" || normalizedName.contains("metropolitan") ->
                LineBadgeInfo(lineId = "metropolitan", displayName = "Metropolitan", backgroundColor = Metropolitan, textColor = LightText, mode = TransitMode.TUBE)

            // Northern City Line must be checked before Tube Northern line!
            normalizedId.contains("great-northern") || normalizedName.contains("great northern") || normalizedName.contains("northern city") ->
                LineBadgeInfo(lineId = "great-northern", displayName = "Northern City Line", backgroundColor = Color(0xFF003882), textColor = LightText, mode = TransitMode.NATIONAL_RAIL)

            normalizedId == "northern" || (normalizedName.contains("northern") && !normalizedName.contains("city")) ->
                LineBadgeInfo(lineId = "northern", displayName = "Northern", backgroundColor = Northern, textColor = LightText, mode = TransitMode.TUBE)

            normalizedId == "piccadilly" || normalizedName.contains("piccadilly") ->
                LineBadgeInfo(lineId = "piccadilly", displayName = "Piccadilly", backgroundColor = Piccadilly, textColor = LightText, mode = TransitMode.TUBE)

            normalizedId == "victoria" || normalizedName.contains("victoria") ->
                LineBadgeInfo(lineId = "victoria", displayName = "Victoria", backgroundColor = Victoria, textColor = LightText, mode = TransitMode.TUBE)

            normalizedId.contains("waterloo") || normalizedName.contains("waterloo & city") ->
                LineBadgeInfo(lineId = "waterloo-city", displayName = "Waterloo & City", backgroundColor = WaterlooAndCity, textColor = DarkText, mode = TransitMode.TUBE)

            // Elizabeth Line
            normalizedId == "elizabeth" || normalizedId == "elizabeth-line" || normalizedName.contains("elizabeth") ->
                LineBadgeInfo(lineId = "elizabeth-line", displayName = "Elizabeth line", backgroundColor = ElizabethLine, textColor = LightText, mode = TransitMode.ELIZABETH_LINE)

            // Overground
            normalizedId.contains("overground") || normalizedName.contains("overground") ||
            normalizedId in listOf("lioness", "mildmay", "windrush", "weaver", "suffragette", "liberty") ->
                LineBadgeInfo(lineId = "overground", displayName = displayName.ifBlank { "London Overground" }, backgroundColor = LondonOverground, textColor = LightText, mode = TransitMode.OVERGROUND)

            // DLR
            normalizedId == "dlr" || normalizedName.contains("dlr") || normalizedName.contains("docklands") ->
                LineBadgeInfo(lineId = "dlr", displayName = "DLR", backgroundColor = Dlr, textColor = LightText, mode = TransitMode.DLR)

            // Tram
            normalizedId.contains("tram") || normalizedName.contains("tram") ->
                LineBadgeInfo(lineId = "tram", displayName = "Tram", backgroundColor = Tram, textColor = DarkText, mode = TransitMode.TRAM)

            // Cable Car
            normalizedId.contains("cable-car") || normalizedName.contains("cable car") ->
                LineBadgeInfo(lineId = "cable-car", displayName = "IFS Cloud Cable Car", backgroundColor = CableCar, textColor = LightText, mode = TransitMode.CABLE_CAR)

            // Thameslink
            normalizedId.contains("thameslink") || normalizedName.contains("thameslink") ->
                LineBadgeInfo(lineId = "thameslink", displayName = "Thameslink", backgroundColor = Color(0xFFC70066), textColor = LightText, mode = TransitMode.NATIONAL_RAIL)

            // Great Northern / Northern City Line
            normalizedId.contains("great-northern") || normalizedName.contains("great northern") || normalizedName.contains("northern city") ->
                LineBadgeInfo(lineId = "great-northern", displayName = "Northern City Line", backgroundColor = Color(0xFF003882), textColor = LightText, mode = TransitMode.NATIONAL_RAIL)

            // Bus
            mode == TransitMode.BUS || normalizedId.toIntOrNull() != null || normalizedId.matches(Regex("[a-z]?[0-9]+[a-z]?")) ->
                LineBadgeInfo(lineId = lineId ?: "bus", displayName = displayName, backgroundColor = Bus, textColor = LightText, mode = TransitMode.BUS)

            // National Rail
            mode == TransitMode.NATIONAL_RAIL || normalizedId.contains("national-rail") ->
                LineBadgeInfo(lineId = "national-rail", displayName = displayName.ifBlank { "National Rail" }, backgroundColor = NationalRail, textColor = LightText, mode = TransitMode.NATIONAL_RAIL)

            else ->
                LineBadgeInfo(lineId = lineId ?: "transit", displayName = displayName, backgroundColor = DefaultTransit, textColor = LightText, mode = mode)
        }
    }
}
