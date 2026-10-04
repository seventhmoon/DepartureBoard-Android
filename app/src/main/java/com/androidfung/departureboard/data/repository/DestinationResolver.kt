package com.androidfung.departureboard.data.repository

/**
 * Resolves the outbound destination for terminus stations when TfL's prediction API reports
 * inbound terminating trains arriving at the station itself (e.g. Edgware, Mill Hill East, Brixton).
 */
internal object DestinationResolver {

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

        // The train terminates at this station or reports "Check Front of Train"; infer the departing destination for passengers on the platform
        return when {
            // Northern Line
            "edgware" in currentStation -> when {
                "via cx" in towardsLower || "charing cross" in towardsLower -> "Morden via Charing Cross"
                "via bank" in towardsLower || "bank" in towardsLower -> "Morden via Bank"
                "battersea" in towardsLower -> "Battersea Power Station"
                else -> "Morden / Battersea"
            }
            "mill hill east" in currentStation -> when {
                "battersea" in towardsLower -> "Battersea Power Station"
                "via cx" in towardsLower || "charing cross" in towardsLower -> "Battersea via Charing Cross"
                "via bank" in towardsLower || "bank" in towardsLower -> "Morden via Bank"
                else -> "Finchley Central"
            }
            "high barnet" in currentStation -> when {
                "via cx" in towardsLower || "charing cross" in towardsLower -> "Battersea Power Station"
                "via bank" in towardsLower || "bank" in towardsLower -> "Morden via Bank"
                else -> "Morden / Battersea"
            }
            "morden" in currentStation -> when {
                "via cx" in towardsLower || "charing cross" in towardsLower -> "Edgware via Charing Cross"
                "via bank" in towardsLower || "bank" in towardsLower -> "High Barnet via Bank"
                else -> "Edgware / High Barnet"
            }
            "battersea" in currentStation -> "High Barnet / Edgware via Charing Cross"

            // Victoria Line
            "brixton" in currentStation -> "Walthamstow Central"
            "walthamstow" in currentStation -> "Brixton"

            // Bakerloo Line
            "elephant & castle" in currentStation -> "Harrow & Wealdstone / Queen's Park"
            "harrow & wealdstone" in currentStation -> "Elephant & Castle"

            // Central Line
            "west ruislip" in currentStation -> "Epping via Bank"
            "ealing broadway" in currentStation -> when {
                "district" in lineLower -> "Upminster via Tower Hill"
                "elizabeth" in lineLower -> "Abbey Wood / Shenfield"
                else -> "Epping / Hainault"
            }
            "epping" in currentStation -> "West Ruislip / Ealing Broadway"
            "hainault" in currentStation -> "Central London via Newbury Park"
            "woodford" in currentStation -> "Central London via Hainault"

            // District & Circle Lines
            "wimbledon" in currentStation -> when {
                "edgware road" in towardsLower -> "Edgware Road via High Street Kensington"
                "tower hill" in towardsLower || "upminster" in towardsLower || "barking" in towardsLower -> "Upminster via Tower Hill"
                else -> "Upminster / Edgware Road"
            }
            "richmond" in currentStation -> when {
                "overground" in lineLower || "mildmay" in lineLower -> "Stratford via Highbury & Islington"
                else -> "Upminster via Tower Hill"
            }
            "upminster" in currentStation -> "Richmond / Ealing Broadway / Wimbledon"
            "edgware road" in currentStation -> when {
                "circle" in lineLower -> "Hammersmith via Tower Hill"
                else -> "Wimbledon via High Street Kensington"
            }
            "hammersmith" in currentStation -> when {
                "hammersmith" in lineLower -> "Barking via King's Cross"
                else -> "Edgware Road via Aldgate"
            }
            "barking" in currentStation -> when {
                "hammersmith" in lineLower -> "Hammersmith via King's Cross"
                else -> "Wimbledon / Richmond / Ealing Broadway"
            }

            // Jubilee Line
            "stanmore" in currentStation -> "Stratford via Central London"
            "stratford" in currentStation -> when {
                "jubilee" in lineLower -> "Stanmore via Central London"
                "central" in lineLower -> "West Ruislip / Ealing Broadway"
                "dlr" in lineLower -> "Lewisham / Woolwich Arsenal"
                else -> "Westbound Services"
            }
            "canary wharf" in currentStation && "jubilee" in lineLower -> when {
                "eastbound" in platLower || "platform 2" in platLower -> "Stratford via North Greenwich"
                else -> "Stanmore / Wembley Park"
            }

            // Metropolitan Line
            "aldgate" in currentStation -> "Uxbridge / Watford / Amersham"
            "amersham" in currentStation -> "Aldgate / Baker Street"
            "chesham" in currentStation -> "Aldgate / Baker Street"
            "watford" in currentStation -> "Aldgate / Baker Street"
            "uxbridge" in currentStation -> if ("piccadilly" in lineLower) "Cockfosters" else "Aldgate / Baker Street"

            // Piccadilly Line
            "cockfosters" in currentStation -> "Heathrow / Uxbridge"
            "heathrow terminal 4" in currentStation -> "Cockfosters via Central London"
            "heathrow terminal 5" in currentStation -> when {
                "elizabeth" in lineLower -> "Abbey Wood / Shenfield"
                else -> "Cockfosters via Central London"
            }

            // Waterloo & City Line
            "waterloo" in currentStation && ("waterloo" in lineLower || "city" in lineLower) -> "Bank"
            "bank" in currentStation && ("waterloo" in lineLower || "city" in lineLower) -> "Waterloo"

            // Elizabeth Line
            "reading" in currentStation -> "Abbey Wood / Shenfield"
            "shenfield" in currentStation -> "Reading / Heathrow Terminal 5"
            "abbey wood" in currentStation -> "Reading / Heathrow Terminal 5"

            // DLR
            "tower gateway" in currentStation -> "Beckton via Canary Wharf"
            "beckton" in currentStation -> "Tower Gateway / Bank"
            "lewisham" in currentStation -> "Bank / Stratford"
            "woolwich arsenal" in currentStation -> "Bank / Stratford International"

            // Fallback by platform direction if available
            "southbound" in platLower -> "Southbound Services"
            "northbound" in platLower -> "Northbound Services"
            "eastbound" in platLower -> "Eastbound Services"
            "westbound" in platLower -> "Westbound Services"
            else -> "Outbound Services"
        }
    }
}
