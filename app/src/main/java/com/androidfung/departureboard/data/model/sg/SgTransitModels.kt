package com.androidfung.departureboard.data.model.sg

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.TransitMode

/**
 * Line definitions and badges for Singapore Mass Rapid Transit (MRT).
 */
enum class SgMrtLine(
    val code: String,
    val lineName: String,
    val chineseName: String,
    val badgeText: String,
    val color: Color
) {
    NSL("NSL", "North-South Line", "南北線", "NS", Color(0xFFD42E12)),
    EWL("EWL", "East-West Line", "東西線", "EW", Color(0xFF009645)),
    NEL("NEL", "North East Line", "東北線", "NE", Color(0xFF8F4199)),
    CCL("CCL", "Circle Line", "環狀線", "CC", Color(0xFFFF9A00)),
    DTL("DTL", "Downtown Line", "濱海市區線", "DT", Color(0xFF005EC4)),
    TEL("TEL", "Thomson-East Coast Line", "湯申－東海岸線", "TE", Color(0xFF9D5B25));

    fun toLineBadge(): LineBadgeInfo = LineBadgeInfo(
        lineId = code,
        displayName = lineName,
        lineCode = badgeText,
        backgroundColor = color,
        textColor = Color.White,
        mode = TransitMode.TUBE
    )

    companion object {
        fun fromCode(code: String?): SgMrtLine? {
            if (code == null) return null
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) || it.badgeText.equals(code, ignoreCase = true) }
        }
    }
}
