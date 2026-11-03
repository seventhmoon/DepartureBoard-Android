package com.androidfung.departureboard.data.repository.direction

import com.androidfung.departureboard.data.model.TransitDirection

/**
 * Common contract for resolving transit directions (e.g. Cardinal, Circular, or Relative).
 */
interface DirectionResolver {
    fun resolve(
        destinationName: String? = null,
        destinationCrs: String? = null,
        destinationNaptanId: String? = null,
        currentStationId: String? = null,
        currentStationName: String? = null,
        platformName: String? = null,
        apiDirection: String? = null
    ): TransitDirection?
}
