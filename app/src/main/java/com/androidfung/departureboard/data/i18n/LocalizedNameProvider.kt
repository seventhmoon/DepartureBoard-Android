package com.androidfung.departureboard.data.i18n

import java.util.Locale

/**
 * Interface for transit entities (stations, routes, lines, stops) that support multi-lingual names.
 */
interface LocalizedNameProvider {
    val nameEn: String?
    val nameTc: String?
    val nameSc: String?

    /**
     * Resolves the entity name dynamically according to the specified or default device [Locale].
     * - Traditional Chinese (zh-HK, zh-TW, zh-MO, zh-Hant): prefers [nameTc]
     * - Simplified Chinese (zh-CN, zh-SG, zh-Hans): prefers [nameSc], falls back to [nameTc]
     * - English / other: prefers [nameEn]
     */
    fun getLocalizedName(locale: Locale = Locale.getDefault()): String {
        return TransitLocaleFormatter.formatLocalized(
            nameEn = nameEn,
            nameTc = nameTc,
            nameSc = nameSc,
            locale = locale
        )
    }

    /**
     * Standard bilingual representation, e.g. "Central (中環)" or "Central 中環".
     */
    fun getBilingualName(parentheses: Boolean = false): String {
        return TransitLocaleFormatter.formatBilingual(
            nameEn = nameEn,
            nameChinese = nameTc ?: nameSc,
            parentheses = parentheses
        )
    }
}
