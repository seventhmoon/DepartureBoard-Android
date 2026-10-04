package com.androidfung.departureboard.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class DepartureDao {

    @Query("SELECT * FROM cached_departures WHERE stationId = :stationId ORDER BY timeToStationSeconds ASC")
    abstract fun getDeparturesForStationFlow(stationId: String): Flow<List<CachedDepartureEntity>>

    @Query("SELECT * FROM cached_departures WHERE stationId = :stationId ORDER BY timeToStationSeconds ASC")
    abstract suspend fun getDeparturesForStation(stationId: String): List<CachedDepartureEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertDepartures(departures: List<CachedDepartureEntity>)

    @Query("DELETE FROM cached_departures WHERE stationId = :stationId")
    abstract suspend fun deleteDeparturesForStation(stationId: String)

    @Transaction
    open suspend fun replaceDeparturesForStation(stationId: String, departures: List<CachedDepartureEntity>) {
        deleteDeparturesForStation(stationId)
        insertDepartures(departures)
    }

    @Query("DELETE FROM cached_departures WHERE cachedAtMillis < :thresholdMillis")
    abstract suspend fun clearStaleDepartures(thresholdMillis: Long)
}
