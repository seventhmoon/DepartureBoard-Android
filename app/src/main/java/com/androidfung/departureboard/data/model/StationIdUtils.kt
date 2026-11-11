package com.androidfung.departureboard.data.model

import com.androidfung.departureboard.data.model.hk.HkMtrStationRef as HkMtrStationRefInternal
import com.androidfung.departureboard.data.model.hk.parseHkMtrStationId as parseHkMtrStationIdInternal
import com.androidfung.departureboard.data.model.hk.extractRawHkBusStopId as extractRawHkBusStopIdInternal

typealias HkMtrStationRef = HkMtrStationRefInternal
fun String.parseHkMtrStationId(): HkMtrStationRef = parseHkMtrStationIdInternal()
fun extractRawHkBusStopId(stationId: String): String = extractRawHkBusStopIdInternal(stationId)

/**
 * Common prefixes and utility functions for transit station and line identifiers across jurisdictions.
 */
object StationIdPrefixes {
    // Hong Kong prefixes
    const val HK = "HK_"
    const val HK_MTR = "HK_MTR_"
    const val HK_LRT = "HK_LRT_"
    const val HK_GMB = "HK_GMB_"
    const val HK_BUS = "HK_BUS_"
    const val HK_BUS_KMB = "HK_BUS_KMB_"
    const val HK_BUS_CTB = "HK_BUS_CTB_"
    const val HK_KMB = "HK_KMB_"
    const val HK_CTB = "HK_CTB_"

    // Hong Kong Bus line/route prefixes
    const val ROUTE_KMB = "KMB_"
    const val ROUTE_CTB = "CTB_"

    // Singapore prefixes
    const val SG = "SG_"
    const val SG_MRT = "SG_MRT_"
    const val SG_BUS = "SG_BUS_"

    // Sydney / Australia prefixes
    const val AU = "AU_"
    const val NSW = "NSW_"
    const val AU_TRAIN = "AU_TRAIN_"
    const val AU_METRO = "AU_METRO_"

    // Tokyo / Japan prefixes
    const val JP = "JP_"
    const val TYO = "TYO_"
    const val JP_METRO = "JP_METRO_"
    const val JP_JR = "JP_JR_"

    // UK / GB prefixes
    const val BODS_LINE = "bods-"
    const val BODS = "BODS"
    const val TFL_METRO_TRAM = "940G"
    const val TFL_NATIONAL_RAIL = "910G"
    const val UK_BUS_STOP = "490"
}

/**
 * Returns true if this string represents any Hong Kong station or transit identifier.
 */
val String.isHkTransitId: Boolean
    get() = startsWith(StationIdPrefixes.HK, ignoreCase = true)

/**
 * Returns true if this string represents any Singapore station or transit identifier.
 */
val String.isSgTransitId: Boolean
    get() = startsWith(StationIdPrefixes.SG, ignoreCase = true)

/**
 * Returns true if this string represents any Sydney / NSW station or transit identifier.
 */
val String.isAuTransitId: Boolean
    get() = startsWith(StationIdPrefixes.AU, ignoreCase = true) ||
            startsWith(StationIdPrefixes.NSW, ignoreCase = true)

/**
 * Returns true if this string represents any Tokyo / Japan station or transit identifier.
 */
val String.isJpTransitId: Boolean
    get() = startsWith(StationIdPrefixes.JP, ignoreCase = true) ||
            startsWith(StationIdPrefixes.TYO, ignoreCase = true)

/**
 * Returns true if this station ID represents an MTR heavy-rail station.
 */
val String.isHkMtrStationId: Boolean
    get() = startsWith(StationIdPrefixes.HK_MTR, ignoreCase = true) ||
            (startsWith(StationIdPrefixes.HK, ignoreCase = true) && !isHkBusStationId && !isHkLightRailStationId)

/**
 * Returns true if this station ID represents a Hong Kong MTR Light Rail stop (`HK_LRT_{station_id}`).
 */
val String.isHkLightRailStationId: Boolean
    get() = startsWith(StationIdPrefixes.HK_LRT, ignoreCase = true)

/**
 * Returns true if this station ID represents a Hong Kong Green Minibus (GMB) stop (`HK_GMB_{stop_id}`).
 */
val String.isHkGmbStopId: Boolean
    get() = startsWith(StationIdPrefixes.HK_GMB, ignoreCase = true)

/**
 * Returns true if this station ID represents a Hong Kong bus or Green Minibus stop.
 */
val String.isHkBusStationId: Boolean
    get() = startsWith(StationIdPrefixes.HK_BUS, ignoreCase = true) ||
            startsWith(StationIdPrefixes.HK_KMB, ignoreCase = true) ||
            startsWith(StationIdPrefixes.HK_CTB, ignoreCase = true) ||
            startsWith(StationIdPrefixes.HK_GMB, ignoreCase = true)

/**
 * Returns true if this route or line identifier represents a Hong Kong bus route.
 */
val String.isHkBusRouteId: Boolean
    get() = startsWith(StationIdPrefixes.HK_BUS, ignoreCase = true) ||
            startsWith(StationIdPrefixes.ROUTE_KMB, ignoreCase = true) ||
            startsWith(StationIdPrefixes.ROUTE_CTB, ignoreCase = true)

/**
 * Returns true if this station or line belongs to the UK Bus Open Data Service (BODS).
 */
val String.isBodsTransitId: Boolean
    get() = startsWith(StationIdPrefixes.BODS_LINE, ignoreCase = true) ||
            startsWith(StationIdPrefixes.BODS, ignoreCase = true)

/**
 * Returns true if this identifier represents a UK National Rail station.
 */
val String.isNationalRailStationId: Boolean
    get() = startsWith(StationIdPrefixes.TFL_NATIONAL_RAIL, ignoreCase = true)

/**
 * Convenience properties on [Station].
 */
val Station.isHkStation: Boolean
    get() = id.isHkTransitId

val Station.isHkMtr: Boolean
    get() = id.isHkMtrStationId

val Station.isHkBus: Boolean
    get() = id.isHkBusStationId

val Station.isNationalRail: Boolean
    get() = id.isNationalRailStationId

/**
 * Convenience properties on [Departure].
 */
val Departure.isHkDeparture: Boolean
    get() = stationId.isHkTransitId || lineId.isHkTransitId || id.isHkTransitId

val Departure.isHkMtr: Boolean
    get() = stationId.isHkMtrStationId || id.isHkMtrStationId

val Departure.isHkBus: Boolean
    get() = lineId.isHkBusRouteId || stationId.isHkBusStationId

val Departure.isBods: Boolean
    get() = lineId.isBodsTransitId
