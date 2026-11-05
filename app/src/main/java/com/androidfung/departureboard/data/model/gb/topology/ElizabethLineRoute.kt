package com.androidfung.departureboard.data.model.gb.topology

import com.androidfung.departureboard.data.topology.RouteStation
/**
 * Ordered linear station sequence for the Elizabeth line from West (index 0) to East.
 */
object ElizabethLineRoute {

    val STATIONS: List<RouteStation> = listOf(
        // Reading Outer Branch (index 0 - 8)
        RouteStation("Reading", "RDG", "910GREADING"),
        RouteStation("Twyford", "TWY", "910GTWYFORD"),
        RouteStation("Maidenhead", "MDN", "910GMAIDNHD"),
        RouteStation("Taplow", "TAP", "910GTAPLOW"),
        RouteStation("Burnham", "BNM", "910GBURNHAM"),
        RouteStation("Slough", "SLO", "910GSLOUGH"),
        RouteStation("Langley", "LNY", "910GLANGLEY"),
        RouteStation("Iver", "IVR", "910GIVER"),
        RouteStation("West Drayton", "WDT", "910GWSTDRTN"),

        // Heathrow Airport Outer Branch (index 9 - 11)
        RouteStation("Heathrow Terminal 5", "HWV", "910GHTRWTM5"),
        RouteStation("Heathrow Terminal 4", "HAF", "910GHTRWTM4"),
        RouteStation(
            name = "Heathrow Terminals 2 & 3",
            crs = "HXX",
            naptanId = "910GHTRWAPT",
            aliases = listOf("heathrow terminals 2 & 3", "heathrow terminals 2 and 3", "heathrow")
        ),

        // West Inner Suburbs (index 12 - 17)
        RouteStation("Hayes & Harlington", "HAY", "910GHAYESAH", listOf("hayes & harlington", "hayes")),
        RouteStation("Southall", "STL", "910GSOUTHAL"),
        RouteStation("Hanwell", "HAN", "910GHANWELL"),
        RouteStation("West Ealing", "WEA", "910GWSTEALN"),
        RouteStation("Ealing Broadway", "EAL", "910GEALINGB"),
        RouteStation("Acton Main Line", "AML", "910GACTONML"),

        // Central Underground Core (index 18 - 23)
        RouteStation("London Paddington", "PAD", "910GPADTON", listOf("paddington", "london paddington")),
        RouteStation("Bond Street", "BDS", "940GZZLUBND"),
        RouteStation("Tottenham Court Road", "TCR", "940GZZLUTCR"),
        RouteStation("Farringdon", "FDN", "910GFARDN"),
        RouteStation("Liverpool Street", "LST", "910GLIVST"),
        RouteStation("Whitechapel", "WHD", "940GZZLUWPL"),

        // South-East Abbey Wood Branch (index 24 - 27)
        RouteStation("Canary Wharf", "CWX", "910GCANWHR"),
        RouteStation("Custom House", "CUS", "910GCUSTMHS"),
        RouteStation("Woolwich", "WWC", "910GWLWHAG"),
        RouteStation("Abbey Wood", "ABW", "910GABWD"),

        // North-East Shenfield Branch (index 28 - 40)
        RouteStation("Stratford", "SRA", "910GSTFD"),
        RouteStation("Maryland", "MYL", "910GMRYLAND"),
        RouteStation("Forest Gate", "FOG", "910GFRSTGT"),
        RouteStation("Manor Park", "MNP", "910GMNRPK"),
        RouteStation("Ilford", "IFD", "910GILFORD"),
        RouteStation("Seven Kings", "SVK", "910GSEVNKS"),
        RouteStation("Goodmayes", "GMY", "910GGODMAYS"),
        RouteStation("Chadwell Heath", "CTH", "910GCHADWLH"),
        RouteStation("Romford", "ROM", "910GROMFORD"),
        RouteStation("Gidea Park", "GDP", "910GGIDEAPK"),
        RouteStation("Harold Wood", "HRO", "910GHRLDWD"),
        RouteStation("Brentwood", "BRE", "910GBRTWOD"),
        RouteStation("Shenfield", "SNF", "910GSNFD")
    )
}
