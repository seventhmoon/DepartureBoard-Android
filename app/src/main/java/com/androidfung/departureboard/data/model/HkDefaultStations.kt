package com.androidfung.departureboard.data.model

import androidx.compose.ui.graphics.Color
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
            zone = "Kowloon",
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
            zone = "Kowloon",
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
            modes = listOf("tube"),
            zone = "HK Island",
            lat = 22.2819,
            lon = 114.1582,
            isFavorite = true,
            lines = listOf(
                StationLineInfo("ISL", "Island Line 港島綫", "tube"),
                StationLineInfo("TWL", "Tsuen Wan Line 荃灣綫", "tube"),
                StationLineInfo("TCL", "Tung Chung Line 東涌綫", "tube"),
                StationLineInfo("AEL", "Airport Express 機場快綫", "tube")
            )
        ),
        // 4. Admiralty MTR Station (金鐘) - Mega 4-line interchange
        Station(
            id = "HK_MTR_ISL_ADM",
            name = "Admiralty (金鐘)",
            modes = listOf("tube"),
            zone = "HK Island",
            lat = 22.2789,
            lon = 114.1652,
            isFavorite = true,
            lines = listOf(
                StationLineInfo("ISL", "Island Line 港島綫", "tube"),
                StationLineInfo("TWL", "Tsuen Wan Line 荃灣綫", "tube"),
                StationLineInfo("EAL", "East Rail Line 東鐵綫", "tube"),
                StationLineInfo("SIL", "South Island Line 南港島綫", "tube")
            )
        ),
        // 5. Mong Kok MTR Station (旺角)
        Station(
            id = "HK_MTR_TWL_MOK",
            name = "Mong Kok (旺角)",
            modes = listOf("tube"),
            zone = "Kowloon",
            lat = 22.3193,
            lon = 114.1694,
            isFavorite = true,
            lines = listOf(
                StationLineInfo("TWL", "Tsuen Wan Line 荃灣綫", "tube"),
                StationLineInfo("KTL", "Kwun Tong Line 觀塘綫", "tube")
            )
        ),
        // 6. Nathan Road Bank Centre Bus Stop (旺角銀行中心)
        Station(
            id = "HK_BUS_KMB_001614",
            name = "Mong Kok Bank Centre (旺角銀行中心)",
            modes = listOf("bus"),
            zone = "Kowloon",
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
            zone = "HK Island",
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
            modes = listOf("tube"),
            zone = "Kowloon",
            lat = 22.2975,
            lon = 114.1722,
            isFavorite = false,
            lines = listOf(
                StationLineInfo("TWL", "Tsuen Wan Line 荃灣綫", "tube"),
                StationLineInfo("TML", "Tuen Ma Line 屯馬綫", "tube")
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
                    timeToStationSeconds = 120,
                    expectedArrivalIso = isoFormatter.format(now.plusSeconds(120)),
                    currentLocation = "Approaching",
                    modeName = "tube",
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
                    timeToStationSeconds = 240,
                    expectedArrivalIso = isoFormatter.format(now.plusSeconds(240)),
                    currentLocation = "In Transit",
                    modeName = "tube",
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
                    timeToStationSeconds = 360,
                    expectedArrivalIso = isoFormatter.format(now.plusSeconds(360)),
                    currentLocation = "In Transit",
                    modeName = "tube",
                    lineBadge = LineBadgeInfo("ISL", "Island 港島", ColorIslandLine, Color.White),
                    status = DepartureStatus.ON_TIME
                )
            )
        }
    }
}
