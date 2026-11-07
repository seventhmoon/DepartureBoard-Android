package com.androidfung.departureboard.data.model.gb

import com.androidfung.departureboard.data.model.Station
/**
 * Canonical TfL / NaPTAN station identifiers across Tube, DLR, and Elizabeth Line.
 * Provides a single source of truth for station IDs, eliminating brittle hardcoded string literals
 * and improving code clarity, maintainability, and compile-time verification.
 */
object TflStationIds {

    // Northern Line
    const val MILL_HILL_EAST = "940GZZLUMHL"
    const val FINCHLEY_CENTRAL = "940GZZLUFYC"
    const val EAST_FINCHLEY = "940GZZLUEFY"
    const val HIGH_BARNET = "940GZZLUHBT"
    const val EDGWARE = "940GZZLUEGW"
    const val MORDEN = "940GZZLUMDN"
    const val BATTERSEA_POWER_STATION = "940GZZBPSUST"
    const val BATTERSEA_POWER_STATION_ALT = "940GZZLUBPS"
    const val KENNINGTON = "940GZZLUKNG"
    const val CAMDEN_TOWN = "940GZZLUCTN"
    const val HIGHGATE = "940GZZLUHGT"
    const val ARCHWAY = "940GZZLUACY"
    const val TUFNELL_PARK = "940GZZLUTFP"
    const val KENTISH_TOWN = "940GZZLUKSH"
    const val CHARING_CROSS = "940GZZLUCHX"
    const val BANK = "940GZZLUBNK"

    // Victoria Line
    const val BRIXTON = "940GZZLUBXN"
    const val BRIXTON_ALT = "940GZZLUBRX"
    const val WALTHAMSTOW_CENTRAL = "940GZZLUWWL"
    const val WALTHAMSTOW_CENTRAL_ALT = "940GZZLUWAC"

    // Bakerloo Line
    const val ELEPHANT_AND_CASTLE = "940GZZLUEAC"
    const val ELEPHANT_AND_CASTLE_ALT = "940GZZLUEHA"
    const val HARROW_AND_WEALDSTONE = "940GZZLUHAW"

    // Waterloo & City Line
    const val WATERLOO = "940GZZLUWLO"

    // Jubilee Line
    const val STANMORE = "940GZZLUSTM"
    const val STRATFORD = "940GZZLUSTD"

    // Central Line
    const val WEST_RUISLIP = "940GZZLUWRP"
    const val EALING_BROADWAY = "940GZZLUEBY"
    const val EPPING = "940GZZLUEPG"
    const val HAINAULT = "940GZZLUHHL"
    const val WOODFORD = "940GZZLUWFD"

    // Piccadilly Line
    const val COCKFOSTERS = "940GZZLUCKF"
    const val UXBRIDGE = "940GZZLUUXB"
    const val HEATHROW_TERMINAL_5 = "940GZZLUHR5"
    const val HEATHROW_TERMINAL_4 = "940GZZLUHR4"
    const val HEATHROW_TERMINALS_123 = "940GZZLUHRC"

    // District & Circle Line
    const val UPMINSTER = "940GZZLUUPM"
    const val WIMBLEDON = "940GZZLUWBR"
    const val WIMBLEDON_ALT = "940GZZLUWIM"
    const val RICHMOND = "940GZZLURMD"
    const val EDGWARE_ROAD_CIRCLE = "940GZZLUERC"
    const val EDGWARE_ROAD_BAKERLOO = "940GZZLUERB"
    const val HAMMERSMITH = "940GZZLUHSC"
    const val HAMMERSMITH_DISTRICT = "940GZZLUHSD"
    const val BARKING = "940GZZLUBKG"

    // Metropolitan Line
    const val ALDGATE = "940GZZLUALD"
    const val AMERSHAM = "940GZZLUAMS"
    const val CHESHAM = "940GZZLUCSM"
    const val WATFORD = "940GZZLUWAF"
    const val WATFORD_ALT = "940GZZLUWFD"

    // Elizabeth Line
    const val ABBEY_WOOD = "910GABWD"
    const val ABBEY_WOOD_HUB = "HUBABW"
    const val SHENFIELD = "910GSHENFLD"
    const val READING = "910GREADING"

    // DLR
    const val TOWER_GATEWAY = "940GZZDLTWR"
    const val BECKTON = "940GZZDLBCK"
    const val LEWISHAM = "940GZZDLLEW"
    const val WOOLWICH_ARSENAL = "940GZZDLWLA"

    // National Rail & Hubs
    const val ST_PANCRAS_INTERNATIONAL = "910GSTPX"
    const val KINGS_CROSS_NR = "910GKNGX"
    const val KINGS_CROSS_ST_PANCRAS_HUB = "HUBKGX"
    const val WEMBLEY_PARK = "940GZZLUWYP"

