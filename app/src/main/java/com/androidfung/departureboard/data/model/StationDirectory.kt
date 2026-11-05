package com.androidfung.departureboard.data.model

/**
 * Universal interface implemented by regional station directories (e.g. HK, JP, SG, AU, GB)
 * providing common query operations and default station sets.
 */
interface StationDirectory {
    val defaultStations: List<Station>
    fun searchStations(query: String): List<Station>
    fun getStationById(stationId: String): Station?
}

/**
 * Common contract for a catalog station entry in a regional [BaseStationDirectory].
 */
interface CatalogStationEntry {
    val code: String
    fun matchesQuery(normalizedQuery: String): Boolean
    fun toStation(): Station
}

/**
 * Base class for regional station directories that provides:
 * - Lazy O(1) [getStationById] lookups without repeated [CatalogStationEntry.toStation] allocations.
 * - Standardized keyword filtering and relevance ranking via [StationSearchRanker].
 */
abstract class BaseStationDirectory<E : CatalogStationEntry> : StationDirectory {

    abstract val entries: List<E>

    private val cachedStations: List<Station> by lazy {
        entries.map { it.toStation() }
    }

    private val stationsByIdUppercase: Map<String, Station> by lazy {
        buildMap {
            for (station in defaultStations + cachedStations) {
                val idUpper = station.id.uppercase()
                put(idUpper, station)
            }
            for (entry in this@BaseStationDirectory.entries) {
                val station = entry.toStation()
                val idUpper = station.id.uppercase()
                putIfAbsent(idUpper, station)
                putIfAbsent(entry.code.uppercase(), station)
                val prefix = idUpper.removeSuffix(entry.code.uppercase())
                val parts = entry.code.uppercase().split("_").filter { it.isNotBlank() }
                if (parts.size == 2) {
                    putIfAbsent("$prefix${parts[1]}_${parts[0]}", station)
                }
                for (part in parts) {
                    putIfAbsent("$prefix$part", station)
                }
            }
        }
    }

    override fun getStationById(stationId: String): Station? {
        if (stationId.isBlank()) return null
        return stationsByIdUppercase[stationId.trim().uppercase()]
    }

    override fun searchStations(query: String): List<Station> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return defaultStations

        val matched = entries
            .asSequence()
            .filter { it.matchesQuery(q) }
            .map { entry ->
                val station = entry.toStation()
                stationsByIdUppercase[station.id.uppercase()] ?: station
            }
            .distinctBy { it.id }
            .toList()

        return StationSearchRanker.rank(matched, q)
    }
}

/**
 * Shared relevance ranking engine for station search results across all regions.
 * Prioritizes:
 * 1. Exact base name match (ignoring parenthesized localized/stop suffixes)
 * 2. Exact full name match
 * 3. Prefix match on base name or full name
 * 4. Word-boundary prefix match
 * 5. Substring match on name vs. line/code match
 * 6. Rail/Metro vs. Bus priority depending on whether the query looks like a bus query
 * 7. Major interchange hubs (more lines) and shorter canonical names
 */
object StationSearchRanker {

    private val WORD_SPLIT_REGEX = Regex("[\\s/\\-()]+")
    private val RAIL_MODES = setOf(
        "tube", "underground", "mtr", "mrt", "metro",
        "national-rail", "rail", "train", "elizabeth-line", "overground", "dlr", "tram", "lrt", "jr"
    )

    fun isLikelyBusQuery(normalizedQuery: String): Boolean {
        return normalizedQuery.any { it.isDigit() } ||
            normalizedQuery.contains("bus") ||
            normalizedQuery.contains("巴士") ||
            normalizedQuery.contains("轉車站") ||
            normalizedQuery.contains("總站") ||
            normalizedQuery.contains("bbi")
    }

    fun rank(stations: List<Station>, rawQuery: String): List<Station> {
        val q = rawQuery.trim().lowercase()
        if (q.isBlank() || stations.size <= 1) return stations

        val busQuery = isLikelyBusQuery(q)

        return stations.sortedWith(
            compareBy<Station> { station ->
                val nameLower = station.name.lowercase().trim()
                val baseName = nameLower.substringBefore(" (").trim()
                when {
                    baseName == q -> 0
                    nameLower == q -> 1
                    baseName.startsWith(q) -> 2
                    nameLower.startsWith(q) -> 3
                    baseName.split(WORD_SPLIT_REGEX).any { it.startsWith(q) } -> 4
                    nameLower.split(WORD_SPLIT_REGEX).any { it.startsWith(q) } -> 5
                    baseName.contains(q) || nameLower.contains(q) -> 6
                    else -> 7
                }
            }.thenBy { station ->
                val isRail = station.modes.any { it.lowercase() in RAIL_MODES }
                if (busQuery) {
                    if (station.modes.any { it.equals("bus", ignoreCase = true) }) 0 else 1
                } else {
                    if (isRail) 0 else 1
                }
            }.thenByDescending { station ->
                station.lines.size
            }.thenBy { station ->
                station.name.length
            }
        )
    }
}
