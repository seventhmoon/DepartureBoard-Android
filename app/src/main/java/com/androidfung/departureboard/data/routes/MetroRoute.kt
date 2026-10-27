package com.androidfung.departureboard.data.routes

import com.androidfung.departureboard.data.model.CallingPoint
import com.androidfung.departureboard.data.model.TransitDirection

/**
 * Abstraction for transit / metro routes providing calling points sequence reconstruction
 * and direction inference.
 *
 * @param T The station representation type (e.g., [RouteStation] or an enum).
 */
interface MetroRoute<T> {

    /**
     * Named route sequences mapped by line identifier (e.g. "metro-green", "metro-yellow").
     */
    val lineSequences: Map<String, List<T>>

    /**
     * Resolves the ordered calling points timeline between origin and destination on a given line.
     */
    fun resolveCallingPoints(
        lineId: String,
        originName: String,
        destinationName: String,
        departureSeconds: Int = 0
    ): List<CallingPoint>

    /**
     * Resolves the transit direction (e.g. Northbound, Southbound, Clockwise, Anti-Clockwise)
     * given the line, origin, destination, and optionally platform information.
     */
    fun resolveDirection(
        lineId: String,
        originName: String,
        destinationName: String,
        platformName: String? = null
    ): TransitDirection?
}