    /**
     * Complete mapping of TfL Interchange Hub IDs to their constituent platform NaPTAN stop IDs.
     * This provides deterministic code-based resolution for multi-modal interchanges (e.g. HUBBAN -> Bank & Monument).
     */
    val HUB_TO_PLATFORM_NAPTAN: Map<String, List<String>> = mapOf(
        "HUBBAN" to listOf("940GZZLUBNK", "940GZZLUMMT", "940GZZDLBNK"), // Bank (Central/Northern/W&C) + Monument (District/Circle) + DLR
        "HUBKGX" to listOf("940GZZLUKSX", "910GKNGX", "910GSTPX"),       // King's Cross St. Pancras (Tube + King's Cross NR + St Pancras Int)
        "HUBPAD" to listOf("940GZZLUPAC", "940GZZLUPAH", "910GPADTON"),  // Paddington (Bakerloo/District/Circle + H&C + NR/Elizabeth)
        "HUBVIC" to listOf("940GZZLUVIC", "910GVICTRIC"),                // Victoria (Victoria/District/Circle + NR)
        "HUBWAT" to listOf("940GZZLUWLO", "910GWATERLO"),                // Waterloo (Bakerloo/Jubilee/Northern/W&C + NR)
        "HUBLST" to listOf("940GZZLULVT", "910GLIVST"),                  // Liverpool Street (Central/Circle/H&C/Met + Elizabeth/Overground/NR)
        "HUBZFD" to listOf("940GZZLUFCN", "910GFARNDN"),                 // Farringdon (Circle/H&C/Met + Elizabeth + Thameslink)
        "HUBSRA" to listOf("940GZZLUSTD", "940GZZDLSTR", "910GSTFD"),    // Stratford (Central/Jubilee + DLR + Elizabeth/Overground/NR)
        "HUBCAW" to listOf("940GZZLUCYF", "940GZZDLCAN", "910GCANWHRF"), // Canary Wharf (Jubilee + DLR + Elizabeth line)
        "HUBWMB" to listOf("940GZZLUWIM", "940GZZCRWMB", "910GWIMBLDN"), // Wimbledon (District + Tramlink + NR)
        "HUBWEH" to listOf("940GZZLUWHM", "940GZZDLWHM", "910GWHAMHL"),  // West Ham (District/H&C/Jubilee + DLR + c2c)
        "HUBWHD" to listOf("940GZZLUWHP", "910GWHMDSTD", "910GWSTHMPD", "910GWSTHMPT"), // West Hampstead (Jubilee + Overground + Thameslink)
        "HUBWFJ" to listOf("910GWATFDJ", "910GWATFJDC"),                 // Watford Junction (NR + London Overground DC)
        "HUBABW" to listOf("910GABWD"),                                  // Abbey Wood (Elizabeth line + Southeastern)
        "HUBBFR" to listOf("940GZZLUBKF", "910GBLKFRS"),                 // Blackfriars (District/Circle + Thameslink)
        "HUBCST" to listOf("940GZZLUCST", "910GCANONST"),                // Cannon Street (District/Circle + Southeastern)
        "HUBCHX" to listOf("940GZZLUCHX", "910GCHRX"),                   // Charing Cross (Bakerloo/Northern + Southeastern)
        "HUBEUF" to listOf("940GZZLUEUS", "910GEUSTON"),                 // Euston (Northern/Victoria + NR/Overground)
        "HUBFCH" to listOf("910GFNCHSTR", "940GZZLUTWH"),                // Fenchurch Street / Tower Hill
        "HUBLBG" to listOf("940GZZLULNB", "910GLNDNBDC"),                // London Bridge (Jubilee/Northern + Thameslink/Southern/Southeastern)
        "HUBMYB" to listOf("940GZZLUMYB", "910GMARYLBN"),                // Marylebone (Bakerloo + Chiltern)
        "HUBVAU" to listOf("940GZZLUVAU", "910GVAUXHL")                  // Vauxhall (Victoria + SWR)
    )

    /**
     * Resolves all platform NaPTAN stop IDs for a given station or hub ID.
     * If the ID is a known Hub (e.g. "HUBBAN"), returns its list of platform IDs.
     * Otherwise, returns a single-element list containing the original ID.
     */
    fun resolvePlatformNaptanIds(stationOrHubId: String?): List<String> {
        if (stationOrHubId.isNullOrBlank()) return emptyList()
        val upper = stationOrHubId.uppercase().trim()
        val mapped = HUB_TO_PLATFORM_NAPTAN[upper]
        return if (!mapped.isNullOrEmpty()) mapped else listOf(upper)
    }

    /**
     * Returns true if the station represents St Pancras International or King's Cross National Rail.
     */
    fun isStPancrasOrKingsCrossNR(stationId: String?, stationName: String? = null): Boolean {
        val idUpper = stationId?.uppercase()?.trim().orEmpty()
        if (idUpper == ST_PANCRAS_INTERNATIONAL || idUpper == KINGS_CROSS_NR) return true
        if (stationName != null) {
            val lower = stationName.lowercase().trim()
            if (lower == "st pancras international" || lower == "st pancras" || lower == "king's cross") return true
        }
        return false
    }

