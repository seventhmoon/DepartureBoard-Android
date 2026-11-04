package com.androidfung.departureboard.data.resolver

import java.time.ZoneId

/**
 * Represents a transit network with predefined linear or branching line sequences.
 */
interface TransitLineNetwork {
    val timeZone: ZoneId
    val defaultMode: String
    val averageSecondsBetweenStops: Long get() = 120L
    val lineSequences: Map<String, List<String>>

    /**
     * Resolves display station name for a code or raw identifier.
     * Defaults to identity if the sequence already stores display names.
     */
    fun resolveStationName(stationCodeOrName: String): String = stationCodeOrName

    /**
     * Resolves station ID if available.
     */
    fun resolveStationId(lineCode: String, stationCodeOrName: String): String? = null
}
