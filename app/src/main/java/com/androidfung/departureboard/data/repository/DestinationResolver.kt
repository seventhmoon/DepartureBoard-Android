package com.androidfung.departureboard.data.repository

/**
 * Resolves the outbound destination for terminus stations when TfL's prediction API reports
 * inbound terminating trains arriving at the station itself (e.g. Edgware, Brixton),
 * or generic placeholder messages like "Check Front of Train".
 *
 * Uses Station NaPTAN ID for deterministic resolution, with fallback to clean station names.
 */
internal object DestinationResolver {

    /**
     * Symmetrical lines where trains departing from End A travel to End B, and vice-versa.
     */
    private data class LineEndPair(
        val lineId: String,
        val endAIds: Set<String> = emptySet(),
        val endAKeywords: Set<String>,
        val endADestination: String,
        val endBIds: Set<String> = emptySet(),
        val endBKeywords: Set<String>,
        val endBDestination: String
    )

    private val SIMPLE_LINE_TERMINI = listOf(
        // Victoria line
        LineEndPair(
            lineId = "victoria",
            endAIds = setOf("940GZZLUBRX"),
            endAKeywords = setOf("brixton"),
            endADestination = "Walthamstow Central",
            endBIds = setOf("940GZZLUWWL"),
            endBKeywords = setOf("walthamstow", "walthamstow central"),
            endBDestination = "Brixton"
        ),
        // Bakerloo line
        LineEndPair(
            lineId = "bakerloo",
            endAIds = setOf("940GZZLUEAC"),
            endAKeywords = setOf("elephant & castle", "elephant and castle"),
            endADestination = "Harrow & Wealdstone / Queen's Park",
            endBIds = setOf("940GZZLUHAW"),
            endBKeywords = setOf("harrow & wealdstone", "harrow and wealdstone"),
            endBDestination = "Elephant & Castle"
        ),
        // Waterloo & City line
        LineEndPair(
            lineId = "waterloo-city",
            endAIds = setOf("940GZZLUWLO"),
            endAKeywords = setOf("waterloo"),
            endADestination = "Bank",
            endBIds = setOf("940GZZLUBNK"),
            endBKeywords = setOf("bank"),
            endBDestination = "Waterloo"
        ),
        // Jubilee line
        LineEndPair(
            lineId = "jubilee",
            endAIds = setOf("940GZZLUSTM"),
            endAKeywords = setOf("stanmore"),
            endADestination = "Stratford via Central London",
            endBIds = setOf("940GZZLUSTD"),
            endBKeywords = setOf("stratford"),
            endBDestination = "Stanmore via Central London"
        ),
        // Elizabeth line
        LineEndPair(
            lineId = "elizabeth",
            endAIds = setOf("910GREADING", "940GZZLUHRC", "940GZZLUHR5", "940GZZLUHR4"),
            endAKeywords = setOf("reading", "heathrow"),
            endADestination = "Abbey Wood / Shenfield",
            endBIds = setOf("910GABWD", "910GSHENFLD"),
            endBKeywords = setOf("abbey wood", "shenfield"),
            endBDestination = "Reading / Heathrow Terminal 5"
        )
    )

