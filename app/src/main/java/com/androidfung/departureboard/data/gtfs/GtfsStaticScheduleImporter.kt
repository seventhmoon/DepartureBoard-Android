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
        calendars: List<GtfsCalendarEntity> = emptyList(),
        calendarDates: List<GtfsCalendarDateEntity> = emptyList(),
        levels: List<GtfsLevelEntity> = emptyList(),
        pathways: List<GtfsPathwayEntity> = emptyList(),
        clearExisting: Boolean = false
    ) {
        dao.importScheduleBatchTransactional(
            stops = stops,
            routes = routes,
            trips = trips,
            stopTimes = stopTimes,
            calendars = calendars,
            calendarDates = calendarDates,
            levels = levels,
            pathways = pathways,
            clearExisting = clearExisting
        )
    }
}
