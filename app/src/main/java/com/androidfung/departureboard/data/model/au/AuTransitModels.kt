package com.androidfung.departureboard.data.model.au

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.TransitMode

/**
 * Line definitions and badges for Sydney Trains & Sydney Metro (TfNSW).
 */
enum class AuSydneyLine(
    val code: String,
    val lineName: String,
    val badgeText: String,
    val color: Color,
    val mode: TransitMode = TransitMode.NATIONAL_RAIL
) {
    M1("M1", "Metro M1 Northwest & City", "M", Color(0xFF009688), TransitMode.TUBE),
    T1("T1", "T1 North Shore & Western", "T1", Color(0xFFF99D1C)),
    T2("T2", "T2 Inner West & Leppington", "T2", Color(0xFF0098CD)),
    T3("T3", "T3 Liverpool & Inner West", "T3", Color(0xFFF37021)),
    T4("T4", "T4 Eastern Suburbs & Illawarra", "T4", Color(0xFF005AA3)),
    T8("T8", "T8 Airport & South Line", "T8", Color(0xFF00A551)),
    T9("T9", "T9 Northern Line", "T9", Color(0xFFD11242)),
    BCCN("CCN", "Central Coast & Newcastle Line", "CCN", Color(0xFFD11242));

    fun toLineBadge(): LineBadgeInfo = LineBadgeInfo(
        lineId = code,
        displayName = lineName,
        lineCode = badgeText,
        backgroundColor = color,
        textColor = Color.White,
        mode = mode
    )

    companion object {
        fun fromCode(code: String?): AuSydneyLine? {
            if (code == null) return null
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) || it.badgeText.equals(code, ignoreCase = true) }
        }
    }
}
