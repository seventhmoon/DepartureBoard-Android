package com.androidfung.departureboard.util

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object LocaleHelper {

    /**
     * Returns the active language tag.
     * If no per-app language is configured and [fallbackToDefault] is true,
     * returns the system default language tag.
     */
    fun getCurrentLanguageTag(fallbackToDefault: Boolean = true): String {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        return if (!appLocales.isEmpty) {
            appLocales[0]?.toLanguageTag().orEmpty()
        } else if (fallbackToDefault) {
            Locale.getDefault().toLanguageTag()
        } else {
            ""
        }
    }

    /**
     * Returns the current app-specific [Locale], or `null` if following system default.
     */
    fun getAppLocale(): Locale? {
        return try {
            val appLocales = AppCompatDelegate.getApplicationLocales()
            if (!appLocales.isEmpty) appLocales[0] else null
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Returns the effective active [Locale] (per-app override if configured, otherwise [Locale.getDefault]).
     * Safe to invoke from pure JVM unit tests where Android framework stubs may not initialize `AppCompatDelegate`.
     */
    fun getActiveLocale(): Locale {
        return getAppLocale() ?: Locale.getDefault()
    }

    /**
     * Sets the app's language using a BCP-47 language tag (e.g., "en-US", "ja").
     * Pass an empty string or null to reset to the system default.
     */
    fun setAppLanguage(languageTag: String?) {
        val locales = if (languageTag.isNullOrEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageTag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    /**
     * Sets the app's language using a [Locale].
     * Pass null to reset to the system default.
     */
    fun setAppLanguage(locale: Locale?) {
        val locales = if (locale == null) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.create(locale)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
