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

    @Query("SELECT * FROM gtfs_stops WHERE stopName LIKE '%' || :query || '%' LIMIT :limit")
    suspend fun searchStopsByName(query: String, limit: Int = 20): List<GtfsStopEntity>

    @Query("SELECT * FROM gtfs_routes WHERE routeId = :routeId LIMIT 1")
    suspend fun getRouteById(routeId: String): GtfsRouteEntity?

    @Query("SELECT * FROM gtfs_trips WHERE tripId = :tripId LIMIT 1")
    suspend fun getTripById(tripId: String): GtfsTripEntity?

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStops(stops: List<GtfsStopEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutes(routes: List<GtfsRouteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrips(trips: List<GtfsTripEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStopTimes(stopTimes: List<GtfsStopTimeEntity>)

    @Query("DELETE FROM gtfs_stops")
    suspend fun clearStops()

    @Query("DELETE FROM gtfs_routes")
    suspend fun clearRoutes()

    @Query("DELETE FROM gtfs_trips")
    suspend fun clearTrips()

    @Query("DELETE FROM gtfs_stop_times")
    suspend fun clearStopTimes()
}