    fun resolve(
        stationName: String,
        itemDestination: String?,
        itemTowards: String?,
        lineId: String?,
        platformName: String?,
        stationId: String? = null,
        destinationNaptanId: String? = null
    ): String {
        val cleanStation = StationNameFormatter.clean(stationName)
        val stationLower = cleanStation.lowercase()
        val stIdUpper = stationId?.uppercase().orEmpty()
        val destIdUpper = destinationNaptanId?.uppercase().orEmpty()

        val rawInput = itemDestination?.takeIf { it.isNotBlank() }
            ?: itemTowards?.takeIf { it.isNotBlank() }
            ?: "Destination"
        val rawDest = StationNameFormatter.clean(rawInput)

        val isGenericCheckFront = rawDest.contains("check front of train", ignoreCase = true)

        // 1. Check if terminating here by exact NaPTAN ID match or station name match
        val isTerminatingById = stIdUpper.isNotBlank() && destIdUpper.isNotBlank() && stIdUpper == destIdUpper
        val isTerminatingByName = rawDest.isNotBlank() && (
            rawDest.equals(cleanStation, ignoreCase = true) ||
            rawDest.startsWith("$cleanStation ", ignoreCase = true)
        )
        val isTerminatingAtThisStation = isTerminatingById || isTerminatingByName

        // Fast-Path: if train is already heading somewhere else, keep it
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
                return towardsClean
            }
        }

        val towardsLower = itemTowards?.lowercase().orEmpty()
        val lineLower = lineId?.lowercase().orEmpty()
        val platLower = platformName?.lowercase().orEmpty()

        // 3. Symmetrical Line Endpoints Lookup (using Station ID first, then keyword fallback)
        for ((lineKeyword, endAIds, endAKeywords, endADestination, endBIds, endBKeywords, endBDestination) in SIMPLE_LINE_TERMINI) {
            if (lineLower.contains(lineKeyword)) {
                if (endAIds.contains(stIdUpper) || endAKeywords.any { stationLower.contains(it) }) return endADestination
                if (endBIds.contains(stIdUpper) || endBKeywords.any { stationLower.contains(it) }) return endBDestination
            }
        }

        // 4. Multi-branch line routing
        resolveBranchLines(stationLower, lineLower, towardsLower, stIdUpper)?.let {
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
        towards: String,
        stationId: String = ""
    ): String? {
        val isNorthern = "northern" in line
        val isCentral = "central" in line
        val isDistrict = "district" in line
        val isPiccadilly = "piccadilly" in line
        val isMetropolitan = "metropolitan" in line

        return when {
            // Northern Line
            isNorthern -> when {
                stationId == "940GZZLUMDN" || "morden" in station -> when {
                    "via cx" in towards || "charing cross" in towards -> "Edgware via Charing Cross"
                    "via bank" in towards || "bank" in towards -> "High Barnet via Bank"
                    else -> "Edgware / High Barnet"
                }
                stationId == "940GZZBPSUST" || "battersea" in station -> "High Barnet / Edgware via Charing Cross"
                stationId in setOf("940GZZLUEGW", "940GZZLUHBT", "940GZZLUMHE") ||
                "edgware" in station || "high barnet" in station || "mill hill east" in station -> when {
                    "battersea" in towards -> "Battersea Power Station"
                    "via cx" in towards || "charing cross" in towards -> "Morden via Charing Cross"
                    "via bank" in towards || "bank" in towards -> "Morden via Bank"
                    else -> if (stationId == "940GZZLUMHE" || "mill hill east" in station) "Finchley Central" else "Morden / Battersea"
                }
                else -> null
            }

            // Central Line
            isCentral -> when {
                stationId in setOf("940GZZLUWRP", "940GZZLUEBY") || "west ruislip" in station || "ealing broadway" in station -> "Epping / Hainault"
                stationId == "940GZZLUEPG" || "epping" in station -> "West Ruislip / Ealing Broadway"
                stationId == "940GZZLUHHL" || "hainault" in station -> "Central London via Newbury Park"
                stationId == "940GZZLUWFD" || "woodford" in station -> "Central London via Hainault"
                else -> null
            }

            // District & Circle Line
            isDistrict || "circle" in line -> when {
                stationId == "940GZZLUWBR" || "wimbledon" in station -> when {
                    "edgware road" in towards -> "Edgware Road via High Street Kensington"
                    else -> "Upminster via Tower Hill"
                }
                stationId == "940GZZLUUPM" || "upminster" in station -> "Richmond / Ealing Broadway / Wimbledon"
                (stationId in setOf("940GZZLUERB", "940GZZLUERC") || "edgware road" in station) && "circle" in line -> "Hammersmith via Tower Hill"
                stationId in setOf("940GZZLUHSD", "940GZZLUHSC") || "hammersmith" in station -> "Barking via King's Cross"
                else -> null
            }

            // Piccadilly Line
            isPiccadilly -> when {
                stationId == "940GZZLUCKF" || "cockfosters" in station -> "Heathrow / Uxbridge"
                stationId in setOf("940GZZLUHRC", "940GZZLUHR4", "940GZZLUHR5") || "heathrow" in station -> "Cockfosters via Central London"
                stationId == "940GZZLUUXB" || "uxbridge" in station -> "Cockfosters"
                else -> null
            }

            // Metropolitan Line
            isMetropolitan -> when {
                stationId == "940GZZLUALD" || "aldgate" in station -> "Uxbridge / Watford / Amersham"
                stationId in setOf("940GZZLUAMS", "940GZZLUCSM", "940GZZLUWFD") ||
                "amersham" in station || "chesham" in station || "watford" in station -> "Aldgate / Baker Street"
                else -> null
            }

            // DLR
            "dlr" in line -> when {
                stationId == "940GZZDLTWR" || "tower gateway" in station -> "Beckton via Canary Wharf"
                stationId == "940GZZDLBCK" || "beckton" in station -> "Tower Gateway / Bank"
                stationId == "940GZZDLLEW" || "lewisham" in station -> "Bank / Stratford"
                stationId == "940GZZDLWLA" || "woolwich arsenal" in station -> "Bank / Stratford International"
                else -> null
            }

            else -> null
        }
    }
}
