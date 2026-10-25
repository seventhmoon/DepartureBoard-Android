package com.androidfung.departureboard.data.resolver

import com.androidfung.departureboard.data.model.CallingPoint
import com.androidfung.departureboard.data.model.Departure

/**
 * Pluggable resolver abstraction for retrieving calling points along a journey/trip.
 * Parallels [com.androidfung.departureboard.data.repository.source.DepartureSource].
 */
interface CallingPointsResolver {
    fun canHandle(departure: Departure): Boolean
    suspend fun resolveCallingPoints(departure: Departure): List<CallingPoint>
}
