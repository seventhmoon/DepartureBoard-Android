package com.androidfung.departureboard.data.model.au

import com.androidfung.departureboard.data.resolver.TransitLineNetwork
import java.time.ZoneId

/**
 * Route network definitions for Sydney Trains & Sydney Metro using canonical station codes.
 */
object AuSydneyRoutes : TransitLineNetwork {

    override val timeZone: ZoneId = ZoneId.of("Australia/Sydney")
    override val defaultMode: String = "national-rail"
    override val averageSecondsBetweenStops: Long = 150L

    val STATION_NAMES_BY_CODE: Map<String, String> = mapOf(
        "BEROWRA" to "Berowra", "HORNSBY" to "Hornsby", "GORDON" to "Gordon",
        "CHATSWOOD" to "Chatswood", "NORTHSYDNEY" to "North Sydney", "WYNYARD" to "Wynyard",
        "TOWNHALL" to "Town Hall", "CENTRAL" to "Central", "REDFERN" to "Redfern",
        "STRATHFIELD" to "Strathfield", "LIDCOMBE" to "Lidcombe", "GRANVILLE" to "Granville",
        "PARRAMATTA" to "Parramatta", "BLACKTOWN" to "Blacktown", "PENRITH" to "Penrith",
        "EMUPLAINS" to "Emu Plains", "CITYCIRCLE" to "City Circle", "ASHFIELD" to "Ashfield",
        "BURWOOD" to "Burwood", "HOMEBUSH" to "Homebush", "AUBURN" to "Auburn",
        "MERRYLANDS" to "Merrylands", "FAIRFIELD" to "Fairfield", "CABRAMATTA" to "Cabramatta",
        "LIVERPOOL" to "Liverpool", "LEPPINGTON" to "Leppington", "SYDENHAM" to "Sydenham",
        "MARRICKVILLE" to "Marrickville", "DULWICHHILL" to "Dulwich Hill",
        "CANTERBURY" to "Canterbury", "BANKSTOWN" to "Bankstown", "BIRRONG" to "Birrong",
        "REGENTSPARK" to "Regents Park", "BONDIJUNCTION" to "Bondi Junction",
        "EDGECLIFF" to "Edgecliff", "KINGSX" to "Kings Cross", "MARTINPLACE" to "Martin Place",
        "WOLLICREEK" to "Wolli Creek", "HURSTVILLE" to "Hurstville", "KOGARAH" to "Kogarah",
        "ROCKDALE" to "Rockdale", "SUTHERLAND" to "Sutherland", "CRONULLA" to "Cronulla",
        "WATERFALL" to "Waterfall", "MUSEUM" to "Museum", "STJAMES" to "St James",
        "CIRCULARQUAY" to "Circular Quay", "GREENSQUARE" to "Green Square", "MASCOT" to "Mascot",
        "SYDNEYDOMESTIC" to "Domestic Airport", "SYDNEYINTL" to "International Airport",
        "TURRELLA" to "Turrella", "BARDWELLPARK" to "Bardwell Park", "BEXLEYNORTH" to "Bexley North",
        "KINGSGROVE" to "Kingsgrove", "BEVERLYHILLS" to "Beverly Hills", "NARWEE" to "Narwee",
        "RIVERWOOD" to "Riverwood", "PADSTOW" to "Padstow", "REVESBY" to "Revesby",
        "PANANIA" to "Panania", "EASTHILLS" to "East Hills", "HOLSWORTHY" to "Holsworthy",
        "GLENFIELD" to "Glenfield", "CAMPBELLTOWN" to "Campbelltown", "MACARTHUR" to "Macarthur",
        "TALLAWONG" to "Tallawong", "ROUSEHILL" to "Rouse Hill", "KELLYVILLE" to "Kellyville",
        "BELLAVISTA" to "Bella Vista", "NORWEST" to "Norwest", "HILLSSHOWGROUND" to "Hills Showground",
        "CASTLEHILL" to "Castle Hill", "CHERRYBROOK" to "Cherrybrook", "EPPING" to "Epping",
        "MACQUARIEUNI" to "Macquarie University", "MACQUARIEPARK" to "Macquarie Park",
        "NORTHRYDE" to "North Ryde", "CROWSNEST" to "Crows Nest", "VICTORIACROSS" to "Victoria Cross",
        "BARANGAROO" to "Barangaroo", "GADIGAL" to "Gadigal", "WATERLOO" to "Waterloo"
    )

