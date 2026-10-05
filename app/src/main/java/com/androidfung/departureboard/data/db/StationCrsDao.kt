package com.androidfung.departureboard.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface StationCrsDao {

    @Query("SELECT crsCode FROM station_crs_mappings WHERE UPPER(stationId) = UPPER(:stationId)")
    suspend fun getCrsCodesForStationId(stationId: String): List<String>

    @Query("SELECT crsCode FROM station_crs_mappings WHERE UPPER(normalizedName) = UPPER(:normalizedName)")
    suspend fun getCrsCodesForExactName(normalizedName: String): List<String>

    @Query("SELECT crsCode FROM station_crs_mappings WHERE normalizedName LIKE '%' || :keyword || '%'")
    suspend fun getCrsCodesForNameKeyword(keyword: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(mappings: List<StationCrsEntity>)

    @Query("SELECT COUNT(*) FROM station_crs_mappings")
    suspend fun getCount(): Int
}
