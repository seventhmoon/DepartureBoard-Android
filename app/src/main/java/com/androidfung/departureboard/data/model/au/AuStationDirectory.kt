package com.androidfung.departureboard.data.model.au

import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.StationDirectory
import com.androidfung.departureboard.data.model.StationLineInfo

/**
 * Directory of Sydney / NSW rail stations with search support.
 */
object AuStationDirectory : StationDirectory {

    override val defaultStations: List<Station>
        get() = AuDefaultStations.POPULAR_STATIONS

    override fun searchStations(query: String): List<Station> = searchAuStations(query)

    override fun getStationById(stationId: String): Station? {
        return ALL_STATIONS.find { it.toStation().id == stationId }?.toStation()
    }

    data class AuStationEntry(
        val code: String,
        val name: String,
        val lines: List<Pair<String, String>>,
        val isMetroOnly: Boolean = false
    ) {
        fun toStation(): Station = Station(
            id = "AU_TRAIN_$code",
            name = name,
            modes = if (isMetroOnly) listOf("tube") else listOf("national-rail"),
            lines = lines.map { StationLineInfo(it.first, it.second) }
        )
    }

    val ALL_STATIONS: List<AuStationEntry> = listOf(
        AuStationEntry("CENTRAL", "Central", listOf("M1" to "Metro M1", "T1" to "T1 North Shore & Western", "T2" to "T2 Inner West", "T4" to "T4 Eastern Suburbs", "T8" to "T8 Airport & South")),
        AuStationEntry("TOWNHALL", "Town Hall", listOf("T1" to "T1 North Shore & Western", "T2" to "T2 Inner West", "T3" to "T3 Liverpool", "T4" to "T4 Eastern Suburbs")),
        AuStationEntry("WYNYARD", "Wynyard", listOf("T1" to "T1 North Shore & Western", "T2" to "T2 Inner West", "T3" to "T3 Liverpool", "T8" to "T8 Airport & South")),
        AuStationEntry("CIRCULARQUAY", "Circular Quay", listOf("T2" to "T2 Inner West", "T3" to "T3 Liverpool", "T8" to "T8 Airport & South")),
        AuStationEntry("STJAMES", "St James", listOf("T2" to "T2 Inner West", "T3" to "T3 Liverpool", "T8" to "T8 Airport & South")),
        AuStationEntry("MUSEUM", "Museum", listOf("T2" to "T2 Inner West", "T3" to "T3 Liverpool", "T8" to "T8 Airport & South")),
        AuStationEntry("MARTINPLACE", "Martin Place", listOf("M1" to "Metro M1", "T4" to "T4 Eastern Suburbs Line")),
        AuStationEntry("KINGSX", "Kings Cross", listOf("T4" to "T4 Eastern Suburbs Line")),
        AuStationEntry("EDGECLIFF", "Edgecliff", listOf("T4" to "T4 Eastern Suburbs Line")),
        AuStationEntry("BONDIJUNCTION", "Bondi Junction", listOf("T4" to "T4 Eastern Suburbs Line")),
        AuStationEntry("REDFERN", "Redfern", listOf("T1" to "T1 North Shore & Western", "T2" to "T2 Inner West", "T3" to "T3 Liverpool", "T4" to "T4 Eastern Suburbs", "T8" to "T8 Airport & South")),
        AuStationEntry("STRATHFIELD", "Strathfield", listOf("T1" to "T1 North Shore & Western", "T2" to "T2 Inner West", "T9" to "T9 Northern Line")),
        AuStationEntry("PARRAMATTA", "Parramatta", listOf("T1" to "T1 Western Line", "T2" to "T2 Leppington", "T5" to "T5 Cumberland")),
        AuStationEntry("BLACKTOWN", "Blacktown", listOf("T1" to "T1 Western Line", "T5" to "T5 Cumberland Line")),
        AuStationEntry("PENRITH", "Penrith", listOf("T1" to "T1 Western Line")),
        AuStationEntry("NORTHSYDNEY", "North Sydney", listOf("T1" to "T1 North Shore Line", "T9" to "T9 Northern Line")),
        AuStationEntry("CHATSWOOD", "Chatswood", listOf("M1" to "Metro M1", "T1" to "T1 North Shore Line", "T9" to "T9 Northern Line")),
        AuStationEntry("HORNSBY", "Hornsby", listOf("T1" to "T1 North Shore Line", "T9" to "T9 Northern Line", "CCN" to "Central Coast Line")),
        AuStationEntry("SYDNEYDOMESTIC", "Domestic Airport", listOf("T8" to "T8 Airport & South Line")),
        AuStationEntry("SYDNEYINTL", "International Airport", listOf("T8" to "T8 Airport & South Line")),
        AuStationEntry("SYDENHAM", "Sydenham", listOf("M1" to "Metro M1", "T4" to "T4 Illawarra Line", "T8" to "T8 South Line")),
        AuStationEntry("TALLAWONG", "Tallawong", listOf("M1" to "Metro M1"), isMetroOnly = true),
        AuStationEntry("CASTLEHILL", "Castle Hill", listOf("M1" to "Metro M1"), isMetroOnly = true),
        AuStationEntry("MACQUARIEUNI", "Macquarie University", listOf("M1" to "Metro M1"), isMetroOnly = true),
        AuStationEntry("BARANGAROO", "Barangaroo", listOf("M1" to "Metro M1"), isMetroOnly = true),
        AuStationEntry("VICTORIACROSS", "Victoria Cross", listOf("M1" to "Metro M1"), isMetroOnly = true)
    )

    fun searchAuStations(query: String): List<Station> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return AuDefaultStations.POPULAR_STATIONS

        return ALL_STATIONS
            .filter { entry ->
                entry.name.lowercase().contains(q) ||
                        entry.code.lowercase().contains(q) ||
                        entry.lines.any { it.first.lowercase().contains(q) || it.second.lowercase().contains(q) }
            }
            .map { it.toStation() }
    }
}
