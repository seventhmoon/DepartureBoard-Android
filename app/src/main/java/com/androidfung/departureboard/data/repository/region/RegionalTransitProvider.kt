package com.androidfung.departureboard.data.repository.region

import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TransitRegion
import com.androidfung.departureboard.data.model.gb.StationServiceHoursInfo

/**
 * Polymorphic strategy encapsulating region-specific station search, station ID lookup,
 * and service-hours resolution for a [TransitRegion].
 */
interface RegionalTransitProvider {
    val region: TransitRegion

    /**
     * Returns true if [stationId] belongs to this region's namespace or station registry.
     */
    fun ownsStationId(stationId: String): Boolean

    /**
     * Searches stations within this region matching [query].
     */
    suspend fun searchStations(query: String): Result<List<Station>>

    /**
     * Resolves a [Station] by its [stationId] within this region, or null if not found.
     */
    suspend fun getStationById(stationId: String): Station?

    /**
     * Checks whether the station is currently closed overnight and returns first/last service times.
     */
    suspend fun checkStationServiceHours(
        stationId: String,
        lineId: String? = null
    ): StationServiceHoursInfo? = null
}
