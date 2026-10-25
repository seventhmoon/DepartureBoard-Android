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
        nameEn: String?,
        nameTc: String?,
        nameSc: String? = null,
        locale: Locale = Locale.getDefault(),
        fallbackToBilingual: Boolean = false
    ): String {
        val cleanEn = nameEn?.trim()?.takeIf { it.isNotBlank() }
        val cleanTc = nameTc?.trim()?.takeIf { it.isNotBlank() }
        val cleanSc = nameSc?.trim()?.takeIf { it.isNotBlank() }

        if (isChinese(locale)) {
            val primaryChinese = if (isSimplifiedChinese(locale)) cleanSc ?: cleanTc else cleanTc ?: cleanSc
            if (primaryChinese != null) {
                return if (fallbackToBilingual && cleanEn != null) "$primaryChinese ($cleanEn)" else primaryChinese
            }
        }

        if (cleanEn != null) {
            return if (fallbackToBilingual && (cleanTc != null || cleanSc != null)) {
                formatBilingual(cleanEn, cleanTc ?: cleanSc, parentheses = true)
            } else {
                cleanEn
            }
        }

        return cleanTc ?: cleanSc ?: ""
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
