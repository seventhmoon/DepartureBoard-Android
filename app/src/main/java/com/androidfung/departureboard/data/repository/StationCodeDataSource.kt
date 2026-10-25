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

        // Guard: Bus stops or stands must NEVER resolve to National Rail train stations
        if (TflStopPointUtils.isBusStop(stationId, stationName)) {
            return emptyList()
        }

        val dao = stationCrsDao ?: return emptyList()

        // 1. Direct O(log N) indexed NaPTAN / Hub ID lookup
        val byId = dao.getCrsCodesForStationId(stationId)
        if (byId.isNotEmpty()) {
            return byId
        }

        // 2. Algorithmic Fallback: Check if station ID embeds an exact 3-letter CRS code
        // TfL National Rail NaPTAN IDs frequently follow patterns like:
        // "910G" + 3-character CRS (e.g. 910GCOV -> COV, 910GKGX -> KGX, 910GWAT -> WAT)
        // CRITICAL: Must be EXACTLY 7 characters ("910G" + 3 letters).
        // Longer IDs like "910GSUTTON" (9 chars) must NEVER be sliced into "SUT" because
        // "SUT" is Sutton Coldfield (Birmingham), not Sutton (London) which is "SUO".
        if (stationId.startsWith("910G", ignoreCase = true) && stationId.length == 7) {
            val possibleCrs = stationId.substring(4, 7).uppercase()
            if (possibleCrs.all { it.isLetter() }) {
                val verified = dao.getCrsCodesForStationId(possibleCrs)
                if (verified.isNotEmpty()) {
                    return listOf(possibleCrs)
                }
            }
        }

        // 3. For TfL stations (HUB... or 940G...), stop here.
        // Interchanges with National Rail are already registered by stationId in station_crs_mappings.
        // Pure Tube/DLR/Tram stops (like Bank: HUBBAN / 940GZZLUBNK) MUST NOT fall back to arbitrary
        // name searches which cause false positives against unrelated UK-wide rail stations.
        if (stationId.startsWith("HUB", ignoreCase = true) || stationId.startsWith(TflStopPointUtils.PREFIX_METRO_TRAM, ignoreCase = true)) {
            return emptyList()
        }

        // 4. Normalized Name exact match (for stations added without a known ID, but matching a real rail station)
        val cleanName = StationNameFormatter.clean(stationName).lowercase()

        // Guard: Bus stops or street-level stops (e.g. "Euston Road", "Euston Road (Stop C)", "Euston / Eversholt St") shouldn't resolve to mainline train stations
        val hasStopIndicator = cleanName.contains("(stop ") || cleanName.contains("stop ") || cleanName.contains("bus stand") || cleanName.contains("bus stop")
        val isStreetBusName = cleanName.endsWith(" road") || cleanName.endsWith(" street") || cleanName.endsWith(" ave") || cleanName.endsWith(" lane")
        if (hasStopIndicator || isStreetBusName) {
            return emptyList()
        }

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

        return emptyList()
    }
}
