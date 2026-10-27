package com.androidfung.departureboard.data.routes

import com.androidfung.departureboard.data.model.CallingPoint
import com.androidfung.departureboard.data.model.TransitDirection
import com.androidfung.departureboard.data.model.TransitMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Station sequence routes for Tyne and Wear Metro (Green Line and Yellow Line).
 * Enables complete calling points timeline reconstruction and direction resolution
 * (including Clockwise / Anti-Clockwise for the Yellow Line loop).
 */
object TyneAndWearMetroRoute : MetroRoute<TyneAndWearMetroStation> {

    private val UK_ZONE = ZoneId.of("Europe/London")
    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

    // Green Line: South Hylton (index 0) <-> Airport (index 30)
    val GREEN_LINE: List<TyneAndWearMetroStation> = listOf(
        TyneAndWearMetroStation.SOUTH_HYLTON,
        TyneAndWearMetroStation.PALLION,
        TyneAndWearMetroStation.MILLFIELD,
        TyneAndWearMetroStation.UNIVERSITY,
        TyneAndWearMetroStation.PARK_LANE,
        TyneAndWearMetroStation.SUNDERLAND,
        TyneAndWearMetroStation.ST_PETERS,
        TyneAndWearMetroStation.STADIUM_OF_LIGHT,
        TyneAndWearMetroStation.SEABURN,
        TyneAndWearMetroStation.EAST_BOLDON,
        TyneAndWearMetroStation.BROCKLEY_WHINS,
        TyneAndWearMetroStation.FELLGATE,
        TyneAndWearMetroStation.PELAW,
        TyneAndWearMetroStation.HEWORTH,
        TyneAndWearMetroStation.FELLING,
        TyneAndWearMetroStation.GATESHEAD_STADIUM,
        TyneAndWearMetroStation.GATESHEAD,
        TyneAndWearMetroStation.CENTRAL_STATION,
        TyneAndWearMetroStation.MONUMENT,
        TyneAndWearMetroStation.HAYMARKET,
        TyneAndWearMetroStation.JESMOND,
        TyneAndWearMetroStation.WEST_JESMOND,
        TyneAndWearMetroStation.ILFORD_ROAD,
        TyneAndWearMetroStation.SOUTH_GOSFORTH,
        TyneAndWearMetroStation.REGENT_CENTRE,
        TyneAndWearMetroStation.WANSBECK_ROAD,
        TyneAndWearMetroStation.FAWDON,
        TyneAndWearMetroStation.KINGSTON_PARK,
        TyneAndWearMetroStation.BANK_FOOT,
        TyneAndWearMetroStation.CALLERTON_PARKWAY,
        TyneAndWearMetroStation.AIRPORT
    )

    // Yellow Line: Circular lasso line between St James and South Shields via the Coast and City Centre.
    // St James -> Coast -> South Gosforth -> Monument -> South Shields represents the forward/clockwise loop.
    val YELLOW_LINE: List<TyneAndWearMetroStation> = listOf(
        TyneAndWearMetroStation.ST_JAMES,
        TyneAndWearMetroStation.MONUMENT,
        TyneAndWearMetroStation.MANORS,
        TyneAndWearMetroStation.BYKER,
        TyneAndWearMetroStation.CHILLINGHAM_ROAD,
        TyneAndWearMetroStation.WALKERGATE,
        TyneAndWearMetroStation.WALLSEND,
        TyneAndWearMetroStation.HADRIAN_ROAD,
        TyneAndWearMetroStation.HOWDON,
        TyneAndWearMetroStation.PERCY_MAIN,
        TyneAndWearMetroStation.MEADOW_WELL,
        TyneAndWearMetroStation.NORTH_SHIELDS,
        TyneAndWearMetroStation.TYNEMOUTH,
        TyneAndWearMetroStation.CULLERCOATS,
        TyneAndWearMetroStation.WHITLEY_BAY,
        TyneAndWearMetroStation.MONKSEATON,
        TyneAndWearMetroStation.WEST_MONKSEATON,
        TyneAndWearMetroStation.SHIREMOOR,
        TyneAndWearMetroStation.PALMERSVILLE,
        TyneAndWearMetroStation.NORTHUMBERLAND_PARK,
        TyneAndWearMetroStation.BENTON,
        TyneAndWearMetroStation.FOUR_LANE_ENDS,
        TyneAndWearMetroStation.LONGBENTON,
        TyneAndWearMetroStation.SOUTH_GOSFORTH,
        TyneAndWearMetroStation.ILFORD_ROAD,
        TyneAndWearMetroStation.WEST_JESMOND,
        TyneAndWearMetroStation.JESMOND,
        TyneAndWearMetroStation.HAYMARKET,
        TyneAndWearMetroStation.MONUMENT,
        TyneAndWearMetroStation.CENTRAL_STATION,
        TyneAndWearMetroStation.GATESHEAD,
        TyneAndWearMetroStation.GATESHEAD_STADIUM,
        TyneAndWearMetroStation.FELLING,
        TyneAndWearMetroStation.HEWORTH,
        TyneAndWearMetroStation.PELAW,
        TyneAndWearMetroStation.HEBBURN,
        TyneAndWearMetroStation.JARROW,
        TyneAndWearMetroStation.BEDE,
        TyneAndWearMetroStation.SIMONSIDE,
        TyneAndWearMetroStation.TYNE_DOCK,
        TyneAndWearMetroStation.CHICHESTER,
        TyneAndWearMetroStation.SOUTH_SHIELDS
    )