    /**
     * Returns true if the station is Wembley Park.
     */
    fun isWembleyPark(stationId: String?, stationName: String? = null): Boolean {
        val idUpper = stationId?.uppercase()?.trim().orEmpty()
        if (idUpper == WEMBLEY_PARK) return true
        if (stationName != null && "wembley park" in stationName.lowercase()) return true
        return false
    }

    /**
     * Map of canonical terminus station name (normalized lowercase) to its primary NaPTAN ID.
     */
    val CANONICAL_TERMINALS_BY_NAME: Map<String, String> = mapOf(
        "finchley central" to FINCHLEY_CENTRAL,
        "battersea power station" to BATTERSEA_POWER_STATION,
        "morden" to MORDEN,
        "edgware" to EDGWARE,
        "high barnet" to HIGH_BARNET,
        "mill hill east" to MILL_HILL_EAST,
        "kennington" to KENNINGTON,
        "east finchley" to EAST_FINCHLEY,
        "camden town" to CAMDEN_TOWN,
        "walthamstow central" to WALTHAMSTOW_CENTRAL,
        "brixton" to BRIXTON,
        "stanmore" to STANMORE,
        "stratford" to STRATFORD,
        "elephant & castle" to ELEPHANT_AND_CASTLE,
        "elephant and castle" to ELEPHANT_AND_CASTLE,
        "harrow & wealdstone" to HARROW_AND_WEALDSTONE,
        "harrow and wealdstone" to HARROW_AND_WEALDSTONE,
        "waterloo" to WATERLOO,
        "bank" to BANK,
        "cockfosters" to COCKFOSTERS,
        "uxbridge" to UXBRIDGE,
        "epping" to EPPING,
        "west ruislip" to WEST_RUISLIP,
        "ealing broadway" to EALING_BROADWAY,
        "upminster" to UPMINSTER,
        "wimbledon" to WIMBLEDON,
        "richmond" to RICHMOND,
        "edgware road" to EDGWARE_ROAD_CIRCLE,
        "hammersmith" to HAMMERSMITH,
        "barking" to BARKING,
        "aldgate" to ALDGATE,
        "abbey wood" to ABBEY_WOOD,
        "shenfield" to SHENFIELD,
        "reading" to READING,
        "tower gateway" to TOWER_GATEWAY,
        "beckton" to BECKTON,
        "lewisham" to LEWISHAM,
        "woolwich arsenal" to WOOLWICH_ARSENAL
    )

    /**
     * Reverse map of primary NaPTAN ID to canonical station display name.
     */
    val CANONICAL_NAMES_BY_ID: Map<String, String> = mapOf(
        BATTERSEA_POWER_STATION to "Battersea Power Station",
        BATTERSEA_POWER_STATION_ALT to "Battersea Power Station",
        MORDEN to "Morden",
        FINCHLEY_CENTRAL to "Finchley Central",
        EAST_FINCHLEY to "East Finchley",
        KENNINGTON to "Kennington",
        CAMDEN_TOWN to "Camden Town",
        EDGWARE to "Edgware",
        HIGH_BARNET to "High Barnet",
        MILL_HILL_EAST to "Mill Hill East",
        WALTHAMSTOW_CENTRAL to "Walthamstow Central",
        WALTHAMSTOW_CENTRAL_ALT to "Walthamstow Central",
        BRIXTON to "Brixton",
        BRIXTON_ALT to "Brixton",
        STANMORE to "Stanmore",
        STRATFORD to "Stratford",
        ELEPHANT_AND_CASTLE to "Elephant & Castle",
        ELEPHANT_AND_CASTLE_ALT to "Elephant & Castle",
        HARROW_AND_WEALDSTONE to "Harrow & Wealdstone",
        WATERLOO to "Waterloo",
        BANK to "Bank",
        COCKFOSTERS to "Cockfosters",
        UXBRIDGE to "Uxbridge",
        EPPING to "Epping",
        WEST_RUISLIP to "West Ruislip",
        EALING_BROADWAY to "Ealing Broadway",
        UPMINSTER to "Upminster",
        WIMBLEDON to "Wimbledon",
        WIMBLEDON_ALT to "Wimbledon",
        RICHMOND to "Richmond",
        EDGWARE_ROAD_CIRCLE to "Edgware Road",
        EDGWARE_ROAD_BAKERLOO to "Edgware Road",
        HAMMERSMITH to "Hammersmith",
        HAMMERSMITH_DISTRICT to "Hammersmith",
        BARKING to "Barking",
        ALDGATE to "Aldgate",
        ABBEY_WOOD to "Abbey Wood",
        ABBEY_WOOD_HUB to "Abbey Wood",
        SHENFIELD to "Shenfield",
        READING to "Reading",
        TOWER_GATEWAY to "Tower Gateway",
        BECKTON to "Beckton",
        LEWISHAM to "Lewisham",
        WOOLWICH_ARSENAL to "Woolwich Arsenal"
    )
}
