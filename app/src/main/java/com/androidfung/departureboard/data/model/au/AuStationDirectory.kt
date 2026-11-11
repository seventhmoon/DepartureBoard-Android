package com.androidfung.departureboard.data.model.au

import com.androidfung.departureboard.data.model.BaseStationDirectory
import com.androidfung.departureboard.data.model.CatalogStationEntry
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.StationLineInfo

/**
 * Directory of Sydney / NSW rail stations with O(1) ID lookup and ranked search support.
 */
object AuStationDirectory : BaseStationDirectory<AuStationDirectory.AuStationEntry>() {

    override val defaultStations: List<Station>
        get() = AuDefaultStations.POPULAR_STATIONS

    override val entries: List<AuStationEntry>
        get() = ALL_STATIONS

    data class AuStationEntry(
        override val code: String,
        val name: String,
        val lines: List<Pair<String, String>>,
        val isMetroOnly: Boolean = false
    ) : CatalogStationEntry {
        override fun matchesQuery(normalizedQuery: String): Boolean {
            return name.lowercase().contains(normalizedQuery) ||
                code.lowercase().contains(normalizedQuery) ||
                lines.any {
                    it.first.lowercase().contains(normalizedQuery) ||
                        it.second.lowercase().contains(normalizedQuery)
                }
        }

        override fun toStation(): Station = Station(
            id = "AU_TRAIN_$code",
            name = name,
            modes = if (isMetroOnly) listOf("tube") else listOf("national-rail"),
            lat = STATION_COORDS[code]?.first,
            lon = STATION_COORDS[code]?.second,
            lines = lines.map { StationLineInfo(it.first, it.second) }
        )
    }

    internal val STATION_COORDS: Map<String, Pair<Double, Double>> = mapOf(
        "CENTRAL" to (-33.8832 to 151.2070),
        "TOWNHALL" to (-33.8736 to 151.2069),
        "WYNYARD" to (-33.8657 to 151.2064),
        "CIRCULARQUAY" to (-33.8613 to 151.2108),
        "STJAMES" to (-33.8706 to 151.2116),
        "MUSEUM" to (-33.8765 to 151.2098),
        "MARTINPLACE" to (-33.8679 to 151.2115),
        "KINGSX" to (-33.8744 to 151.2245),
        "EDGECLIFF" to (-33.8797 to 151.2370),
        "BONDIJUNCTION" to (-33.8914 to 151.2484),
        "REDFERN" to (-33.8922 to 151.1995),
        "STRATHFIELD" to (-33.8716 to 151.0942),
        "PARRAMATTA" to (-33.8175 to 151.0053),
        "BLACKTOWN" to (-33.7686 to 150.9061),
        "PENRITH" to (-33.7501 to 150.6953),
        "NORTHSYDNEY" to (-33.8415 to 151.2074),
        "CHATSWOOD" to (-33.7973 to 151.1809),
        "HORNSBY" to (-33.7034 to 151.0984),
        "SYDNEYDOMESTIC" to (-33.9336 to 151.1812),
        "SYDNEYINTL" to (-33.9351 to 151.1660),
        "SYDENHAM" to (-33.9148 to 151.1662),
        "TALLAWONG" to (-33.6920 to 150.9056),
        "CASTLEHILL" to (-33.7318 to 151.0046),
        "MACQUARIEUNI" to (-33.7768 to 151.1180),
        "BARANGAROO" to (-33.8602 to 151.2022),
        "VICTORIACROSS" to (-33.8368 to 151.2075)
    )

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

    fun searchAuStations(query: String): List<Station> = searchStations(query)
}
