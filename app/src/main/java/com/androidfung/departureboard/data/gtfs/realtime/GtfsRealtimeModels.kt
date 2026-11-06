package com.androidfung.departureboard.data.gtfs.realtime

import com.androidfung.departureboard.data.model.GtfsAlertEffect
import com.androidfung.departureboard.data.model.GtfsAlertSeverity
import com.androidfung.departureboard.data.model.GtfsOccupancyStatus
import com.androidfung.departureboard.data.model.GtfsScheduleRelationship

data class GtfsRtTripDescriptor(
    val tripId: String?,
    val routeId: String?,
    val directionId: Int? = null,
    val startTime: String? = null,
    val startDate: String? = null,
    val scheduleRelationship: GtfsScheduleRelationship = GtfsScheduleRelationship.SCHEDULED
)

data class GtfsRtVehicleDescriptor(
    val id: String?,
    val label: String? = null,
    val licensePlate: String? = null
)

data class GtfsRtStopTimeEvent(
    val delay: Int? = null,       // Delay in seconds (+ for late, - for early)
    val time: Long? = null,       // Epoch timestamp in seconds
    val uncertainty: Int? = null
)

data class GtfsRtStopTimeUpdate(
    val stopSequence: Int? = null,
    val stopId: String? = null,
    val arrival: GtfsRtStopTimeEvent? = null,
    val departure: GtfsRtStopTimeEvent? = null,
    val scheduleRelationship: GtfsScheduleRelationship = GtfsScheduleRelationship.SCHEDULED
)

data class GtfsRtTripUpdate(
    val trip: GtfsRtTripDescriptor,
    val vehicle: GtfsRtVehicleDescriptor? = null,
    val stopTimeUpdates: List<GtfsRtStopTimeUpdate> = emptyList(),
    val timestamp: Long? = null,
    val delay: Int? = null
)

data class GtfsRtPosition(
    val latitude: Double,
    val longitude: Double,
    val bearing: Float? = null,
    val odometer: Double? = null,
    val speed: Float? = null
)

data class GtfsRtVehiclePosition(
    val trip: GtfsRtTripDescriptor? = null,
    val vehicle: GtfsRtVehicleDescriptor? = null,
    val position: GtfsRtPosition? = null,
    val currentStopSequence: Int? = null,
    val stopId: String? = null,
    val currentStatus: String? = null, // INCOMING_AT, STOPPED_AT, IN_TRANSIT_TO
    val timestamp: Long? = null,
    val occupancyStatus: GtfsOccupancyStatus? = null,
    val occupancyPercentage: Int? = null
)

data class GtfsRtEntitySelector(
    val agencyId: String? = null,
    val routeId: String? = null,
    val routeType: Int? = null,
    val stopId: String? = null,
    val tripId: String? = null
)

data class GtfsRtAlert(
    val informedEntities: List<GtfsRtEntitySelector> = emptyList(),
    val cause: String? = null,
    val effect: GtfsAlertEffect? = null,
    val url: String? = null,
    val headerText: String? = null,
    val descriptionText: String? = null,
    val severityLevel: GtfsAlertSeverity = GtfsAlertSeverity.INFO
)

data class GtfsRtFeedMessage(
    val headerTimestamp: Long,
    val tripUpdates: List<GtfsRtTripUpdate> = emptyList(),
    val vehiclePositions: List<GtfsRtVehiclePosition> = emptyList(),
    val alerts: List<GtfsRtAlert> = emptyList()
)
