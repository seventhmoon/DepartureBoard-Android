package com.androidfung.departureboard.data.model.gb

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.TflLineColors
import com.androidfung.departureboard.data.model.TransitLineDefinition
import com.androidfung.departureboard.data.model.TransitLineRegistry
import com.androidfung.departureboard.data.model.TransitMode

/**
 * Unified UK (`GB`) rapid-transit and commuter rail line definitions covering:
 * - Transport for London (11 London Underground lines, Elizabeth line, 6 London Overground lines, DLR, Tram, Cable Car)
 * - National Rail Corridors (Thameslink, Northern City / Great Northern)
 * - Regional UK Metro (Tyne and Wear Metro Yellow & Green lines)
 */
enum class GbTransitLine(
    override val code: String,
    val lineName: String,
    override val badgeText: String,
    override val color: Color,
    override val textColor: Color = TflLineColors.LightText,
    override val mode: TransitMode = TransitMode.TUBE,
    val aliases: Set<String> = emptySet()
) : TransitLineDefinition {

    // London Underground (11 lines)
    BAKERLOO("bakerloo", "Bakerloo", "BAK", TflLineColors.Bakerloo),
    CENTRAL("central", "Central", "CEN", TflLineColors.Central),
    CIRCLE("circle", "Circle", "CIR", TflLineColors.Circle, TflLineColors.DarkText),
    DISTRICT("district", "District", "DIS", TflLineColors.District),
    HAMMERSMITH_CITY(
        "hammersmith-city",
        "Hammersmith & City",
        "H&C",
        TflLineColors.HammersmithAndCity,
        TflLineColors.DarkText,
        aliases = setOf("hammersmith", "hammersmith & city", "hammersmith and city")
    ),
    JUBILEE("jubilee", "Jubilee", "JUB", TflLineColors.Jubilee),
    METROPOLITAN("metropolitan", "Metropolitan", "MET", TflLineColors.Metropolitan),
    // Note: GREAT_NORTHERN is placed before NORTHERN so "northern city" / "great northern" matches before "northern"
    GREAT_NORTHERN(
        "great-northern",
        "Northern City Line",
        "NCL",
        Color(0xFF003882),
        TflLineColors.LightText,
        TransitMode.NATIONAL_RAIL,
        aliases = setOf("great northern", "northern city", "northern city line")
    ),
    NORTHERN("northern", "Northern", "NOR", TflLineColors.Northern),
    PICCADILLY("piccadilly", "Piccadilly", "PIC", TflLineColors.Piccadilly),
    VICTORIA("victoria", "Victoria", "VIC", TflLineColors.Victoria),
    WATERLOO_CITY(
        "waterloo-city",
        "Waterloo & City",
        "W&C",
        TflLineColors.WaterlooAndCity,
        TflLineColors.DarkText,
        aliases = setOf("waterloo", "waterloo & city", "waterloo and city")
    ),

    // Elizabeth line
    ELIZABETH(
        "elizabeth-line",
        "Elizabeth line",
        "ELIZ",
        TflLineColors.ElizabethLine,
        TflLineColors.LightText,
        TransitMode.ELIZABETH_LINE,
        aliases = setOf("elizabeth", "elizabeth line")
    ),

    // London Overground (6 named lines + generic)
    LIBERTY("liberty", "Liberty", "LIB", TflLineColors.OvergroundLiberty, TflLineColors.LightText, TransitMode.OVERGROUND),
    LIONESS("lioness", "Lioness", "LIO", TflLineColors.OvergroundLioness, TflLineColors.LightText, TransitMode.OVERGROUND),
    MILDMAY("mildmay", "Mildmay", "MIL", TflLineColors.OvergroundMildmay, TflLineColors.LightText, TransitMode.OVERGROUND),
    SUFFRAGETTE("suffragette", "Suffragette", "SUF", TflLineColors.OvergroundSuffragette, TflLineColors.LightText, TransitMode.OVERGROUND),
    WEAVER("weaver", "Weaver", "WEA", TflLineColors.OvergroundWeaver, TflLineColors.LightText, TransitMode.OVERGROUND),
    WINDRUSH("windrush", "Windrush", "WIN", TflLineColors.OvergroundWindrush, TflLineColors.LightText, TransitMode.OVERGROUND),
    OVERGROUND(
        "overground",
        "London Overground",
        "LO",
        TflLineColors.LondonOverground,
        TflLineColors.LightText,
        TransitMode.OVERGROUND,
        aliases = setOf("london-overground", "london overground")
    ),

    // DLR, Tram, Cable Car
    DLR(
        "dlr",
        "DLR",
        "DLR",
        TflLineColors.Dlr,
        TflLineColors.LightText,
        TransitMode.DLR,
        aliases = setOf("docklands", "docklands light railway")
    ),
    TRAM("tram", "Tram", "TRAM", TflLineColors.Tram, TflLineColors.DarkText, TransitMode.TRAM),
    CABLE_CAR(
        "cable-car",
        "IFS Cloud Cable Car",
        "IFS",
        TflLineColors.CableCar,
        TflLineColors.LightText,
        TransitMode.CABLE_CAR,
        aliases = setOf("cable car", "ifs cloud cable car")
    ),

    // National Rail Corridors
    THAMESLINK(
        "thameslink",
        "Thameslink",
        "TL",
        Color(0xFFC70066),
        TflLineColors.LightText,
        TransitMode.NATIONAL_RAIL
    ),

    // Tyne and Wear Metro
    METRO_YELLOW(
        "metro-yellow",
        "Yellow Line",
        "MET",
        Color(0xFFF7B11E),
        Color.Black,
        TransitMode.TUBE
    ),
    METRO_GREEN(
        "metro-green",
        "Green Line",
        "MET",
        Color(0xFF009640),
        Color.White,
        TransitMode.TUBE
    );

    override val nameEn: String get() = lineName

    fun matches(lineId: String?, lineName: String?): Boolean {
        val normId = lineId?.lowercase()?.trim().orEmpty()
        val normName = lineName?.lowercase()?.trim().orEmpty()
        if (normId.isEmpty() && normName.isEmpty()) return false

        if (normId == code || normName == code) return true
        if (aliases.any { normId.contains(it) || normName.contains(it) }) return true

        return when (this) {
            NORTHERN -> normId == "northern" || (normName.contains("northern") && !normName.contains("city"))
            OVERGROUND -> normId.contains("overground") || normName.contains("overground")
            TRAM -> normId.contains("tram") || normName.contains("tram")
            else -> normId == code || normName.contains(code)
        }
    }

    companion object : TransitLineRegistry {
        override val allLines: Collection<GbTransitLine> get() = entries

        override fun fromCode(code: String?): GbTransitLine? {
            if (code.isNullOrBlank()) return null
            val trimmed = code.trim()
            return entries.firstOrNull {
                it.code.equals(trimmed, ignoreCase = true) ||
                    it.badgeText.equals(trimmed, ignoreCase = true) ||
                    it.matches(trimmed, trimmed)
            }
        }

        fun matchLine(lineId: String?, lineName: String?): GbTransitLine? =
            entries.firstOrNull { it.matches(lineId, lineName) }

        fun resolveBadge(lineId: String?, lineName: String?, modeName: String?): LineBadgeInfo? {
            val matched = matchLine(lineId, lineName) ?: return null
            if (matched == OVERGROUND && !lineName.isNullOrBlank()) {
                return matched.toLineBadge().copy(displayName = lineName)
            }
            return matched.toLineBadge()
        }
    }
}
