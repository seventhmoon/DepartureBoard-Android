package com.androidfung.departureboard.data.repository.region.gb.direction

import com.androidfung.departureboard.data.model.gb.TflStationIds
import com.androidfung.departureboard.data.repository.region.gb.StationNameFormatter

/**
 * Resolves the outbound destination for terminus stations when TfL's prediction API reports
 * inbound terminating trains arriving at the station itself (e.g. Edgware, Brixton),
 * or generic placeholder messages like "Check Front of Train".
 *
 * Uses Station NaPTAN ID for deterministic resolution, with fallback to clean station names.
 */
object DestinationResolver {

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
            endAIds = setOf(TflStationIds.BRIXTON_ALT, TflStationIds.BRIXTON),
            endAKeywords = setOf("brixton"),
            endADestination = "Walthamstow Central",
            endBIds = setOf(TflStationIds.WALTHAMSTOW_CENTRAL, TflStationIds.WALTHAMSTOW_CENTRAL_ALT),
            endBKeywords = setOf("walthamstow", "walthamstow central"),
            endBDestination = "Brixton"
        ),
        // Bakerloo line
        LineEndPair(
            lineId = "bakerloo",
            endAIds = setOf(TflStationIds.ELEPHANT_AND_CASTLE, TflStationIds.ELEPHANT_AND_CASTLE_ALT),
            endAKeywords = setOf("elephant & castle", "elephant and castle"),
            endADestination = "Harrow & Wealdstone / Queen's Park",
            endBIds = setOf(TflStationIds.HARROW_AND_WEALDSTONE),
            endBKeywords = setOf("harrow & wealdstone", "harrow and wealdstone"),
            endBDestination = "Elephant & Castle"
        ),
        // Waterloo & City line
        LineEndPair(
            lineId = "waterloo-city",
            endAIds = setOf(TflStationIds.WATERLOO),
            endAKeywords = setOf("waterloo"),
            endADestination = "Bank",
            endBIds = setOf(TflStationIds.BANK),
            endBKeywords = setOf("bank"),
            endBDestination = "Waterloo"
        ),
        // Jubilee line
        LineEndPair(
            lineId = "jubilee",
            endAIds = setOf(TflStationIds.STANMORE),
            endAKeywords = setOf("stanmore"),
            endADestination = "Stratford via Central London",
            endBIds = setOf(TflStationIds.STRATFORD),
            endBKeywords = setOf("stratford"),
            endBDestination = "Stanmore via Central London"
        ),
        // Elizabeth line
        LineEndPair(
            lineId = "elizabeth",
            endAIds = setOf(TflStationIds.READING, TflStationIds.HEATHROW_TERMINALS_123, TflStationIds.HEATHROW_TERMINAL_5, TflStationIds.HEATHROW_TERMINAL_4),
            endAKeywords = setOf("reading", "heathrow"),
            endADestination = "Abbey Wood / Shenfield",
            endBIds = setOf(TflStationIds.ABBEY_WOOD, TflStationIds.SHENFIELD, TflStationIds.ABBEY_WOOD_HUB),
            endBKeywords = setOf("abbey wood", "shenfield"),
            endBDestination = "Reading / Heathrow Terminal 5"
        )
    )

    private val CANONICAL_TERMINAL_NAPTAN_IDS = TflStationIds.CANONICAL_TERMINALS_BY_NAME

    /**
     * Checks whether a raw prediction is an inbound train terminating at the queried station.
     */
    fun isTerminatingAtStation(
        stationId: String?,
        stationName: String,
        itemDestination: String?,
        itemTowards: String? = null,
        destinationNaptanId: String? = null,
        itemNaptanId: String? = null
    ): Boolean {
        val cleanStation = StationNameFormatter.clean(stationName)
        val stIdUpper = stationId?.uppercase().orEmpty()
        val naptanUpper = itemNaptanId?.uppercase().orEmpty()
        val destIdUpper = destinationNaptanId?.uppercase().orEmpty()

        val isTerminatingById = destIdUpper.isNotBlank() && (
            (stIdUpper.isNotBlank() && stIdUpper == destIdUpper) ||
            (naptanUpper.isNotBlank() && naptanUpper == destIdUpper)
        )
        if (isTerminatingById) return true

        val rawInput = itemDestination?.takeIf { it.isNotBlank() }
            ?: itemTowards?.takeIf { it.isNotBlank() }
            ?: return false
        val rawDest = StationNameFormatter.clean(rawInput)
        return rawDest.isNotBlank() && cleanStation.isNotBlank() && (
            rawDest.equals(cleanStation, ignoreCase = true) ||
            rawDest.startsWith("$cleanStation ", ignoreCase = true)
        )
    }

    /**
     * Resolves the canonical NaPTAN ID for an unambiguous resolved outbound destination name
     * (stripping any "via ..." routing suffix). Returns null for multi-option strings (e.g. "Morden / Battersea").
     */
    fun resolveDestinationNaptanId(resolvedDestination: String?, lineId: String? = null): String? {
        if (resolvedDestination.isNullOrBlank() || "/" in resolvedDestination) return null
        val terminalOnly = StationNameFormatter.clean(
            resolvedDestination.split(Regex("""\s+via\s+""", RegexOption.IGNORE_CASE)).first()
        ).lowercase()
        if (terminalOnly.isBlank()) return null
        return CANONICAL_TERMINAL_NAPTAN_IDS[terminalOnly]
    }

    /**
     * Returns the physical outbound cardinal direction for single-direction terminus stations
     * where TfL's API reports the inbound arrival track direction on platform labels (e.g.
     * "Northbound - Platform 1" at Mill Hill East where all departing trains travel Southbound).
     */
    fun resolveTerminusOutboundTransitDirection(
        stationId: String?,
        stationName: String,
        lineId: String?
    ): com.androidfung.departureboard.data.model.TransitDirection? {
        val stId = stationId?.uppercase()?.trim().orEmpty()
        val station = StationNameFormatter.clean(stationName).lowercase()
        val line = lineId?.lowercase()?.trim().orEmpty()

        return when {
            "northern" in line -> when {
                stId in setOf(TflStationIds.MILL_HILL_EAST, TflStationIds.HIGH_BARNET, TflStationIds.EDGWARE) ||
                "mill hill east" in station ||
                "high barnet" in station ||
                ("edgware" in station && "edgware road" !in station) -> com.androidfung.departureboard.data.model.TransitDirection.SOUTHBOUND
                stId in setOf(TflStationIds.MORDEN, TflStationIds.BATTERSEA_POWER_STATION, TflStationIds.BATTERSEA_POWER_STATION_ALT) ||
                ("morden" in station && "south morden" !in station) ||
                "battersea power station" in station -> com.androidfung.departureboard.data.model.TransitDirection.NORTHBOUND
                else -> null
            }
            "victoria" in line -> when {
                stId in setOf(TflStationIds.WALTHAMSTOW_CENTRAL, TflStationIds.WALTHAMSTOW_CENTRAL_ALT) || "walthamstow" in station -> com.androidfung.departureboard.data.model.TransitDirection.SOUTHBOUND
                stId in setOf(TflStationIds.BRIXTON, TflStationIds.BRIXTON_ALT) || "brixton" in station -> com.androidfung.departureboard.data.model.TransitDirection.NORTHBOUND
                else -> null
            }
            "jubilee" in line -> when {
                stId == TflStationIds.STANMORE || "stanmore" in station -> com.androidfung.departureboard.data.model.TransitDirection.SOUTHBOUND
                stId == TflStationIds.STRATFORD || ("stratford" in station && "stratford international" !in station) -> com.androidfung.departureboard.data.model.TransitDirection.WESTBOUND
                else -> null
            }
            "bakerloo" in line -> when {
                stId == TflStationIds.HARROW_AND_WEALDSTONE || "harrow & wealdstone" in station || "harrow and wealdstone" in station -> com.androidfung.departureboard.data.model.TransitDirection.SOUTHBOUND
                stId in setOf(TflStationIds.ELEPHANT_AND_CASTLE, TflStationIds.ELEPHANT_AND_CASTLE_ALT) || "elephant & castle" in station || "elephant and castle" in station -> com.androidfung.departureboard.data.model.TransitDirection.NORTHBOUND
                else -> null
            }
            "central" in line -> when {
                stId == TflStationIds.EPPING || "epping" in station -> com.androidfung.departureboard.data.model.TransitDirection.WESTBOUND
                stId in setOf(TflStationIds.WEST_RUISLIP, TflStationIds.EALING_BROADWAY) || "west ruislip" in station || "ealing broadway" in station -> com.androidfung.departureboard.data.model.TransitDirection.EASTBOUND
                else -> null
            }
            "piccadilly" in line -> when {
                stId == TflStationIds.COCKFOSTERS || "cockfosters" in station -> com.androidfung.departureboard.data.model.TransitDirection.WESTBOUND
                stId in setOf(TflStationIds.HEATHROW_TERMINAL_5, TflStationIds.HEATHROW_TERMINAL_4, TflStationIds.UXBRIDGE) ||
                "heathrow terminal 5" in station || "heathrow terminal 4" in station || "uxbridge" in station -> com.androidfung.departureboard.data.model.TransitDirection.EASTBOUND
                else -> null
            }
            "district" in line -> when {
                stId == TflStationIds.UPMINSTER || "upminster" in station -> com.androidfung.departureboard.data.model.TransitDirection.WESTBOUND
                stId in setOf(TflStationIds.RICHMOND, TflStationIds.WIMBLEDON, TflStationIds.WIMBLEDON_ALT, TflStationIds.EALING_BROADWAY) ||
                "richmond" in station || "ealing broadway" in station ||
                ("wimbledon" in station && "wimbledon park" !in station && "south wimbledon" !in station) -> com.androidfung.departureboard.data.model.TransitDirection.EASTBOUND
                else -> null
            }
            "metropolitan" in line -> when {
                stId == TflStationIds.ALDGATE || ("aldgate" in station && "aldgate east" !in station) -> com.androidfung.departureboard.data.model.TransitDirection.NORTHBOUND
                stId in setOf(TflStationIds.AMERSHAM, TflStationIds.CHESHAM, TflStationIds.WATFORD, TflStationIds.WATFORD_ALT) ||
                "amersham" in station || "chesham" in station || "watford" in station -> com.androidfung.departureboard.data.model.TransitDirection.SOUTHBOUND
                stId == TflStationIds.UXBRIDGE || "uxbridge" in station -> com.androidfung.departureboard.data.model.TransitDirection.EASTBOUND
                else -> null
            }
            "elizabeth" in line -> when {
                stId in setOf(TflStationIds.READING, TflStationIds.HEATHROW_TERMINAL_5, TflStationIds.HEATHROW_TERMINAL_4) ||
                "reading" in station || "heathrow terminal 5" in station || "heathrow terminal 4" in station -> com.androidfung.departureboard.data.model.TransitDirection.EASTBOUND
                stId in setOf(TflStationIds.SHENFIELD, TflStationIds.ABBEY_WOOD, TflStationIds.ABBEY_WOOD_HUB) ||
                "shenfield" in station || "abbey wood" in station -> com.androidfung.departureboard.data.model.TransitDirection.WESTBOUND
                else -> null
            }
            else -> null
        }
    }

    fun resolveTerminusOutboundDirection(
        stationId: String?,
        stationName: String,
        lineId: String?
    ): String? = resolveTerminusOutboundTransitDirection(stationId, stationName, lineId)?.displayName

    /**
     * Canonicalizes corridor via and terminal names, expanding common TfL dot-matrix abbreviations:
     * - "via CX" / "via Chx" -> "via Charing Cross"
     * - "via Bnk" -> "via Bank"
     * - "Battersea via ..." / "BPS via ..." -> "Battersea Power Station via ..."
     * - "Battersea" / "BPS" -> "Battersea Power Station"
     */
    fun normalizeCorridorDestination(destination: String): String {
        var result = destination.trim()

        // 1. Expand "via CX" / "via Chx" to "via Charing Cross"
        result = result.replace(Regex("""\bvia\s+(?:cx|chx)\b""", RegexOption.IGNORE_CASE), "via Charing Cross")

        // 2. Expand "via Bnk" to "via Bank"
        result = result.replace(Regex("""\bvia\s+bnk\b""", RegexOption.IGNORE_CASE), "via Bank")

        // 3. Expand "Battersea via" or "BPS via" to "Battersea Power Station via"
        result = result.replace(Regex("""^(?:Battersea|BPS)\s+via\b""", RegexOption.IGNORE_CASE), "Battersea Power Station via")

        // 4. Expand lone "Battersea" or "BPS" (exact match) to "Battersea Power Station"
        if (result.equals("Battersea", ignoreCase = true) || result.equals("BPS", ignoreCase = true)) {
            result = "Battersea Power Station"
        }

        return result
    }

    fun resolve(
        stationName: String,
        itemDestination: String?,
        itemTowards: String?,
        lineId: String?,
        platformName: String? = null,
        stationId: String? = null,
        destinationNaptanId: String? = null
    ): String {
        val rawResolved = resolveInternal(
            stationName = stationName,
            itemDestination = itemDestination,
            itemTowards = itemTowards,
            lineId = lineId,
            platformName = platformName,
            stationId = stationId,
            destinationNaptanId = destinationNaptanId
        )
        return normalizeCorridorDestination(rawResolved)
    }

    private fun resolveInternal(
        stationName: String,
        itemDestination: String?,
        itemTowards: String?,
        lineId: String?,
        platformName: String? = null,
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

        // Fast-Path: if train is already heading somewhere else, keep it (preserving corridor routing if provided in towards)
        if (!isTerminatingAtThisStation && !isGenericCheckFront) {
            val towardsClean = itemTowards?.let { StationNameFormatter.clean(it) }?.takeIf { it.isNotBlank() }
            if (towardsClean != null && !towardsClean.equals("null", ignoreCase = true)) {
                val towardsLower = towardsClean.lowercase()
                if (towardsLower.contains("via ") && !towardsLower.startsWith(stationLower)) {
                    return if (towardsLower.startsWith("via ")) {
                        "$rawDest $towardsClean"
                    } else {
                        towardsClean
                    }
                }
            }
            return rawDest
        }

        // 2. Dynamic 'towards' extraction: if TfL provided a real destination in 'towards', use it
        val towardsClean = itemTowards?.let { StationNameFormatter.clean(it) }?.takeIf { it.isNotBlank() }
        if (towardsClean != null && !towardsClean.equals("null", ignoreCase = true)) {
            val towardsLower = towardsClean.lowercase()
            val isRoutingOnly = towardsLower.startsWith("via ") || towardsLower == "central london"
            val isCurrentStation = towardsLower == stationLower || towardsLower.startsWith("$stationLower ")

            // If towards includes a branch route like "Edgware via Bank", extract the destination/branch
            // Note: Mill Hill East is handled specifically below or via timetable/branch resolution
            // because inbound shuttle trains to Mill Hill East display "Mill Hill East via CX" while heading to MHL.
            val isMillHillEast = stIdUpper == "940GZZLUMHL" || stationLower.contains("mill hill east")
            if (!isMillHillEast && isCurrentStation && towardsLower.contains("via ")) {
                val branch = towardsClean.substringAfter("via ", "").trim()
                if (branch.isNotBlank()) {
                    val branchLower = branch.lowercase()
                    if (branchLower == "bank") return "Morden via Bank"
                    if (branchLower.contains("cx") || branchLower.contains("charing cross")) return "Morden via Charing Cross"
                }
            }

            if (!isRoutingOnly && !isCurrentStation && !towardsLower.contains("check front of train")) {
                return towardsClean
            }
        }

        val towardsLower = itemTowards?.lowercase().orEmpty()
        val lineLower = lineId?.lowercase().orEmpty()
        val platLower = platformName?.lowercase().orEmpty()

        // 3. Symmetrical Line Endpoints Lookup (using Station ID first, then keyword fallback)
        for (pair in SIMPLE_LINE_TERMINI) {
            if (lineLower.contains(pair.lineId)) {
                if (pair.endAIds.contains(stIdUpper) || pair.endAKeywords.any { stationLower.contains(it) }) return pair.endADestination
                if (pair.endBIds.contains(stIdUpper) || pair.endBKeywords.any { stationLower.contains(it) }) return pair.endBDestination
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

    fun resolve(
        rawDestination: String,
        stationId: String,
        stationName: String,
        lineId: String
    ): String {
        return resolve(
            stationName = stationName,
            itemDestination = rawDestination,
            itemTowards = null,
            lineId = lineId,
            stationId = stationId
        )
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
                stationId == TflStationIds.MORDEN || "morden" in station -> when {
                    "via cx" in towards || "charing cross" in towards -> "Edgware via Charing Cross"
                    "via bank" in towards || "bank" in towards -> "High Barnet via Bank"
                    else -> "Edgware / High Barnet"
                }
                stationId in setOf(TflStationIds.BATTERSEA_POWER_STATION, TflStationIds.BATTERSEA_POWER_STATION_ALT) || "battersea" in station -> "High Barnet / Edgware via Charing Cross"
                stationId == TflStationIds.MILL_HILL_EAST || "mill hill east" in station -> when {
                    "battersea" in towards -> "Battersea Power Station via Charing Cross"
                    "via cx" in towards || "charing cross" in towards -> "Battersea Power Station via Charing Cross"
                    "via bank" in towards || "bank" in towards -> "Morden via Bank"
                    else -> "Finchley Central"
                }
                stationId in setOf(TflStationIds.EDGWARE, TflStationIds.HIGH_BARNET) ||
                "edgware" in station || "high barnet" in station -> when {
                    "battersea" in towards -> "Battersea Power Station"
                    "via cx" in towards || "charing cross" in towards -> "Morden via Charing Cross"
                    "via bank" in towards || "bank" in towards -> "Morden via Bank"
                    else -> "Morden / Battersea"
                }
                else -> null
            }

            // Central Line
            isCentral -> when {
                stationId in setOf(TflStationIds.WEST_RUISLIP, TflStationIds.EALING_BROADWAY) || "west ruislip" in station || "ealing broadway" in station -> "Epping / Hainault"
                stationId == TflStationIds.EPPING || "epping" in station -> "West Ruislip / Ealing Broadway"
                stationId == TflStationIds.HAINAULT || "hainault" in station -> "Central London via Newbury Park"
                stationId == TflStationIds.WOODFORD || "woodford" in station -> "Central London via Hainault"
                else -> null
            }

            // District & Circle Line
            isDistrict || "circle" in line -> when {
                stationId in setOf(TflStationIds.WIMBLEDON, TflStationIds.WIMBLEDON_ALT) || "wimbledon" in station -> when {
                    "edgware road" in towards -> "Edgware Road via High Street Kensington"
                    else -> "Upminster via Tower Hill"
                }
                stationId == TflStationIds.UPMINSTER || "upminster" in station -> "Richmond / Ealing Broadway / Wimbledon"
                (stationId in setOf(TflStationIds.EDGWARE_ROAD_BAKERLOO, TflStationIds.EDGWARE_ROAD_CIRCLE) || "edgware road" in station) && "circle" in line -> "Hammersmith via Tower Hill"
                stationId in setOf(TflStationIds.HAMMERSMITH_DISTRICT, TflStationIds.HAMMERSMITH) || "hammersmith" in station -> "Barking via King's Cross"
                else -> null
            }

            // Piccadilly Line
            isPiccadilly -> when {
                stationId == TflStationIds.COCKFOSTERS || "cockfosters" in station -> "Heathrow / Uxbridge"
                stationId in setOf(TflStationIds.HEATHROW_TERMINALS_123, TflStationIds.HEATHROW_TERMINAL_4, TflStationIds.HEATHROW_TERMINAL_5) || "heathrow" in station -> "Cockfosters via Central London"
                stationId == TflStationIds.UXBRIDGE || "uxbridge" in station -> "Cockfosters"
                else -> null
            }

            // Metropolitan Line
            isMetropolitan -> when {
                stationId == TflStationIds.ALDGATE || "aldgate" in station -> "Uxbridge / Watford / Amersham"
                stationId in setOf(TflStationIds.AMERSHAM, TflStationIds.CHESHAM, TflStationIds.WATFORD_ALT, TflStationIds.WATFORD) ||
                "amersham" in station || "chesham" in station || "watford" in station -> "Aldgate / Baker Street"
                else -> null
            }

            // DLR
            "dlr" in line -> when {
                stationId == TflStationIds.TOWER_GATEWAY || "tower gateway" in station -> "Beckton via Canary Wharf"
                stationId == TflStationIds.BECKTON || "beckton" in station -> "Tower Gateway / Bank"
                stationId == TflStationIds.LEWISHAM || "lewisham" in station -> "Bank / Stratford"
                stationId == TflStationIds.WOOLWICH_ARSENAL || "woolwich arsenal" in station -> "Bank / Stratford International"
                else -> null
            }

            else -> null
        }
    }
}
