package com.androidfung.departureboard.data.routes

/**
 * Ordered linear station sequence for the Elizabeth line from West (index 0) to East.
 */
object ElizabethLineRoute {

    val STATIONS: List<RouteStation> = listOf(
        // Reading Outer Branch (index 0 - 8)
        RouteStation("RDG", "910GREADING", "Reading"),
        RouteStation("TWY", "910GTWYFORD", "Twyford"),
        RouteStation("MDN", "910GMAIDNHD", "Maidenhead"),
        RouteStation("TAP", "910GTAPLOW", "Taplow"),
        RouteStation("BNM", "910GBURNHAM", "Burnham"),
        RouteStation("SLO", "910GSLOUGH", "Slough"),
        RouteStation("LNY", "910GLANGLEY", "Langley"),
        RouteStation("IVR", "910GIVER", "Iver"),
        RouteStation("WDT", "910GWSTDRTN", "West Drayton"),

        // Heathrow Airport Outer Branch (index 9 - 11)
        RouteStation("HWV", "910GHTRWTM5", "Heathrow Terminal 5"),
        RouteStation("HAF", "910GHTRWTM4", "Heathrow Terminal 4"),
        RouteStation(
            "HXX",
            "910GHTRWAPT",
            "Heathrow Terminals 2 & 3",
            listOf("heathrow terminals 2 & 3", "heathrow terminals 2 and 3", "heathrow")
        ),

        // West Inner Suburbs (index 12 - 17)
        RouteStation("HAY", "910GHAYESAH", "Hayes & Harlington", listOf("hayes & harlington", "hayes")),
        RouteStation("STL", "910GSOUTHAL", "Southall"),
        RouteStation("HAN", "910GHANWELL", "Hanwell"),
        RouteStation("WEA", "910GWSTEALN", "West Ealing"),
        RouteStation("EAL", "910GEALINGB", "Ealing Broadway"),
        RouteStation("AML", "910GACTONML", "Acton Main Line"),

        // Central Underground Core (index 18 - 23)
        RouteStation("PAD", "910GPADTON", "London Paddington", listOf("paddington", "london paddington")),
        RouteStation("BDS", "940GZZLUBND", "Bond Street"),
        RouteStation("TCR", "940GZZLUTCR", "Tottenham Court Road"),
        RouteStation("FDN", "910GFARDN", "Farringdon"),
        RouteStation("LST", "910GLIVST", "Liverpool Street"),
        RouteStation("WHD", "940GZZLUWPL", "Whitechapel"),

        // South-East Abbey Wood Branch (index 24 - 27)
        RouteStation("CWX", "910GCANWHR", "Canary Wharf"),
        RouteStation("CUS", "910GCUSTMHS", "Custom House"),
        RouteStation("WWC", "910GWLWHAG", "Woolwich"),
        RouteStation("ABW", "910GABWD", "Abbey Wood"),

        // North-East Shenfield Branch (index 28 - 40)
        RouteStation("SRA", "910GSTFD", "Stratford"),
        RouteStation("MYL", "910GMRYLAND", "Maryland"),
        RouteStation("FOG", "910GFRSTGT", "Forest Gate"),
        RouteStation("MNP", "910GMNRPK", "Manor Park"),
        RouteStation("IFD", "910GILFORD", "Ilford"),
        RouteStation("SVK", "910GSEVNKS", "Seven Kings"),
        RouteStation("GMY", "910GGODMAYS", "Goodmayes"),
        RouteStation("CTH", "910GCHADWLH", "Chadwell Heath"),
        RouteStation("ROM", "910GROMFORD", "Romford"),
        RouteStation("GDP", "910GGIDEAPK", "Gidea Park"),
        RouteStation("HRO", "910GHRLDWD", "Harold Wood"),
        RouteStation("BRE", "910GBRTWOD", "Brentwood"),
        RouteStation("SNF", "910GSNFD", "Shenfield")
    )
}
