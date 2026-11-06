package com.androidfung.departureboard.data.repository.region.hk

import com.androidfung.departureboard.data.model.StationServiceHoursInfo
import com.androidfung.departureboard.data.model.isHkBusStationId
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Resolves service hours for Hong Kong franchised bus stops.
 * Most daytime franchised bus routes conclude service between ~00:30 and 01:15,
 * resuming around ~05:30.
 */
object HkBusServiceHoursResolver {

    private val HK_ZONE = ZoneId.of("Asia/Hong_Kong")

    fun checkStationServiceHours(stationId: String, stationName: String? = null): StationServiceHoursInfo? {
        if (!stationId.isHkBusStationId && (stationName?.contains("海底隧道") != true) && (stationName?.contains("轉車站") != true)) {
            return null
        }

        val nowHk = ZonedDateTime.now(HK_ZONE)
        val hour = nowHk.hour
        val minute = nowHk.minute

        // Late night hours when regular bus services have ended (approx 01:15 - 05:15)
        val isLateNight = (hour == 1 && minute >= 15) || (hour in 2..4) || (hour == 5 && minute < 15)

        return if (isLateNight) {
            StationServiceHoursInfo(
                isClosedForNight = true,
                lastTrainTime = "01:00",
                firstTrainTime = "05:30",
                lineName = "Franchised Bus"
            )
        } else {
            null
        }
    }
}
