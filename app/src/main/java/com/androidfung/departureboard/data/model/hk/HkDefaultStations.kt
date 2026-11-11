package com.androidfung.departureboard.data.model.hk

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.StationLineInfo
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Pre-configured popular Hong Kong transit hubs across MTR and major high-frequency Bus Interchanges.
 */
object HkDefaultStations {

    // Official MTR Line Colors
    val ColorTsuenWanLine = Color(0xFFE2231A)
    val ColorIslandLine = Color(0xFF007DC5)
    val ColorKwunTongLine = Color(0xFF00AB4E)
    val ColorEastRailLine = Color(0xFF53B7E8)
    val ColorTuenMaLine = Color(0xFF923D19)
    val ColorTungChungLine = Color(0xFFF38B00)
    val ColorAirportExpress = Color(0xFF007078)
    val ColorSouthIslandLine = Color(0xFFB5BD00)

    // HK Bus Operator Colors
    val ColorKmbRed = Color(0xFFED1D24)
    val ColorCitybusYellow = Color(0xFFFFD200)

    /**
     * Default stations for Hong Kong demo/initial use: 1 Train (Central MTR) + 1 Bus (Cross Harbour Tunnel).
     */
    val DEFAULT_STATIONS: List<Station> = listOf(
        Station(
            id = "HK_MTR_ISL_CEN",
            name = "Central (中環)",
            modes = listOf("mtr"),
            zone = null,
            lat = 22.2819,
            lon = 114.1582,
            isFavorite = true,
            lines = listOf(
                StationLineInfo("ISL", "Island Line 港島綫", "mtr"),
                StationLineInfo("TWL", "Tsuen Wan Line 荃灣綫", "mtr"),
                StationLineInfo("TCL", "Tung Chung Line 東涌綫", "mtr"),
                StationLineInfo("AEL", "Airport Express 機場快綫", "mtr")
            )
        ),
        Station(
            id = "HK_BUS_KMB_001554",
            name = "Cross Harbour Tunnel (海底隧道)",
            modes = listOf("bus"),
            zone = null,
            lat = 22.3034,
            lon = 114.1812,
            isFavorite = true,
            lines = listOf(
                StationLineInfo("KMB_101", "101", "bus"),
                StationLineInfo("KMB_104", "104", "bus"),
                StationLineInfo("KMB_111", "111", "bus"),
                StationLineInfo("KMB_112", "112", "bus"),
                StationLineInfo("KMB_116", "116", "bus")
            )
        )
    )

