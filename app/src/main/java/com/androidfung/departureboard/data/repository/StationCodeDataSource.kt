package com.androidfung.departureboard.data.repository

import com.androidfung.departureboard.data.db.StationCrsDao

/**
 * Data source providing fast binary SQLite indexed lookups for National Rail CRS codes
 * via Room [StationCrsDao], eliminating runtime JSON parsing and heap memory allocations.
 */
class StationCodeDataSource(private val stationCrsDao: StationCrsDao? = null) {

    /**
     * Resolves the list of CRS codes for a given station ID and name.
     */
    suspend fun findCrsCodes(stationId: String, stationName: String): List<String> {
        // Direct ID lookup if user or system passed a 3-letter CRS code
        if (stationId.length == 3 && stationId.all { it.isLetter() }) {
            return listOf(stationId.uppercase())
        }

        val dao = stationCrsDao ?: return emptyList()

        // 1. Direct O(log N) indexed NaPTAN / Hub ID lookup
        val byId = dao.getCrsCodesForStationId(stationId)
        if (byId.isNotEmpty()) {
            return byId
        }

        // 2. Normalized Name exact or keyword lookup
        val cleanName = StationNameFormatter.clean(stationName).lowercase()

        // Guard: Tube-only stations like "King's Cross St. Pancras" shouldn't resolve to mainline
        if (cleanName.contains("king's cross st. pancras") || cleanName.contains("kings cross st. pancras")) {
            return emptyList()
        }

        val byExactName = dao.getCrsCodesForExactName(cleanName)
        if (byExactName.isNotEmpty()) {
            return byExactName
        }

        // Try stripping common suffixes like "rail station", "station"
        val stripped = cleanName
            .replace("rail station", "")
            .replace("underground station", "")
            .replace("station", "")
            .trim()

        if (stripped.isNotBlank() && stripped != cleanName) {
            val byStripped = dao.getCrsCodesForExactName(stripped)
            if (byStripped.isNotEmpty()) {
                return byStripped
            }
            val byName = dao.getCrsCodesForStationName(stripped)
            if (byName.isNotEmpty()) {
                return byName
            }
        }

        // Keyword lookup fallback
        val keywords = listOf(
            "west hampstead", "brent cross west", "st pancras international", "st pancras",
            "king's cross", "kings cross", "finsbury park", "paddington", "waterloo east",
            "waterloo", "victoria", "london bridge", "liverpool street", "farringdon", "euston",
            "charing cross", "clapham junction", "stratford international", "stratford",
            "ealing broadway", "blackfriars", "cannon street", "marylebone", "moorgate",
            "old street", "highbury & islington", "vauxhall", "wimbledon", "elephant & castle",
            "wembley central", "watford junction", "coventry", "st albans abbey", "st albans city",
            "st albans", "west ealing"
        )

        for (kw in keywords) {
            if (cleanName.contains(kw)) {
                val byKw = dao.getCrsCodesForNameKeyword(kw)
                if (byKw.isNotEmpty()) return byKw
            }
        }

        // 3. Algorithmic Fallback: Check if station ID embeds a 3-letter CRS code
        // TfL National Rail NaPTAN IDs frequently follow patterns like:
        // "910G" + 3-character CRS (e.g. 910GCOV -> COV, 910GKGX -> KGX, 910GWAT -> WAT)
        if (stationId.startsWith("910G", ignoreCase = true) && stationId.length >= 7) {
            val possibleCrs = stationId.substring(4, 7).uppercase()
            if (possibleCrs.all { it.isLetter() }) {
                val verified = dao.getCrsCodesForStationId(possibleCrs)
                if (verified.isNotEmpty()) {
                    return listOf(possibleCrs)
                }
            }
        }

        return emptyList()
    }
}
