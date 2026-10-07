package com.androidfung.departureboard.data.routes

/**
 * Ordered linear station sequence for the Thameslink corridor from North (index 0) to South.
 */
object ThameslinkRoute {

    val STATIONS: List<RouteStation> = listOf(
        // North Termini & Outer Branch Stations (index 0 - 11)
        RouteStation("PBO", "910GPETRBO", "Peterborough"),
        RouteStation("CBG", "910GCAMBDG", "Cambridge"),
        RouteStation("RSY", "910GROYSTON", "Royston"),
        RouteStation("BED", "910GBEDFDM", "Bedford"),
        RouteStation("FLT", "910GFLITWCK", "Flitwick"),
        RouteStation("HRP", "910GHARPEND", "Harpenden"),
        RouteStation("LUT", "910GLUTON", "Luton"),
        RouteStation(
            "LTN",
            "910GLUTONAP",
            "Luton Airport Parkway",
            listOf("luton airport parkway", "luton airport")
        ),
        RouteStation("SAC", "910GSTALBNC", "St Albans City", listOf("st albans city", "st albans")),
        RouteStation("RDT", "910GRADLETT", "Radlett"),
        RouteStation("STV", "910GSTVNG", "Stevenage"),
        RouteStation(
            "WGC",
            "910GWELWYNG",
            "Welwyn Garden City",
            listOf("welwyn garden city", "welwyn")
        ),

        // Northern Inner Suburbs (index 12 - 16)
        RouteStation("FST", "910GFNPK", "Finsbury Park"),
        RouteStation("ENF", "910GENFLDC", "Enfield Chase"),
        RouteStation("BCX", "910GBRENTXW", "Brent Cross West"),
        RouteStation(
            "WHD",
            "910GWHMDSTD",
            "West Hampstead Thameslink",
            listOf("west hampstead thameslink", "west hampstead")
        ),
        RouteStation("KNT", "910GKNTHTN", "Kentish Town"),

        // London Central Core (index 17 - 22)
        RouteStation(
            "STP",
            "910GSTPX",
            "St Pancras International",
            listOf("st pancras international", "st pancras", "king's cross st. pancras")
        ),
        RouteStation("ZFD", "910GFARDN", "Farringdon"),
        RouteStation("CTK", "910GCTMSLNK", "City Thameslink"),
        RouteStation("BFR", "910GBLFR", "London Blackfriars", listOf("blackfriars", "london blackfriars")),
        RouteStation("EPH", "910GELEPHNT", "Elephant & Castle"),
        RouteStation("LBG", "910GLNDNBDG", "London Bridge"),

        // South Inner Suburbs (index 23 - 27)
        RouteStation("DMK", "910GDENMRKH", "Denmark Hill"),
        RouteStation("NWX", "910GNEWX", "New Cross Gate"),
        RouteStation("GRH", "910GGRENH", "Greenwich"),
        RouteStation("WWA", "910GWOOLWCA", "Woolwich Arsenal"),
        RouteStation("ECR", "910GEASTCRD", "East Croydon"),

        // South Outer Branch Stations (index 28 - 40)
        RouteStation("SUO", "910GSUTTON", "Sutton"),
        RouteStation("PUR", "910GPURLEY", "Purley"),
        RouteStation("RNH", "910GRAINHAM", "Rainham"),
        RouteStation("CHM", "910GCHATHAM", "Chatham"),
        RouteStation("GBS", "910GGILLNGH", "Gillingham"),
        RouteStation("RTR", "910GROCHSTR", "Rochester"),
        RouteStation("HRH", "910GHORSHAM", "Horsham"),
        RouteStation("TBD", "910GTHBDG", "Three Bridges"),
        RouteStation("GTW", "910GGATWCK", "Gatwick Airport", listOf("gatwick airport", "gatwick")),
        RouteStation("HHE", "910GHAYWRDH", "Haywards Heath"),
        RouteStation("BUG", "910GBURGSSH", "Burgess Hill"),
        RouteStation("HGS", "910GHASSOCKS", "Hassocks"),
        RouteStation("BTN", "910GBRIGHTN", "Brighton")
    )
}
