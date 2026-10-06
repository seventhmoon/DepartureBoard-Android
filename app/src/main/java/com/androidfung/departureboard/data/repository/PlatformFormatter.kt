package com.androidfung.departureboard.data.repository

/**
 * Standardizes platform and stand display strings across Rail, Tube, and Bus modes.
 */
internal object PlatformFormatter {

    private val SINGLE_CHAR_PLATFORM_REGEX = Regex("^[A-Z0-9]$", RegexOption.IGNORE_CASE)

    /**
     * Formats the raw platform string or towards description into a clear user-facing label.
     */
    fun format(rawPlatform: String?, towards: String?, isBus: Boolean): String {
        val trimmed = rawPlatform?.trim() ?: ""

        if (trimmed.isNotBlank() && !trimmed.equals("null", ignoreCase = true)) {
            // Reformat "Southbound - Platform 3" to "Plat 3 • Southbound" so platform number is prominent
            // and never clipped on narrow mobile displays.
            val dirPlatMatch = Regex("""^(Northbound|Southbound|Eastbound|Westbound|Inbound|Outbound)\s*-\s*Platform\s*(\w+)$""", RegexOption.IGNORE_CASE).find(trimmed)
            if (dirPlatMatch != null) {
                val dir = dirPlatMatch.groupValues[1]
                val plat = dirPlatMatch.groupValues[2]
                return "Plat $plat • $dir"
            }

            return when {
                isBus -> {
                    if (trimmed.startsWith("Stop ", ignoreCase = true)) trimmed else "Stop $trimmed"
                }
                SINGLE_CHAR_PLATFORM_REGEX.matches(trimmed) -> {
                    "Platform $trimmed"
                }
                !trimmed.startsWith("Platform", ignoreCase = true) &&
                !trimmed.contains("bound", ignoreCase = true) &&
                !trimmed.startsWith("Stop", ignoreCase = true) -> {
                    "Platform $trimmed"
                }
                else -> trimmed
            }
        }

        if (isBus && !towards.isNullOrBlank() && !towards.trim().equals("null", ignoreCase = true)) {
            return "towards ${StationNameFormatter.clean(towards)}"
        }

        return if (isBus) "Bus Stand" else "Platform"
    }
}