    override val lineSequences: Map<String, List<TyneAndWearMetroStation>> = mapOf(
        "metro-green" to GREEN_LINE,
        "metro-yellow" to YELLOW_LINE
    )

    /**
     * Resolves calling points from origin station to destination on Green or Yellow Line.
     */
    override fun resolveCallingPoints(
        lineId: String,
        originName: String,
        destinationName: String,
        departureSeconds: Int
    ): List<CallingPoint> {
        val seq = if (lineId.contains("green", ignoreCase = true)) GREEN_LINE else YELLOW_LINE
        val originStation = TyneAndWearMetroStation.fromString(originName)
        val destStation = TyneAndWearMetroStation.fromString(destinationName)

        val originIdx = if (originStation != null) {
            seq.indexOf(originStation)
        } else {
            seq.indexOfFirst { station ->
                station.stationName.contains(originName, ignoreCase = true) ||
                    originName.contains(station.stationName, ignoreCase = true)
            }
        }
        if (originIdx == -1) return emptyList()

        val destIdx = if (destStation != null) {
            seq.indexOf(destStation)
        } else {
            seq.indexOfFirst { station ->
                station.stationName.contains(destinationName, ignoreCase = true) ||
                    destinationName.contains(station.stationName, ignoreCase = true)
            }
        }

        val range = when {
            destIdx == -1 -> seq.subList(originIdx, seq.size)
            destIdx >= originIdx -> seq.subList(originIdx, destIdx + 1)
            else -> seq.subList(destIdx, originIdx + 1).reversed()
        }

        val baseEpoch = Instant.now().epochSecond + departureSeconds

        return range.mapIndexed { idx, station ->
            val stopEpoch = baseEpoch + (idx * 120L)
            val clockTime = Instant.ofEpochSecond(stopEpoch).atZone(UK_ZONE).format(TIME_FORMATTER)
            val estBadge = when {
                idx == 0 -> if (departureSeconds <= 45) "Due" else "${departureSeconds / 60} mins"
                else -> "${(departureSeconds + idx * 120) / 60} mins"
            }

            CallingPoint(
                stationName = station.stationName,
                scheduledTime = clockTime,
                estimatedTime = estBadge,
                isCurrentStation = idx == 0,
                isDestination = idx == range.lastIndex,
                modes = listOf(TransitMode.TUBE.id)
            )
        }
    }

    /**
     * Resolves directional flow for Green Line (Northbound vs Southbound)
     * and Yellow Line (Clockwise vs Anti-Clockwise for the loop, or Inbound/Outbound).
     */
    override fun resolveDirection(
        lineId: String,
        originName: String,
        destinationName: String,
        platformName: String?
    ): TransitDirection? {
        val isGreen = lineId.contains("green", ignoreCase = true)
        val originStation = TyneAndWearMetroStation.fromString(originName) ?: return null
        val destStation = TyneAndWearMetroStation.fromString(destinationName) ?: return null

        if (isGreen) {
            val originIdx = GREEN_LINE.indexOf(originStation)
            val destIdx = GREEN_LINE.indexOf(destStation)
            if (originIdx == -1 || destIdx == -1 || (originIdx == destIdx)) return null
            // Index 0 is South Hylton (South), Index 30 is Airport (North)
            return if (destIdx > originIdx) TransitDirection.NORTHBOUND else TransitDirection.SOUTHBOUND
        } else {
            // Yellow Line: Loop between St James and South Shields via the coast
            // If traversing in forward sequence (St James -> North Shields -> South Gosforth -> Monument -> South Shields)
            // that represents the Clockwise loop around North Tyneside / Newcastle.
            val originIdx = YELLOW_LINE.indexOf(originStation)
            val destIdx = YELLOW_LINE.indexOf(destStation)
            if (originIdx == -1 || destIdx == -1 || (originIdx == destIdx)) return null

            return if (destIdx > originIdx) TransitDirection.CLOCKWISE else TransitDirection.ANTI_CLOCKWISE
        }
    }
}
