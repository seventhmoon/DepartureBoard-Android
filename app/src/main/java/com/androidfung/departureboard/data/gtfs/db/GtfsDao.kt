package com.androidfung.departureboard.data.gtfs.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Result data class for join between GtfsStopTime and GtfsStop.
 */
data class GtfsCallingPointRecord(
    val tripId: String,
    val stopSequence: Int,
    val stopId: String,
    val stopName: String,
    val arrivalTime: String?,
    val departureTime: String?,
    val stopHeadsign: String?,
    val platformCode: String?
)

@Dao
interface GtfsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStops(stops: List<GtfsStopEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutes(routes: List<GtfsRouteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrips(trips: List<GtfsTripEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStopTimes(stopTimes: List<GtfsStopTimeEntity>)

    @Query("SELECT * FROM gtfs_stops WHERE stopId = :stopId LIMIT 1")
    suspend fun getStopById(stopId: String): GtfsStopEntity?

    @Query("SELECT * FROM gtfs_stops WHERE stopCode = :stopCode LIMIT 1")
    suspend fun getStopByCode(stopCode: String): GtfsStopEntity?

    @Query("SELECT * FROM gtfs_stops WHERE parentStation = :parentStationId")
    suspend fun getPlatformsForStation(parentStationId: String): List<GtfsStopEntity>

    @Query("SELECT * FROM gtfs_stops WHERE stopName LIKE '%' || :query || '%' OR stopCode LIKE '%' || :query || '%' LIMIT :limit")
    suspend fun searchStops(query: String, limit: Int = 50): List<GtfsStopEntity>

    @Query("SELECT * FROM gtfs_routes WHERE routeId = :routeId LIMIT 1")
    suspend fun getRouteById(routeId: String): GtfsRouteEntity?

    @Query("SELECT * FROM gtfs_routes")
    suspend fun getAllRoutes(): List<GtfsRouteEntity>

    @Query("SELECT * FROM gtfs_trips WHERE tripId = :tripId LIMIT 1")
    suspend fun getTripById(tripId: String): GtfsTripEntity?

    @Query("SELECT * FROM gtfs_trips WHERE routeId = :routeId")
    suspend fun getTripsForRoute(routeId: String): List<GtfsTripEntity>

    @Query("SELECT * FROM gtfs_stop_times WHERE tripId = :tripId ORDER BY stopSequence ASC")
    suspend fun getStopTimesForTrip(tripId: String): List<GtfsStopTimeEntity>

    @Query("""
        SELECT 
            st.tripId AS tripId,
            st.stopSequence AS stopSequence,
            s.stopId AS stopId,
            s.stopName AS stopName,
            st.arrivalTime AS arrivalTime,
            st.departureTime AS departureTime,
            st.stopHeadsign AS stopHeadsign,
            s.platformCode AS platformCode
        FROM gtfs_stop_times st
        INNER JOIN gtfs_stops s ON st.stopId = s.stopId
        WHERE st.tripId = :tripId
        ORDER BY st.stopSequence ASC
    """)
    suspend fun getCallingPointsForTrip(tripId: String): List<GtfsCallingPointRecord>

    @Query("""
        SELECT DISTINCT r.* 
        FROM gtfs_routes r
        INNER JOIN gtfs_trips t ON r.routeId = t.routeId
        INNER JOIN gtfs_stop_times st ON t.tripId = st.tripId
        WHERE st.stopId = :stopId OR st.stopId IN (SELECT s2.stopId FROM gtfs_stops s2 WHERE s2.parentStation = :stopId)
    """)
    suspend fun getRoutesServingStop(stopId: String): List<GtfsRouteEntity>

    @Query("DELETE FROM gtfs_stops")
    suspend fun clearStops()

    @Query("DELETE FROM gtfs_routes")
    suspend fun clearRoutes()

    @Query("DELETE FROM gtfs_trips")
    suspend fun clearTrips()

    @Query("DELETE FROM gtfs_stop_times")
    suspend fun clearStopTimes()
}
