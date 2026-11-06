package com.androidfung.departureboard.data.model.hk

import com.androidfung.departureboard.data.model.StationIdPrefixes

/**
 * Parsed components of an HK MTR station identifier (e.g. "HK_MTR_ISL_ADM" -> lineCode="ISL", stationCode="ADM").
 */
data class HkMtrStationRef(
    val lineCode: String?,
    val stationCode: String
)

/**
 * Parses an HK MTR station identifier ("HK_MTR_ISL_ADM", "HK_MTR_ADM", or "ADM")
 * into its optional line code and 3-letter station code.
 */
fun String.parseHkMtrStationId(): HkMtrStationRef {
    val clean = removePrefix(StationIdPrefixes.HK_MTR).trim()
    val parts = clean.split("_").filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> HkMtrStationRef(lineCode = parts[0].uppercase(), stationCode = parts[1].uppercase())
        parts.size == 1 -> HkMtrStationRef(lineCode = null, stationCode = parts[0].uppercase())
        else -> HkMtrStationRef(lineCode = null, stationCode = "")
    }
}

/**
 * Removes operator and region prefixes from an HK bus stop ID to get the raw operator stop ID.
 */
fun extractRawHkBusStopId(stationId: String): String {
    return when {
        stationId.startsWith(StationIdPrefixes.HK_BUS_KMB, ignoreCase = true) -> stationId.substring(StationIdPrefixes.HK_BUS_KMB.length)
        stationId.startsWith(StationIdPrefixes.HK_BUS_CTB, ignoreCase = true) -> stationId.substring(StationIdPrefixes.HK_BUS_CTB.length)
        stationId.startsWith(StationIdPrefixes.HK_BUS, ignoreCase = true) -> stationId.substring(StationIdPrefixes.HK_BUS.length)
        stationId.startsWith(StationIdPrefixes.HK_KMB, ignoreCase = true) -> stationId.substring(StationIdPrefixes.HK_KMB.length)
        stationId.startsWith(StationIdPrefixes.HK_CTB, ignoreCase = true) -> stationId.substring(StationIdPrefixes.HK_CTB.length)
        else -> stationId
    }
}
