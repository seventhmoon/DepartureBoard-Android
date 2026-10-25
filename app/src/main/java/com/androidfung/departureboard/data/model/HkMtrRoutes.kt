package com.androidfung.departureboard.data.model

/**
 * Route station sequence and interchange lines definition for Hong Kong MTR.
 */
object HkMtrRoutes {

    // Clean, ordered station code sequences along each line
    val LINE_SEQUENCES: Map<String, List<String>> = mapOf(
        "ISL" to listOf("KET", "HKU", "SYP", "SHW", "CEN", "ADM", "WAC", "CAB", "TIH", "FOH", "NOP", "QUB", "TAK", "SWH", "SKW", "HFC", "CHW"),
        "TWL" to listOf("CEN", "ADM", "TST", "JOR", "YMT", "MOK", "PRE", "SSP", "CSW", "LCK", "MEF", "LAK", "KWF", "KWH", "TWH", "TSW"),
        "KTL" to listOf("WHA", "HOM", "YMT", "MOK", "PRE", "SKM", "KOT", "LOF", "WTS", "DIH", "CHH", "KOB", "NTK", "KWT", "LAT", "YAT", "TIK"),
        "EAL" to listOf("ADM", "EXC", "HUH", "MKK", "KOT", "TAW", "SHT", "FOT", "RAC", "UNI", "TAP", "TWO", "FAN", "SHS", "LOW", "LMC"),
        "TML" to listOf("WKS", "MOS", "HEO", "TSH", "SHM", "CIO", "STW", "CKT", "TAW", "HIK", "DIH", "KAT", "SUW", "TKW", "HOM", "HUH", "ETS", "AUS", "NAC", "MEF", "TWW", "KSR", "YUL", "LOP", "TIS", "SIH", "TUM"),
        "TCL" to listOf("HOK", "KOW", "OLY", "NAC", "LAK", "TSY", "SUN", "TUC"),
        "AEL" to listOf("HOK", "KOW", "TSY", "AIR", "AWE"),
        "SIL" to listOf("ADM", "OCP", "WCH", "LET", "SOH"),
        "TKL" to listOf("NOP", "QUB", "YAT", "TIK", "TKO", "HAH", "POA", "LHP"),
        "DRL" to listOf("SUN", "DIS")
    )

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

    /**
     * Resolves calling points from origin station code to destination station code on a given line.
     */
    fun resolveCallingPoints(lineCode: String, originSta: String, destStaOrName: String): List<CallingPoint> {
        val seq = LINE_SEQUENCES[lineCode] ?: return emptyList()
        val originIdx = seq.indexOfFirst { it.equals(originSta, ignoreCase = true) }
        if (originIdx == -1) return emptyList()

        val destClean = destStaOrName.trim().uppercase()
        val destIdx = seq.indexOfFirst { code ->
            code.equals(destClean, ignoreCase = true) ||
            destClean.contains(code) ||
            HkMtrStationRegistry.getName(code).contains(destStaOrName, ignoreCase = true) ||
            destStaOrName.contains(HkMtrStationRegistry.getName(code), ignoreCase = true)
        }

        val range = when {
            destIdx == -1 -> seq.subList(originIdx, seq.size)
            destIdx >= originIdx -> seq.subList(originIdx, destIdx + 1)
            else -> seq.subList(destIdx, originIdx + 1).reversed()
        }

        return range.mapIndexed { idx, code ->
            CallingPoint(
                stationName = HkMtrStationRegistry.getName(code),
                isCurrentStation = idx == 0,
                isDestination = idx == range.lastIndex,
                stationId = code,
                modes = listOf(TransitMode.TUBE.id)
            )
        }
    }
}
