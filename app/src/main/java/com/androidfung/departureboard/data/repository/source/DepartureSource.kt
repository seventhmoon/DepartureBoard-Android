package com.androidfung.departureboard.data.repository.source

import com.androidfung.departureboard.data.model.Departure

/**
 * Pluggable source abstraction for retrieving live departures from transit providers.
 */
interface DepartureSource {
    fun canHandle(stationId: String, stationName: String, isBusStop: Boolean): Boolean
    suspend fun fetchDepartures(stationId: String, stationName: String, isBusStop: Boolean): Result<List<Departure>>
}
