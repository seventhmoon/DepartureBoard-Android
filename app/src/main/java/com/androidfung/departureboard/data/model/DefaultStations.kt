package com.androidfung.departureboard.data.model

/**
 * Pre-configured popular London stations across Tube, Elizabeth Line, Overground, DLR, and Rail hubs.
 * Provides instant search hints, quick filter options, and offline fallback stations.
 */
object DefaultStations {

    val POPULAR_STATIONS: List<Station> = listOf(
        Station(
            id = "940GZZLUOXC",
            name = "Oxford Circus",
            modes = listOf("tube"),
            zone = "1",
            lat = 51.5152,
            lon = -0.1419,
            isFavorite = true
        ),
        Station(
            id = "940GZZLUKSX",
            name = "King's Cross St. Pancras",
            modes = listOf("tube", "national-rail"),
            zone = "1",
            lat = 51.5308,
            lon = -0.1238,
            isFavorite = true
        ),
        Station(
            id = "940GZZLUWLO",
            name = "Waterloo",
            modes = listOf("tube", "national-rail"),
            zone = "1",
            lat = 51.5036,
            lon = -0.1143,
            isFavorite = true
        ),
        Station(
            id = "940GZZLUVIC",
            name = "Victoria",
            modes = listOf("tube", "national-rail", "bus"),
            zone = "1",
            lat = 51.4965,
            lon = -0.1447,
            isFavorite = true
        ),
        Station(
            id = "940GZZLUPCC",
            name = "Piccadilly Circus",
            modes = listOf("tube"),
            zone = "1",
            lat = 51.5101,
            lon = -0.1342,
            isFavorite = false
        ),
        Station(
            id = "940GZZLUWSM",
            name = "Westminster",
            modes = listOf("tube"),
            zone = "1",
            lat = 51.5014,
            lon = -0.1251,
            isFavorite = false
        ),
        Station(
            id = "940GZZLULNB",
            name = "London Bridge",
            modes = listOf("tube", "national-rail"),
            zone = "1",
            lat = 51.5057,
            lon = -0.0863,
            isFavorite = false
        ),
        Station(
            id = "HUBPAD",
            name = "Paddington",
            modes = listOf("tube", "elizabeth-line", "national-rail"),
            zone = "1",
            lat = 51.5165,
            lon = -0.1757,
            isFavorite = false
        ),
        Station(
            id = "HUBZFD",
            name = "Farringdon",
            modes = listOf("tube", "elizabeth-line", "national-rail"),
            zone = "1",
            lat = 51.5202,
            lon = -0.1051,
            isFavorite = false
        ),
        Station(
            id = "HUBLST",
            name = "Liverpool Street",
            modes = listOf("tube", "elizabeth-line", "overground", "national-rail"),
            zone = "1",
            lat = 51.5178,
            lon = -0.0823,
            isFavorite = false
        ),
        Station(
            id = "HUBCAW",
            name = "Canary Wharf",
            modes = listOf("tube", "elizabeth-line", "dlr"),
            zone = "2",
            lat = 51.5054,
            lon = -0.0173,
            isFavorite = false
        ),
        Station(
            id = "HUBBAN",
            name = "Bank",
            modes = listOf("tube", "dlr"),
            zone = "1",
            lat = 51.5134,
            lon = -0.0890,
            isFavorite = false
        )
    )

