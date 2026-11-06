package com.androidfung.departureboard.data.db.gb

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Pre-populated indexed mapping entity connecting TfL NaPTAN / Hub IDs to National Rail 3-letter CRS codes.
 */
@Entity(
    tableName = "station_crs_mappings",
    indices = [
        Index(value = ["stationId"]),
        Index(value = ["crsCode"]),
        Index(value = ["normalizedName"])
    ]
)
data class GbStationCrsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val stationId: String,
    val crsCode: String,
    val stationName: String,
    val normalizedName: String,
    val modes: String = "" // Comma-separated transit modes
)

typealias StationCrsEntity = GbStationCrsEntity
