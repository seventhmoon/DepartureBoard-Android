package com.androidfung.departureboard.data.model.sg

import com.androidfung.departureboard.data.model.BaseStationDirectory
import com.androidfung.departureboard.data.model.CatalogStationEntry
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.StationLineInfo

/**
 * Directory of Singapore MRT stations with bilingual search support and O(1) ID lookup.
 */
object SgStationDirectory : BaseStationDirectory<SgStationDirectory.SgStationEntry>() {

    override val defaultStations: List<Station>
        get() = SgDefaultStations.POPULAR_STATIONS

    override val entries: List<SgStationEntry>
        get() = ALL_STATIONS

    data class SgStationEntry(
        override val code: String,
        val englishName: String,
        val chineseName: String,
        val lines: List<Pair<String, String>>,
        val lat: Double? = null,
        val lon: Double? = null
    ) : CatalogStationEntry {
        override fun matchesQuery(normalizedQuery: String): Boolean {
            return englishName.lowercase().contains(normalizedQuery) ||
                chineseName.contains(normalizedQuery) ||
                code.lowercase().contains(normalizedQuery) ||
                lines.any {
                    it.first.lowercase().contains(normalizedQuery) ||
                        it.second.lowercase().contains(normalizedQuery)
                }
        }

        override fun toStation(): Station = Station(
            id = "SG_MRT_$code",
            name = englishName,
            modes = listOf("tube"),
            lat = lat ?: STATION_COORDS[englishName]?.first,
            lon = lon ?: STATION_COORDS[englishName]?.second,
            lines = lines.map { StationLineInfo(it.first, it.second) }
        )
    }

    internal val STATION_COORDS: Map<String, Pair<Double, Double>> = mapOf(
        "Jurong East" to (1.3331 to 103.7423),
        "Bukit Batok" to (1.3490 to 103.7496),
        "Bukit Gombak" to (1.3586 to 103.7518),
        "Choa Chu Kang" to (1.3854 to 103.7443),
        "Woodlands" to (1.4369 to 103.7865),
        "Khatib" to (1.4173 to 103.8330),
        "Yio Chu Kang" to (1.3817 to 103.8449),
        "Ang Mo Kio" to (1.3700 to 103.8495),
        "Bishan" to (1.3513 to 103.8491),
        "Toa Payoh" to (1.3326 to 103.8475),
        "Novena" to (1.3204 to 103.8438),
        "Newton" to (1.3138 to 103.8380),
        "Orchard" to (1.3040 to 103.8318),
        "Somerset" to (1.3003 to 103.8390),
        "Dhoby Ghaut" to (1.2993 to 103.8456),
        "City Hall" to (1.2931 to 103.8520),
        "Raffles Place" to (1.2839 to 103.8515),
        "Marina Bay" to (1.2764 to 103.8546),
        "Marina South Pier" to (1.2711 to 103.8636),
        "Pasir Ris" to (1.3730 to 103.9493),
        "Tampines" to (1.3533 to 103.9452),
        "Tanah Merah" to (1.3272 to 103.9465),
        "Changi Airport" to (1.3574 to 103.9888),
        "Paya Lebar" to (1.3177 to 103.8927),
        "Lavender" to (1.3074 to 103.8630),
        "Bugis" to (1.3005 to 103.8560),
        "Outram Park" to (1.2803 to 103.8395),
        "Buona Vista" to (1.3072 to 103.7902),
        "Boon Lay" to (1.3386 to 103.7061),
        "Tuas Link" to (1.3405 to 103.6368),
        "HarbourFront" to (1.2654 to 103.8223),
        "Chinatown" to (1.2848 to 103.8440),
        "Clarke Quay" to (1.2884 to 103.8465),
        "Little India" to (1.3068 to 103.8496),
        "Farrer Park" to (1.3124 to 103.8542),
        "Serangoon" to (1.3497 to 103.8736),
        "Sengkang" to (1.3917 to 103.8955),
        "Punggol" to (1.4052 to 103.9024),
        "Promenade" to (1.2932 to 103.8609),
        "Bayfront" to (1.2819 to 103.8591),
        "Gardens by the Bay" to (1.2784 to 103.8674)
    )

