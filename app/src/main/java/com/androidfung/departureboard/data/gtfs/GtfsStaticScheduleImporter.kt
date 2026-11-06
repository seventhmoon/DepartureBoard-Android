package com.androidfung.departureboard.data.gtfs

/**
 * Ingestion engine for static GTFS transit feeds.
 * Imports routes, stops, trips, and scheduled calling points into the local [GtfsDao].
 */
class GtfsStaticScheduleImporter(
    private val dao: GtfsDao
) {
    constructor(database: GtfsDatabase) : this(database.gtfsDao())

    /**
     * Imports a batch of static GTFS schedules atomically into the data store.
     */
    suspend fun importScheduleBatch(
        stops: List<GtfsStopEntity> = emptyList(),
        routes: List<GtfsRouteEntity> = emptyList(),
        trips: List<GtfsTripEntity> = emptyList(),
        stopTimes: List<GtfsStopTimeEntity> = emptyList(),
        clearExisting: Boolean = false
    ) {
        if (clearExisting) {
            dao.clearStopTimes()
            dao.clearTrips()
            dao.clearRoutes()
            dao.clearStops()
        }
        if (stops.isNotEmpty()) dao.insertStops(stops)
        if (routes.isNotEmpty()) dao.insertRoutes(routes)
        if (trips.isNotEmpty()) dao.insertTrips(trips)
        if (stopTimes.isNotEmpty()) dao.insertStopTimes(stopTimes)
    }
}
