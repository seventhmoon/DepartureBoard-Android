package com.androidfung.departureboard.data.gtfs

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * GTFS static stop entity representing a station, stop, or platform.
 */
@Entity(
    tableName = "gtfs_stops",
    indices = [
        Index(value = ["stopName"]),
        Index(value = ["parentStation"])
    ]
)
data class GtfsStopEntity(
    @PrimaryKey
    val stopId: String,
    val stopName: String,
    val stopLat: Double? = null,
    val stopLon: Double? = null,
    val locationType: Int = 0, // 0 = stop/platform, 1 = station
    val parentStation: String? = null
)

/**
 * GTFS static transit route entity.
 */
@Entity(
    tableName = "gtfs_routes",
    indices = [
        Index(value = ["routeShortName"])
    ]
)
data class GtfsRouteEntity(
    @PrimaryKey
    val routeId: String,
    val agencyId: String? = null,
    val routeShortName: String,
    val routeLongName: String,
    val routeType: Int, // 0 = Tram, 1 = Metro, 2 = Rail, 3 = Bus, 4 = Ferry
    val routeColor: String? = null,
    val routeTextColor: String? = null
)

/**
 * GTFS static trip entity representing an individual scheduled journey.
 */
@Entity(
    tableName = "gtfs_trips",
    indices = [
        Index(value = ["routeId"]),
        Index(value = ["serviceId"])
    ]
)
data class GtfsTripEntity(
    @PrimaryKey
    val tripId: String,
    val routeId: String,
    val serviceId: String,
    val tripHeadsign: String? = null,
    val directionId: Int? = null
)

/**
 * GTFS static stop time entity representing a scheduled calling point on a trip.
 */
@Entity(
    tableName = "gtfs_stop_times",
    indices = [
        Index(value = ["stopId", "departureTimeSeconds"]),
        Index(value = ["tripId", "stopSequence"])
    ]
)
data class GtfsStopTimeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tripId: String,
    val stopId: String,
    val stopSequence: Int,
    val arrivalTimeSeconds: Int,
    val departureTimeSeconds: Int,
    val stopHeadsign: String? = null
)
