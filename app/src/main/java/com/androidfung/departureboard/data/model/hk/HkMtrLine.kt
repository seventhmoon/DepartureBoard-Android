package com.androidfung.departureboard.data.model.hk

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.TransitLineDefinition
import com.androidfung.departureboard.data.model.TransitLineRegistry

/**
 * Hong Kong MTR lines with code, bilingual names, and official badge colors.
 * The companion object doubles as the regional [TransitLineRegistry].
 */
enum class HkMtrLine(
    override val code: String,
    val lineName: String,
    val chineseName: String,
    val shortName: String,
    override val color: Color,
    override val textColor: Color = Color.White
) : TransitLineDefinition {
    ISLAND("ISL", "Island Line", "港島綫", "ISL", Color(0xFF007DC5)),
    TSUEN_WAN("TWL", "Tsuen Wan Line", "荃灣綫", "TWL", Color(0xFFE2231A)),
    KWUN_TONG("KTL", "Kwun Tong Line", "觀塘綫", "KTL", Color(0xFF00AB4E)),
    EAST_RAIL("EAL", "East Rail Line", "東鐵綫", "EAL", Color(0xFF53B7E8)),
    TUEN_MA("TML", "Tuen Ma Line", "屯馬綫", "TML", Color(0xFF923D19)),
    TUNG_CHUNG("TCL", "Tung Chung Line", "東涌綫", "TCL", Color(0xFFF38B00)),
    AIRPORT_EXPRESS("AEL", "Airport Express", "機場快綫", "AEL", Color(0xFF007078)),
    SOUTH_ISLAND("SIL", "South Island Line", "南港島綫", "SIL", Color(0xFFB5BD00)),
    TSEUNG_KWAN_O("TKL", "Tseung Kwan O Line", "將軍澳綫", "TKL", Color(0xFF6B2C91)),
    DISNEYLAND_RESORT("DRL", "Disneyland Resort Line", "迪士尼綫", "DRL", Color(0xFFF173AC));

    override val localizedNames: Map<String, String> get() = mapOf(
        "en" to lineName,
        "zh-Hant" to chineseName
    )

    override val badgeText: String get() = shortName
    val bilingualName: String get() = getBilingualName(parentheses = false)
    val displayName: String get() = getBilingualName(parentheses = true)

    override fun toLineBadge(): LineBadgeInfo = LineBadgeInfo(
        lineId = code,
        displayName = shortName,
        lineCode = shortName,
        backgroundColor = color,
        textColor = textColor,
        mode = mode
    )

    companion object : TransitLineRegistry {
        private val DEFAULT_COLOR = Color(0xFF007DC5)

        /** Lines keyed by their uppercase MTR code (e.g. `"TWL"`). */
        val LINES: Map<String, HkMtrLine> = entries.associateBy { it.code }

        override val allLines: Collection<HkMtrLine> get() = entries

        fun getLine(code: String): HkMtrLine? = LINES[code.uppercase()]

        fun getLineName(code: String): String = getLine(code)?.bilingualName ?: "MTR $code"

        fun getLineColor(code: String): Color = getLine(code)?.color ?: DEFAULT_COLOR

        fun getLineBadge(code: String): LineBadgeInfo = getLineBadge(code = code, fallbackColor = DEFAULT_COLOR)
    }
}
