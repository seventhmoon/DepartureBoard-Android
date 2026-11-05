package com.androidfung.departureboard.data.i18n

import com.androidfung.departureboard.util.LocaleHelper
import java.util.Locale

/**
 * Interface for transit entities (stations, routes, lines, stops) that support multi-lingual names.
 */
interface LocalizedNameProvider {
    val localizedNames: Map<String, String>

    /**
     * Convenience property for English name.
     */
    val nameEn: String?
        get() = localizedNames["en"]?.takeIf { it.isNotBlank() }

    /**
     * Resolves the entity name dynamically according to the specified or active app [Locale].
     */
    fun getLocalizedName(locale: Locale = LocaleHelper.getAppLocale() ?: Locale.getDefault()): String {
        return TransitLocaleFormatter.formatLocalized(
            names = localizedNames,
            locale = locale
        )
    }

    /**
     * Standard bilingual representation, e.g. "Central (中環)" or "Central 中環".
     */
    fun getBilingualName(parentheses: Boolean = false): String {
        return TransitLocaleFormatter.formatBilingual(
            names = localizedNames,
            parentheses = parentheses
        )
    }
}
