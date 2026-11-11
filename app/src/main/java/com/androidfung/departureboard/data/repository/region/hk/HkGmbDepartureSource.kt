package com.androidfung.departureboard.data.repository.region.hk

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.StationIdPrefixes
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.model.isHkGmbStopId
import com.androidfung.departureboard.data.network.hk.HkGmbApiService
import com.androidfung.departureboard.data.network.hk.HkGmbNetworkClient
import com.androidfung.departureboard.data.repository.DepartureSource
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException

/**
 * Live key-free departure source for Hong Kong Green Minibus (`HK_GMB_{stop_id}`)
 * powered by `https://data.etagmb.gov.hk/eta/stop/{stop_id}`.
 */
class HkGmbDepartureSource(
    private val apiService: HkGmbApiService = HkGmbNetworkClient.apiService,
    private val routeNameLookup: (Long?) -> Pair<String, String>? = { routeId -> KNOWN_GMB_ROUTES[routeId] }
) : DepartureSource {

    companion object {
        private val GMB_BADGE_BG = Color(0xFF007A33)
        private val GMB_BADGE_FG = Color.White

        val KNOWN_GMB_ROUTES: Map<Long, Pair<String, String>> = mapOf(
            2000412L to ("40" to "Stanley Village (赤柱市集)"),
            2000318L to ("10" to "Cyberport (數碼港)"),
            2000340L to ("28" to "Baguio Villa (碧瑤灣)"),
            2003210L to ("1A" to "Sai Kung Pier (西貢碼頭)")
        )
    }

    override fun canHandle(stationId: String, stationName: String, isBusStop: Boolean): Boolean =
        stationId.isHkGmbStopId

    override suspend fun fetchDepartures(
        stationId: String,
        stationName: String,
        isBusStop: Boolean
    ): Result<List<Departure>> {
        val rawStopId = stationId.removePrefix(StationIdPrefixes.HK_GMB).trim()
        if (rawStopId.isBlank()) return Result.success(emptyList())

        return try {
            val response = apiService.getStopEta(rawStopId)
            val now = Instant.now()
            val departures = mutableListOf<Departure>()

            response.data.filter { it.enabled }.forEach { routeGroup ->
                val known = routeNameLookup(routeGroup.routeId)
                val routeDisplay = known?.first ?: (routeGroup.routeId?.toString()?.takeLast(3) ?: "GMB")
                val destDisplay = known?.second ?: "Green Minibus Terminus"

                routeGroup.eta.forEach { entry ->
                    val waitSeconds = when {
                        entry.diff != null -> (entry.diff * 60).coerceAtLeast(0)
                        !entry.timestamp.isNullOrBlank() -> {
                            runCatching {
                                val target = OffsetDateTime.parse(entry.timestamp).toInstant()
                                ChronoUnit.SECONDS.between(now, target).toInt().coerceAtLeast(0)
                            }.getOrDefault(180)
                        }
                        else -> return@forEach
                    }

                    val arrivalInstant = now.plusSeconds(waitSeconds.toLong())
                    val remark = entry.remarks_en?.takeIf { it.isNotBlank() }
                    departures.add(
                        Departure(
                            id = "HK_GMB_${rawStopId}_${routeGroup.routeId}_${entry.etaSeq}",
                            stationId = stationId,
                            stationName = stationName,
                            lineId = "GMB_$routeDisplay",
                            lineName = "GMB $routeDisplay",
                            platformName = "Minibus Stand",
                            destinationName = destDisplay,
                            towards = remark ?: destDisplay,
                            timeToStationSeconds = waitSeconds,
                            expectedArrivalIso = arrivalInstant.toString(),
                            currentLocation = remark ?: if (waitSeconds <= 45) "Arriving" else "${waitSeconds / 60} min",
                            modeName = TransitMode.BUS.id,
                            lineBadge = LineBadgeInfo(
                                lineId = "GMB_$routeDisplay",
                                displayName = routeDisplay,
                                backgroundColor = GMB_BADGE_BG,
                                textColor = GMB_BADGE_FG,
                                mode = TransitMode.BUS,
                                lineCode = routeDisplay
                            ),
                            status = if (waitSeconds <= 45) DepartureStatus.DUE else DepartureStatus.ON_TIME,
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
