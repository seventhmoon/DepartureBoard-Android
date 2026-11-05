package com.androidfung.departureboard.data.model.hk

import com.androidfung.departureboard.data.model.CallingPoint
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TransitDirection
import com.androidfung.departureboard.data.model.isHkMtr
import com.androidfung.departureboard.data.topology.LinearRouteCallingPointsResolver
import com.androidfung.departureboard.data.topology.TransitLineNetwork
import java.time.ZoneId

/**
 * Route station sequence and interchange lines definition for Hong Kong MTR.
 * Implements [TransitLineNetwork] so calling-point interpolation is shared across all regions.
 */
object HkMtrRoutes : TransitLineNetwork {

    override val timeZone: ZoneId = ZoneId.of("Asia/Hong_Kong")
    override val defaultMode: String = "mtr"
    override val averageSecondsBetweenStops: Long = 120L

    // Clean, ordered station code sequences along each line
    val LINE_SEQUENCES: Map<String, List<String>> = mapOf(
        "ISL" to listOf("KET", "HKU", "SYP", "SHW", "CEN", "ADM", "WAC", "CAB", "TIH", "FOH", "NOP", "QUB", "TAK", "SWH", "SKW", "HFC", "CHW"),
        "TWL" to listOf("CEN", "ADM", "TST", "JOR", "YMT", "MOK", "PRE", "SSP", "CSW", "LCK", "MEF", "LAK", "KWF", "KWH", "TWH", "TSW"),
        "KTL" to listOf("WHA", "HOM", "YMT", "MOK", "PRE", "SKM", "KOT", "LOF", "WTS", "DIH", "CHH", "KOB", "NTK", "KWT", "LAT", "YAT", "TIK"),
        "EAL" to listOf("ADM", "EXC", "HUH", "MKK", "KOT", "TAW", "SHT", "FOT", "UNI", "TAP", "TWO", "FAN", "SHS", "LOW", "LMC"),
        "EAL_RAC" to listOf("ADM", "EXC", "HUH", "MKK", "KOT", "TAW", "SHT", "RAC", "UNI", "TAP", "TWO", "FAN", "SHS", "LOW", "LMC"),
        "TML" to listOf("WKS", "MOS", "HEO", "TSH", "SHM", "CIO", "STW", "CKT", "TAW", "HIK", "DIH", "KAT", "SUW", "TKW", "HOM", "HUH", "ETS", "AUS", "NAC", "MEF", "TWW", "KSR", "YUL", "LOP", "TIS", "SIH", "TUM"),
        "TCL" to listOf("HOK", "KOW", "OLY", "NAC", "LAK", "TSY", "SUN", "TUC"),
        "AEL" to listOf("HOK", "KOW", "TSY", "AIR", "AWE"),
        "SIL" to listOf("ADM", "OCP", "WCH", "LET", "SOH"),
        "TKL" to listOf("NOP", "QUB", "YAT", "TIK", "TKO", "HAH", "POA", "LHP"),
        "DRL" to listOf("SUN", "DIS")
    )

    override val lineSequences: Map<String, List<String>> = LINE_SEQUENCES

    // Complete list of lines serving each station (Interchange Stations!)
    val STATION_LINES: Map<String, List<String>> = mapOf(
        "ADM" to listOf("ISL", "TWL", "EAL", "SIL"),
        "CEN" to listOf("ISL", "TWL", "TCL", "AEL"), // Central connected to Hong Kong Station
        "HOK" to listOf("TCL", "AEL", "ISL", "TWL"),
        "MOK" to listOf("TWL", "KTL"),
        "PRE" to listOf("TWL", "KTL"),
        "YMT" to listOf("TWL", "KTL"),
        "HOM" to listOf("KTL", "TML"),
        "HUH" to listOf("EAL", "TML"),
        "TAW" to listOf("EAL", "TML"),
        "KOT" to listOf("EAL", "KTL"),
        "DIH" to listOf("KTL", "TML"),
        "NOP" to listOf("ISL", "TKL"),
        "QUB" to listOf("ISL", "TKL"),
        "YAT" to listOf("KTL", "TKL"),
        "TIK" to listOf("KTL", "TKL"),
        "MEF" to listOf("TWL", "TML"),
        "LAK" to listOf("TWL", "TCL"),
        "NAC" to listOf("TCL", "TML"),
        "TSY" to listOf("TCL", "AEL"),
        "SUN" to listOf("TCL", "DRL"),
        "TST" to listOf("TWL", "TML") // TST connected with East Tsim Sha Tsui
    )

