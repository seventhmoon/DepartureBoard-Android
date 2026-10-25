package com.androidfung.departureboard.data.repository

/**
 * Common utilities and heuristics for handling TfL and NaPTAN stop identifiers.
 */
internal object TflStopPointUtils {

    /**
     * Regex matching London bus NaPTAN StopPoint IDs according to the NaPTAN schema:
     * Area 490 (Greater London) + 0 (StopPoint flag) + 5 digits + 1-2 alphanumeric stop letters (e.g. 490000185A -> "A", 490013323SB -> "SB").
     *
     * Note: StopAreas use 490G and pure numeric IDs without letters (e.g. 490G000773 or 490000000000) are excluded.
     */
    private val BUS_STOP_ID_REGEX = Regex("^4900\\d{5}([A-Za-z]{1,2})$")

    /**
     * TfL NaPTAN ID prefixes for transit modes.
     */
    const val PREFIX_METRO_TRAM: String = "940G"
    const val PREFIX_NATIONAL_RAIL: String = "910G"
    const val PREFIX_BUS_STOP: String = "490"

    /**
     * Determines whether a station identifier or name represents an individual bus stop/stand
     * versus a train, metro, or railway station.
     */
    fun isBusStop(stationId: String, stationName: String, modes: List<String> = emptyList()): Boolean {
        if (modes.isNotEmpty() && modes.all { it.equals("bus", ignoreCase = true) }) {
            return true
        }
        val idUpper = stationId.uppercase()
        // 490... = London bus stop/pole, regional DfT ATCO bus stop codes, or BODS query identifiers
        if (com.androidfung.departureboard.data.model.AtcoAreaRegistry.isLondonBusStop(idUpper) ||
            com.androidfung.departureboard.data.model.AtcoAreaRegistry.isRegionalBusStop(idUpper)) {
            return true
        }
        val nameLower = stationName.lowercase()
        return nameLower.contains("(stop ") ||
                nameLower.contains("bus stand") ||
                nameLower.contains("bus stop") ||
                nameLower.contains("(route ") ||
                nameLower.endsWith(" bus station")
    }

    /**
     * Extracts the bus stop letter code from a NaPTAN bus stop ID if applicable (e.g. 490000185A -> "A").
     */
    fun extractBusStopLetter(id: String): String? {
        return BUS_STOP_ID_REGEX.find(id)?.groupValues?.getOrNull(1)?.uppercase()
    }

    /**
     * Determines child stop point sorting priority when expanding transit hubs.
     * Metro/Tube/Tram are prioritized over National Rail, which are prioritized over individual Bus stands.
     */
    fun getHubChildPriority(childId: String): Int {
        return when {
            childId.startsWith(PREFIX_METRO_TRAM) -> 3
            childId.startsWith(PREFIX_NATIONAL_RAIL) -> 2
            else -> 1
        }
    }

    /**
     * Returns true if the station represents St Pancras International or King's Cross National Rail
     * (where Tube arrivals from the shared King's Cross St. Pancras Underground hub should be excluded).
     */
    fun isStPancrasOrKingsCrossNR(stationId: String, stationName: String): Boolean {
        if (stationId.equals("910GSTPX", ignoreCase = true) || stationId.equals("910GKNGX", ignoreCase = true)) {
            return true
        }
        val lowerName = stationName.lowercase()
        return lowerName == "st pancras international" || lowerName == "st pancras" || lowerName == "king's cross"
    }

    /**
     * Returns true if the station is Wembley Park (used to filter spurious H&C depot movements).
     */
    fun isWembleyPark(stationId: String, stationName: String): Boolean {
        return stationId.equals("940GZZLUWYP", ignoreCase = true) || "wembley park" in stationName.lowercase()
    }

    /**
     * Known inter-modal sibling NaPTAN IDs for unified stations with split modal IDs:
     * - Watford Junction: National Rail + London Overground (Lioness line) DC lines
     * - Wimbledon: District line (Tube) + Croydon Tramlink + South Western Railway
     * - Stratford: Tube (Central, Jubilee) + DLR + National Rail / Overground / Elizabeth line
     * - West Ham: Tube (District, H&C, Jubilee) + DLR + c2c Rail
     * - Canary Wharf: Jubilee line (Tube) + DLR + Elizabeth line
     * - West Hampstead: Jubilee line (Tube) + Overground (Mildmay) + Thameslink
     */
    fun getInterchangeSiblings(stationId: String): List<String> {
        return when (stationId.uppercase()) {
            "HUBWFJ", "910GWATFDJ" -> listOf("910GWATFJDC")
            "HUBWMB", "940GZZLUWIM", "910GWIMBLDN" -> listOf("940GZZCRWMB", "940GZZLUWIM", "910GWIMBLDN")
            "940GZZCRWMB" -> listOf("940GZZLUWIM", "910GWIMBLDN")
            "HUBSRA", "940GZZLUSTD", "940GZZDLSTR", "910GSTFD" ->
                listOf("940GZZLUSTD", "940GZZDLSTR", "910GSTFD")
            "HUBWEH", "940GZZLUWHM", "940GZZDLWHM", "910GWHAMHL" ->
                listOf("940GZZLUWHM", "940GZZDLWHM", "910GWHAMHL")
            "HUBCAW", "940GZZLUCYF", "940GZZDLCAN", "910GCANWHRF", "910GCW" ->
                listOf("940GZZLUCYF", "940GZZDLCAN", "910GCANWHRF")
            "HUBWHD", "940GZZLUWHP", "910GWHMDSTD", "910GWSTHMPD", "910GWSTHMPT" ->
                listOf("940GZZLUWHP", "910GWHMDSTD", "910GWSTHMPT")
            else -> emptyList()
        }
    }
}
