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
    val locationType: Int = 0, // 0 = stop/platform, 1 = station, 2 = entrance/exit, 3 = generic node, 4 = boarding area
    val parentStation: String? = null,
    val platformCode: String? = null,
    val wheelchairBoarding: Int = 0, // 0 = unknown/inherit, 1 = accessible, 2 = not accessible
    val zoneId: String? = null
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
    val directionId: Int? = null,
    val wheelchairAccessible: Int = 0, // 0 = unknown, 1 = accessible, 2 = not accessible
    val bikesAllowed: Int = 0,         // 0 = unknown, 1 = allowed, 2 = not allowed
    val blockId: String? = null
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
    val stopHeadsign: String? = null,
    val pickupType: Int = 0,  // 0 = regular, 1 = none, 2 = phone agency, 3 = coordinate with driver
    val dropOffType: Int = 0, // 0 = regular, 1 = none, 2 = phone agency, 3 = coordinate with driver
    val timepoint: Int = 1    // 0 = approximate times, 1 = exact times
)

/**
 * GTFS calendar.txt entity defining weekly service patterns and validity dates (YYYYMMDD).
 */
@Entity(tableName = "gtfs_calendar")
data class GtfsCalendarEntity(
    @PrimaryKey
    val serviceId: String,
    val monday: Int = 0,
    val tuesday: Int = 0,
    val wednesday: Int = 0,
    val thursday: Int = 0,
    val friday: Int = 0,
    val saturday: Int = 0,
    val sunday: Int = 0,
    val startDate: String, // YYYYMMDD
    val endDate: String    // YYYYMMDD
)

/**
 * GTFS calendar_dates.txt entity defining date-specific service additions (1) or removals (2).
 */
@Entity(
    tableName = "gtfs_calendar_dates",
    primaryKeys = ["serviceId", "date"],
    indices = [Index(value = ["date"])]
)
data class GtfsCalendarDateEntity(
    val serviceId: String,
    val date: String,      // YYYYMMDD
    val exceptionType: Int // 1 = Service added for date, 2 = Service removed for date
)

/**
 * GTFS levels.txt entity defining vertical station levels (mezzanine, concourse, deep-level platforms).
 */
@Entity(tableName = "gtfs_levels")
data class GtfsLevelEntity(
    @PrimaryKey
    val levelId: String,
    val levelIndex: Double,
    val levelName: String? = null
)

/**
 * GTFS pathways.txt entity defining indoor station graph edges (walkways, stairs, escalators, elevators).
 */
@Entity(
    tableName = "gtfs_pathways",
    indices = [
        Index(value = ["fromStopId"]),
        Index(value = ["toStopId"])
    ]
)
data class GtfsPathwayEntity(
    @PrimaryKey
    val pathwayId: String,
    val fromStopId: String,
    val toStopId: String,
    val pathwayMode: Int, // 1=walkway, 2=stairs, 3=moving_sidewalk, 4=escalator, 5=elevator, 6=fare_gate, 7=exit_gate
    val isBidirectional: Int = 1,
    val length: Double? = null,
    val traversalTime: Int? = null,
    val stairCount: Int? = null,
    val maxSlope: Double? = null,
    val minWidth: Double? = null,
    val signpostedAs: String? = null,
    val reversedSignpostedAs: String? = null
)
