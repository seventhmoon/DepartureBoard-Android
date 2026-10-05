package com.androidfung.departureboard.data.repository

/**
 * Rule configuration for resolving outbound destinations when TfL reports terminating trains
 * at the station itself or generic messages like "Check Front of Train".
 */
internal data class TerminusResolutionRule(
    val stationMatcher: (station: String) -> Boolean,
    val lineMatcher: ((line: String) -> Boolean)? = null,
    val subConditions: List<SubCondition> = emptyList(),
    val defaultDestination: String
) {
    data class SubCondition(
        val matches: (towards: String, platform: String, line: String) -> Boolean,
        val destination: String
    )
}

/**
 * Resolves the outbound destination for terminus stations when TfL's prediction API reports
 * inbound terminating trains arriving at the station itself (e.g. Edgware, Mill Hill East, Brixton).
 */
internal object DestinationResolver {

    private val RULES = listOf(
        // Northern Line
        TerminusResolutionRule(
            stationMatcher = { "edgware" in it },
            subConditions = listOf(
                TerminusResolutionRule.SubCondition(
                    matches = { towards, _, _ -> "via cx" in towards || "charing cross" in towards },
                    destination = "Morden via Charing Cross"
                ),
                TerminusResolutionRule.SubCondition(
                    matches = { towards, _, _ -> "via bank" in towards || "bank" in towards },
                    destination = "Morden via Bank"
                ),
                TerminusResolutionRule.SubCondition(
                    matches = { towards, _, _ -> "battersea" in towards },
                    destination = "Battersea Power Station"
                )
            ),
            defaultDestination = "Morden / Battersea"
        ),
        TerminusResolutionRule(
            stationMatcher = { "mill hill east" in it },
            subConditions = listOf(
                TerminusResolutionRule.SubCondition(
                    matches = { towards, _, _ -> "battersea" in towards },
                    destination = "Battersea Power Station"
                ),
                TerminusResolutionRule.SubCondition(
                    matches = { towards, _, _ -> "via cx" in towards || "charing cross" in towards },
                    destination = "Battersea via Charing Cross"
                ),
                TerminusResolutionRule.SubCondition(
                    matches = { towards, _, _ -> "via bank" in towards || "bank" in towards },
                    destination = "Morden via Bank"
                )
            ),
            defaultDestination = "Finchley Central"
        ),
        TerminusResolutionRule(
            stationMatcher = { "high barnet" in it },
            subConditions = listOf(
                TerminusResolutionRule.SubCondition(
                    matches = { towards, _, _ -> "via cx" in towards || "charing cross" in towards },
                    destination = "Battersea Power Station"
                ),
                TerminusResolutionRule.SubCondition(
                    matches = { towards, _, _ -> "via bank" in towards || "bank" in towards },
                    destination = "Morden via Bank"
                )
            ),
            defaultDestination = "Morden / Battersea"
        ),
        TerminusResolutionRule(
            stationMatcher = { "morden" in it },
            subConditions = listOf(
                TerminusResolutionRule.SubCondition(
                    matches = { towards, _, _ -> "via cx" in towards || "charing cross" in towards },
                    destination = "Edgware via Charing Cross"
                ),
                TerminusResolutionRule.SubCondition(
                    matches = { towards, _, _ -> "via bank" in towards || "bank" in towards },
                    destination = "High Barnet via Bank"
                )
            ),
            defaultDestination = "Edgware / High Barnet"
        ),
        TerminusResolutionRule(
            stationMatcher = { "battersea" in it },
            defaultDestination = "High Barnet / Edgware via Charing Cross"
        ),

        // Victoria Line
        TerminusResolutionRule(
            stationMatcher = { "brixton" in it },
            defaultDestination = "Walthamstow Central"
        ),
        TerminusResolutionRule(
            stationMatcher = { "walthamstow" in it },
            defaultDestination = "Brixton"
        ),

        // Bakerloo Line
        TerminusResolutionRule(
            stationMatcher = { "elephant & castle" in it },
            defaultDestination = "Harrow & Wealdstone / Queen's Park"
        ),
        TerminusResolutionRule(
            stationMatcher = { "harrow & wealdstone" in it },
            defaultDestination = "Elephant & Castle"
        ),

        // Central Line
        TerminusResolutionRule(
            stationMatcher = { "west ruislip" in it },
            defaultDestination = "Epping via Bank"
        ),
        TerminusResolutionRule(
            stationMatcher = { "ealing broadway" in it },
            subConditions = listOf(
                TerminusResolutionRule.SubCondition(
                    matches = { _, _, line -> "district" in line },
                    destination = "Upminster via Tower Hill"
                ),
                TerminusResolutionRule.SubCondition(
                    matches = { _, _, line -> "elizabeth" in line },
                    destination = "Abbey Wood / Shenfield"
                )
            ),
            defaultDestination = "Epping / Hainault"
        ),
        TerminusResolutionRule(
            stationMatcher = { "epping" in it },
            defaultDestination = "West Ruislip / Ealing Broadway"
        ),
        TerminusResolutionRule(
            stationMatcher = { "hainault" in it },
            defaultDestination = "Central London via Newbury Park"
        ),
        TerminusResolutionRule(
            stationMatcher = { "woodford" in it },
            defaultDestination = "Central London via Hainault"
        ),

        // District & Circle Lines
        TerminusResolutionRule(
            stationMatcher = { "wimbledon" in it },
            subConditions = listOf(
                TerminusResolutionRule.SubCondition(
                    matches = { towards, _, _ -> "edgware road" in towards },
                    destination = "Edgware Road via High Street Kensington"
                ),
                TerminusResolutionRule.SubCondition(
                    matches = { towards, _, _ -> "tower hill" in towards || "upminster" in towards || "barking" in towards },
                    destination = "Upminster via Tower Hill"
                )
            ),
            defaultDestination = "Upminster / Edgware Road"
        ),
        TerminusResolutionRule(
            stationMatcher = { "richmond" in it },
            subConditions = listOf(
                TerminusResolutionRule.SubCondition(
                    matches = { _, _, line -> "overground" in line || "mildmay" in line },
                    destination = "Stratford via Highbury & Islington"
                )
            ),
            defaultDestination = "Upminster via Tower Hill"
        ),
        TerminusResolutionRule(
            stationMatcher = { "upminster" in it },
            defaultDestination = "Richmond / Ealing Broadway / Wimbledon"
        ),
        TerminusResolutionRule(
            stationMatcher = { "edgware road" in it },
            subConditions = listOf(
                TerminusResolutionRule.SubCondition(
                    matches = { _, _, line -> "circle" in line },
                    destination = "Hammersmith via Tower Hill"
                )
            ),
            defaultDestination = "Wimbledon via High Street Kensington"
        ),
        TerminusResolutionRule(
            stationMatcher = { "hammersmith" in it },
            subConditions = listOf(
                TerminusResolutionRule.SubCondition(
                    matches = { _, _, line -> "hammersmith" in line },
                    destination = "Barking via King's Cross"
                )
            ),
            defaultDestination = "Edgware Road via Aldgate"
        ),
        TerminusResolutionRule(
            stationMatcher = { "barking" in it },
            subConditions = listOf(
                TerminusResolutionRule.SubCondition(
                    matches = { _, _, line -> "hammersmith" in line },
                    destination = "Hammersmith via King's Cross"
                )
            ),
            defaultDestination = "Wimbledon / Richmond / Ealing Broadway"
        ),

        // Jubilee Line
        TerminusResolutionRule(
            stationMatcher = { "stanmore" in it },
            defaultDestination = "Stratford via Central London"
        ),
        TerminusResolutionRule(
            stationMatcher = { "stratford" in it },
            subConditions = listOf(
                TerminusResolutionRule.SubCondition(
                    matches = { _, _, line -> "jubilee" in line },
                    destination = "Stanmore via Central London"
                ),
                TerminusResolutionRule.SubCondition(
                    matches = { _, _, line -> "central" in line },
                    destination = "West Ruislip / Ealing Broadway"
                ),
                TerminusResolutionRule.SubCondition(
                    matches = { _, _, line -> "dlr" in line },
                    destination = "Lewisham / Woolwich Arsenal"
                )
            ),
            defaultDestination = "Westbound Services"
        ),
        TerminusResolutionRule(
            stationMatcher = { "canary wharf" in it },
            lineMatcher = { "jubilee" in it },
            subConditions = listOf(
                TerminusResolutionRule.SubCondition(
                    matches = { _, plat, _ -> "eastbound" in plat || "platform 2" in plat },
                    destination = "Stratford via North Greenwich"
                )
            ),
            defaultDestination = "Stanmore / Wembley Park"
        ),

        // Metropolitan Line
        TerminusResolutionRule(
            stationMatcher = { "aldgate" in it },
            defaultDestination = "Uxbridge / Watford / Amersham"
        ),
        TerminusResolutionRule(
            stationMatcher = { "amersham" in it },
            defaultDestination = "Aldgate / Baker Street"
        ),
        TerminusResolutionRule(
            stationMatcher = { "chesham" in it },
            defaultDestination = "Aldgate / Baker Street"
        ),
        TerminusResolutionRule(
            stationMatcher = { "watford" in it },
            defaultDestination = "Aldgate / Baker Street"
        ),
        TerminusResolutionRule(
            stationMatcher = { "uxbridge" in it },
            subConditions = listOf(
                TerminusResolutionRule.SubCondition(
                    matches = { _, _, line -> "piccadilly" in line },
                    destination = "Cockfosters"
                )
            ),
            defaultDestination = "Aldgate / Baker Street"
        ),

        // Piccadilly Line
        TerminusResolutionRule(
            stationMatcher = { "cockfosters" in it },
            defaultDestination = "Heathrow / Uxbridge"
        ),
        TerminusResolutionRule(
            stationMatcher = { "heathrow terminal 4" in it },
            defaultDestination = "Cockfosters via Central London"
        ),
        TerminusResolutionRule(
            stationMatcher = { "heathrow terminal 5" in it },
            subConditions = listOf(
                TerminusResolutionRule.SubCondition(
                    matches = { _, _, line -> "elizabeth" in line },
                    destination = "Abbey Wood / Shenfield"
                )
            ),
            defaultDestination = "Cockfosters via Central London"
        ),

        // Waterloo & City Line
        TerminusResolutionRule(
            stationMatcher = { "waterloo" in it },
            lineMatcher = { "waterloo" in it || "city" in it },
            defaultDestination = "Bank"
        ),
        TerminusResolutionRule(
            stationMatcher = { "bank" in it },
            lineMatcher = { "waterloo" in it || "city" in it },
            defaultDestination = "Waterloo"
        ),

        // Elizabeth Line
        TerminusResolutionRule(
            stationMatcher = { "reading" in it },
            defaultDestination = "Abbey Wood / Shenfield"
        ),
        TerminusResolutionRule(
            stationMatcher = { "shenfield" in it },
            defaultDestination = "Reading / Heathrow Terminal 5"
        ),
        TerminusResolutionRule(
            stationMatcher = { "abbey wood" in it },
            defaultDestination = "Reading / Heathrow Terminal 5"
        ),

        // DLR
        TerminusResolutionRule(
            stationMatcher = { "tower gateway" in it },
            defaultDestination = "Beckton via Canary Wharf"
        ),
        TerminusResolutionRule(
            stationMatcher = { "beckton" in it },
            defaultDestination = "Tower Gateway / Bank"
        ),
        TerminusResolutionRule(
            stationMatcher = { "lewisham" in it },
            defaultDestination = "Bank / Stratford"
        ),
        TerminusResolutionRule(
            stationMatcher = { "woolwich arsenal" in it },
            defaultDestination = "Bank / Stratford International"
        )
    )

