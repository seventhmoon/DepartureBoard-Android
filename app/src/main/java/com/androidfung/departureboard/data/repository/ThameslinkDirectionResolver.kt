package com.androidfung.departureboard.data.repository

/**
 * Deterministic O(1) cardinal direction resolver for Thameslink services (Northbound vs Southbound).
 * Prioritizes 3-letter National Rail CRS codes before falling back to destination name keywords.
 */
internal object ThameslinkDirectionResolver {

    const val NORTHBOUND = "Northbound"
    const val SOUTHBOUND = "Southbound"

    // 1. National Rail 3-Letter CRS Codes (Darwin API) - O(1) lookup
    private val NORTHBOUND_CRS = setOf(
        "BED", // Bedford
        "LUT", // Luton
        "LTN", // Luton Airport Parkway
        "SAC", // St Albans City
        "PBO", // Peterborough
        "CBG", // Cambridge
        "STV", // Stevenage
        "WGC", // Welwyn Garden City
        "FST", // Finsbury Park
        "FLT", // Flitwick
        "HRP", // Harpenden
        "RDT", // Radlett
        "ENF", // Enfield Chase (branching/diversions)
        "RSY"  // Royston
    )

    private val SOUTHBOUND_CRS = setOf(
        "BTN", // Brighton
        "GTW", // Gatwick Airport
        "TBD", // Three Bridges
        "HRH", // Horsham
        "SUO", // Sutton
        "RNH", // Rainham (Kent)
        "DMK", // Denmark Hill
        "EPH", // Elephant & Castle
        "BDM", // Blackfriars
        "HHE", // Haywards Heath
        "BUG", // Burgess Hill
        "HGS", // Hassocks
        "ECR", // East Croydon
        "GBS", // Gillingham (Kent)
        "CHM", // Chatham
        "RTR"  // Rochester
    )

    private val NORTHBOUND_KEYWORDS = listOf(
        "bedford", "luton", "peterborough", "cambridge", "st albans", "stevenage", "welwyn", "finsbury park"
    )

    private val SOUTHBOUND_KEYWORDS = listOf(
        "brighton", "gatwick", "horsham", "sutton", "rainham", "three bridges", "east croydon", "gillingham"
    )

    fun resolve(destinationCrs: String? = null, destinationName: String? = null): String? {
        if (!destinationCrs.isNullOrBlank()) {
            val upperCrs = destinationCrs.trim().uppercase()
            if (upperCrs in NORTHBOUND_CRS) return NORTHBOUND
            if (upperCrs in SOUTHBOUND_CRS) return SOUTHBOUND
        }

        if (!destinationName.isNullOrBlank()) {
            val destTrim = destinationName.trim()
            if (NORTHBOUND_KEYWORDS.any { destTrim.contains(it, ignoreCase = true) }) return NORTHBOUND
            if (SOUTHBOUND_KEYWORDS.any { destTrim.contains(it, ignoreCase = true) }) return SOUTHBOUND
        }

        return null
    }
}
