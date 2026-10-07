package com.androidfung.departureboard.data.repository

import com.androidfung.departureboard.data.model.TransitDirection
import com.androidfung.departureboard.data.routes.RouteStation

/**
 * Base class for linear rail corridors (e.g. Elizabeth line, Thameslink, Overground lines).
 *
 * Resolves cardinal direction ([TransitDirection]) by comparing the relative indices
 * of stations ordered along the corridor:
 *
 *   currentIndex < destIndex => forwardDirection (e.g. Eastbound / Southbound)
 *   currentIndex > destIndex => reverseDirection (e.g. Westbound / Northbound)
 *
 * Does not rely on arbitrary midpoints or global platform letter assumptions.
 */
internal abstract class LinearCorridorDirectionResolver(
    private val forwardDirection: TransitDirection,
    private val reverseDirection: TransitDirection,
    stationsOrder: List<RouteStation>
) {

    private val crsIndexMap: Map<String, Int> = buildMap {
        stationsOrder.forEachIndexed { index, entry ->
            put(entry.crs.uppercase(), index)
        }
    }

    private val naptanIndexMap: Map<String, Int> = buildMap {
        stationsOrder.forEachIndexed { index, entry ->
            entry.naptanId?.let { put(it.uppercase(), index) }
            put(entry.crs.uppercase(), index)
        }
    }

    private val aliasEntries: List<Pair<List<String>, Int>> = stationsOrder.mapIndexed { index, entry ->
        entry.aliases to index
    }

    /**
     * Resolves cardinal direction by comparing relative position of stations along the route.
     */
    fun resolve(
        destinationName: String? = null,
        destinationCrs: String? = null,
        destinationNaptanId: String? = null,
        currentStationId: String? = null,
        currentStationName: String? = null,
        platformName: String? = null,
        apiDirection: String? = null
    ): TransitDirection? {
        val plat = platformName?.trim()?.lowercase().orEmpty()

        // 1. Explicit Platform Directions
        if (plat.contains(forwardDirection.displayName, ignoreCase = true) ||
            plat.contains(forwardDirection.shortCode, ignoreCase = true)
        ) {
            return forwardDirection
        }
        if (plat.contains(reverseDirection.displayName, ignoreCase = true) ||
            plat.contains(reverseDirection.shortCode, ignoreCase = true)
        ) {
            return reverseDirection
        }

        // 2. Relative Route Position Comparison (Primary deterministic source of truth)
        val destIndex = findStationIndex(destinationCrs, destinationNaptanId, destinationName)
        val currentIndex = findStationIndex(currentStationId, currentStationId, currentStationName)

        if (destIndex != null && currentIndex != null) {
            if (destIndex > currentIndex) return forwardDirection
            if (destIndex < currentIndex) return reverseDirection
        }

        // 3. Fallback to API direction if provided ("inbound" / "outbound")
        return when (apiDirection?.trim()?.lowercase()) {
            "inbound" -> forwardDirection
            "outbound" -> reverseDirection
            else -> null
        }
    }

    private fun findStationIndex(crs: String?, naptanId: String?, name: String?): Int? {
        if (!crs.isNullOrBlank()) {
            val upper = crs.trim().uppercase()
            crsIndexMap[upper]?.let { return it }
            if (upper.startsWith("910G") && upper.length >= 7) {
                crsIndexMap[upper.substring(4, 7)]?.let { return it }
            }
        }

        if (!naptanId.isNullOrBlank()) {
            val upper = naptanId.trim().uppercase()
            naptanIndexMap[upper]?.let { return it }
            crsIndexMap[upper]?.let { return it }
        }

        if (!name.isNullOrBlank()) {
            val clean = name.trim().lowercase()
            for ((aliases, index) in aliasEntries) {
                if (aliases.any { clean.contains(it) || it.contains(clean) }) {
                    return index
                }
            }
        }

        return null
    }
}