    fun resolve(
        stationName: String,
        itemDestination: String?,
        itemTowards: String?,
        lineId: String?,
        platformName: String?
    ): String {
        val currentStation = StationNameFormatter.clean(stationName).lowercase()
        val rawDest = StationNameFormatter.clean(itemDestination ?: itemTowards ?: "Destination")
        val towardsLower = (itemTowards ?: "").lowercase()
        val platLower = (platformName ?: "").lowercase()
        val lineLower = (lineId ?: "").lowercase()

        val isTerminatingAtThisStation = rawDest.isNotBlank() && (
            rawDest.equals(currentStation, ignoreCase = true) ||
            rawDest.startsWith(currentStation, ignoreCase = true) ||
            currentStation.startsWith(rawDest, ignoreCase = true)
        )
        val isGenericCheckFront = rawDest.contains("check front of train", ignoreCase = true)

        if (!isTerminatingAtThisStation && !isGenericCheckFront) {
            return rawDest
        }

        // Check configured rules
        val matchedRule = RULES.firstOrNull { rule ->
            rule.stationMatcher(currentStation) && (rule.lineMatcher == null || rule.lineMatcher.invoke(lineLower))
        }

        if (matchedRule != null) {
            for (subCondition in matchedRule.subConditions) {
                if (subCondition.matches(towardsLower, platLower, lineLower)) {
                    return subCondition.destination
                }
            }
            return matchedRule.defaultDestination
        }

        // Fallback by platform direction if available
        return when {
            "southbound" in platLower -> "Southbound Services"
            "northbound" in platLower -> "Northbound Services"
            "eastbound" in platLower -> "Eastbound Services"
            "westbound" in platLower -> "Westbound Services"
            else -> "Outbound Services"
        }
    }
}
