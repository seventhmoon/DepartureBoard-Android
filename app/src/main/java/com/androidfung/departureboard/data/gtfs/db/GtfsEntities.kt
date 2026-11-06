package com.androidfung.departureboard.data.gtfs.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.androidfung.departureboard.data.gtfs.model.GtfsRoute
import com.androidfung.departureboard.data.gtfs.model.GtfsStop
import com.androidfung.departureboard.data.gtfs.model.GtfsStopTime
import com.androidfung.departureboard.data.gtfs.model.GtfsTrip

@Entity(
    tableName = "gtfs_stops",
    indices = [
        Index(value = ["stopName"]),
        Index(value = ["parentStation"]),
        Index(value = ["stopCode"])
    ]
)
data class GtfsStopEntity(
    @PrimaryKey
    val stopId: String,
    val stopCode: String?,
    val stopName: String,
    val stopDesc: String?,
    val stopLat: Double?,
    val stopLon: Double?,
    val zoneId: String?,
    val locationType: Int,
    val parentStation: String?,
    val wheelchairBoarding: Int,
    val platformCode: String?
) {
    fun toGtfsStop(): GtfsStop = GtfsStop(
        stopId = stopId,
        stopCode = stopCode,
        stopName = stopName,
        stopDesc = stopDesc,
        stopLat = stopLat,
        stopLon = stopLon,
        zoneId = zoneId,
        locationType = locationType,
        parentStation = parentStation,
        wheelchairBoarding = wheelchairBoarding,
        platformCode = platformCode
    )

    companion object {
        fun fromGtfsStop(stop: GtfsStop): GtfsStopEntity = GtfsStopEntity(
            stopId = stop.stopId,
            stopCode = stop.stopCode,
            stopName = stop.stopName,
            stopDesc = stop.stopDesc,
            stopLat = stop.stopLat,
            stopLon = stop.stopLon,
            zoneId = stop.zoneId,
            locationType = stop.locationType,
            parentStation = stop.parentStation,
            wheelchairBoarding = stop.wheelchairBoarding,
            platformCode = stop.platformCode
        )
    }
}

@Entity(
    tableName = "gtfs_routes",
    indices = [
        Index(value = ["agencyId"]),
        Index(value = ["routeShortName"])
    ]
)
data class GtfsRouteEntity(
    @PrimaryKey
    val routeId: String,
    val agencyId: String?,
    val routeShortName: String?,
    val routeLongName: String?,
    val routeType: Int,
    val routeColor: String?,
    val routeTextColor: String?
) {
    fun toGtfsRoute(): GtfsRoute = GtfsRoute(
        routeId = routeId,
        agencyId = agencyId,
        routeShortName = routeShortName,
        routeLongName = routeLongName,
        routeType = routeType,
        routeColor = routeColor,
        routeTextColor = routeTextColor
    )

    companion object {
        fun fromGtfsRoute(route: GtfsRoute): GtfsRouteEntity = GtfsRouteEntity(
            routeId = route.routeId,
            agencyId = route.agencyId,
            routeShortName = route.routeShortName,
            routeLongName = route.routeLongName,
            routeType = route.routeType,
            routeColor = route.routeColor,
            routeTextColor = route.routeTextColor
        )
    }
}

@Entity(
    tableName = "gtfs_trips",
    indices = [
        Index(value = ["routeId"]),
        Index(value = ["directionId"])
    ]
)
data class GtfsTripEntity(
    @PrimaryKey
    val tripId: String,
    val routeId: String,
    val serviceId: String,
    val tripHeadsign: String?,
    val tripShortName: String?,
    val directionId: Int?,
    val wheelchairAccessible: Int
) {
    fun toGtfsTrip(): GtfsTrip = GtfsTrip(
        tripId = tripId,
        routeId = routeId,
        serviceId = serviceId,
        tripHeadsign = tripHeadsign,
        tripShortName = tripShortName,
        directionId = directionId,
        wheelchairAccessible = wheelchairAccessible
    )

    companion object {
        fun fromGtfsTrip(trip: GtfsTrip): GtfsTripEntity = GtfsTripEntity(
            tripId = trip.tripId,
            routeId = trip.routeId,
            serviceId = trip.serviceId,
            tripHeadsign = trip.tripHeadsign,
            tripShortName = trip.tripShortName,
            directionId = trip.directionId,
            wheelchairAccessible = trip.wheelchairAccessible
        )
    }
}

@Entity(
    tableName = "gtfs_stop_times",
    primaryKeys = ["tripId", "stopSequence"],
    indices = [
        Index(value = ["stopId"]),
        Index(value = ["tripId"])
    ]
)
data class GtfsStopTimeEntity(
    val tripId: String,
    val arrivalTime: String?,
    val departureTime: String?,
    val stopId: String,
    val stopSequence: Int,
    val stopHeadsign: String?,
    val pickupType: Int,
    val dropOffType: Int
) {
    fun toGtfsStopTime(): GtfsStopTime = GtfsStopTime(
        tripId = tripId,
        arrivalTime = arrivalTime,
        departureTime = departureTime,
        stopId = stopId,
        stopSequence = stopSequence,
        stopHeadsign = stopHeadsign,
        pickupType = pickupType,
        dropOffType = dropOffType
    )

    companion object {
        fun fromGtfsStopTime(st: GtfsStopTime): GtfsStopTimeEntity = GtfsStopTimeEntity(
            tripId = st.tripId,
            arrivalTime = st.arrivalTime,
            departureTime = st.departureTime,
            stopId = st.stopId,
            stopSequence = st.stopSequence,
            stopHeadsign = st.stopHeadsign,
            pickupType = st.pickupType,
            dropOffType = st.dropOffType
        )
    }
}
