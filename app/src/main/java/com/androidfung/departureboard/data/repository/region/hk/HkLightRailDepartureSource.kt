package com.androidfung.departureboard.data.repository.region.hk

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.PlatformInfo
import com.androidfung.departureboard.data.model.StationIdPrefixes
import com.androidfung.departureboard.data.model.TrainFormation
import com.androidfung.departureboard.data.model.TrainInfo
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.model.TransitOperator
import com.androidfung.departureboard.data.model.isHkLightRailStationId
import com.androidfung.departureboard.data.network.hk.HkLightRailApiService
import com.androidfung.departureboard.data.network.hk.HkMtrNetworkClient
import com.androidfung.departureboard.data.repository.DepartureSource
import java.time.Instant
import kotlinx.coroutines.CancellationException

/**
 * Live key-free departure source for Hong Kong MTR Light Rail (`HK_LRT_{station_id}`)
 * powered by `https://rt.data.gov.hk/v1/transport/mtr/lrt/getSchedule`.
 */
class HkLightRailDepartureSource(
    private val apiService: HkLightRailApiService = HkMtrNetworkClient.lightRailApiService
) : DepartureSource {

    companion object {
        private val LRT_BADGE_BG = Color(0xFFD3A809)
        private val LRT_BADGE_FG = Color(0xFF1A1A1A)
        private val MINUTES_REGEX = Regex("(\\d+)")
    }

    override fun canHandle(stationId: String, stationName: String, isBusStop: Boolean): Boolean =
        stationId.isHkLightRailStationId

    override suspend fun fetchDepartures(
        stationId: String,
        stationName: String,
        isBusStop: Boolean
    ): Result<List<Departure>> {
        val rawId = stationId.removePrefix(StationIdPrefixes.HK_LRT).trim()
        if (rawId.isBlank()) return Result.success(emptyList())

        return try {
            val response = apiService.getLightRailSchedule(rawId)
            val now = Instant.now()
            val departures = mutableListOf<Departure>()

            response.platformList.forEach { platform ->
                val platStr = "Platform ${platform.platformId}"
                platform.routeList.forEachIndexed { idx, item ->
                    if (item.stopFlag == 1) return@forEachIndexed
                    val routeNo = item.routeNo.trim()
                    if (routeNo.isBlank()) return@forEachIndexed

                    val timeEn = item.timeEn?.trim().orEmpty()
                    val waitSeconds = when {
                        timeEn.equals("-", ignoreCase = true) ||
                            timeEn.contains("Arriving", ignoreCase = true) ||
                            timeEn.contains("Departing", ignoreCase = true) -> 20
                        else -> {
                            val mins = MINUTES_REGEX.find(timeEn)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 3
                            (mins * 60).coerceAtLeast(30)
                        }
                    }

                    val destEn = item.destEn?.trim().orEmpty()
                    val destCh = item.destCh?.trim().orEmpty()
                    val destinationLabel = when {
                        destEn.isNotBlank() && destCh.isNotBlank() -> "$destEn ($destCh)"
                        destEn.isNotBlank() -> destEn
                        destCh.isNotBlank() -> destCh
                        else -> "Light Rail"
                    }

                    val arrivalInstant = now.plusSeconds(waitSeconds.toLong())
                    departures.add(
                        Departure(
                            id = "HK_LRT_${rawId}_${platform.platformId}_${routeNo}_$idx",
                            stationId = stationId,
                            stationName = stationName,
                            lineId = "LRT_$routeNo",
                            lineName = "Light Rail $routeNo",
                            platformName = platStr,
                            destinationName = destinationLabel,
                            towards = destinationLabel,
                            timeToStationSeconds = waitSeconds,
                            expectedArrivalIso = arrivalInstant.toString(),
                            currentLocation = if (waitSeconds <= 45) "Arriving" else timeEn.ifBlank { "${waitSeconds / 60} min" },
                            modeName = TransitMode.TRAM.id,
                            lineBadge = LineBadgeInfo(
                                lineId = "LRT_$routeNo",
                                displayName = routeNo,
                                backgroundColor = LRT_BADGE_BG,
                                textColor = LRT_BADGE_FG,
                                mode = TransitMode.TRAM,
                                lineCode = routeNo
                            ),
                            status = if (waitSeconds <= 45) DepartureStatus.DUE else DepartureStatus.ON_TIME,
                            platform = PlatformInfo(code = platform.platformId.toString(), isConfirmed = true),
                            train = TrainInfo(
                                operator = TransitOperator(id = "MTR_LRT", name = "MTR Light Rail"),
                                formation = item.trainLength?.takeIf { it > 0 }?.let { TrainFormation(coachCount = it) }
                            ),
                            expectedDepartureInstant = arrivalInstant
                        )
                    )
                }
            }
            Result.success(departures.sortedBy { it.timeToStationSeconds })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
