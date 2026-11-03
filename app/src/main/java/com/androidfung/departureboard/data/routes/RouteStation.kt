package com.androidfung.departureboard.data.routes

/**
 * Domain representation of a station along a transit corridor or line route sequence.
 *
 * @param crs Optional 3-letter National Rail CRS station code (e.g. "BED", "STP", "PAD").
 * @param naptanId Optional TfL / NaPTAN station identifier (e.g. "910GSTPX", "910GPADTON").
 * @param name Official primary station display name.
 * @param aliases Normalized lowercase search aliases and alternative colloquial names.
 */
data class RouteStation(
    val name: String,
    val crs: String? = null,
    val naptanId: String? = null,
    val aliases: List<String> = listOf(name.lowercase())
) {
    fun matches(query: String?): Boolean {
        if (query.isNullOrBlank()) return false
        val clean = query.replace("Metro", "")
            .replace("Station", "")
            .replace("Underground", "")
            .trim()
            .lowercase()

        if (name.equals(clean, ignoreCase = true)) return true
        if (crs?.equals(query.trim(), ignoreCase = true) == true) return true
        if (naptanId?.equals(query.trim(), ignoreCase = true) == true) return true

        return aliases.any { alias ->
            clean.contains(alias) || alias.contains(clean)
        }
    }
}
