package com.androidfung.departureboard.core.wearcontract

/**
 * Shared multi-region, multi-language compact transit formatters used across Phone (`:app`)
 * and Wear OS (`:wear`) surfaces.
 */
object CompactTransitFormatter {

    private val PLATFORM_NUMBER_REGEX = Regex("""(?:Plat(?:form)?\s*|Stop\s*|Stand\s*|[0-9]+號月台|(\d+)番線)?(\d+[A-Za-z]?)\b""", RegexOption.IGNORE_CASE)
    private val CJK_PLATFORM_REGEX = Regex("""(\d+[A-Za-z]?)\s*(?:號月台|号站台|番線|번\s*승강장)""")
    private val PARENS_SUFFIX_REGEX = Regex("""\s*\([^)]*\)""")
    private val MULTI_CODE_PREFIXES = listOf("HK_MTR_", "SG_MRT_", "AU_SYD_", "JP_JR_", "JP_METRO_")

    /**
     * Compacts verbose platform strings across UK, HK, SG, AU, and JP feeds
     * (e.g., `"6 • Eastbound"`, `"Eastbound - Platform 6"`, `"1號月台"`, `"3番線"`)
     * into a concise token (`"6"`, `"12A"`, `"EB"`, `"NB"`, or `"TBD"`) suitable for narrow badges.
     */
    fun compactPlatformCode(raw: String): String {
        val cleaned = raw
            .replace("Platform", "", ignoreCase = true)
            .replace("Plat", "", ignoreCase = true)
            .trim()
        if (cleaned.isBlank() || cleaned.equals("TBD", ignoreCase = true)) return "TBD"

        CJK_PLATFORM_REGEX.find(raw)?.groupValues?.getOrNull(1)?.let { num ->
            if (num.isNotBlank()) return num.uppercase()
        }

        PLATFORM_NUMBER_REGEX.find(cleaned)?.groupValues?.lastOrNull { it.isNotBlank() }?.let { num ->
            if (num.any { ch -> ch.isDigit() }) return num.uppercase()
        }

        val lower = cleaned.lowercase()
        return when {
            lower.contains("eastbound") -> "EB"
            lower.contains("westbound") -> "WB"
            lower.contains("northbound") -> "NB"
            lower.contains("southbound") -> "SB"
            lower.contains("inner") -> "IR"
            lower.contains("outer") -> "OR"
            else -> cleaned.split("•", "-").first().trim().take(5).ifBlank { "TBD" }
        }
    }

    /**
     * Derives a compact 2–4 character station code across all supported transit regions and scripts:
     * - UK NaPTAN Rail (`910GKGX` -> `"KGX"`) & Tube (`940GZZLUOXC` -> `"OXC"`)
     * - HK MTR (`HK_MTR_ADM` / `HK_MTR_ISL_ADM` -> `"ADM"`), SG MRT (`SG_MRT_EW13` -> `"EW13"`),
     *   AU Sydney (`AU_SYD_CEN` -> `"CEN"`), JP Tokyo (`JP_JR_SJK` / `JP_METRO_G01` -> `"SJK"` / `"G01"`)
     * - CJK / Hangul names without ASCII letters (`"新宿"` -> `"新宿"`, `"金鐘"` -> `"金鐘"`)
     * - Latin station names (`"Paddington"` -> `"PAD"`)
     */
    fun deriveStationShortCode(stationId: String, stationName: String): String {
        val cleanId = stationId.trim().uppercase()
        if (cleanId.startsWith("910G") && cleanId.length >= 7) {
            return cleanId.removePrefix("910G").take(3)
        }
        if (cleanId.startsWith("940GZZLU") && cleanId.length >= 11) {
            return cleanId.removePrefix("940GZZLU").take(3)
        }
        if (cleanId.startsWith("940GZZDL") && cleanId.length >= 11) {
            return cleanId.removePrefix("940GZZDL").take(3)
        }
        if (MULTI_CODE_PREFIXES.any { cleanId.startsWith(it) }) {
            val tail = cleanId.substringAfterLast('_')
            if (tail.length in 2..4 && tail.any { it.isLetter() }) {
                return tail
            }
        }

        val cleanName = stationName.replace(PARENS_SUFFIX_REGEX, "").trim()
        val cjkOrHangul = cleanName.filter { ch ->
            Character.UnicodeScript.of(ch.code) in setOf(
                Character.UnicodeScript.HAN,
                Character.UnicodeScript.HIRAGANA,
                Character.UnicodeScript.KATAKANA,
                Character.UnicodeScript.HANGUL
            )
        }
        val asciiLetters = cleanName.uppercase().filter { it in 'A'..'Z' }
        return when {
            asciiLetters.length >= 3 -> asciiLetters.take(3)
            cjkOrHangul.isNotEmpty() -> cjkOrHangul.take(3)
            asciiLetters.isNotEmpty() -> asciiLetters
            else -> "STN"
        }
    }

    /**
     * Shortens verbose station and destination names for compact watch/widget displays while
     * preserving CJK/Japanese/Korean station names cleanly.
     */
    fun compactStationName(raw: String): String {
        var name = raw
            .replace("Underground Station", "", ignoreCase = true)
            .replace("Rail Station", "", ignoreCase = true)
            .replace("DLR Station", "", ignoreCase = true)
            .replace("Overground Station", "", ignoreCase = true)
            .replace("Station", "", ignoreCase = true)
            .replace("King's Cross St. Pancras", "King's Cross")
            .replace("Kings Cross St Pancras", "King's Cross")
            .replace("London ", "")
            .replace(PARENS_SUFFIX_REGEX, "")
            .trim()
        if (name.isBlank()) name = raw.trim()
        return name
    }
}
