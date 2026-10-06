package com.androidfung.departureboard.data.repository

/**
 * Deterministic O(1) cardinal direction resolver for the Elizabeth line (Eastbound vs Westbound).
 * Prioritizes 3-letter National Rail CRS codes and TfL NaPTAN station IDs over fragile string operations.
 */
internal object ElizabethLineDirectionResolver {

    const val EASTBOUND = "Eastbound"
    const val WESTBOUND = "Westbound"

    // 1. National Rail 3-Letter CRS Codes (Darwin API) - O(1) lookup
    private val EASTBOUND_CRS = setOf(
        // Abbey Wood Branch
        "ABW", // Abbey Wood
        "WWC", // Woolwich
        "CUS", // Custom House
        "CWX", // Canary Wharf
        // Shenfield Branch
        "SNF", // Shenfield
        "BRE", // Brentwood
        "HRO", // Harold Wood
        "GDP", // Gidea Park
        "ROM", // Romford
        "CTH", // Chadwell Heath
        "GMY", // Goodmayes
        "SVK", // Seven Kings
        "IFD", // Ilford
        "MNP", // Manor Park
        "FOG", // Forest Gate
        "MYL", // Maryland
        "SRA"  // Stratford
    )

    private val WESTBOUND_CRS = setOf(
        // Central Terminal
        "PAD", // London Paddington
        // Reading Branch
        "RDG", // Reading
        "TWY", // Twyford
        "MDN", // Maidenhead
        "TAP", // Taplow
        "BNM", // Burnham
        "SLO", // Slough
        "LNY", // Langley
        "IVR", // Iver
        "WDT", // West Drayton
        "HAY", // Hayes & Harlington
        "STL", // Southall
        "HAN", // Hanwell
        "WEA", // West Ealing
        "EAL", // Ealing Broadway
        "AML", // Acton Main Line
        // Heathrow Airport Branch
        "HXX", // Heathrow Terminals 2 & 3
        "HAF", // Heathrow Terminal 4
        "HWV"  // Heathrow Terminal 5
    )

    // 2. TfL NaPTAN Station IDs - O(1) lookup
    private val EASTBOUND_NAPTAN = setOf(
        "910GABWD", "940GZZLUABW", "910GWLWHAG", "910GCUSTMHS", "910GCANWHR", "940GZZLUCYF",
        "910GSNFD", "910GBRTWOD", "910GHRLDWD", "910GGIDEAPK", "910GROMFORD", "910GCHADWLH",
        "910GGODMAYS", "910GSEVNKS", "910GILFORD", "910GMNRPK", "910GFRSTGT", "910GMRYLAND",
        "910GSTFD", "940GZZLUSTD"
    )

    private val WESTBOUND_NAPTAN = setOf(
        "910GPADTON", "940GZZLUPAD", "910GREADING", "910GTWYFORD", "910GMAIDNHD", "910GTAPLOW",
        "910GBURNHAM", "910GSLOUGH", "910GLANGLEY", "910GIVER", "910GWSTDRTN", "910GHAYESAH",
        "910GSOUTHAL", "910GHANWELL", "910GWSTEALN", "910GEALINGB", "940GZZLUEBY", "910GACTONML",
        "910GHTRWAPT", "910GHTRWTM4", "910GHTRWTM5"
    )

    // Fallback keyword sets for unmapped stations
    private val EASTBOUND_KEYWORDS = listOf(
        "abbey wood", "shenfield", "stratford", "canary wharf", "custom house",
        "woolwich", "ilford", "romford", "gidea park", "harold wood", "brentwood"
    )

    private val WESTBOUND_KEYWORDS = listOf(
        "paddington", "reading", "heathrow", "maidenhead", "slough",
        "hayes", "ealing broadway", "southall", "west drayton"
    )

    /**
     * Resolves Elizabeth line direction using standard machine identifiers (CRS / NaPTAN ID)
     * with platform letter and name keyword fallbacks.
     */
    fun resolve(
        destinationName: String? = null,
        destinationCrs: String? = null,
        destinationNaptanId: String? = null,
        platformName: String? = null,
        apiDirection: String? = null
    ): String? {
        // Priority 1: O(1) 3-Letter CRS Code Check (National Rail)
        if (!destinationCrs.isNullOrBlank()) {
            val upperCrs = destinationCrs.trim().uppercase()
            if (upperCrs in EASTBOUND_CRS) return EASTBOUND
            if (upperCrs in WESTBOUND_CRS) return WESTBOUND
        }

        // Priority 2: O(1) TfL NaPTAN Station ID Check
        if (!destinationNaptanId.isNullOrBlank()) {
            val upperNaptan = destinationNaptanId.trim().uppercase()
            if (upperNaptan in EASTBOUND_NAPTAN) return EASTBOUND
            if (upperNaptan in WESTBOUND_NAPTAN) return WESTBOUND
        }

        // Priority 3: Central Core Platform Letters (Plat A = Eastbound, Plat B = Westbound)
        val plat = platformName?.trim()?.lowercase() ?: ""
        if (plat.endsWith("platform a") || plat.endsWith("plat a") || plat == "a" || plat.contains("platform a")) return EASTBOUND
        if (plat.endsWith("platform b") || plat.endsWith("plat b") || plat == "b" || plat.contains("platform b")) return WESTBOUND

        // Priority 4: Platform description explicit directions
        if (plat.contains("eastbound")) return EASTBOUND
        if (plat.contains("westbound")) return WESTBOUND

        // Priority 5: Station Name Keywords fallback
        if (!destinationName.isNullOrBlank()) {
            val destClean = destinationName.trim().lowercase()
            if (EASTBOUND_KEYWORDS.any { destClean.contains(it) }) return EASTBOUND
            if (WESTBOUND_KEYWORDS.any { destClean.contains(it) }) return WESTBOUND
        }

        // Priority 6: API directional fallback
        return when (apiDirection?.trim()?.lowercase()) {
            "inbound" -> EASTBOUND
            "outbound" -> WESTBOUND
            else -> null
        }
    }
}
