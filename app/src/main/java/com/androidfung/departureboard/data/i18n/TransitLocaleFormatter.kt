package com.androidfung.departureboard.data.i18n

import java.util.Locale

/**
 * Utility for formatting bilingual and locale-aware transit text.
 */
object TransitLocaleFormatter {

    fun isChinese(locale: Locale = Locale.getDefault()): Boolean {
        return locale.language.equals("zh", ignoreCase = true)
    }

    fun isJapanese(locale: Locale = Locale.getDefault()): Boolean {
        return locale.language.equals("ja", ignoreCase = true)
    }

    fun isWelsh(locale: Locale = Locale.getDefault()): Boolean {
        return locale.language.equals("cy", ignoreCase = true)
    }

    fun isSimplifiedChinese(locale: Locale = Locale.getDefault()): Boolean {
        if (!isChinese(locale)) return false
        val script = locale.script
        val country = locale.country
        return script.equals("Hans", ignoreCase = true) ||
                country.equals("CN", ignoreCase = true) ||
                country.equals("SG", ignoreCase = true)
    }

    fun formatLocalized(
        names: Map<String, String>,
        locale: Locale = Locale.getDefault(),
        fallbackToBilingual: Boolean = false
    ): String {
        val cleanEn = names["en"]?.trim()?.takeIf { it.isNotBlank() }
        val cleanTc = (names["zh-Hant"] ?: names["zh-HK"] ?: names["zh-TW"] ?: names["zh"])?.trim()?.takeIf { it.isNotBlank() }
        val cleanSc = (names["zh-Hans"] ?: names["zh-CN"] ?: names["zh-SG"])?.trim()?.takeIf { it.isNotBlank() }

        if (isChinese(locale)) {
            val primaryChinese = if (isSimplifiedChinese(locale)) cleanSc ?: cleanTc else cleanTc ?: cleanSc
            if (primaryChinese != null) {
                return if (fallbackToBilingual && cleanEn != null) "$primaryChinese ($cleanEn)" else primaryChinese
            }
        }

        if (isJapanese(locale)) {
            val ja = (names["ja"] ?: names["ja-Jpan"] ?: names["ja-Hrkt"] ?: names["ja-Kana"] ?: names["ja-Latn"])?.trim()?.takeIf { it.isNotBlank() }
            if (ja != null) {
                return if (fallbackToBilingual && cleanEn != null) "$ja ($cleanEn)" else ja
            }
        }

        if (isWelsh(locale)) {
            val cy = names["cy"]?.trim()?.takeIf { it.isNotBlank() }
            if (cy != null) {
                return if (fallbackToBilingual && cleanEn != null) "$cy ($cleanEn)" else cy
            }
        }

        if (cleanEn != null) {
            val primaryChinese = if (isSimplifiedChinese(locale)) cleanSc ?: cleanTc else cleanTc ?: cleanSc
            val primaryJapanese = (names["ja"] ?: names["ja-Jpan"])?.trim()?.takeIf { it.isNotBlank() }
            val primarySecondary = primaryChinese ?: primaryJapanese ?: names["cy"]?.trim()?.takeIf { it.isNotBlank() }
            return if (fallbackToBilingual && primarySecondary != null) {
                formatBilingual(cleanEn, primarySecondary, parentheses = true)
            } else {
                cleanEn
            }
        }

        // Fallback for any other language or when English is missing
        val requestedLang = locale.language.takeIf { it.isNotBlank() }
        if (requestedLang != null && names.containsKey(requestedLang)) {
            val valForLang = names[requestedLang]?.trim()?.takeIf { it.isNotBlank() }
            if (valForLang != null) return valForLang
        }

        return cleanTc ?: cleanSc ?: names["ja"] ?: names.values.firstOrNull { it.isNotBlank() }?.trim() ?: ""
    }

    fun formatBilingual(
        names: Map<String, String>,
        parentheses: Boolean = false
    ): String {
        val en = names["en"]?.trim()?.takeIf { it.isNotBlank() }
        val zh = (names["zh-Hant"] ?: names["zh-Hans"] ?: names["zh-HK"] ?: names["zh-TW"] ?: names["zh-CN"] ?: names["zh"])?.trim()?.takeIf { it.isNotBlank() }
        val ja = (names["ja"] ?: names["ja-Jpan"])?.trim()?.takeIf { it.isNotBlank() }
        val cy = names["cy"]?.trim()?.takeIf { it.isNotBlank() }
        val secondary = zh ?: ja ?: cy

        return formatBilingual(en, secondary, parentheses)
    }

    fun formatBilingual(
        nameEn: String?,
        nameChinese: String?,
        parentheses: Boolean = false
    ): String {
        val en = nameEn?.trim()?.takeIf { it.isNotBlank() }
        val zh = nameChinese?.trim()?.takeIf { it.isNotBlank() }

        return when {
            en != null && zh != null -> if (parentheses) "$en ($zh)" else "$en $zh"
            en != null -> en
            zh != null -> zh
            else -> ""
        }
    }
}
