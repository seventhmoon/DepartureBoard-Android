package com.androidfung.departureboard.data.routes

/**
 * Ordered linear station sequence for the Thameslink corridor from North (index 0) to South.
 */
object ThameslinkRoute {

    val STATIONS: List<RouteStation> = listOf(
        // North Termini & Outer Branch Stations (index 0 - 11)
        RouteStation(name = "Peterborough", crs = "PBO", naptanId = "910GPETRBO"),
        RouteStation(name = "Cambridge", crs = "CBG", naptanId = "910GCAMBDG"),
        RouteStation(name = "Royston", crs = "RSY", naptanId = "910GROYSTON"),
        RouteStation(name = "Bedford", crs = "BED", naptanId = "910GBEDFDM"),
        RouteStation(name = "Flitwick", crs = "FLT", naptanId = "910GFLITWCK"),
        RouteStation(name = "Harpenden", crs = "HRP", naptanId = "910GHARPEND"),
        RouteStation(name = "Luton", crs = "LUT", naptanId = "910GLUTON"),
        RouteStation(
            name = "Luton Airport Parkway",
            crs = "LTN",
            naptanId = "910GLUTONAP",
            aliases = listOf("luton airport parkway", "luton airport")
        ),
        RouteStation(
            name = "St Albans City",
            crs = "SAC",
            naptanId = "910GSTALBNC",
            aliases = listOf("st albans city", "st albans")
        ),
        RouteStation(name = "Radlett", crs = "RDT", naptanId = "910GRADLETT"),
        RouteStation(name = "Stevenage", crs = "STV", naptanId = "910GSTVNG"),
        RouteStation(
            name = "Welwyn Garden City",
            crs = "WGC",
            naptanId = "910GWELWYNG",
            aliases = listOf("welwyn garden city", "welwyn")
        ),

        // Northern Inner Suburbs (index 12 - 16)
        RouteStation(name = "Finsbury Park", crs = "FST", naptanId = "910GFNPK"),
        RouteStation(name = "Enfield Chase", crs = "ENF", naptanId = "910GENFLDC"),
        RouteStation(name = "Brent Cross West", crs = "BCX", naptanId = "910GBRENTXW"),
        RouteStation(
            name = "West Hampstead Thameslink",
            crs = "WHD",
            naptanId = "910GWHMDSTD",
            aliases = listOf("west hampstead thameslink", "west hampstead")
        ),
        RouteStation(name = "Kentish Town", crs = "KNT", naptanId = "910GKNTHTN"),

        // London Central Core (index 17 - 22)
        RouteStation(
            name = "St Pancras International",
            crs = "STP",
            naptanId = "910GSTPX",
            aliases = listOf("st pancras international", "st pancras", "king's cross st. pancras")
        ),
        RouteStation(name = "Farringdon", crs = "ZFD", naptanId = "910GFARDN"),
        RouteStation(name = "City Thameslink", crs = "CTK", naptanId = "910GCTMSLNK"),
        RouteStation(
            name = "London Blackfriars",
            crs = "BFR",
            naptanId = "910GBLFR",
            aliases = listOf("blackfriars", "london blackfriars")
        ),
        RouteStation(name = "Elephant & Castle", crs = "EPH", naptanId = "910GELEPHNT"),
        RouteStation(name = "London Bridge", crs = "LBG", naptanId = "910GLNDNBDG"),

        // South Inner Suburbs (index 23 - 27)
        RouteStation(name = "Denmark Hill", crs = "DMK", naptanId = "910GDENMRKH"),
        RouteStation(name = "New Cross Gate", crs = "NWX", naptanId = "910GNEWX"),
        RouteStation(name = "Greenwich", crs = "GRH", naptanId = "910GGRENH"),
        RouteStation(name = "Woolwich Arsenal", crs = "WWA", naptanId = "910GWOOLWCA"),
        RouteStation(name = "East Croydon", crs = "ECR", naptanId = "910GEASTCRD"),

        // South Outer Branch Stations (index 28 - 40)
        RouteStation(name = "Sutton", crs = "SUO", naptanId = "910GSUTTON"),
        RouteStation(name = "Purley", crs = "PUR", naptanId = "910GPURLEY"),
        RouteStation(name = "Rainham", crs = "RNH", naptanId = "910GRAINHAM"),
        RouteStation(name = "Chatham", crs = "CHM", naptanId = "910GCHATHAM"),
        RouteStation(name = "Gillingham", crs = "GBS", naptanId = "910GGILLNGH"),
        RouteStation(name = "Rochester", crs = "RTR", naptanId = "910GROCHSTR"),
        RouteStation(name = "Horsham", crs = "HRH", naptanId = "910GHORSHAM"),
        RouteStation(name = "Three Bridges", crs = "TBD", naptanId = "910GTHBDG"),
        RouteStation(
            name = "Gatwick Airport",
            crs = "GTW",
            naptanId = "910GGATWCK",
            aliases = listOf("gatwick airport", "gatwick")
        ),
        RouteStation(name = "Haywards Heath", crs = "HHE", naptanId = "910GHAYWRDH"),
        RouteStation(name = "Burgess Hill", crs = "BUG", naptanId = "910GBURGSSH"),
        RouteStation(name = "Hassocks", crs = "HGS", naptanId = "910GHASSOCKS"),
        RouteStation(name = "Brighton", crs = "BTN", naptanId = "910GBRIGHTN")
    )
}
