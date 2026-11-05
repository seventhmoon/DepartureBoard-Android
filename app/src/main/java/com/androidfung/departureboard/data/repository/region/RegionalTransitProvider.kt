package com.androidfung.departureboard.data.repository.region

import com.androidfung.departureboard.data.model.CallingPoint
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TransitRegion
import com.androidfung.departureboard.data.model.gb.StationServiceHoursInfo
import com.androidfung.departureboard.data.repository.DepartureSource
import com.androidfung.departureboard.data.topology.CallingPointsResolver

/**
 * Polymorphic strategy encapsulating region-specific transit operations:
 * - Station search & discovery
 * - Station ID resolution
 * - Unified departure fetching across all regional APIs/sub-modes
 * - Calling points resolution
 * - Service hours checks
 */
interface RegionalTransitProvider {
    val region: TransitRegion

    /**
     * Regional departure sources available in this region.
     */
    val departureSources: List<DepartureSource> get() = emptyList()

    /**
     * Regional calling points resolvers available in this region.
     */
    val callingPointsResolvers: List<CallingPointsResolver> get() = emptyList()

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
     * Fetches live departures for a station within this region.
     * Default implementation delegates to [departureSources] concurrently.
     */
    suspend fun getDepartures(stationId: String, stationName: String): Result<List<Departure>>

    /**
     * Resolves calling points for a departure within this region.
     */
    suspend fun getCallingPoints(departure: Departure): List<CallingPoint> {
        val resolver = callingPointsResolvers.firstOrNull { it.canHandle(departure) }
        return resolver?.resolveCallingPoints(departure) ?: emptyList()
    }

    /**
     * Checks whether the station is currently closed overnight and returns first/last service times.
     */
    suspend fun checkStationServiceHours(
        stationId: String,
        lineId: String? = null
    ): StationServiceHoursInfo? = null
}
