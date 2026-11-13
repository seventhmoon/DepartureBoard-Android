package com.androidfung.departureboard.data.gtfs

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Data Access Object for querying static GTFS schedules and station topology.
 */
@Dao
interface GtfsDao {

    @Query("SELECT * FROM gtfs_stops WHERE stopId = :stopId LIMIT 1")
    suspend fun getStopById(stopId: String): GtfsStopEntity?

    @Query("SELECT * FROM gtfs_stops WHERE stopId IN (:stopIds)")
    suspend fun getStopsByIds(stopIds: List<String>): List<GtfsStopEntity>

    @Query("SELECT * FROM gtfs_stops WHERE stopName LIKE '%' || :query || '%' LIMIT :limit")
    suspend fun searchStopsByName(query: String, limit: Int = 20): List<GtfsStopEntity>

    @Query("SELECT * FROM gtfs_stops WHERE parentStation = :parentStationId")
    suspend fun getChildStops(parentStationId: String): List<GtfsStopEntity>

    @Query("SELECT * FROM gtfs_routes WHERE routeId = :routeId LIMIT 1")
    suspend fun getRouteById(routeId: String): GtfsRouteEntity?

    @Query("SELECT * FROM gtfs_trips WHERE tripId = :tripId LIMIT 1")
    suspend fun getTripById(tripId: String): GtfsTripEntity?

    @Query("SELECT * FROM gtfs_calendar")
    suspend fun getAllCalendars(): List<GtfsCalendarEntity>

    @Query("SELECT * FROM gtfs_calendar_dates WHERE date = :date")
    suspend fun getCalendarDatesForDate(date: String): List<GtfsCalendarDateEntity>

    @Query("SELECT * FROM gtfs_levels WHERE levelId IN (:levelIds)")
    suspend fun getLevelsByIds(levelIds: List<String>): List<GtfsLevelEntity>

    @Query("SELECT * FROM gtfs_pathways WHERE fromStopId = :stopId OR (isBidirectional = 1 AND toStopId = :stopId)")
    suspend fun getPathwaysForStop(stopId: String): List<GtfsPathwayEntity>

    @Query("SELECT * FROM gtfs_pathways WHERE fromStopId IN (:stopIds) OR toStopId IN (:stopIds)")
    suspend fun getPathwaysForStops(stopIds: List<String>): List<GtfsPathwayEntity>

    @Query(
        """
        SELECT * FROM gtfs_stop_times 
        WHERE tripId = :tripId 
        ORDER BY stopSequence ASC
        """
    )
    suspend fun getCallingPointsForTrip(tripId: String): List<GtfsStopTimeEntity>

    @Query(
        """
        SELECT * FROM gtfs_stop_times 
        WHERE stopId = :stopId 
          AND departureTimeSeconds >= :afterTimeSeconds 
        ORDER BY departureTimeSeconds ASC 
        LIMIT :limit
        """
    )
    suspend fun getUpcomingStopTimes(
        stopId: String,
        afterTimeSeconds: Int,
        limit: Int = 10
    ): List<GtfsStopTimeEntity>

    @Query(
        """
        SELECT st.* FROM gtfs_stop_times st
        INNER JOIN gtfs_trips t ON st.tripId = t.tripId
        WHERE st.stopId IN (:stopIds)
          AND t.serviceId IN (:serviceIds)
          AND st.departureTimeSeconds >= :afterTimeSeconds
        ORDER BY st.departureTimeSeconds ASC
        LIMIT :limit
        """
    )
    suspend fun getUpcomingStopTimesForServices(
        stopIds: List<String>,
        serviceIds: List<String>,
        afterTimeSeconds: Int,
        limit: Int = 10
    ): List<GtfsStopTimeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStops(stops: List<GtfsStopEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutes(routes: List<GtfsRouteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrips(trips: List<GtfsTripEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStopTimes(stopTimes: List<GtfsStopTimeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalendars(calendars: List<GtfsCalendarEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalendarDates(calendarDates: List<GtfsCalendarDateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLevels(levels: List<GtfsLevelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPathways(pathways: List<GtfsPathwayEntity>)

    @Query("DELETE FROM gtfs_stops")
    suspend fun clearStops()

    @Query("DELETE FROM gtfs_routes")
    suspend fun clearRoutes()

    @Query("DELETE FROM gtfs_trips")
    suspend fun clearTrips()

    @Query("DELETE FROM gtfs_stop_times")
    suspend fun clearStopTimes()

    @Query("DELETE FROM gtfs_calendar")
    suspend fun clearCalendars()

    @Query("DELETE FROM gtfs_calendar_dates")
    suspend fun clearCalendarDates()

    @Query("DELETE FROM gtfs_levels")
    suspend fun clearLevels()

    @Query("DELETE FROM gtfs_pathways")
    suspend fun clearPathways()

    @androidx.room.Transaction
    suspend fun importScheduleBatchTransactional(
        stops: List<GtfsStopEntity>,
        routes: List<GtfsRouteEntity>,
        trips: List<GtfsTripEntity>,
        stopTimes: List<GtfsStopTimeEntity>,
        calendars: List<GtfsCalendarEntity>,
        calendarDates: List<GtfsCalendarDateEntity>,
        levels: List<GtfsLevelEntity> = emptyList(),
        pathways: List<GtfsPathwayEntity> = emptyList(),
        clearExisting: Boolean
    ) {
        if (clearExisting) {
            clearPathways()
            clearLevels()
            clearStopTimes()
            clearTrips()
            clearRoutes()
            clearStops()
            clearCalendars()
            clearCalendarDates()
        }
        if (stops.isNotEmpty()) insertStops(stops)
        if (routes.isNotEmpty()) insertRoutes(routes)
        if (trips.isNotEmpty()) insertTrips(trips)
        if (stopTimes.isNotEmpty()) insertStopTimes(stopTimes)
        if (calendars.isNotEmpty()) insertCalendars(calendars)
        if (calendarDates.isNotEmpty()) insertCalendarDates(calendarDates)
        if (levels.isNotEmpty()) insertLevels(levels)
        if (pathways.isNotEmpty()) insertPathways(pathways)
    }
}
