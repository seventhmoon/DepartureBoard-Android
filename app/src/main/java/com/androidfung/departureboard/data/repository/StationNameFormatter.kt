package com.androidfung.departureboard.data.repository

/**
 * Utility for normalizing station names and removing redundant transport suffixes while
 * preserving key location indicators (e.g. stop letters or Battersea Power Station).
 */
internal object StationNameFormatter {

    fun clean(name: String): String {
        val trimmed = name.trim()
        // "Battersea Power Station" has "Station" as part of its proper landmark name
        if (trimmed.startsWith("Battersea Power Station", ignoreCase = true)) {
            return trimmed
                .replace(" Underground Station", "")
                .replace(" Underground", "")
                .replace(" Rail Station", "")
                .trim()
        }

        // Preserve any stop indicators and towards clauses in parentheses, e.g. "Euston (Stop D)", "Euston (Stop B, towards Aldwych)"
        val parenthesisMatch = Regex("""\s*(\([^)]+\))$""").find(trimmed)
        val suffix = parenthesisMatch?.value ?: ""
        val baseName = if (parenthesisMatch != null) trimmed.substring(0, parenthesisMatch.range.first).trim() else trimmed

        val cleanedBase = baseName
            .replace(" Underground Station", "")
            .replace(" Underground", "")
            .replace(" Rail Station", "")
            .replace(" DLR Station", "")
            .replace(" Tram Stop", "")
            .replace(" Bus Station", "")
            .replace(Regex("""\s+Station$"""), "")
            .trim()

        val trimmedSuffix = suffix.trim()
        return if (trimmedSuffix.isNotEmpty()) "$cleanedBase $trimmedSuffix" else cleanedBase
    }
}