    val ALL_STATIONS: List<SgStationEntry> = listOf(
        SgStationEntry("NS1_EW24", "Jurong East", "裕廊東", listOf("NSL" to "North-South Line", "EWL" to "East-West Line")),
        SgStationEntry("NS2", "Bukit Batok", "武吉巴督", listOf("NSL" to "North-South Line")),
        SgStationEntry("NS3", "Bukit Gombak", "武吉甘柏", listOf("NSL" to "North-South Line")),
        SgStationEntry("NS4_BP1", "Choa Chu Kang", "蔡厝港", listOf("NSL" to "North-South Line")),
        SgStationEntry("NS9_TE2", "Woodlands", "兀蘭", listOf("NSL" to "North-South Line", "TEL" to "Thomson-East Coast Line")),
        SgStationEntry("NS14", "Khatib", "卡迪", listOf("NSL" to "North-South Line")),
        SgStationEntry("NS15", "Yio Chu Kang", "楊厝港", listOf("NSL" to "North-South Line")),
        SgStationEntry("NS16", "Ang Mo Kio", "宏茂橋", listOf("NSL" to "North-South Line")),
        SgStationEntry("NS17_CC15", "Bishan", "碧山", listOf("NSL" to "North-South Line", "CCL" to "Circle Line")),
        SgStationEntry("NS19", "Toa Payoh", "大巴窯", listOf("NSL" to "North-South Line")),
        SgStationEntry("NS20", "Novena", "諾維娜", listOf("NSL" to "North-South Line")),
        SgStationEntry("NS21_DT11", "Newton", "紐頓", listOf("NSL" to "North-South Line", "DTL" to "Downtown Line")),
        SgStationEntry("NS22_TE14", "Orchard", "烏節", listOf("NSL" to "North-South Line", "TEL" to "Thomson-East Coast Line")),
        SgStationEntry("NS23", "Somerset", "索美塞", listOf("NSL" to "North-South Line")),
        SgStationEntry("NS24_NE6_CC1", "Dhoby Ghaut", "多美歌", listOf("NSL" to "North-South Line", "NEL" to "North East Line", "CCL" to "Circle Line")),
        SgStationEntry("NS25_EW13", "City Hall", "政府大廈", listOf("NSL" to "North-South Line", "EWL" to "East-West Line")),
        SgStationEntry("NS26_EW14", "Raffles Place", "萊佛士坊", listOf("NSL" to "North-South Line", "EWL" to "East-West Line")),
        SgStationEntry("NS27_CE2_TE20", "Marina Bay", "濱海灣", listOf("NSL" to "North-South Line", "CCL" to "Circle Line", "TEL" to "Thomson-East Coast Line")),
        SgStationEntry("NS28", "Marina South Pier", "濱海南碼頭", listOf("NSL" to "North-South Line")),

        SgStationEntry("EW1", "Pasir Ris", "巴西立", listOf("EWL" to "East-West Line")),
        SgStationEntry("EW2_DT32", "Tampines", "淡濱尼", listOf("EWL" to "East-West Line", "DTL" to "Downtown Line")),
        SgStationEntry("EW4_CG", "Tanah Merah", "丹那美拉", listOf("EWL" to "East-West Line")),
        SgStationEntry("CG2", "Changi Airport", "樟宜機場", listOf("EWL" to "East-West Line")),
        SgStationEntry("EW8_CC9", "Paya Lebar", "巴耶利峇", listOf("EWL" to "East-West Line", "CCL" to "Circle Line")),
        SgStationEntry("EW11", "Lavender", "勞明達", listOf("EWL" to "East-West Line")),
        SgStationEntry("EW12_DT14", "Bugis", "武吉士", listOf("EWL" to "East-West Line", "DTL" to "Downtown Line")),
        SgStationEntry("EW16_NE3_TE17", "Outram Park", "歐南園", listOf("EWL" to "East-West Line", "NEL" to "North East Line", "TEL" to "Thomson-East Coast Line")),
        SgStationEntry("EW21_CC22", "Buona Vista", "波那維斯達", listOf("EWL" to "East-West Line", "CCL" to "Circle Line")),
        SgStationEntry("EW27", "Boon Lay", "文禮", listOf("EWL" to "East-West Line")),
        SgStationEntry("EW33", "Tuas Link", "大士連路", listOf("EWL" to "East-West Line")),

        SgStationEntry("NE1_CC29", "HarbourFront", "港灣", listOf("NEL" to "North East Line", "CCL" to "Circle Line")),
        SgStationEntry("NE4_DT19", "Chinatown", "牛車水", listOf("NEL" to "North East Line", "DTL" to "Downtown Line")),
        SgStationEntry("NE5", "Clarke Quay", "克拉碼頭", listOf("NEL" to "North East Line")),
        SgStationEntry("NE7_DT12", "Little India", "小印度", listOf("NEL" to "North East Line", "DTL" to "Downtown Line")),
        SgStationEntry("NE8", "Farrer Park", "花拉公園", listOf("NEL" to "North East Line")),
        SgStationEntry("NE12_CC13", "Serangoon", "實龍崗", listOf("NEL" to "North East Line", "CCL" to "Circle Line")),
        SgStationEntry("NE16", "Sengkang", "盛港", listOf("NEL" to "North East Line")),
        SgStationEntry("NE17", "Punggol", "榜鵝", listOf("NEL" to "North East Line")),

        SgStationEntry("CC4_DT15", "Promenade", "寶門廊", listOf("CCL" to "Circle Line", "DTL" to "Downtown Line")),
        SgStationEntry("DT16_CE1", "Bayfront", "海灣舫", listOf("DTL" to "Downtown Line", "CCL" to "Circle Line")),
        SgStationEntry("TE22", "Gardens by the Bay", "濱海灣花園", listOf("TEL" to "Thomson-East Coast Line"))
    )

    fun searchSgStations(query: String): List<Station> = searchStations(query)
}
