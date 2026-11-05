package com.androidfung.departureboard.data.model.gb

import com.androidfung.departureboard.data.model.CallingPoint
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.model.gb.topology.ElizabethLineRoute
import com.androidfung.departureboard.data.model.gb.topology.ThameslinkRoute
import com.androidfung.departureboard.data.topology.LinearRouteCallingPointsResolver
import com.androidfung.departureboard.data.topology.RouteStation
import com.androidfung.departureboard.data.topology.TransitLineNetwork
import java.time.Instant
import java.time.ZoneId

/**
 * Unified UK (`GB`) route topology implementing [TransitLineNetwork] using canonical station codes
 * (3-letter TfL / CRS / Metro codes) across all lines:
 * - London Underground (Bakerloo, Central, Circle [spiral topology, NOT a closed loop], District,
 *   Hammersmith & City, Jubilee, Metropolitan, Northern, Piccadilly, Victoria, Waterloo & City)
 * - Elizabeth line (via [ElizabethLineRoute])
 * - 6 Named London Overground lines (Liberty, Lioness, Mildmay, Suffragette, Weaver, Windrush)
 * - DLR, Thameslink (via [ThameslinkRoute]), and Tyne & Wear Metro (Green & Yellow lines).
 */
object GbTransitRoutes : TransitLineNetwork {

    override val timeZone: ZoneId = ZoneId.of("Europe/London")
    override val defaultMode: String = TransitMode.TUBE.id
    override val averageSecondsBetweenStops: Long = 120L

    /**
     * Only Tyne & Wear Metro Yellow Line (`METRO-YELLOW`) is a continuous loop in the UK network.
     * London Underground Circle line (`CIRCLE`) is a linear spiral terminating at Hammersmith and Edgware Road.
     */
    override val loopLineCodes: Set<String> = setOf("METRO-YELLOW")

