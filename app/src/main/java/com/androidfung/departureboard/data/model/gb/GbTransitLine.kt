package com.androidfung.departureboard.data.model.gb

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.model.LineBadgeInfo
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
    override val mode: TransitMode = TransitMode.METRO,
    override val minHeadwaySeconds: Int = mode.defaultMinHeadwaySeconds,
    override val typicalFrequencyMinutes: Int = 5,
    val aliases: Set<String> = emptySet()
) : TransitLineDefinition {

    // London Underground (11 lines)
    BAKERLOO("bakerloo", "Bakerloo", "BAK", TflLineColors.Bakerloo, minHeadwaySeconds = 90, typicalFrequencyMinutes = 3),
    CENTRAL("central", "Central", "CEN", TflLineColors.Central, minHeadwaySeconds = 90, typicalFrequencyMinutes = 2),
    CIRCLE("circle", "Circle", "CIR", TflLineColors.Circle, TflLineColors.DarkText, minHeadwaySeconds = 120, typicalFrequencyMinutes = 10),
    DISTRICT("district", "District", "DIS", TflLineColors.District, minHeadwaySeconds = 100, typicalFrequencyMinutes = 3),
    HAMMERSMITH_CITY(
        "hammersmith-city",
        "Hammersmith & City",
        "H&C",
        TflLineColors.HammersmithAndCity,
        TflLineColors.DarkText,
        minHeadwaySeconds = 120,
        typicalFrequencyMinutes = 10,
        aliases = setOf("hammersmith & city", "hammersmith and city", "h&c")
    ),
    JUBILEE("jubilee", "Jubilee", "JUB", TflLineColors.Jubilee, minHeadwaySeconds = 90, typicalFrequencyMinutes = 2),
    METROPOLITAN("metropolitan", "Metropolitan", "MET", TflLineColors.Metropolitan, minHeadwaySeconds = 120, typicalFrequencyMinutes = 4),
    // Note: GREAT_NORTHERN is placed before NORTHERN so "northern city" / "great northern" matches before "northern"
    GREAT_NORTHERN(
        "great-northern",
        "Great Northern",
        "GN",
        Color(0xFF003882),
        TflLineColors.LightText,
        TransitMode.RAIL,
        minHeadwaySeconds = 180,
        typicalFrequencyMinutes = 10,
        aliases = setOf("great northern", "northern city", "northern city line", "northern-city-line", "nr-gn")
    ),
    NORTHERN("northern", "Northern", "NOR", TflLineColors.Northern, minHeadwaySeconds = 90, typicalFrequencyMinutes = 2),
    PICCADILLY("piccadilly", "Piccadilly", "PIC", TflLineColors.Piccadilly, minHeadwaySeconds = 90, typicalFrequencyMinutes = 3),
    VICTORIA("victoria", "Victoria", "VIC", TflLineColors.Victoria, minHeadwaySeconds = 80, typicalFrequencyMinutes = 2),
    WATERLOO_CITY(
        "waterloo-city",
        "Waterloo & City",
        "W&C",
        TflLineColors.WaterlooAndCity,
        TflLineColors.DarkText,
        minHeadwaySeconds = 150,
        typicalFrequencyMinutes = 5,
        aliases = setOf("waterloo & city", "waterloo and city", "w&c")
    ),

    // Elizabeth line (central tunnel ATO signalling minimum separation is ~120s)
    ELIZABETH(
        "elizabeth-line",
        "Elizabeth line",
        "ELIZ",
        TflLineColors.ElizabethLine,
        TflLineColors.LightText,
        TransitMode.RAIL,
        minHeadwaySeconds = 120,
        typicalFrequencyMinutes = 5,
        aliases = setOf("elizabeth", "elizabeth line", "xr")
    ),

    // London Overground (6 named lines + generic)
    LIBERTY("liberty", "Liberty", "LIB", TflLineColors.OvergroundLiberty, TflLineColors.LightText, TransitMode.RAIL),
    LIONESS("lioness", "Lioness", "LIO", TflLineColors.OvergroundLioness, TflLineColors.LightText, TransitMode.RAIL, aliases = setOf("lioness")),
    MILDMAY("mildmay", "Mildmay", "MIL", TflLineColors.OvergroundMildmay, TflLineColors.LightText, TransitMode.RAIL),
    SUFFRAGETTE("suffragette", "Suffragette", "SUF", TflLineColors.OvergroundSuffragette, TflLineColors.LightText, TransitMode.RAIL),
    WEAVER("weaver", "Weaver", "WEA", TflLineColors.OvergroundWeaver, TflLineColors.LightText, TransitMode.RAIL),
    WINDRUSH("windrush", "Windrush", "WIN", TflLineColors.OvergroundWindrush, TflLineColors.LightText, TransitMode.RAIL),
    OVERGROUND(
        "overground",
        "London Overground",
        "LO",
        TflLineColors.LondonOverground,
        TflLineColors.LightText,
        TransitMode.RAIL,
        aliases = setOf("london-overground", "london overground", "nr-lo", "lo")
    ),

    // DLR, Tram, Cable Car
    DLR(
        "dlr",
        "DLR",
        "DLR",
        TflLineColors.Dlr,
        TflLineColors.LightText,
        TransitMode.TRAM,
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
        TransitMode.RAIL
    ),

    // Tyne and Wear Metro
    METRO_YELLOW(
        "metro-yellow",
        "Yellow Line",
        "YEL",
        Color(0xFFF7B11E),
        Color.Black,
        TransitMode.METRO
    ),
    METRO_GREEN(
        "metro-green",
        "Green Line",
        "GRN",
        Color(0xFF009640),
        Color.White,
        TransitMode.METRO
    );

    override val localizedNames: Map<String, String> get() = mapOf("en" to lineName)

    val isOverground: Boolean get() = this in OVERGROUND_LINES

    fun matches(lineId: String?, lineName: String?): Boolean {
        val normId = lineId?.lowercase()?.trim().orEmpty()
        val normName = lineName?.lowercase()?.trim().orEmpty()
        if (normId.isEmpty() && normName.isEmpty()) return false

        val lowerLineName = this.lineName.lowercase()
        if (normId == code || normName == code || normName == lowerLineName ||
            normName == "$code line" || normName == "$lowerLineName line"
        ) return true
        if (aliases.any { alias ->
                if (alias.length <= 3) {
                    normId == alias || normName == alias
                } else {
                    normId == alias || normName == alias || normId.contains(alias) || normName.contains(alias)
                }
            }
        ) return true

        return when (this) {
            CENTRAL -> normId == "central" || normName == "central" || normName == "central line"
            NORTHERN -> normId == "northern" || (
                (normName == "northern" || normName == "northern line") &&
                    !normName.contains("city") && !normName.contains("great") && !normName.contains("rail") && !normName.contains("train")
                )
            OVERGROUND -> normId.contains("overground") || normName.contains("overground")
            TRAM -> normId == "tram" || normId == "london-tram" || normName == "tram" || normName == "london trams" || normName == "tramlink"
            else -> false
        }
    }

    companion object : TransitLineRegistry {
        val OVERGROUND_LINES: Set<GbTransitLine> = setOf(
            LIBERTY, LIONESS, MILDMAY, SUFFRAGETTE, WEAVER, WINDRUSH, OVERGROUND
        )

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
            val normMode = modeName?.lowercase()?.trim().orEmpty()
            val normId = lineId?.lowercase()?.trim().orEmpty()
            // Prevent National Rail TOCs (e.g. Northern Trains in Northern England, or Southeastern Victoria)
            // from resolving to a London Underground METRO line unless the lineId explicitly matches the Tube code.
            if (matched.mode == TransitMode.METRO &&
                (normMode == "national-rail" || normMode == "rail" || normId.startsWith("nr-")) &&
                normId != matched.code
            ) {
                return null
            }
            if (matched == OVERGROUND && !lineName.isNullOrBlank()) {
                return matched.toLineBadge().copy(displayName = lineName)
            }
            return matched.toLineBadge()
        }
    }
}