    /**
     * Default popular stations/interchanges for Hong Kong.
     * MTR station IDs use format "HK_MTR_{LINE}_{STATION_CODE}".
     * Bus stop IDs use format "HK_BUS_KMB_{STOP_ID}" or "HK_BUS_CTB_{STOP_ID}".
     */
    val POPULAR_STATIONS: List<Station> = listOf(
        // 1. Cross Harbour Tunnel Bus Interchange (紅磡海底隧道收費廣場) - One of the busiest bus hubs in HK
        Station(
            id = "HK_BUS_KMB_001554", // Cross Harbour Tunnel Toll Plaza
            name = "Cross Harbour Tunnel (海底隧道)",
            modes = listOf("bus"),
            zone = null,
            lat = 22.3034,
            lon = 114.1812,
            isFavorite = true,
            lines = listOf(
                StationLineInfo("KMB_101", "101", "bus"),
                StationLineInfo("KMB_104", "104", "bus"),
                StationLineInfo("KMB_111", "111", "bus"),
                StationLineInfo("KMB_112", "112", "bus"),
                StationLineInfo("KMB_116", "116", "bus")
            )
        ),
        // 2. Star Ferry Bus Terminus (尖沙咀天星碼頭) - Major tourist & commuter terminus
        Station(
            id = "HK_BUS_KMB_001476",
            name = "Star Ferry TST (尖沙咀天星碼頭)",
            modes = listOf("bus"),
            zone = null,
            lat = 22.2938,
            lon = 114.1685,
            isFavorite = true,
            lines = listOf(
                StationLineInfo("KMB_1", "1", "bus"),
                StationLineInfo("KMB_1A", "1A", "bus"),
                StationLineInfo("KMB_2", "2", "bus"),
                StationLineInfo("KMB_6", "6", "bus"),
                StationLineInfo("KMB_7", "7", "bus")
            )
        ),
        // 3. Central MTR Station (中環) - Connected to Hong Kong Station (TCL & AEL)
        Station(
            id = "HK_MTR_ISL_CEN",
            name = "Central (中環)",
            modes = listOf("mtr"),
            zone = null,
            lat = 22.2819,
            lon = 114.1582,
            isFavorite = true,
            lines = listOf(
                StationLineInfo("ISL", "Island Line 港島綫", "mtr"),
                StationLineInfo("TWL", "Tsuen Wan Line 荃灣綫", "mtr"),
                StationLineInfo("TCL", "Tung Chung Line 東涌綫", "mtr"),
                StationLineInfo("AEL", "Airport Express 機場快綫", "mtr")
            )
        ),
        // 4. Admiralty MTR Station (金鐘) - Mega 4-line interchange
        Station(
            id = "HK_MTR_ISL_ADM",
            name = "Admiralty (金鐘)",
            modes = listOf("mtr"),
            zone = null,
            lat = 22.2789,
            lon = 114.1652,
            isFavorite = true,
            lines = listOf(
                StationLineInfo("ISL", "Island Line 港島綫", "mtr"),
                StationLineInfo("TWL", "Tsuen Wan Line 荃灣綫", "mtr"),
                StationLineInfo("EAL", "East Rail Line 東鐵綫", "mtr"),
                StationLineInfo("SIL", "South Island Line 南港島綫", "mtr")
            )
        ),
        // 5. Mong Kok MTR Station (旺角)
        Station(
            id = "HK_MTR_TWL_MOK",
            name = "Mong Kok (旺角)",
            modes = listOf("mtr"),
            zone = null,
            lat = 22.3193,
            lon = 114.1694,
            isFavorite = true,
            lines = listOf(
                StationLineInfo("TWL", "Tsuen Wan Line 荃灣綫", "mtr"),
                StationLineInfo("KTL", "Kwun Tong Line 觀塘綫", "mtr")
            )
        ),
        // 6. Nathan Road Bank Centre Bus Stop (旺角銀行中心)
        Station(
            id = "HK_BUS_KMB_001614",
            name = "Mong Kok Bank Centre (旺角銀行中心)",
            modes = listOf("bus"),
            zone = null,
            lat = 22.3184,
            lon = 114.1699,
            isFavorite = false,
            lines = listOf(
                StationLineInfo("KMB_1", "1", "bus"),
                StationLineInfo("KMB_1A", "1A", "bus"),
                StationLineInfo("KMB_6", "6", "bus"),
                StationLineInfo("KMB_9", "9", "bus")
            )
        ),
        // 7. Causeway Bay Sogo (銅鑼灣崇光百貨) - Iconic HK Island Citybus & Cross-Harbour hub
        Station(
            id = "HK_BUS_CTB_002436",
            name = "Causeway Bay Sogo (銅鑼灣崇光)",
            modes = listOf("bus"),
            zone = null,
            lat = 22.2802,
            lon = 114.1843,
            isFavorite = false,
            lines = listOf(
                StationLineInfo("CTB_10", "10", "bus"),
                StationLineInfo("CTB_72", "72", "bus"),
                StationLineInfo("CTB_102", "102", "bus"),
                StationLineInfo("CTB_112", "112", "bus"),
                StationLineInfo("CTB_592", "592", "bus")
            )
        ),
        // 8. Tsim Sha Tsui MTR Station (尖沙咀 / 尖東)
        Station(
            id = "HK_MTR_TWL_TST",
            name = "Tsim Sha Tsui (尖沙咀)",
            modes = listOf("mtr"),
            zone = null,
            lat = 22.2975,
            lon = 114.1722,
            isFavorite = false,
            lines = listOf(
                StationLineInfo("TWL", "Tsuen Wan Line 荃灣綫", "mtr"),
                StationLineInfo("TML", "Tuen Ma Line 屯馬綫", "mtr")
            )
        ),
        // 9. Sha Tin Central Bus Terminus (沙田市中心巴士總站)
        Station(
            id = "HK_BUS_KMB_001802",
            name = "Sha Tin Central (沙田市中心)",
            modes = listOf("bus"),
            zone = null,
            lat = 22.3817,
            lon = 114.1887,
            isFavorite = false,
            lines = listOf(
                StationLineInfo("KMB_89", "89", "bus"),
                StationLineInfo("KMB_170", "170", "bus"),
                StationLineInfo("KMB_280X", "280X", "bus"),
                StationLineInfo("KMB_282", "282", "bus"),
                StationLineInfo("KMB_288", "288", "bus")
            )
        ),
        // 10. Tuen Mun Road Interchange (屯門公路轉車站) - Kowloon-bound
        Station(
            id = "HK_BUS_KMB_002772",
            name = "Tuen Mun Road Interchange (屯門公路轉車站)",
            modes = listOf("bus"),
            zone = null,
            lat = 22.3653,
            lon = 114.0152,
            isFavorite = false,
            lines = listOf(
                StationLineInfo("KMB_58M", "58M", "bus"),
                StationLineInfo("KMB_59X", "59X", "bus"),
                StationLineInfo("KMB_60X", "60X", "bus"),
                StationLineInfo("KMB_67X", "67X", "bus"),
                StationLineInfo("KMB_960", "960", "bus")
            )
        ),
        // 11. Tai Lam Tunnel Interchange (大欖隧道轉車站)
        Station(
            id = "HK_BUS_KMB_002073",
            name = "Tai Lam Tunnel Interchange (大欖隧道轉車站)",
            modes = listOf("bus"),
            zone = null,
            lat = 22.4206,
            lon = 114.0628,
            isFavorite = false,
            lines = listOf(
                StationLineInfo("KMB_68M", "68M", "bus"),
                StationLineInfo("KMB_68X", "68X", "bus"),
                StationLineInfo("KMB_69X", "69X", "bus"),
                StationLineInfo("KMB_268C", "268C", "bus"),
                StationLineInfo("KMB_968", "968", "bus")
            )
        ),
        // 12. Lam Tin Station Bus Terminus (藍田站巴士總站)
        Station(
            id = "HK_BUS_KMB_001676",
            name = "Lam Tin Station PTI (藍田站公共運輸交匯處)",
            modes = listOf("bus"),
            zone = null,
            lat = 22.3065,
            lon = 114.2343,
            isFavorite = false,
            lines = listOf(
                StationLineInfo("KMB_42C", "42C", "bus"),
                StationLineInfo("KMB_89D", "89D", "bus"),
                StationLineInfo("KMB_216M", "216M", "bus"),
                StationLineInfo("KMB_606", "606", "bus")
            )
        ),
        // 13. Tsuen Wan Station Bus Terminus (荃灣站巴士總站)
        Station(
            id = "HK_BUS_KMB_002015",
            name = "Tsuen Wan Station (荃灣站總站)",
            modes = listOf("bus"),
            zone = null,
            lat = 22.3739,
            lon = 114.1172,
            isFavorite = false,
            lines = listOf(
                StationLineInfo("KMB_41M", "41M", "bus"),
                StationLineInfo("KMB_59M", "59M", "bus"),
                StationLineInfo("KMB_66M", "66M", "bus"),
                StationLineInfo("KMB_68M", "68M", "bus")
            )
        ),
        // 14. Mei Foo Bus Terminus (美孚巴士總站)
        Station(
            id = "HK_BUS_KMB_001582",
            name = "Mei Foo Bus Terminus (美孚巴士總站)",
            modes = listOf("bus"),
            zone = null,
            lat = 22.3364,
            lon = 114.1396,
            isFavorite = false,
            lines = listOf(
                StationLineInfo("KMB_2A", "2A", "bus"),
                StationLineInfo("KMB_6C", "6C", "bus"),
                StationLineInfo("KMB_6D", "6D", "bus"),
                StationLineInfo("KMB_102", "102", "bus")
            )
        ),
        // 15. Central Exchange Square Bus Terminus (中環交易廣場巴士總站)
        Station(
            id = "HK_BUS_CTB_001089",
            name = "Central Exchange Square (中環交易廣場)",
            modes = listOf("bus"),
            zone = null,
            lat = 22.2838,
            lon = 114.1583,
            isFavorite = false,
            lines = listOf(
                StationLineInfo("CTB_2", "2", "bus"),
                StationLineInfo("CTB_6", "6", "bus"),
                StationLineInfo("CTB_70", "70", "bus"),
                StationLineInfo("CTB_260", "260", "bus"),
                StationLineInfo("CTB_962X", "962X", "bus")
            )
        ),
        // 16. Yuen Long MTR Light Rail Terminus (元朗輕鐵總站)
        Station(
            id = "HK_LRT_600",
            name = "Yuen Long Light Rail (元朗輕鐵站)",
            modes = listOf("tram"),
            zone = null,
            lat = 22.4461,
            lon = 114.0353,
            isFavorite = false,
            lines = listOf(
                StationLineInfo("LRT_610", "610", "tram"),
                StationLineInfo("LRT_614", "614", "tram"),
                StationLineInfo("LRT_615", "615", "tram"),
                StationLineInfo("LRT_761P", "761P", "tram")
            )
        ),
        // 17. Tuen Mun Ferry Pier Light Rail (屯門碼頭輕鐵總站)
        Station(
            id = "HK_LRT_001",
            name = "Tuen Mun Ferry Pier LRT (屯門碼頭輕鐵站)",
            modes = listOf("tram"),
            zone = null,
            lat = 22.3731,
            lon = 113.9664,
            isFavorite = false,
            lines = listOf(
                StationLineInfo("LRT_507", "507", "tram"),
                StationLineInfo("LRT_610", "610", "tram"),
                StationLineInfo("LRT_614", "614", "tram"),
                StationLineInfo("LRT_615", "615", "tram")
            )
        ),
        // 18. Causeway Bay Jardine's Bazaar Green Minibus Terminus (銅鑼灣渣甸街專線小巴總站)
        Station(
            id = "HK_GMB_20000105",
            name = "Causeway Bay GMB (銅鑼灣專線小巴總站)",
            modes = listOf("bus"),
            zone = null,
            lat = 22.2796,
            lon = 114.1855,
            isFavorite = false,
            lines = listOf(
                StationLineInfo("GMB_40", "GMB 40", "bus"),
                StationLineInfo("GMB_10", "GMB 10", "bus"),
                StationLineInfo("GMB_28", "GMB 28", "bus")
            )
        )
    )