    /**
     * Canonical mapping from UK 3-letter station codes (TfL Tube, Overground CRS, DLR, Nexus Metro) to station display names.
     */
    val STATION_NAMES_BY_CODE: Map<String, String> = buildMap {
        // Seed from ElizabethLineRoute and ThameslinkRoute
        for ((name, crs) in ElizabethLineRoute.STATIONS + ThameslinkRoute.STATIONS) {
            crs?.uppercase()?.let { put(it, name) }
        }

        putAll(
            mapOf(
                // Victoria Line
                "WWL" to "Walthamstow Central", "BHR" to "Blackhorse Road", "TTH" to "Tottenham Hale",
                "SVS" to "Seven Sisters", "FPK" to "Finsbury Park", "HBY" to "Highbury & Islington",
                "KXX" to "King's Cross St. Pancras", "EUS" to "Euston", "WRR" to "Warren Street",
                "OXC" to "Oxford Circus", "GPK" to "Green Park", "VIC" to "Victoria",
                "PIM" to "Pimlico", "VUX" to "Vauxhall", "STK" to "Stockwell", "BXN" to "Brixton",

                // Waterloo & City / Bakerloo
                "BNK" to "Bank", "WLO" to "Waterloo",
                "HAW" to "Harrow & Wealdstone", "KNT" to "Kenton", "SKN" to "South Kenton",
                "NWM" to "North Wembley", "WMB" to "Wembley Central", "SBP" to "Stonebridge Park",
                "HDN" to "Harlesden", "WIJ" to "Willesden Junction", "KGN" to "Kensal Green",
                "QPK" to "Queen's Park", "KPK" to "Kilburn Park", "MDV" to "Maida Vale",
                "WKA" to "Warwick Avenue", "PAD" to "Paddington", "EDG" to "Edgware Road",
                "MYB" to "Marylebone", "BST" to "Baker Street", "RGP" to "Regent's Park",
                "PIC" to "Piccadilly Circus", "CHX" to "Charing Cross", "EMB" to "Embankment",
                "LAM" to "Lambeth North", "EAC" to "Elephant & Castle",

                // Jubilee Line
                "STM" to "Stanmore", "CPK" to "Canons Park", "QBY" to "Queensbury",
                "KBY" to "Kingsbury", "WPK" to "Wembley Park", "NSD" to "Neasden",
                "DLH" to "Dollis Hill", "WLG" to "Willesden Green", "KBN" to "Kilburn",
                "WHP" to "West Hampstead", "FIN" to "Finchley Road", "SWC" to "Swiss Cottage",
                "SJW" to "St. John's Wood", "BND" to "Bond Street", "WMS" to "Westminster",
                "SWK" to "Southwark", "LBG" to "London Bridge", "BMO" to "Bermondsey",
                "CWR" to "Canada Water", "CWF" to "Canary Wharf", "NGW" to "North Greenwich",
                "CNT" to "Canning Town", "WHM" to "West Ham", "SRA" to "Stratford",

                // Circle & Hammersmith & City & District & Metropolitan shared codes
                "HSC" to "Hammersmith", "GHK" to "Goldhawk Road", "SBM" to "Shepherd's Bush Market",
                "WDL" to "Wood Lane", "LTR" to "Latimer Road", "LBG_W" to "Ladbroke Grove",
                "WBP" to "Westbourne Park", "RYO" to "Royal Oak", "GPS" to "Great Portland Street",
                "ESQ" to "Euston Square", "FDN" to "Farringdon", "BAR" to "Barbican",
                "MGT" to "Moorgate", "LST" to "Liverpool Street", "ALD" to "Aldgate",
                "TWH" to "Tower Hill", "MON" to "Monument", "CST" to "Cannon Street",
                "MAN" to "Mansion House", "BFR" to "Blackfriars", "TMP" to "Temple",
                "SJP" to "St. James's Park", "SSQ" to "Sloane Square", "SKN_D" to "South Kensington",
                "GLR" to "Gloucester Road", "HSK" to "High Street Kensington", "NHG" to "Notting Hill Gate",
                "BAY" to "Bayswater", "ALE" to "Aldgate East", "WPL" to "Whitechapel",
                "STG" to "Stepney Green", "MLE" to "Mile End", "BWR" to "Bow Road",
                "BBB" to "Bromley-by-Bow", "PLW" to "Plaistow", "UPK" to "Upton Park",
                "EHM" to "East Ham", "BKG" to "Barking", "UPN" to "Upney",
                "BEC" to "Becontree", "DGH" to "Dagenham Heathway", "DGE" to "Dagenham East",
                "ELM" to "Elm Park", "HCH" to "Hornchurch", "UPB" to "Upminster Bridge",
                "UPM" to "Upminster", "ECT" to "Earl's Court", "WKN" to "West Kensington",
                "BCT" to "Barons Court", "RVP" to "Ravenscourt Park", "STB" to "Stamford Brook",
                "TGR" to "Turnham Green", "CHP" to "Chiswick Park", "ACT" to "Acton Town",
                "ECM" to "Ealing Common", "EBY" to "Ealing Broadway", "WBT" to "West Brompton",
                "FBY" to "Fulham Broadway", "PGR" to "Parsons Green", "PUT" to "Putney Bridge",
                "EPY" to "East Putney", "SFD" to "Southfields", "WMP" to "Wimbledon Park",
                "WIM" to "Wimbledon", "PRD" to "Preston Road", "NWP" to "Northwick Park",
                "HOH" to "Harrow-on-the-Hill", "NHR" to "North Harrow", "PIN" to "Pinner",
                "NWH" to "Northwood Hills", "NWD" to "Northwood", "MPK" to "Moor Park",
                "RKW" to "Rickmansworth", "CWD" to "Chorleywood", "CLF" to "Chalfont & Latimer",
                "AMS" to "Amersham",

                // Central Line
                "WRP" to "West Ruislip", "RSG" to "Ruislip Gardens", "SRP" to "South Ruislip",
                "NHT" to "Northolt", "GFD" to "Greenford", "PVL" to "Perivale",
                "HGR" to "Hanger Lane", "NAC" to "North Acton", "EAC_C" to "East Acton",
                "WCT" to "White City", "SBU" to "Shepherd's Bush", "HPK" to "Holland Park",
                "QWY" to "Queensway", "LGT" to "Lancaster Gate", "MBA" to "Marble Arch",
                "TCR" to "Tottenham Court Road", "HOL" to "Holborn", "CHL" to "Chancery Lane",
                "STP_U" to "St. Paul's", "BGR" to "Bethnal Green", "LEY" to "Leyton",
                "LYS" to "Leytonstone", "SNB" to "Snaresbrook", "SWF" to "South Woodford",
                "WFD" to "Woodford", "BKH" to "Buckhurst Hill", "LGN" to "Loughton",
                "DBD" to "Debden", "THB" to "Theydon Bois", "EPG" to "Epping",
                "WAC" to "West Acton", "WAN" to "Wanstead", "RDB" to "Redbridge",
                "GTH" to "Gants Hill", "NBP" to "Newbury Park", "BKS" to "Barkingside",
                "FLP" to "Fairlop", "HHL" to "Hainault",

                // Northern Line
                "HBT" to "High Barnet", "TOT" to "Totteridge & Whetstone", "WSP" to "Woodside Park",
                "WFY" to "West Finchley", "FYC" to "Finchley Central", "EFY" to "East Finchley",
                "HGT" to "Highgate", "ACY" to "Archway", "TFP" to "Tufnell Park",
                "KSH" to "Kentish Town", "CTN" to "Camden Town", "ANG" to "Angel",
                "OLD" to "Old Street", "BOR" to "Borough", "KNG" to "Kennington",
                "OVL" to "Oval", "CPN" to "Clapham North", "CPC" to "Clapham Common",
                "CPS" to "Clapham South", "BAL" to "Balham", "TBC" to "Tooting Bec",
                "TBY" to "Tooting Broadway", "CLW" to "Colliers Wood", "SWM" to "South Wimbledon",
                "MDN" to "Morden", "MCR" to "Mornington Crescent", "GDG" to "Goodge Street",
                "LSQ" to "Leicester Square", "NEL" to "Nine Elms", "BPS" to "Battersea Power Station",
                "EGW" to "Edgware", "BTO" to "Burnt Oak", "CND" to "Colindale",
                "HNC" to "Hendon Central", "BTX" to "Brent Cross", "GGR" to "Golders Green",
                "HMP" to "Hampstead", "BZP" to "Belsize Park", "CFM" to "Chalk Farm",
                "MHE" to "Mill Hill East",

                // Piccadilly Line
                "CKF" to "Cockfosters", "OAK" to "Oakwood", "SGT" to "Southgate",
                "AGR" to "Arnos Grove", "BGR_P" to "Bounds Green", "WGR" to "Wood Green",
                "TPL" to "Turnpike Lane", "MNR" to "Manor House", "ASL" to "Arsenal",
                "HRD" to "Holloway Road", "CAR" to "Caledonian Road", "RSQ" to "Russell Square",
                "COV" to "Covent Garden", "HPC" to "Hyde Park Corner", "KNB" to "Knightsbridge",
                "SEA" to "South Ealing", "NFD" to "Northfields", "BSM" to "Boston Manor",
                "OST" to "Osterley", "HNE" to "Hounslow East", "HNC_P" to "Hounslow Central",
                "HNW" to "Hounslow West", "HTX" to "Hatton Cross", "HR2" to "Heathrow Terminals 2 & 3",
                "HR5" to "Heathrow Terminal 5",

                // London Overground
                "ROM" to "Romford", "EMP" to "Emerson Park",
                "WFJ" to "Watford Junction", "WFH" to "Watford High Street", "BSH" to "Bushey",
                "CPP" to "Carpenders Park", "HTE" to "Hatch End", "HDL" to "Headstone Lane",
                "KHR" to "Kilburn High Road", "SHP" to "South Hampstead",
                "RMD" to "Richmond", "KWG" to "Kew Gardens", "GUN" to "Gunnersbury",
                "SAC_O" to "South Acton", "ACC" to "Acton Central", "KNR" to "Kensal Rise",
                "BBP" to "Brondesbury Park", "BSY" to "Brondesbury", "FRF" to "Finchley Road & Frognal",
                "HHD" to "Hampstead Heath", "GPO" to "Gospel Oak", "KTW" to "Kentish Town West",
                "CMD" to "Camden Road", "CIR_O" to "Caledonian Road & Barnsbury", "CNN" to "Canonbury",
                "DLK" to "Dalston Kingsland", "HKC" to "Hackney Central", "HMN" to "Homerton",
                "HKW" to "Hackney Wick", "UHL" to "Upper Holloway", "CRH" to "Crouch Hill",
                "HGL" to "Harringay Green Lanes", "STO" to "South Tottenham",
                "WQR" to "Walthamstow Queen's Road", "LMR" to "Leyton Midland Road",
                "LHR" to "Leytonstone High Road", "WNP" to "Wanstead Park", "WGP" to "Woodgrange Park",
                "BGV" to "Barking Riverside", "CBH" to "Cambridge Heath", "LOF" to "London Fields",
                "HKD" to "Hackney Downs", "RTR_O" to "Rectory Road", "SKW" to "Stoke Newington",
                "SMH" to "Stamford Hill", "BCG" to "Bruce Grove", "WHL" to "White Hart Lane",
                "SLV" to "Silver Street", "EDR" to "Edmonton Green", "SBU_O" to "Southbury",
                "TKY" to "Turkey Street", "TEO" to "Theobalds Grove", "CHH" to "Cheshunt",
                "DLJ" to "Dalston Junction", "HGG" to "Haggerston", "HOX" to "Hoxton",
                "SDC" to "Shoreditch High Street", "SDE" to "Shadwell", "WPE" to "Wapping",
                "RTH" to "Rotherhithe", "SQE" to "Surrey Quays", "NXG" to "New Cross Gate",
                "BCY" to "Brockley", "HOP" to "Honor Oak Park", "FOH" to "Forest Hill",
                "SYD" to "Sydenham", "CYP" to "Crystal Palace", "NWD_O" to "Norwood Junction",
                "WCY" to "West Croydon",

                // DLR
                "LIM" to "Limehouse", "WFE" to "Westferry", "POP" to "Poplar",
                "BLA" to "Blackwall", "EIN" to "East India", "RVC" to "Royal Victoria",
                "CUS" to "Custom House", "PRR" to "Prince Regent", "RAL" to "Royal Albert",
                "BEP" to "Beckton Park", "CYP_D" to "Cyprus", "GAL" to "Gallions Reach",
                "BCK" to "Beckton",

                // Tyne and Wear Metro
                "SHL" to "South Hylton", "PAL" to "Pallion", "MLF" to "Millfield",
                "UNI" to "University", "PLI" to "Park Lane", "SUN" to "Sunderland",
                "STP_M" to "St Peter's", "SOL" to "Stadium of Light", "SBN" to "Seaburn",
                "EBO" to "East Boldon", "BKW" to "Brockley Whins", "FEL" to "Fellgate",
                "PLW_M" to "Pelaw", "HEW" to "Heworth", "FLG" to "Felling",
                "GHD" to "Gateshead Stadium", "GAT" to "Gateshead", "CEN_M" to "Central Station",
                "MTS" to "Monument", "HAY_M" to "Haymarket", "JES" to "Jesmond",
                "WJS" to "West Jesmond", "ILF_M" to "Ilford Road", "SGF" to "South Gosforth",
                "REG" to "Regent Centre", "WBR" to "Wansbeck Road", "FAW" to "Fawdon",
                "KGP" to "Kingston Park", "BKF" to "Bank Foot", "CLP" to "Callerton Parkway",
                "AIR_M" to "Airport", "STJ" to "St James", "MAN_M" to "Manors",
                "BYK" to "Byker", "CHR" to "Chillingham Road", "WKG" to "Walkergate",
                "WLS" to "Wallsend", "HAD" to "Hadrian Road", "HWD" to "Howdon",
                "PCM" to "Percy Main", "MDW" to "Meadow Well", "NSH" to "North Shields",
                "TYN" to "Tynemouth", "CUL" to "Cullercoats", "WTL" to "Whitley Bay",
                "MSN" to "Monkseaton", "WMS_M" to "West Monkseaton", "SHM" to "Shiremoor",
                "PMV" to "Palmersville", "Nbp_M" to "Northumberland Park", "BTN_M" to "Benton",
                "FLE" to "Four Lane Ends", "LBN" to "Longbenton", "HEB" to "Hebburn",
                "JAR" to "Jarrow", "BDE" to "Bede", "SMD" to "Simonside",
                "TDK" to "Tyne Dock", "CHI" to "Chichester", "SSS" to "South Shields"
            )
        )
    }

