package com.androidfung.departureboard.data.repository

/**
 * Standardizes platform and stand display strings across Rail, Tube, and Bus modes.
 */
internal object PlatformFormatter {

    private val SINGLE_CHAR_PLATFORM_REGEX = Regex("^[A-Z0-9]$", RegexOption.IGNORE_CASE)
    private val CARDINAL_DIRECTION_REGEX = Regex("""\b(Northbound|Southbound|Eastbound|Westbound|Inbound|Outbound)\b""", RegexOption.IGNORE_CASE)

    private val DIR_PLAT_REGEX = Regex("""^(Northbound|Southbound|Eastbound|Westbound|Inbound|Outbound)\s*-\s*Platform\s*(\w+)$""", RegexOption.IGNORE_CASE)

    /**
     * Formats the raw platform string or towards description into a clear user-facing label.
     *
     * @param overrideDirection When non-null at a single-direction terminus (e.g. "Southbound" at Mill Hill East),
     *                          replaces misleading inbound track directions reported by TfL (e.g. "Northbound - Platform 1").
     */
    fun format(
        rawPlatform: String?,
        towards: String?,
        isBus: Boolean,
        overrideDirection: String? = null
    ): String {
        val trimmed = rawPlatform?.trim() ?: ""

        if (trimmed.isNotBlank() && !trimmed.equals("null", ignoreCase = true)) {
            // Reformat "Southbound - Platform 3" to "Plat 3 • Southbound" so platform number is prominent
            // and never clipped on narrow mobile displays.
            val dirPlatMatch = DIR_PLAT_REGEX.find(trimmed)
            if (dirPlatMatch != null) {
                val dir = overrideDirection ?: dirPlatMatch.groupValues[1]
                val plat = dirPlatMatch.groupValues[2]
                return "Plat $plat • $dir"
            }

            val directionAdjusted = if (!isBus && !overrideDirection.isNullOrBlank() && CARDINAL_DIRECTION_REGEX.containsMatchIn(trimmed)) {
                CARDINAL_DIRECTION_REGEX.replace(trimmed, overrideDirection)
            } else {
                trimmed
            }

            return when {
                isBus -> {
                    if (directionAdjusted.startsWith("Stop ", ignoreCase = true)) directionAdjusted else "Stop $directionAdjusted"
                }
                SINGLE_CHAR_PLATFORM_REGEX.matches(directionAdjusted) -> {
                    "Platform $directionAdjusted"
                }
                !directionAdjusted.startsWith("Platform", ignoreCase = true) &&
                !directionAdjusted.contains("bound", ignoreCase = true) &&
                !directionAdjusted.startsWith("Stop", ignoreCase = true) -> {
                    "Platform $directionAdjusted"
                }
                else -> directionAdjusted
            }
        }

        if (isBus && !towards.isNullOrBlank() && !towards.trim().equals("null", ignoreCase = true)) {
            return "towards ${StationNameFormatter.clean(towards)}"
        }

        return if (isBus) "Bus Stand" else "Platform"
    }
}
