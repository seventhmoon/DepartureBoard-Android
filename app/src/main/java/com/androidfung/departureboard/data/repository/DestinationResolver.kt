package com.androidfung.departureboard.data.repository

/**
 * Resolves the outbound destination for terminus stations when TfL's prediction API reports
 * inbound terminating trains arriving at the station itself (e.g. Edgware, Brixton),
 * or generic placeholder messages like "Check Front of Train".
 */
internal object DestinationResolver {

    /**
     * Symmetrical lines where trains departing from End A travel to End B, and vice-versa.
     */
    private data class LineEndPair(
        val lineId: String,
        val endAKeywords: Set<String>,
        val endADestination: String,
        val endBKeywords: Set<String>,
        val endBDestination: String
    )

    private val SIMPLE_LINE_TERMINI = listOf(
        // Victoria line
        LineEndPair(
            lineId = "victoria",
            endAKeywords = setOf("brixton"),
            endADestination = "Walthamstow Central",
            endBKeywords = setOf("walthamstow"),
            endBDestination = "Brixton"
        ),
        // Bakerloo line
        LineEndPair(
            lineId = "bakerloo",
            endAKeywords = setOf("elephant & castle", "elephant and castle"),
            endADestination = "Harrow & Wealdstone / Queen's Park",
            endBKeywords = setOf("harrow & wealdstone", "harrow and wealdstone"),
            endBDestination = "Elephant & Castle"
        ),
        // Waterloo & City line
        LineEndPair(
            lineId = "waterloo-city",
            endAKeywords = setOf("waterloo"),
            endADestination = "Bank",
            endBKeywords = setOf("bank"),
            endBDestination = "Waterloo"
        ),
        // Jubilee line
        LineEndPair(
            lineId = "jubilee",
            endAKeywords = setOf("stanmore"),
            endADestination = "Stratford via Central London",
            endBKeywords = setOf("stratford"),
            endBDestination = "Stanmore via Central London"
        ),
        // Elizabeth line
        LineEndPair(
            lineId = "elizabeth",
            endAKeywords = setOf("reading", "heathrow"),
            endADestination = "Abbey Wood / Shenfield",
            endBKeywords = setOf("abbey wood", "shenfield"),
            endBDestination = "Reading / Heathrow Terminal 5"
        )
    )

    fun resolve(
        stationName: String,
        itemDestination: String?,
        itemTowards: String?,
        lineId: String?,
        platformName: String?
    ): String {
        val cleanStation = StationNameFormatter.clean(stationName)
        val stationLower = cleanStation.lowercase()

        val rawInput = itemDestination?.takeIf { it.isNotBlank() }
            ?: itemTowards?.takeIf { it.isNotBlank() }
            ?: "Destination"
        val rawDest = StationNameFormatter.clean(rawInput)

        val isGenericCheckFront = rawDest.contains("check front of train", ignoreCase = true)
        val isTerminatingAtThisStation = rawDest.isNotBlank() && (
            rawDest.equals(cleanStation, ignoreCase = true) ||
            rawDest.startsWith("$cleanStation ", ignoreCase = true)
        )

        // 1. Fast-Path: if train is already heading somewhere else, keep it
        if (!isTerminatingAtThisStation && !isGenericCheckFront) {
            return rawDest
        }

        // 2. Dynamic 'towards' extraction: if TfL provided a real destination in 'towards', use it
        val towardsClean = itemTowards?.let { StationNameFormatter.clean(it) }?.takeIf { it.isNotBlank() }
        if (towardsClean != null && !towardsClean.equals("null", ignoreCase = true)) {
            val towardsLower = towardsClean.lowercase()
            val isRoutingOnly = towardsLower.startsWith("via ") || towardsLower == "central london"
            val isCurrentStation = towardsLower == stationLower || towardsLower.startsWith("$stationLower ")

            if (!isRoutingOnly && !isCurrentStation && !towardsLower.contains("check front of train")) {
                // If it includes a branch via qualifier like "Morden via Bank", return as-is
                return towardsClean
            }
        }

        val towardsLower = itemTowards?.lowercase().orEmpty()
        val lineLower = lineId?.lowercase().orEmpty()
        val platLower = platformName?.lowercase().orEmpty()

        // 3. Symmetrical Line Endpoints Lookup (Victoria, Bakerloo, Jubilee, Waterloo & City, Elizabeth)
        for ((lineKeyword, endAKeywords, endADestination, endBKeywords, endBDestination) in SIMPLE_LINE_TERMINI) {
            if (lineLower.contains(lineKeyword)) {
                if (endAKeywords.any { stationLower.contains(it) }) return endADestination
                if (endBKeywords.any { stationLower.contains(it) }) return endBDestination
            }
        }

        // 4. Multi-branch line routing
        resolveBranchLines(stationLower, lineLower, towardsLower)?.let {
            return it
        }

        // 5. Fallback: directional platform indicator
        return when {
            "southbound" in platLower -> "Southbound Services"
            "northbound" in platLower -> "Northbound Services"
            "eastbound" in platLower -> "Eastbound Services"
            "westbound" in platLower -> "Westbound Services"
            else -> "Outbound Services"
        }
    }