    private val routeStationsByCode: Map<String, RouteStation> by lazy {
        buildMap {
            for (station in ElizabethLineRoute.STATIONS + ThameslinkRoute.STATIONS) {
                station.crs?.uppercase()?.let { putIfAbsent(it, station) }
                station.naptanId?.uppercase()?.let { putIfAbsent(it, station) }
                putIfAbsent(station.name.uppercase(), station)
            }
            for ((nameLower, naptanId) in TflStationIds.CANONICAL_TERMINALS_BY_NAME) {
                val displayName = TflStationIds.CANONICAL_NAMES_BY_ID[naptanId]
                    ?: nameLower.split(" ").joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
                val rs = RouteStation(name = displayName, naptanId = naptanId)
                putIfAbsent(naptanId.uppercase(), rs)
                putIfAbsent(displayName.uppercase(), rs)
            }
        }
    }

    /**
     * Ordered station code sequences for all UK lines.
     */
    override val lineSequences: Map<String, List<String>> = mapOf(
        // Victoria Line
        "VICTORIA" to listOf(
            "WWL", "BHR", "TTH", "SVS", "FPK", "HBY", "KXX", "EUS",
            "WRR", "OXC", "GPK", "VIC", "PIM", "VUX", "STK", "BXN"
        ),

        // Waterloo & City Line
        "WATERLOO-CITY" to listOf("BNK", "WLO"),

        // Bakerloo Line
        "BAKERLOO" to listOf(
            "HAW", "KNT", "SKN", "NWM", "WMB", "SBP", "HDN", "WIJ", "KGN", "QPK",
            "KPK", "MDV", "WKA", "PAD", "EDG", "MYB", "BST", "RGP", "OXC", "PIC",
            "CHX", "EMB", "WLO", "LAM", "EAC"
        ),

        // Jubilee Line
        "JUBILEE" to listOf(
            "STM", "CPK", "QBY", "KBY", "WPK", "NSD", "DLH", "WLG", "KBN", "WHP",
            "FIN", "SWC", "SJW", "BST", "BND", "GPK", "WMS", "WLO", "SWK", "LBG",
            "BMO", "CWR", "CWF", "NGW", "CNT", "WHM", "SRA"
        ),

        // Circle Line (Spiral topology: HSC -> EDG -> KXX -> LST -> TWH -> VIC -> HSK -> PAD -> EDG)
        "CIRCLE" to listOf(
            "HSC", "GHK", "SBM", "WDL", "LTR", "LBG_W", "WBP", "RYO", "PAD", "EDG",
            "BST", "GPS", "ESQ", "KXX", "FDN", "BAR", "MGT", "LST", "ALD", "TWH",
            "MON", "CST", "MAN", "BFR", "TMP", "EMB", "WMS", "SJP", "VIC", "SSQ",
            "SKN_D", "GLR", "HSK", "NHG", "BAY", "PAD", "EDG"
        ),

        // Hammersmith & City Line
        "HAMMERSMITH-CITY" to listOf(
            "HSC", "GHK", "SBM", "WDL", "LTR", "LBG_W", "WBP", "RYO", "PAD", "EDG",
            "BST", "GPS", "ESQ", "KXX", "FDN", "BAR", "MGT", "LST", "ALE", "WPL",
            "STG", "MLE", "BWR", "BBB", "WHM", "PLW", "UPK", "EHM", "BKG"
        ),

        // Central Line
        "CENTRAL" to listOf(
            "WRP", "RSG", "SRP", "NHT", "GFD", "PVL", "HGR", "NAC", "EAC_C", "WCT",
            "SBU", "HPK", "NHG", "QWY", "LGT", "MBA", "BND", "OXC", "TCR", "HOL",
            "CHL", "STP_U", "BNK", "LST", "BGR", "MLE", "SRA", "LEY", "LYS", "SNB",
            "SWF", "WFD", "BKH", "LGN", "DBD", "THB", "EPG"
        ),
        "CENTRAL_EALING" to listOf(
            "EBY", "WAC", "NAC", "EAC_C", "WCT", "SBU", "HPK", "NHG", "QWY", "LGT",
            "MBA", "BND", "OXC", "TCR", "HOL", "CHL", "STP_U", "BNK", "LST", "BGR",
            "MLE", "SRA", "LEY", "LYS", "WAN", "RDB", "GTH", "NBP", "BKS", "FLP", "HHL"
        ),

        // Northern Line branches
        "NORTHERN" to listOf(
            "HBT", "TOT", "WSP", "WFY", "FYC", "EFY", "HGT", "ACY", "TFP", "KSH",
            "CTN", "EUS", "KXX", "ANG", "OLD", "MGT", "BNK", "LBG", "BOR", "EAC",
            "KNG", "OVL", "STK", "CPN", "CPC", "CPS", "BAL", "TBC", "TBY", "CLW", "SWM", "MDN"
        ),
        "NORTHERN_CX_BATTERSEA" to listOf(
            "FYC", "EFY", "HGT", "ACY", "TFP", "KSH", "CTN", "MCR", "EUS", "WRR",
            "GDG", "TCR", "LSQ", "CHX", "EMB", "WLO", "KNG", "NEL", "BPS"
        ),
        "NORTHERN_EDGWARE_CX" to listOf(
            "EGW", "BTO", "CND", "HNC", "BTX", "GGR", "HMP", "BZP", "CFM", "CTN",
            "MCR", "EUS", "WRR", "GDG", "TCR", "LSQ", "CHX", "EMB", "WLO", "KNG",
            "OVL", "STK", "CPN", "CPC", "CPS", "BAL", "TBC", "TBY", "CLW", "SWM", "MDN"
        ),
        "NORTHERN_MHE" to listOf(
            "MHE", "FYC", "EFY", "HGT", "ACY", "TFP", "KSH", "CTN", "EUS", "KXX",
            "ANG", "OLD", "MGT", "BNK", "LBG", "BOR", "EAC", "KNG", "NEL", "BPS"
        ),

        // Piccadilly Line
        "PICCADILLY" to listOf(
            "CKF", "OAK", "SGT", "AGR", "BGR_P", "WGR", "TPL", "MNR", "FPK", "ASL",
            "HRD", "CAR", "KXX", "RSQ", "HOL", "COV", "LSQ", "PIC", "GPK", "HPC",
            "KNB", "SKN_D", "GLR", "ECT", "BCT", "HSC", "TGR", "ACT", "SEA", "NFD",
            "BSM", "OST", "HNE", "HNC_P", "HNW", "HTX", "HR2", "HR5"
        ),

        // District Line
        "DISTRICT" to listOf(
            "UPM", "UPB", "HCH", "ELM", "DGE", "DGH", "BEC", "UPN", "BKG", "EHM",
            "UPK", "PLW", "WHM", "BBB", "BWR", "MLE", "STG", "WPL", "ALE", "TWH",
            "MON", "CST", "MAN", "BFR", "TMP", "EMB", "WMS", "SJP", "VIC", "SSQ",
            "SKN_D", "GLR", "ECT", "WKN", "BCT", "HSC", "RVP", "STB", "TGR", "CHP",
            "ACT", "ECM", "EBY"
        ),
        "DISTRICT_WIMBLEDON" to listOf(
            "TWH", "MON", "CST", "MAN", "BFR", "TMP", "EMB", "WMS", "SJP", "VIC",
            "SSQ", "SKN_D", "GLR", "ECT", "WBT", "FBY", "PGR", "PUT", "EPY", "SFD",
            "WMP", "WIM"
        ),

        // Metropolitan Line
        "METROPOLITAN" to listOf(
            "ALD", "LST", "MGT", "BAR", "FDN", "KXX", "ESQ", "GPS", "BST", "FIN",
            "WPK", "PRD", "NWP", "HOH", "NHR", "PIN", "NWH", "NWD", "MPK", "RKW",
            "CWD", "CLF", "AMS"
        ),

        // Elizabeth Line (3-letter CRS codes from ElizabethLineRoute)
        "ELIZABETH-LINE" to ElizabethLineRoute.STATIONS.mapNotNull { it.crs },
        "ELIZABETH" to ElizabethLineRoute.STATIONS.mapNotNull { it.crs },

        // 6 Named London Overground Lines (3-letter CRS codes)
        "LIBERTY" to listOf("ROM", "EMP", "UPM"),
        "LIONESS" to listOf(
            "WFJ", "WFH", "BSH", "CPP", "HTE", "HDL", "HAW", "KNT", "SKN",
            "NWM", "WMB", "SBP", "HDN", "WIJ", "KGN", "QPK", "KHR", "SHP", "EUS"
        ),
        "MILDMAY" to listOf(
            "RMD", "KWG", "GUN", "SAC_O", "ACC", "WIJ", "KNR", "BBP", "BSY",
            "WHP", "FRF", "HHD", "GPO", "KTW", "CMD", "CIR_O", "HBY", "CNN",
            "DLK", "HKC", "HMN", "HKW", "SRA"
        ),
        "SUFFRAGETTE" to listOf(
            "GPO", "UHL", "CRH", "HGL", "STO", "BHR", "WQR", "LMR", "LHR",
            "WNP", "WGP", "BKG", "BGV"
        ),
        "WEAVER" to listOf(
            "LST", "BGR", "CBH", "LOF", "HKD", "RTR_O", "SKW", "SMH", "SVS",
            "BCG", "WHL", "SLV", "EDR", "SBU_O", "TKY", "TEO", "CHH"
        ),
        "WINDRUSH" to listOf(
            "HBY", "CNN", "DLJ", "HGG", "HOX", "SDC", "WPL", "SDE", "WPE",
            "RTH", "CWR", "SQE", "NXG", "BCY", "HOP", "FOH", "SYD", "CYP", "NWD_O", "WCY"
        ),

        // DLR
        "DLR" to listOf(
            "BNK", "SDE", "LIM", "WFE", "POP", "BLA", "EIN", "CNT",
            "RVC", "CUS", "PRR", "RAL", "BEP", "CYP_D", "GAL", "BCK"
        ),

        // Thameslink (3-letter CRS codes from ThameslinkRoute)
        "THAMESLINK" to ThameslinkRoute.STATIONS.mapNotNull { it.crs },

        // Tyne and Wear Metro (3-letter Nexus Metro codes)
        "METRO-GREEN" to listOf(
            "SHL", "PAL", "MLF", "UNI", "PLI", "SUN", "STP_M", "SOL", "SBN", "EBO",
            "BKW", "FEL", "PLW_M", "HEW", "FLG", "GHD", "GAT", "CEN_M", "MTS", "HAY_M",
            "JES", "WJS", "ILF_M", "SGF", "REG", "WBR", "FAW", "KGP", "BKF", "CLP", "AIR_M"
        ),
        "METRO-YELLOW" to listOf(
            "STJ", "MTS", "MAN_M", "BYK", "CHR", "WKG", "WLS", "HAD", "HWD", "PCM",
            "MDW", "NSH", "TYN", "CUL", "WTL", "MSN", "WMS_M", "SHM", "PMV", "Nbp_M",
            "BTN_M", "FLE", "LBN", "SGF", "ILF_M", "WJS", "JES", "HAY_M", "MTS", "CEN_M",
            "GAT", "GHD", "FLG", "HEW", "PLW_M", "HEB", "JAR", "BDE", "SMD", "TDK", "CHI", "SSS"
        )
    )

