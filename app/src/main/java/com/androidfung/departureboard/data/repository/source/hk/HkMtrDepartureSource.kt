package com.androidfung.departureboard.data.repository.source.hk

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.HkDefaultStations
import com.androidfung.departureboard.data.model.HkMtrLineRegistry
import com.androidfung.departureboard.data.model.HkMtrRoutes
import com.androidfung.departureboard.data.model.HkMtrStationRegistry
import com.androidfung.departureboard.data.network.HkMtrApiService
import com.androidfung.departureboard.data.network.HkMtrNetworkClient
import com.androidfung.departureboard.data.repository.source.DepartureSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.max

/**
 * Departure source for Hong Kong MTR heavy rail via data.gov.hk (getSchedule.php).
 */
class HkMtrDepartureSource(
    private val apiService: HkMtrApiService = HkMtrNetworkClient.apiService
) : DepartureSource {

    companion object {
        private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private val HK_ZONE = ZoneId.of("Asia/Hong_Kong")
    }

    override fun canHandle(stationId: String, stationName: String, isBusStop: Boolean): Boolean {
        if (isBusStop) return false
        return stationId.startsWith("HK_MTR_") ||
                stationName.contains("港鐵") ||
                stationName.contains("MTR")
    }

    override suspend fun fetchDepartures(
        stationId: String,
        stationName: String,
        isBusStop: Boolean
    ): Result<List<Departure>> = coroutineScope {
        try {
            // e.g. "HK_MTR_ISL_CEN" -> line = "ISL", sta = "CEN"
            val parts = stationId.removePrefix("HK_MTR_").split("_")
            val defaultLine = parts.getOrNull(0) ?: "ISL"
            val sta = parts.getOrNull(1) ?: (if (parts.size == 1) parts[0] else "CEN")

            // Query ALL lines serving this station (e.g. for ADM query ISL, TWL, EAL, SIL)
            val linesToQuery = HkMtrRoutes.STATION_LINES[sta] ?: listOf(defaultLine)

            val now = Instant.now()
            val deferredList = linesToQuery.map { lineCode ->
                async {
                    try {
                        val response = apiService.getSchedule(line = lineCode, station = sta)
                        val scheduleData = response.data?.get("${lineCode}-$sta")
                        val allItems = (scheduleData?.upDepartures.orEmpty() + scheduleData?.downDepartures.orEmpty())

                        val lineTitle = HkMtrLineRegistry.getLineName(lineCode)
                        val lineBadge = HkMtrLineRegistry.getLineBadge(lineCode)

                        allItems.mapNotNull { item ->
                            val timeStr = item.time?.trim() ?: return@mapNotNull null
                            val departureInstant = try {
                                LocalDateTime.parse(timeStr, DATE_TIME_FORMATTER)
                                    .atZone(HK_ZONE)
                                    .toInstant()
                            } catch (_: Exception) {
                                null
                            } ?: return@mapNotNull null

                            val diffSeconds = ChronoUnit.SECONDS.between(now, departureInstant).toInt()
                            if (diffSeconds < -60) return@mapNotNull null

                            val timeToStation = max(0, diffSeconds)
                            val destCode = item.dest?.uppercase() ?: ""
                            val destName = HkMtrStationRegistry.getName(destCode).ifBlank { "MTR Terminus" }
                            val plat = item.plat ?: "1"

                            // Pre-compute calling points for MTR so tapping opens complete stop sequence
                            val callingPoints = HkMtrRoutes.resolveCallingPoints(
                                lineCode = lineCode,
                                originSta = sta,
                                destStaOrName = destCode
                            )

                            Departure(
                                id = "HK_MTR_${lineCode}_${sta}_${plat}_${departureInstant.epochSecond}_${item.seq ?: "0"}",
                                stationId = stationId,
                                stationName = stationName,
                                lineId = lineCode,
                                lineName = lineTitle,
                                platformName = plat,
                                destinationName = destName,
                                towards = destName,
                                timeToStationSeconds = timeToStation,
                                expectedArrivalIso = DateTimeFormatter.ISO_INSTANT.format(departureInstant),
                                currentLocation = if (timeToStation <= 60) "Approaching" else "In Transit",
                                modeName = "tube",
                                lineBadge = lineBadge,
                                callingPoints = callingPoints,
                                status = if (timeToStation <= 45) DepartureStatus.DUE else DepartureStatus.ON_TIME
                            )
                        }
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }

            val departures = deferredList.awaitAll().flatten().sortedBy { it.timeToStationSeconds }

            if (departures.isEmpty()) {
                Result.success(HkDefaultStations.getFallbackDepartures(stationId, stationName))
            } else {
                Result.success(departures)
            }
        } catch (_: Exception) {
            Result.success(HkDefaultStations.getFallbackDepartures(stationId, stationName))
        }
    }
}
