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
        val lines: List<Pair<String, String>>
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
            lines = lines.map { StationLineInfo(it.first, it.second) }
        )
    }

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