    override fun resolveSequenceKey(
        lineCode: String,
        originIdentifier: String,
        destIdentifier: String
    ): String {
        val lineUpper = lineCode.uppercase()
        val origLower = originIdentifier.lowercase()
        val destLower = destIdentifier.lowercase()

        if (lineUpper == "NORTHERN") {
            val isMhe = origLower.contains("mill hill east") || destLower.contains("mill hill east") ||
                origLower == "mhe" || destLower == "mhe"
            if (isMhe) return "NORTHERN_MHE"

            val isBattersea = origLower.contains("battersea") || destLower.contains("battersea") ||
                destLower.contains("charing cross") || origLower == "bps" || destLower == "bps"
            val isEdgware = origLower.contains("edgware") || destLower.contains("edgware") ||
                origLower == "egw" || destLower == "egw"
            if (isEdgware) return "NORTHERN_EDGWARE_CX"
            if (isBattersea) return "NORTHERN_CX_BATTERSEA"
        }

        if (lineUpper == "CENTRAL") {
            if (origLower.contains("ealing") || destLower.contains("ealing") ||
                origLower.contains("hainault") || destLower.contains("hainault") ||
                origLower == "eby" || destLower == "eby" || origLower == "hhl" || destLower == "hhl"
            ) {
                return "CENTRAL_EALING"
            }
        }

        if (lineUpper == "DISTRICT") {
            if (origLower.contains("wimbledon") || destLower.contains("wimbledon") ||
                origLower.contains("fulham") || destLower.contains("fulham") ||
                origLower == "wim" || destLower == "wim"
            ) {
                return "DISTRICT_WIMBLEDON"
            }
        }

        return lineUpper
    }

