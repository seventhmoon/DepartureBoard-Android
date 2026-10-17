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

    @Query("SELECT crsCode FROM station_crs_mappings WHERE UPPER(stationName) = UPPER(:name)")
    suspend fun getCrsCodesForStationName(name: String): List<String>

    @Query("SELECT crsCode FROM station_crs_mappings WHERE normalizedName LIKE '%' || :keyword || '%'")
    suspend fun getCrsCodesForNameKeyword(keyword: String): List<String>

    @Query("SELECT * FROM station_crs_mappings WHERE normalizedName LIKE '%' || :keyword || '%' LIMIT 10")
    suspend fun getStationsByNameKeyword(keyword: String): List<StationCrsEntity>

    @Query("SELECT * FROM station_crs_mappings WHERE UPPER(crsCode) = UPPER(:crsCode) LIMIT 1")
    suspend fun getStationByCrsCode(crsCode: String): StationCrsEntity?

    @Query("SELECT * FROM station_crs_mappings WHERE UPPER(stationId) = UPPER(:stationId) OR UPPER(crsCode) = UPPER(:stationId) LIMIT 1")
    suspend fun getStationByStationId(stationId: String): StationCrsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(mappings: List<StationCrsEntity>)

    @Query("SELECT COUNT(*) FROM station_crs_mappings")
    suspend fun getCount(): Int
}
