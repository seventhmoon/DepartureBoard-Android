package com.androidfung.departureboard.data.repository.region.hk

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.hk.HkDefaultStations
import com.androidfung.departureboard.data.model.hk.HkMtrLine
import com.androidfung.departureboard.data.model.hk.HkMtrStationRegistry
import com.androidfung.departureboard.data.model.hk.HkStationDirectory
import com.androidfung.departureboard.data.model.hk.parseHkMtrStationId
import com.androidfung.departureboard.data.model.hk.topology.HkMtrRoutes
import com.androidfung.departureboard.data.model.isHkMtrStationId
import com.androidfung.departureboard.data.network.hk.HkMtrApiService
import com.androidfung.departureboard.data.network.hk.HkMtrNetworkClient
import com.androidfung.departureboard.data.repository.DepartureSource
import com.androidfung.departureboard.data.repository.PlatformFormatter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

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
        val sta = stationId.parseHkMtrStationId().stationCode
        val isMtrCode = HkMtrStationRegistry.getStation(sta) != null || HkMtrStationRegistry.getStation(stationId) != null
        return stationId.isHkMtrStationId ||
                isMtrCode ||
                HkStationDirectory.getStationById(stationId) != null
    }

    override suspend fun fetchDepartures(
        stationId: String,
        stationName: String,
        isBusStop: Boolean
    ): Result<List<Departure>> = coroutineScope {
        try {
            // e.g. "HK_MTR_ISL_CEN" -> line = "ISL", sta = "CEN", or raw station code e.g. "CEN"
            val ref = stationId.parseHkMtrStationId()
            val defaultLine = ref.lineCode ?: "ISL"
            val sta = ref.stationCode.ifBlank { "CEN" }

            // Query ALL lines serving this station (e.g. for ADM query ISL, TWL, EAL, SIL)
            val linesToQuery = HkMtrRoutes.STATION_LINES[sta] ?: listOf(defaultLine)

            val now = Instant.now()
            val deferredList = linesToQuery.map { lineCode ->
                async {
                    try {
                        val response = apiService.getSchedule(line = lineCode, station = sta)
                        val scheduleData = response.data?.get("${lineCode}-$sta")
                        val allItems = (scheduleData?.upDepartures.orEmpty() + scheduleData?.downDepartures.orEmpty())

                        val lineTitle = HkMtrLine.getLineName(lineCode)
                        val lineBadge = HkMtrLine.getLineBadge(lineCode)

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
                            if (destCode.equals(sta, ignoreCase = true)) return@mapNotNull null
                            val destName = HkMtrStationRegistry.getName(destCode).ifBlank { "MTR Terminus" }
                            val rawPlat = item.plat ?: "1"
                            val formattedPlatform = PlatformFormatter.format(
                                rawPlatform = rawPlat,
                                towards = destName,
                                isBus = false
                            )

                            // Pre-compute calling points for MTR so tapping opens complete stop sequence with estimated times
                            val callingPoints = HkMtrRoutes.resolveCallingPoints(
                                lineCode = lineCode,
                                originSta = sta,
                                destStaOrName = destCode,
                                departureEpochSeconds = departureInstant.epochSecond
                            )

                            val resolvedDir = HkMtrRoutes.resolveDirection(lineCode, sta, destCode)?.displayName

                            Departure(
                                id = "HK_MTR_${lineCode}_${sta}_${rawPlat}_${departureInstant.epochSecond}_${item.seq ?: "0"}",
                                stationId = stationId,
                                stationName = stationName,
                                lineId = lineCode,
                                lineName = lineTitle,
                                platformName = formattedPlatform,
                                destinationName = destName,
                                towards = destName,
                                direction = resolvedDir,
                                timeToStationSeconds = timeToStation,
                                expectedArrivalIso = DateTimeFormatter.ISO_INSTANT.format(departureInstant),
                                currentLocation = if (timeToStation <= 60) "Approaching" else "In Transit",
                                modeName = "mtr",
                                lineBadge = lineBadge,
                                callingPoints = callingPoints,
                                status = if (timeToStation <= 45) DepartureStatus.DUE else DepartureStatus.ON_TIME,
                                destinationStationId = destCode.ifBlank { null }
                            )
                        }
                    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }

            val departures = deferredList.awaitAll().flatten().sortedBy { it.timeToStationSeconds }
            Result.success(departures)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