    /**
     * Handles complex lines with multiple branches (Northern, Central, District, Piccadilly, Met, DLR).
     */
    private fun resolveBranchLines(
        station: String,
        line: String,
        towards: String
    ): String? {
        val isNorthern = "northern" in line
        val isCentral = "central" in line
        val isDistrict = "district" in line
        val isPiccadilly = "piccadilly" in line
        val isMetropolitan = "metropolitan" in line

        return when {
            // Northern Line
            isNorthern -> when {
                "morden" in station -> when {
                    "via cx" in towards || "charing cross" in towards -> "Edgware via Charing Cross"
                    "via bank" in towards || "bank" in towards -> "High Barnet via Bank"
                    else -> "Edgware / High Barnet"
                }
                "battersea" in station -> "High Barnet / Edgware via Charing Cross"
                "edgware" in station || "high barnet" in station || "mill hill east" in station -> when {
                    "battersea" in towards -> "Battersea Power Station"
                    "via cx" in towards || "charing cross" in towards -> "Morden via Charing Cross"
                    "via bank" in towards || "bank" in towards -> "Morden via Bank"
                    else -> if ("mill hill east" in station) "Finchley Central" else "Morden / Battersea"
                }
                else -> null
            }

            // Central Line
            isCentral -> when {
                "west ruislip" in station || "ealing broadway" in station -> "Epping / Hainault"
                "epping" in station -> "West Ruislip / Ealing Broadway"
                "hainault" in station -> "Central London via Newbury Park"
                "woodford" in station -> "Central London via Hainault"
                else -> null
            }

            // District & Circle Line
            isDistrict || "circle" in line -> when {
                "wimbledon" in station -> when {
                    "edgware road" in towards -> "Edgware Road via High Street Kensington"
                    else -> "Upminster via Tower Hill"
                }
                "upminster" in station -> "Richmond / Ealing Broadway / Wimbledon"
                "edgware road" in station && "circle" in line -> "Hammersmith via Tower Hill"
                "hammersmith" in station -> "Barking via King's Cross"
                else -> null
            }

            // Piccadilly Line
            isPiccadilly -> when {
                "cockfosters" in station -> "Heathrow / Uxbridge"
                "heathrow" in station -> "Cockfosters via Central London"
                "uxbridge" in station -> "Cockfosters"
                else -> null
            }

            // Metropolitan Line
            isMetropolitan -> when {
                "aldgate" in station -> "Uxbridge / Watford / Amersham"
                "amersham" in station || "chesham" in station || "watford" in station -> "Aldgate / Baker Street"
                else -> null
            }

            // DLR
            "dlr" in line -> when {
                "tower gateway" in station -> "Beckton via Canary Wharf"
                "beckton" in station -> "Tower Gateway / Bank"
                "lewisham" in station -> "Bank / Stratford"
                "woolwich arsenal" in station -> "Bank / Stratford International"
                else -> null
            }

            else -> null
        }
    }
}
