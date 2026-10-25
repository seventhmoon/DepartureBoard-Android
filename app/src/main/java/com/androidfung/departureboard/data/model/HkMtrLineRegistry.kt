package com.androidfung.departureboard.data.model

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.i18n.LocalizedNameProvider

/**
 * Definition of an individual MTR line with code, bilingual names, badge colors, and route sequence.
 */
data class HkMtrLine(
    val code: String,
    override val nameEn: String,
    override val nameTc: String,
    val shortName: String,
    val color: Color,
    val textColor: Color = Color.White
) : LocalizedNameProvider {
    override val nameSc: String? get() = null
    val bilingualName: String get() = getBilingualName(parentheses = false)
    val displayName: String get() = getBilingualName(parentheses = true)
}

/**
 * Central registry for Hong Kong MTR lines, their official colors, and line badge factories.
 * Equivalent to [TflLineColors] for London.
 */
object HkMtrLineRegistry {

    val IslandLine = HkMtrLine("ISL", "Island Line", "港島綫", "ISL", Color(0xFF007DC5))
    val TsuenWanLine = HkMtrLine("TWL", "Tsuen Wan Line", "荃灣綫", "TWL", Color(0xFFE2231A))
    val KwunTongLine = HkMtrLine("KTL", "Kwun Tong Line", "觀塘綫", "KTL", Color(0xFF00AB4E))
    val EastRailLine = HkMtrLine("EAL", "East Rail Line", "東鐵綫", "EAL", Color(0xFF53B7E8))
    val TuenMaLine = HkMtrLine("TML", "Tuen Ma Line", "屯馬綫", "TML", Color(0xFF923D19))
    val TungChungLine = HkMtrLine("TCL", "Tung Chung Line", "東涌綫", "TCL", Color(0xFFF38B00))
    val AirportExpress = HkMtrLine("AEL", "Airport Express", "機場快綫", "AEL", Color(0xFF007078))
    val SouthIslandLine = HkMtrLine("SIL", "South Island Line", "南港島綫", "SIL", Color(0xFFB5BD00))
    val TseungKwanOLine = HkMtrLine("TKL", "Tseung Kwan O Line", "將軍澳綫", "TKL", Color(0xFF6B2C91))
    val DisneylandResortLine = HkMtrLine("DRL", "Disneyland Resort Line", "迪士尼綫", "DRL", Color(0xFFF173AC))

    val LINES: Map<String, HkMtrLine> = listOf(
        IslandLine,
        TsuenWanLine,
        KwunTongLine,
        EastRailLine,
        TuenMaLine,
        TungChungLine,
        AirportExpress,
        SouthIslandLine,
        TseungKwanOLine,
        DisneylandResortLine
    ).associateBy { it.code }

    fun getLine(code: String): HkMtrLine? = LINES[code.uppercase()]

    fun getLineName(code: String): String = LINES[code.uppercase()]?.bilingualName ?: "MTR $code"

    fun getLineColor(code: String): Color = LINES[code.uppercase()]?.color ?: Color(0xFF007DC5)

    fun getLineBadge(code: String): LineBadgeInfo {
        val line = LINES[code.uppercase()]
        return if (line != null) {
            LineBadgeInfo(
                lineId = line.code,
                displayName = line.shortName,
                backgroundColor = line.color,
                textColor = line.textColor,
                mode = TransitMode.TUBE
            )
        } else {
            LineBadgeInfo(
                lineId = code,
                displayName = code,
                backgroundColor = Color(0xFF007DC5),
                textColor = Color.White,
                mode = TransitMode.TUBE
            )
        }
    }
}
