package com.androidfung.departureboard.data.repository.region.gb

/**
 * Utility for normalizing station names and removing redundant transport suffixes while
 * preserving key location indicators (e.g. stop letters or Battersea Power Station).
 */
internal object StationNameFormatter {

    private val PARENTHESIS_REGEX = Regex("""\s*(\([^)]+\))$""")
    private val STATION_SUFFIX_REGEX = Regex("""\s+Station$""")

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
        val parenthesisMatch = PARENTHESIS_REGEX.find(trimmed)
        val suffix = parenthesisMatch?.value ?: ""
        val baseName = if (parenthesisMatch != null) trimmed.substring(0, parenthesisMatch.range.first).trim() else trimmed

        val cleanedBase = baseName
            .replace(" Underground Station", "")
            .replace(" Underground", "")
            .replace(" Rail Station", "")
            .replace(" DLR Station", "")
            .replace(" Tram Stop", "")
            .replace(" Bus Station", "")
            .replace(STATION_SUFFIX_REGEX, "")
            .trim()

        val normalizedBase = normalizePassengerFacingStationName(cleanedBase)

        val trimmedSuffix = suffix.trim()
        return if (trimmedSuffix.isNotEmpty()) "$normalizedBase $trimmedSuffix" else normalizedBase
    }

    /**
     * Standardizes operator-specific shorthand (e.g. Darwin NRE "Heathrow Airport T4")
     * into passenger-facing passenger wayfinding signage used across the network.
     */
    private fun normalizePassengerFacingStationName(name: String): String {
        val lower = name.lowercase().trim()
        return when {
            lower == "heathrow airport t4" || lower == "heathrow airport terminal 4" || lower == "heathrow t4" ->
                "Heathrow Terminal 4"
            lower == "heathrow airport t5" || lower == "heathrow airport terminal 5" || lower == "heathrow t5" ->
                "Heathrow Terminal 5"
            lower == "heathrow airport t2&3" || lower == "heathrow airport t2 and 3" ||
                lower == "heathrow airport terminals 2 and 3" || lower == "heathrow terminals 2 and 3" ||
                lower == "heathrow t2&3" || lower == "heathrow airport t123" || lower == "heathrow terminals 1, 2, 3" ->
                "Heathrow Terminals 2 & 3"
            else -> name
        }
    }

    private val SUBSTRING_COLLISION_PAIRS = listOf(
        "edgware" to setOf("edgware road"),
        "wimbledon" to setOf("wimbledon park", "south wimbledon"),
        "aldgate" to setOf("aldgate east"),
        "bank" to setOf("embankment")
    )

    /**
     * Guards against distinct London stations where one station's name is a substring of another.
     */
    fun isConflictingStationSubstring(station1: String, station2: String): Boolean {
        val s1 = station1.lowercase()
        val s2 = station2.lowercase()
        for ((base, conflicts) in SUBSTRING_COLLISION_PAIRS) {
            if ((s1 == base || s2 == base) && (conflicts.any { s1.contains(it) || s2.contains(it) })) {
                return true
            }
        }
        return false
    }
}
