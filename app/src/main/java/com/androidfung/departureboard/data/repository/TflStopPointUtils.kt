package com.androidfung.departureboard.data.repository

/**
 * Common utilities and heuristics for handling TfL and NaPTAN stop identifiers.
 */
internal object TflStopPointUtils {

    /**
     * Regex matching London bus NaPTAN IDs (typically starting with 490 followed by digits and stop letter/code).
     */
    private val BUS_STOP_ID_REGEX = Regex("^490\\d+([A-Za-z0-9]+)$")

    /**
     * TfL NaPTAN ID prefixes for transit modes.
     */
    const val PREFIX_METRO_TRAM: String = "940G"
    const val PREFIX_NATIONAL_RAIL: String = "910G"
    const val PREFIX_BUS_STOP: String = "490"

    /**
     * Extracts the bus stop letter code from a NaPTAN bus stop ID if applicable (e.g. 490000185A -> "A").
     */
    fun extractBusStopLetter(id: String): String? {
        return BUS_STOP_ID_REGEX.find(id)?.groupValues?.getOrNull(1)?.uppercase()
    }

    /**
     * Determines child stop point sorting priority when expanding transit hubs.
     * Metro/Tube/Tram are prioritized over National Rail, which are prioritized over individual Bus stands.
     */
    fun getHubChildPriority(childId: String): Int {
        return when {
            childId.startsWith(PREFIX_METRO_TRAM) -> 3
            childId.startsWith(PREFIX_NATIONAL_RAIL) -> 2
            else -> 1
        }
    }
}
