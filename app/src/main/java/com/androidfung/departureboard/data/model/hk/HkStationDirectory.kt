package com.androidfung.departureboard.data.model

import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.StationDirectory

/**
 * Directory of Hong Kong MTR stations and major franchised bus hubs/stops for local search.
 */
object HkStationDirectory : StationDirectory {

    override val defaultStations: List<Station>
        get() = HkDefaultStations.POPULAR_STATIONS

    override fun searchStations(query: String): List<Station> = searchHkStations(query)

    override fun getStationById(stationId: String): Station? {
        HkDefaultStations.POPULAR_STATIONS.firstOrNull {
            it.id.equals(stationId, ignoreCase = true)
        }?.let { return it }

        val ref = stationId.parseHkMtrStationId()
        val mtr = HkMtrStationRegistry.getStation(ref.stationCode)
            ?: HkMtrStationRegistry.getStation(stationId)
            ?: return null
        return mtrToStation(mtr, ref.lineCode)
    }

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
            modes = listOf("mtr"),
            zone = null,
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
    fun searchHkStations(query: String, dynamicBusStops: List<Station> = emptyList()): List<Station> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return HkDefaultStations.POPULAR_STATIONS
        }

        val mtrMatches = HkMtrStationRegistry.STATIONS.values.filter { station ->
            station.nameEn.contains(trimmed, ignoreCase = true) ||
                    station.nameTc.contains(trimmed, ignoreCase = true) ||
                    station.code.equals(trimmed, ignoreCase = true)
        }.map { mtrToStation(it) }

        val busMatches = (HkDefaultStations.POPULAR_STATIONS.filter { station ->
            station.name.contains(trimmed, ignoreCase = true) ||
                    station.lines.any { it.name.contains(trimmed, ignoreCase = true) }
        } + dynamicBusStops).distinctBy { it.id }

        val combined = (mtrMatches + busMatches).distinctBy { it.id }
        return StationSearchRanker.rank(combined, trimmed)
    }
}