    override val lineSequences: Map<String, List<String>> = mapOf(
        "T1" to listOf(
            "BEROWRA", "HORNSBY", "GORDON", "CHATSWOOD", "NORTHSYDNEY", "WYNYARD", "TOWNHALL", "CENTRAL",
            "REDFERN", "STRATHFIELD", "LIDCOMBE", "GRANVILLE", "PARRAMATTA", "BLACKTOWN", "PENRITH", "EMUPLAINS"
        ),
        "T2" to listOf(
            "CITYCIRCLE", "CENTRAL", "REDFERN", "ASHFIELD", "BURWOOD", "STRATHFIELD", "HOMEBUSH",
            "LIDCOMBE", "AUBURN", "GRANVILLE", "MERRYLANDS", "FAIRFIELD", "CABRAMATTA", "LIVERPOOL", "LEPPINGTON"
        ),
        "T3" to listOf(
            "CITYCIRCLE", "CENTRAL", "REDFERN", "SYDENHAM", "MARRICKVILLE", "DULWICHHILL", "CANTERBURY",
            "BANKSTOWN", "BIRRONG", "REGENTSPARK", "LIDCOMBE"
        ),
        "T4" to listOf(
            "BONDIJUNCTION", "EDGECLIFF", "KINGSX", "MARTINPLACE", "TOWNHALL", "CENTRAL",
            "REDFERN", "SYDENHAM", "WOLLICREEK", "HURSTVILLE", "KOGARAH", "ROCKDALE", "SUTHERLAND", "CRONULLA", "WATERFALL"
        ),
        "T8" to listOf(
            "CITYCIRCLE", "MUSEUM", "STJAMES", "CIRCULARQUAY", "WYNYARD", "TOWNHALL", "CENTRAL",
            "GREENSQUARE", "MASCOT", "SYDNEYDOMESTIC", "SYDNEYINTL", "WOLLICREEK",
            "TURRELLA", "BARDWELLPARK", "BEXLEYNORTH", "KINGSGROVE", "BEVERLYHILLS", "NARWEE",
            "RIVERWOOD", "PADSTOW", "REVESBY", "PANANIA", "EASTHILLS", "HOLSWORTHY", "GLENFIELD",
            "CAMPBELLTOWN", "MACARTHUR"
        ),
        "M1" to listOf(
            "TALLAWONG", "ROUSEHILL", "KELLYVILLE", "BELLAVISTA", "NORWEST", "HILLSSHOWGROUND",
            "CASTLEHILL", "CHERRYBROOK", "EPPING", "MACQUARIEUNI", "MACQUARIEPARK",
            "NORTHRYDE", "CHATSWOOD", "CROWSNEST", "VICTORIACROSS", "BARANGAROO",
            "MARTINPLACE", "GADIGAL", "CENTRAL", "WATERLOO", "SYDENHAM"
        )
    )

    override fun resolveStationName(stationCodeOrName: String): String {
        val clean = stationCodeOrName.trim().uppercase()
        return STATION_NAMES_BY_CODE[clean]
            ?: AuStationDirectory.getStationById("AU_TRAIN_$clean")?.name
            ?: stationCodeOrName
    }

    override fun resolveStationId(lineCode: String, stationCodeOrName: String): String {
        val clean = stationCodeOrName.trim().uppercase()
        return AuStationDirectory.getStationById("AU_TRAIN_$clean")?.id
            ?: "AU_TRAIN_$clean"
    }
}