    override fun resolveStationName(stationCodeOrName: String): String {
        val upper = stationCodeOrName.trim().uppercase()
        return STATION_NAMES_BY_CODE[upper]
            ?: routeStationsByCode[upper]?.name
            ?: TflStationIds.CANONICAL_NAMES_BY_ID[upper]
            ?: stationCodeOrName
    }

    override fun resolveStationId(lineCode: String, stationCodeOrName: String): String? {
        val upper = stationCodeOrName.trim().uppercase()
        routeStationsByCode[upper]?.let { return it.naptanId ?: it.crs }
        val resolvedName = resolveStationName(stationCodeOrName).trim().lowercase()
        return TflStationIds.CANONICAL_TERMINALS_BY_NAME[resolvedName] ?: upper
    }

    private val fallbackResolver = object : LinearRouteCallingPointsResolver(this) {
        override fun canHandle(departure: Departure): Boolean = true
    }

    /**
     * Resolves calling points locally using [GbTransitRoutes] when live TfL Route Sequence API is unavailable.
     */
    fun resolveLocalCallingPoints(departure: Departure): List<CallingPoint> {
        val cleanDest = departure.destinationName.split(Regex("\\s+via\\s+", RegexOption.IGNORE_CASE)).first().trim()
        return fallbackResolver.resolveCallingPointsForTrip(
            lineCode = departure.lineId,
            originStationIdentifier = departure.stationName,
            destStationIdentifier = cleanDest,
            departureEpochSeconds = Instant.now().epochSecond + departure.timeToStationSeconds,
            fallbackCallingPoints = emptyList()
        )
    }
}