    fun getFallbackDepartures(stationId: String, stationName: String): List<Departure> {
        val now = Instant.now()
        val isoFormatter = DateTimeFormatter.ISO_INSTANT

        if (stationId.startsWith("HK_BUS_")) {
            return listOf(
                Departure(
                    id = "${stationId}_1",
                    stationId = stationId,
                    stationName = stationName,
                    lineId = "KMB_1A",
                    lineName = "1A",
                    platformName = "",
                    destinationName = "Sau Mau Ping (中秀茂坪)",
                    towards = "Sau Mau Ping",
                    timeToStationSeconds = 180,
                    expectedArrivalIso = isoFormatter.format(now.plusSeconds(180)),
                    currentLocation = "Approaching",
                    modeName = "bus",
                    lineBadge = LineBadgeInfo("1A", "1A", ColorKmbRed, Color.White),
                    status = DepartureStatus.ON_TIME
                ),
                Departure(
                    id = "${stationId}_2",
                    stationId = stationId,
                    stationName = stationName,
                    lineId = "KMB_104",
                    lineName = "104",
                    platformName = "",
                    destinationName = "Pak Tin (白田)",
                    towards = "Pak Tin",
                    timeToStationSeconds = 420,
                    expectedArrivalIso = isoFormatter.format(now.plusSeconds(420)),
                    currentLocation = "In Transit",
                    modeName = "bus",
                    lineBadge = LineBadgeInfo("104", "104", ColorKmbRed, Color.White),
                    status = DepartureStatus.ON_TIME
                ),
                Departure(
                    id = "${stationId}_3",
                    stationId = stationId,
                    stationName = stationName,
                    lineId = "KMB_112",
                    lineName = "112",
                    platformName = "",
                    destinationName = "So Uk (蘇屋)",
                    towards = "So Uk",
                    timeToStationSeconds = 720,
                    expectedArrivalIso = isoFormatter.format(now.plusSeconds(720)),
                    currentLocation = "In Transit",
                    modeName = "bus",
                    lineBadge = LineBadgeInfo("112", "112", ColorKmbRed, Color.White),
                    status = DepartureStatus.ON_TIME
                )
            )
        } else {
            return listOf(
                Departure(
                    id = "${stationId}_m1",
                    stationId = stationId,
                    stationName = stationName,
                    lineId = "ISL",
                    lineName = "Island Line 港島綫",
                    platformName = "1",
                    destinationName = "Chai Wan 柴灣",
                    towards = "Chai Wan",
                    direction = "Eastbound",
                    timeToStationSeconds = 120,
                    expectedArrivalIso = isoFormatter.format(now.plusSeconds(120)),
                    currentLocation = "Approaching",
                    modeName = "mtr",
                    lineBadge = LineBadgeInfo("ISL", "Island 港島", ColorIslandLine, Color.White),
                    status = DepartureStatus.DUE
                ),
                Departure(
                    id = "${stationId}_m2",
                    stationId = stationId,
                    stationName = stationName,
                    lineId = "TWL",
                    lineName = "Tsuen Wan Line 荃灣綫",
                    platformName = "2",
                    destinationName = "Tsuen Wan 荃灣",
                    towards = "Tsuen Wan",
                    direction = "Northbound",
                    timeToStationSeconds = 240,
                    expectedArrivalIso = isoFormatter.format(now.plusSeconds(240)),
                    currentLocation = "In Transit",
                    modeName = "mtr",
                    lineBadge = LineBadgeInfo("TWL", "Tsuen Wan 荃灣", ColorTsuenWanLine, Color.White),
                    status = DepartureStatus.ON_TIME
                ),
                Departure(
                    id = "${stationId}_m3",
                    stationId = stationId,
                    stationName = stationName,
                    lineId = "ISL",
                    lineName = "Island Line 港島綫",
                    platformName = "3",
                    destinationName = "Kennedy Town 堅尼地城",
                    towards = "Kennedy Town",
                    direction = "Westbound",
                    timeToStationSeconds = 360,
                    expectedArrivalIso = isoFormatter.format(now.plusSeconds(360)),
                    currentLocation = "In Transit",
                    modeName = "mtr",
                    lineBadge = LineBadgeInfo("ISL", "Island 港島", ColorIslandLine, Color.White),
                    status = DepartureStatus.ON_TIME
                )
            )
        }
    }
}
