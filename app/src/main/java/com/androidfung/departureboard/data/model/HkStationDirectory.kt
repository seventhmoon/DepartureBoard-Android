package com.androidfung.departureboard.data.model

/**
 * Directory of Hong Kong MTR stations and major franchised bus hubs/stops for local search.
 */
object HkStationDirectory {

    /**
     * Converts MTR info into domain Station model with complete interchange lines.
     */
    fun mtrToStation(station: HkMtrStation, lineCode: String? = null): Station {
        // Find all lines serving this station (e.g. ADM has ISL, TWL, EAL, SIL)
        val allLinesForStation = HkMtrRoutes.STATION_LINES[station.code]
            ?: listOfNotNull(lineCode ?: findPrimaryLineForStation(station.code))

        val linesList = allLinesForStation.distinct().map { lCode ->
            val lineName = HkMtrLineRegistry.getLineName(lCode)
            StationLineInfo(lCode, lineName, "tube")
        }

        val primaryLine = lineCode ?: allLinesForStation.firstOrNull() ?: "ISL"

        return Station(
            id = "HK_MTR_${primaryLine}_${station.code}",
            name = station.displayName,
            modes = listOf("tube"),
            zone = "Hong Kong",
            lat = station.lat,
            lon = station.lon,
            isFavorite = false,
            lines = linesList
        )
    }

    private fun findPrimaryLineForStation(code: String): String {
        return HkMtrRoutes.LINE_SEQUENCES.entries.firstOrNull { (_, stops) ->
            stops.contains(code)
        }?.key ?: "ISL"
    }

    /**
     * Searches Hong Kong transit stations (MTR and popular bus hubs) by keyword.
     */
    fun searchHkStations(query: String): List<Station> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return HkDefaultStations.POPULAR_STATIONS
        }

        val mtrMatches = HkMtrStationRegistry.STATIONS.values.filter { station ->
            station.nameEn.contains(trimmed, ignoreCase = true) ||
                    station.nameTc.contains(trimmed, ignoreCase = true) ||
                    station.code.equals(trimmed, ignoreCase = true)
        }.map { mtrToStation(it) }

        val busMatches = HkDefaultStations.POPULAR_STATIONS.filter { station ->
            station.name.contains(trimmed, ignoreCase = true) ||
                    station.lines.any { it.name.contains(trimmed, ignoreCase = true) }
        }

        val combined = (mtrMatches + busMatches).distinctBy { it.id }
        val q = trimmed.lowercase()

        return combined.sortedWith(
            compareBy<Station> { station ->
                val nameLower = station.name.lowercase().trim()
                val baseName = nameLower.substringBefore(" (").trim()
                when {
                    baseName == q -> 0
                    nameLower == q -> 1
                    baseName.startsWith(q) -> 2
                    nameLower.startsWith(q) -> 3
                    baseName.split(Regex("[\\s/\\-]+")).any { it.startsWith(q) } -> 4
                    nameLower.split(Regex("[\\s/\\-]+")).any { it.startsWith(q) } -> 5
                    baseName.contains(q) -> 6
                    else -> 7
                }
            }.thenBy { station ->
                // MTR / Rail stations prioritized over bus stops
                if (station.modes.contains("tube") || station.modes.contains("rail")) 0 else 1
            }.thenByDescending { station ->
                station.lines.size
            }.thenBy { station ->
                station.name.length
            }
        )
    }
}
