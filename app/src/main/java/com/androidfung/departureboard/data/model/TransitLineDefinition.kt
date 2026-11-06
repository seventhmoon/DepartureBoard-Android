package com.androidfung.departureboard.data.model

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.i18n.LocalizedNameProvider

/**
 * Unified domain contract for a transit line across all supported regions (GB, HK, SG, AU, JP).
 * Provides standardized localization, brand colors, transit mode, and [LineBadgeInfo] conversion.
 */
interface TransitLineDefinition : LocalizedNameProvider {
    val code: String
    val badgeText: String get() = code
    val color: Color
    val textColor: Color get() = Color.White
    val mode: TransitMode get() = TransitMode.METRO

    fun toLineBadge(): LineBadgeInfo = LineBadgeInfo(
        lineId = code,
        displayName = nameEn ?: code,
        lineCode = badgeText,
        backgroundColor = color,
        textColor = textColor,
        mode = mode
    )
}

/**
 * Unified registry contract for looking up transit lines and their visual badges within a region.
 */
interface TransitLineRegistry {
    val allLines: Collection<TransitLineDefinition>

    fun fromCode(code: String?): TransitLineDefinition? {
        if (code.isNullOrBlank()) return null
        val trimmed = code.trim()
        return allLines.firstOrNull {
            it.code.equals(trimmed, ignoreCase = true) ||
                it.badgeText.equals(trimmed, ignoreCase = true)
        }
    }

    fun getLineBadge(
        code: String,
        fallbackColor: Color = Color(0xFF0019A8)
    ): LineBadgeInfo {
        return fromCode(code)?.toLineBadge() ?: LineBadgeInfo(
            lineId = code,
            displayName = code,
            lineCode = code,
            backgroundColor = fallbackColor,
            textColor = Color.White,
            mode = TransitMode.METRO
        )
    }
}
