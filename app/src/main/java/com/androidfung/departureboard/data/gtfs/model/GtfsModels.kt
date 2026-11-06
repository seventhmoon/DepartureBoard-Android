package com.androidfung.departureboard.data.gtfs.model

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.model.GtfsLocationType
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.StationLineInfo
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.model.WheelchairAccessibility

/**
 * GTFS Agency representation (agency.txt).
 * Transit agency providing public transit service.
 */
data class GtfsAgency(
    val agencyId: String,
    val agencyName: String,
    val agencyUrl: String? = null,
    val agencyTimezone: String,
    val agencyLang: String? = null,
    val agencyPhone: String? = null,
    val agencyFareUrl: String? = null,
    val agencyEmail: String? = null
)

/**
 * GTFS Stop or Station representation (stops.txt).
 * Represents an individual platform, stop, or station complex.
 */
data class GtfsStop(
    val stopId: String,
    val stopCode: String? = null,
    val stopName: String,
    val stopDesc: String? = null,
    val stopLat: Double? = null,
    val stopLon: Double? = null,
    val zoneId: String? = null,
    val stopUrl: String? = null,
    val locationType: Int = 0, // 0=Stop/Platform, 1=Station, 2=Entrance/Exit, 3=Generic Node, 4=Boarding Area
    val parentStation: String? = null,
    val stopTimezone: String? = null,
    val wheelchairBoarding: Int = 0, // 0=No info, 1=Accessible, 2=Not accessible
    val levelId: String? = null,
    val platformCode: String? = null
) {
    val gtfsLocationType: GtfsLocationType
        get() = GtfsLocationType.fromCode(locationType)

    val wheelchairAccessibility: WheelchairAccessibility
        get() = WheelchairAccessibility.fromCode(wheelchairBoarding)

    fun toStation(
        modes: List<String> = emptyList(),
        lines: List<StationLineInfo> = emptyList()
    ): Station = Station(
        id = stopId,
        name = stopName,
        modes = modes,
        zone = zoneId,
        lat = stopLat,
        lon = stopLon,
        lines = lines,
        locationType = gtfsLocationType,
        parentStationId = parentStation,
        platformCode = platformCode,
        wheelchairBoarding = wheelchairAccessibility
    )
}

/**
 * GTFS Route representation (routes.txt).
 * Represents a transit line or commercial route.
 */
data class GtfsRoute(
    val routeId: String,
    val agencyId: String? = null,
    val routeShortName: String? = null,
    val routeLongName: String? = null,
    val routeDesc: String? = null,
    val routeType: Int, // 0=Tram, 1=Subway/Metro, 2=Rail, 3=Bus, 4=Ferry, 5=Cable Tram, 6=Aerial, 7=Funicular, 11=Trolleybus, 12=Monorail
    val routeUrl: String? = null,
    val routeColor: String? = null,     // 6-character hex without # (e.g. "0019A8")
    val routeTextColor: String? = null, // 6-character hex without # (e.g. "FFFFFF")
    val routeSortOrder: Int? = null,
    val continuousPickup: Int? = null,
    val continuousDropOff: Int? = null,
    val networkId: String? = null
) {
    val displayName: String
        get() = routeShortName?.takeIf { it.isNotBlank() }
            ?: routeLongName?.takeIf { it.isNotBlank() }
            ?: routeId

    val transitMode: TransitMode
        get() = when (routeType) {
            0, 900 -> TransitMode.TRAM
            1, 400 -> TransitMode.METRO
            2, 100 -> TransitMode.RAIL
            3, 700 -> TransitMode.BUS
            4, 1000 -> TransitMode.FERRY
            6, 1300 -> TransitMode.CABLE_CAR
            7, 1400 -> TransitMode.FUNICULAR
            12 -> TransitMode.MONORAIL
            else -> TransitMode.OTHER
        }

    fun toLineBadgeInfo(
        statusSeverity: Int = 10,
        statusDescription: String? = null,
        disruptionReason: String? = null
    ): LineBadgeInfo {
        val bg = parseHexColor(routeColor, Color(0xFF1E88E5))
        val fg = parseHexColor(routeTextColor, Color.White)

        return LineBadgeInfo(
            lineId = routeId,
            displayName = displayName,
            backgroundColor = bg,
            textColor = fg,
            mode = transitMode,
            statusSeverity = statusSeverity,
            statusDescription = statusDescription,
            disruptionReason = disruptionReason,
            lineCode = routeShortName ?: ""
        )
    }

    private companion object {
        fun parseHexColor(hex: String?, fallback: Color): Color {
            if (hex.isNullOrBlank()) return fallback
            return try {
                val clean = hex.removePrefix("#").trim()
                if (clean.length == 6) {
                    val r = clean.substring(0, 2).toInt(16)
                    val g = clean.substring(2, 4).toInt(16)
                    val b = clean.substring(4, 6).toInt(16)
                    Color(r, g, b)
                } else fallback
            } catch (_: Exception) {
                fallback
            }
        }
    }
}

/**
 * GTFS Trip representation (trips.txt).
 * A specific journey along a route.
 */
data class GtfsTrip(
    val tripId: String,
    val routeId: String,
    val serviceId: String,
    val tripHeadsign: String? = null,
    val tripShortName: String? = null,
    val directionId: Int? = null, // 0 = outbound / one direction, 1 = inbound / opposite
    val blockId: String? = null,
    val shapeId: String? = null,
    val wheelchairAccessible: Int = 0,
    val bikesAllowed: Int = 0
)

/**
 * GTFS Stop Time representation (stop_times.txt).
 * Scheduled arrival/departure of a trip at a specific stop sequence.
 */
data class GtfsStopTime(
    val tripId: String,
    val arrivalTime: String? = null,   // HH:MM:SS format
    val departureTime: String? = null, // HH:MM:SS format
    val stopId: String,
    val stopSequence: Int,
    val stopHeadsign: String? = null,
    val pickupType: Int = 0,
    val dropOffType: Int = 0,
    val shapeDistTraveled: Double? = null,
    val timepoint: Int = 1
)
