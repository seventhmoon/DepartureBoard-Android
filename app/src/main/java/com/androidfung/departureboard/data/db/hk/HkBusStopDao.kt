package com.androidfung.departureboard.data.db.hk

import androidx.room.Dao
import androidx.room.Query

@Dao
interface HkBusStopDao {

    @Query(
        """
        SELECT * FROM hk_bus_stops
        WHERE nameEn LIKE '%' || :query || '%'
           OR nameTc LIKE '%' || :query || '%'
        LIMIT :limit
        """
    )
    suspend fun searchStops(query: String, limit: Int = 30): List<HkBusStopEntity>

    @Query("SELECT * FROM hk_bus_stops WHERE stopId = :stopId LIMIT 1")
    suspend fun getStopById(stopId: String): HkBusStopEntity?

    @Query("SELECT COUNT(*) FROM hk_bus_stops")
    suspend fun getStopCount(): Int
}
