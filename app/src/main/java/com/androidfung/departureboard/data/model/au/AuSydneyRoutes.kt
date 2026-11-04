package com.androidfung.departureboard.data.model.au

import com.androidfung.departureboard.data.resolver.TransitLineNetwork
import java.time.ZoneId

/**
 * Route network definitions for Sydney Trains & Metro.
 */
object AuSydneyRoutes : TransitLineNetwork {

    override val timeZone: ZoneId = ZoneId.of("Australia/Sydney")
    override val defaultMode: String = "national-rail"
    override val averageSecondsBetweenStops: Long = 150L

    override val lineSequences: Map<String, List<String>> = mapOf(
        "T1" to listOf(
            "Berowra", "Hornsby", "Gordon", "Chatswood", "North Sydney", "Wynyard", "Town Hall", "Central",
            "Redfern", "Strathfield", "Lidcombe", "Granville", "Parramatta", "Blacktown", "Penrith", "Emu Plains"
        ),
        "T2" to listOf(
            "City Circle", "Central", "Redfern", "Ashfield", "Burwood", "Strathfield", "Homebush",
            "Lidcombe", "Auburn", "Granville", "Merrylands", "Fairfield", "Cabramatta", "Liverpool", "Leppington"
        ),
        "T3" to listOf(
            "City Circle", "Central", "Redfern", "Sydenham", "Marrickville", "Dulwich Hill", "Canterbury",
            "Bankstown", "Birrong", "Regents Park", "Lidcombe"
        ),
        "T4" to listOf(
            "Bondi Junction", "Edgecliff", "Kings Cross", "Martin Place", "Town Hall", "Central",
            "Redfern", "Sydenham", "Wolli Creek", "Hurstville", "Kogarah", "Rockdale", "Sutherland", "Cronulla", "Waterfall"
        ),
        "T8" to listOf(
            "City Circle", "Museum", "St James", "Circular Quay", "Wynyard", "Town Hall", "Central",
            "Green Square", "Mascot", "Domestic Airport", "International Airport", "Wolli Creek",
            "Turrella", "Bardwell Park", "Bexley North", "Kingsgrove", "Beverly Hills", "Narwee",
            "Riverwood", "Padstow", "Revesby", "Panania", "East Hills", "Holsworthy", "Glenfield",
            "Campbelltown", "Macarthur"
        ),
        "M1" to listOf(
            "Tallawong", "Rouse Hill", "Kellyville", "Bella Vista", "Norwest", "Hills Showground",
            "Castle Hill", "Cherrybrook", "Epping", "Macquarie University", "Macquarie Park",
            "North Ryde", "Chatswood", "Crows Nest", "Victoria Cross", "Barangaroo",
            "Martin Place", "Gadigal", "Central", "Waterloo", "Sydenham"
        )
    )
}
