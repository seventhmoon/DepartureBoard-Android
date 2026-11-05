package com.androidfung.departureboard.data.i18n

import java.util.Locale

/**
 * Utility for formatting bilingual and locale-aware transit text.
 */
object TransitLocaleFormatter {

    fun isChinese(locale: Locale = Locale.getDefault()): Boolean {
        return locale.language.equals("zh", ignoreCase = true)
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

        if (cleanEn != null) {
            val primaryChinese = if (isSimplifiedChinese(locale)) cleanSc ?: cleanTc else cleanTc ?: cleanSc
            return if (fallbackToBilingual && primaryChinese != null) {
                formatBilingual(cleanEn, primaryChinese, parentheses = true)
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

        return cleanTc ?: cleanSc ?: names.values.firstOrNull { it.isNotBlank() }?.trim() ?: ""
    }

    fun formatBilingual(
        names: Map<String, String>,
        parentheses: Boolean = false
    ): String {
        val en = names["en"]?.trim()?.takeIf { it.isNotBlank() }
        val zh = (names["zh-Hant"] ?: names["zh-Hans"] ?: names["zh-HK"] ?: names["zh-TW"] ?: names["zh-CN"] ?: names["zh"])?.trim()?.takeIf { it.isNotBlank() }

        return formatBilingual(en, zh, parentheses)
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
