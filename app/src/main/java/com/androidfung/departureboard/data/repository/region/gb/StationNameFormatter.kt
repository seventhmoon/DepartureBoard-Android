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

        // Handle Darwin disambiguation suffix "(London)" before general parenthesis extraction
        val withoutLondonSuffix = if (trimmed.endsWith("(London)", ignoreCase = true)) {
            trimmed.substring(0, trimmed.length - 8).trim()
        } else {
            trimmed
        }

        // Preserve any stop indicators and towards clauses in parentheses, e.g. "Euston (Stop D)", "Euston (Stop B, towards Aldwych)"
        val parenthesisMatch = PARENTHESIS_REGEX.find(withoutLondonSuffix)
        val suffix = parenthesisMatch?.value ?: ""
        val baseName = if (parenthesisMatch != null) withoutLondonSuffix.substring(0, parenthesisMatch.range.first).trim() else withoutLondonSuffix

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
     * Standardizes operator-specific shorthand (e.g. Darwin NRE "London Paddington", "Heathrow Airport T4", "Stratford (London)")
     * into passenger-facing wayfinding signage used consistently across the network.
     */
    private fun normalizePassengerFacingStationName(name: String): String {
        var s = name.trim()

        // 1. Expand common rail abbreviation suffixes (e.g. "Clapham Jct" -> "Clapham Junction")
        if (s.endsWith(" Jct", ignoreCase = true)) {
            s = s.substring(0, s.length - 4) + " Junction"
        }

        val lower = s.lowercase().trim()

        // 2. Heathrow airport terminal normalizations
        when {
            lower == "heathrow airport t4" || lower == "heathrow airport terminal 4" || lower == "heathrow t4" ->
                return "Heathrow Terminal 4"
            lower == "heathrow airport t5" || lower == "heathrow airport terminal 5" || lower == "heathrow t5" ->
                return "Heathrow Terminal 5"
            lower == "heathrow airport t2&3" || lower == "heathrow airport t2 and 3" ||
                lower == "heathrow airport terminals 2 and 3" || lower == "heathrow terminals 2 and 3" ||
                lower == "heathrow t2&3" || lower == "heathrow airport t123" || lower == "heathrow terminals 1, 2, 3" ->
                return "Heathrow Terminals 2 & 3"
        }

        // 3. Other airport stations (e.g. "Gatwick" -> "Gatwick Airport", "Stansted" -> "Stansted Airport")
        when (lower) {
            "gatwick" -> return "Gatwick Airport"
            "stansted" -> return "Stansted Airport"
            "luton airport" -> return "Luton Airport Parkway"
        }

        // 4. Strip redundant "London " prefix from London rail termini when used in departure boards / destinations
        // Note: Preserves "London Bridge" and "London Fields" as "Bridge" or "Fields" alone are ambiguous/different
        val londonTerminiPrefixes = listOf(
            "london paddington" to "Paddington",
            "london liverpool street" to "Liverpool Street",
            "london victoria" to "Victoria",
            "london waterloo" to "Waterloo",
            "london waterloo east" to "Waterloo East",
            "london euston" to "Euston",
            "london kings cross" to "King's Cross",
            "london king's cross" to "King's Cross",
            "london st pancras" to "St Pancras International",
            "london st pancras international" to "St Pancras International",
            "london blackfriars" to "Blackfriars",
            "london cannon street" to "Cannon Street",
            "london charing cross" to "Charing Cross",
            "london marylebone" to "Marylebone",
            "london fenchurch street" to "Fenchurch Street"
        )
        for ((prefixed, canonical) in londonTerminiPrefixes) {
            if (lower == prefixed) {
                return canonical
            }
        }

        // 5. Standardize "Stratford (London)" Darwin label to "Stratford"
        if (lower == "stratford (london)") {
            return "Stratford"
        }

        // 6. Common conjunction standardization
        if (lower == "highbury and islington") {
            return "Highbury & Islington"
        }
        if (lower == "shepherds bush") {
            return "Shepherd's Bush"
        }

        return s
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