    /**
     * Fallback departure predictions for popular stations when network is unavailable.
     */
    fun getFallbackDepartures(stationId: String, stationName: String): List<Departure> {
        return when (stationId) {
            "940GZZLUOXC" -> listOf(
                Departure(
                    id = "fb_oxc_1",
                    stationId = stationId,
                    stationName = stationName,
                    lineId = "victoria",
                    lineName = "Victoria",
                    platformName = "Northbound - Platform 3",
                    destinationName = "Walthamstow Central",
                    towards = "Walthamstow Central",
                    timeToStationSeconds = 60,
                    expectedArrivalIso = null,
                    currentLocation = "Approaching Oxford Circus",
                    modeName = "tube",
                    lineBadge = TflLineColors.getLineBadge("victoria", "Victoria", "tube")
                ),
                Departure(
                    id = "fb_oxc_2",
                    stationId = stationId,
                    stationName = stationName,
                    lineId = "central",
                    lineName = "Central",
                    platformName = "Eastbound - Platform 1",
                    destinationName = "Epping",
                    towards = "Epping via Bank",
                    timeToStationSeconds = 180,
                    expectedArrivalIso = null,
                    currentLocation = "Between Marble Arch and Bond Street",
                    modeName = "tube",
                    lineBadge = TflLineColors.getLineBadge("central", "Central", "tube")
                ),
                Departure(
                    id = "fb_oxc_3",
                    stationId = stationId,
                    stationName = stationName,
                    lineId = "bakerloo",
                    lineName = "Bakerloo",
                    platformName = "Southbound - Platform 2",
                    destinationName = "Elephant & Castle",
                    towards = "Elephant & Castle",
                    timeToStationSeconds = 240,
                    expectedArrivalIso = null,
                    currentLocation = "Leaving Regent's Park",
                    modeName = "tube",
                    lineBadge = TflLineColors.getLineBadge("bakerloo", "Bakerloo", "tube")
                ),
                Departure(
                    id = "fb_oxc_4",
                    stationId = stationId,
                    stationName = stationName,
                    lineId = "victoria",
                    lineName = "Victoria",
                    platformName = "Southbound - Platform 4",
                    destinationName = "Brixton",
                    towards = "Brixton",
                    timeToStationSeconds = 360,
                    expectedArrivalIso = null,
                    currentLocation = "Between Warren Street and Oxford Circus",
                    modeName = "tube",
                    lineBadge = TflLineColors.getLineBadge("victoria", "Victoria", "tube")
                )
            )
            "940GZZLUKSX" -> listOf(
                Departure(
                    id = "fb_ksx_1",
                    stationId = stationId,
                    stationName = stationName,
                    lineId = "piccadilly",
                    lineName = "Piccadilly",
                    platformName = "Westbound - Platform 1",
                    destinationName = "Heathrow Terminal 5",
                    towards = "Heathrow Terminals 1, 2, 3 and 5",
                    timeToStationSeconds = 45,
                    expectedArrivalIso = null,
                    currentLocation = "At King's Cross Platform",
                    modeName = "tube",
                    lineBadge = TflLineColors.getLineBadge("piccadilly", "Piccadilly", "tube")
                ),
                Departure(
                    id = "fb_ksx_2",
                    stationId = stationId,
                    stationName = stationName,
                    lineId = "northern",
                    lineName = "Northern",
                    platformName = "Southbound - Platform 8",
                    destinationName = "Morden",
                    towards = "Morden via Bank",
                    timeToStationSeconds = 120,
                    expectedArrivalIso = null,
                    currentLocation = "Approaching King's Cross",
                    modeName = "tube",
                    lineBadge = TflLineColors.getLineBadge("northern", "Northern", "tube")
                ),
                Departure(
                    id = "fb_ksx_3",
                    stationId = stationId,
                    stationName = stationName,
                    lineId = "victoria",
                    lineName = "Victoria",
                    platformName = "Southbound - Platform 4",
                    destinationName = "Brixton",
                    towards = "Brixton",
                    timeToStationSeconds = 210,
                    expectedArrivalIso = null,
                    currentLocation = "Between Highbury & Islington and King's Cross",
                    modeName = "tube",
                    lineBadge = TflLineColors.getLineBadge("victoria", "Victoria", "tube")
                )
            )
            else -> listOf(
                Departure(
                    id = "fb_gen_1",
                    stationId = stationId,
                    stationName = stationName,
                    lineId = "tube",
                    lineName = "Underground",
                    platformName = "Platform 1",
                    destinationName = "City Center",
                    towards = "Central London",
                    timeToStationSeconds = 90,
                    expectedArrivalIso = null,
                    currentLocation = "Approaching Station",
                    modeName = "tube",
                    lineBadge = TflLineColors.getLineBadge("tube", "Underground", "tube")
                ),
                Departure(
                    id = "fb_gen_2",
                    stationId = stationId,
                    stationName = stationName,
                    lineId = "tube",
                    lineName = "Underground",
                    platformName = "Platform 2",
                    destinationName = "Outbound",
                    towards = "Terminal",
                    timeToStationSeconds = 270,
                    expectedArrivalIso = null,
                    currentLocation = "2 stops away",
                    modeName = "tube",
                    lineBadge = TflLineColors.getLineBadge("tube", "Underground", "tube")
                )
            )
        }
    }
}
