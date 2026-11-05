package com.androidfung.departureboard.data.repository.region.hk

import com.androidfung.departureboard.data.model.gb.StationServiceHoursInfo
import com.androidfung.departureboard.data.model.hk.HkMtrLine
import com.androidfung.departureboard.data.model.hk.HkMtrStationRegistry
import com.androidfung.departureboard.data.model.hk.topology.HkMtrRoutes
import com.androidfung.departureboard.data.model.parseHkMtrStationId
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Resolves service hours (last train departed / first train) for Hong Kong MTR stations.
 */
object HkMtrServiceHoursResolver {

    private val HK_ZONE = ZoneId.of("Asia/Hong_Kong")

    /**
     * Approximate last/first train service hour templates per line.
     * MTR system generally runs ~05:55-06:15 (first train) to ~00:45-01:15 (last train).
     */
    private val LINE_SERVICE_HOURS = mapOf(
        "TKL" to ("01:05" to "06:00"),
        "ISL" to ("00:55" to "05:55"),
        "TWL" to ("00:50" to "06:00"),
        "KTL" to ("00:50" to "06:00"),
        "EAL" to ("00:40" to "05:30"),
        "TML" to ("00:45" to "05:45"),
        "TCL" to ("00:50" to "06:00"),
        "AEL" to ("00:48" to "05:50"),
        "SIL" to ("01:05" to "06:00"),
        "DRL" to ("00:40" to "06:15")
    )

    fun checkStationServiceHours(stationId: String, lineId: String? = null): StationServiceHoursInfo? {
        val ref = stationId.parseHkMtrStationId()
        val defaultLine = ref.lineCode
        val staCode = ref.stationCode

        val station = HkMtrStationRegistry.getStation(staCode) ?: HkMtrStationRegistry.getStation(stationId)
        val validStaCode = station?.code ?: staCode

        // Determine line
        val candidateLine = lineId
            ?: defaultLine
            ?: HkMtrRoutes.STATION_LINES[validStaCode]?.firstOrNull()
            ?: HkMtrRoutes.LINE_SEQUENCES.entries.firstOrNull { (_, stops) -> stops.contains(validStaCode) }?.key
            ?: return null

        val nowHk = ZonedDateTime.now(HK_ZONE)
        // MTR services generally end between ~01:15 and resume at ~05:45
        val hour = nowHk.hour
        val minute = nowHk.minute
        val isLateNight = (hour == 1 && minute >= 20) || (hour in 2..4) || (hour == 5 && minute < 40)

        val (lastTrain, firstTrain) = LINE_SERVICE_HOURS[candidateLine] ?: ("01:00" to "06:00")
        val lineName = HkMtrLine.getLineName(candidateLine)

        return if (isLateNight) {
            StationServiceHoursInfo(
                isClosedForNight = true,
                lastTrainTime = lastTrain,
                firstTrainTime = firstTrain,
                lineName = lineName
            )
        } else {
            null
        }
    }
}