    override fun resolveSequenceKey(lineCode: String, originIdentifier: String, destIdentifier: String): String {
        val destClean = destIdentifier.trim().uppercase()
        val servesRacecourse = originIdentifier.equals("RAC", ignoreCase = true) ||
            destClean.equals("RAC", ignoreCase = true) ||
            destClean.contains("RACECOURSE") ||
            destClean.contains("馬場")
        return if (lineCode.equals("EAL", ignoreCase = true) && servesRacecourse) {
            "EAL_RAC"
        } else {
            lineCode.uppercase()
        }
    }

    override fun resolveStationName(stationCodeOrName: String): String =
        HkMtrStationRegistry.getName(stationCodeOrName)

    override fun resolveStationId(lineCode: String, stationCodeOrName: String): String =
        "HK_MTR_${lineCode.uppercase()}_${stationCodeOrName.uppercase()}"

    private val sharedCallingPointsResolver = object : LinearRouteCallingPointsResolver(this) {
        override fun canHandle(departure: Departure): Boolean = departure.isHkMtr
    }

    /**
     * Resolves cardinal or line direction (e.g. "Eastbound" / "Westbound" / "Northbound" / "Southbound")
     * for a train on a given MTR line travelling from origin station to destination station.
     */
    fun resolveDirection(lineCode: String, originSta: String, destSta: String): TransitDirection? {
        val seqKey = resolveSequenceKey(lineCode, originSta, destSta)
        val seq = LINE_SEQUENCES[seqKey] ?: LINE_SEQUENCES[lineCode] ?: return null
        val originIdx = seq.indexOfFirst { it.equals(originSta, ignoreCase = true) }
        val destIdx = seq.indexOfFirst { it.equals(destSta, ignoreCase = true) }
        if (originIdx == -1 || destIdx == -1 || originIdx == destIdx) return null

        val isForward = destIdx > originIdx
        return when (lineCode.uppercase()) {
            "ISL" -> if (isForward) TransitDirection.EASTBOUND else TransitDirection.WESTBOUND
            "TWL" -> if (isForward) TransitDirection.NORTHBOUND else TransitDirection.SOUTHBOUND
            "KTL" -> if (isForward) TransitDirection.EASTBOUND else TransitDirection.WESTBOUND
            "EAL", "EAL_RAC" -> if (isForward) TransitDirection.NORTHBOUND else TransitDirection.SOUTHBOUND
            "TML" -> if (isForward) TransitDirection.WESTBOUND else TransitDirection.EASTBOUND
            "TCL", "AEL" -> if (isForward) TransitDirection.WESTBOUND else TransitDirection.EASTBOUND
            "SIL" -> if (isForward) TransitDirection.SOUTHBOUND else TransitDirection.NORTHBOUND
            "TKL" -> if (isForward) TransitDirection.EASTBOUND else TransitDirection.WESTBOUND
            else -> if (isForward) TransitDirection.OUTBOUND else TransitDirection.INBOUND
        }
    }

    /**
     * Resolves calling points from origin station code to destination station code on a given line.
     * Delegates to the shared [LinearRouteCallingPointsResolver].
     */
    fun resolveCallingPoints(
        lineCode: String,
        originSta: String,
        destStaOrName: String,
        departureEpochSeconds: Long? = null
    ): List<CallingPoint> = sharedCallingPointsResolver.resolveCallingPointsForTrip(
        lineCode = lineCode,
        originStationIdentifier = originSta,
        destStationIdentifier = destStaOrName,
        departureEpochSeconds = departureEpochSeconds,
        fallbackCallingPoints = emptyList()
    )
}
