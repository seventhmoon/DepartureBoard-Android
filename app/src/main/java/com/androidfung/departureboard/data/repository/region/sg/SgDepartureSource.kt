package com.androidfung.departureboard.data.repository.region.sg

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.BuildConfig
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.StationIdPrefixes
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.model.isSgTransitId
import com.androidfung.departureboard.data.model.sg.SgDefaultStations
import com.androidfung.departureboard.data.model.sg.SgStationDirectory
import com.androidfung.departureboard.data.network.sg.SgLtaApiService
import com.androidfung.departureboard.data.network.sg.SgLtaNetworkClient
import com.androidfung.departureboard.data.network.sg.SgLtaNextBus
import com.androidfung.departureboard.data.repository.DepartureSource
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException

/**
 * Departure source for Singapore transit (LTA DataMall `BusArrivalv2` + MRT).
 * Maps LTA load levels (`SEA`, `SDA`, `LSD`) to [com.androidfung.departureboard.data.model.OccupancyStatus]
 * and bus deck types (`DD` Double-Decker, `SD` Single-Decker, `BD` Bendy).
 */
class SgDepartureSource(
    private val ltaApiService: SgLtaApiService = SgLtaNetworkClient.apiService,
    private val apiKeyProvider: () -> String = { BuildConfig.LTA_DATAMALL_API_KEY }
) : DepartureSource {

    companion object {
        private val LTA_LUSH_GREEN = Color(0xFF4CAF50)

        fun mapLtaLoadToOccupancy(load: String?): String? = when (load?.uppercase()) {
            "SEA" -> "MANY_SEATS_AVAILABLE"
            "SDA" -> "STANDING_ROOM_ONLY"
            "LSD" -> "FULL"
            else -> null
        }

        fun mapDeckTypeLabel(deckType: String?): String = when (deckType?.uppercase()) {
            "DD" -> "Double-Decker"
            "BD" -> "Bendy Bus"
            "SD" -> "Single-Decker"
            else -> "Bus Stop"
        }
    }

    override fun canHandle(stationId: String, stationName: String, isBusStop: Boolean): Boolean {
        return stationId.isSgTransitId || SgStationDirectory.getStationById(stationId) != null
    }

    override suspend fun fetchDepartures(
        stationId: String,
        stationName: String,
        isBusStop: Boolean
    ): Result<List<Departure>> {
        val isSgBus = isBusStop || stationId.startsWith(StationIdPrefixes.SG_BUS, ignoreCase = true)
        if (isSgBus && apiKeyProvider().isNotBlank()) {
            val stopCode = stationId.removePrefix(StationIdPrefixes.SG_BUS).trim()
            try {
                val res = ltaApiService.getBusArrival(stopCode)
                val now = Instant.now()
                val live = mutableListOf<Departure>()
                res.services.forEach { svc ->
                    val slots: List<SgLtaNextBus?> = listOf(svc.nextBus, svc.nextBus2, svc.nextBus3)
                    slots.forEachIndexed { idx, slot ->
                        val iso = slot?.estimatedArrival?.takeIf { it.isNotBlank() } ?: return@forEachIndexed
                        val target = runCatching { OffsetDateTime.parse(iso).toInstant() }.getOrNull() ?: return@forEachIndexed
                        val waitSec = ChronoUnit.SECONDS.between(now, target).toInt().coerceAtLeast(0)
                        val deckLabel = mapDeckTypeLabel(slot.deckType)
                        live.add(
                            Departure(
                                id = "SG_BUS_${stopCode}_${svc.serviceNo}_$idx",
                                stationId = stationId,
                                stationName = stationName,
                                lineId = "SG_BUS_${svc.serviceNo}",
                                lineName = svc.serviceNo,
                                platformName = deckLabel,
                                destinationName = slot.destinationCode?.let { "Interchange $it" } ?: "Bus Service ${svc.serviceNo}",
                                towards = svc.operatorCode,
                                timeToStationSeconds = waitSec,
                                expectedArrivalIso = target.toString(),
                                currentLocation = if (waitSec <= 45) "Arriving" else deckLabel,
                                modeName = TransitMode.BUS.id,
                                lineBadge = LineBadgeInfo(
                                    lineId = "SG_BUS_${svc.serviceNo}",
                                    displayName = svc.serviceNo,
                                    backgroundColor = LTA_LUSH_GREEN,
                                    textColor = Color.White,
                                    mode = TransitMode.BUS,
                                    lineCode = svc.serviceNo
                                ),
                                status = if (waitSec <= 45) DepartureStatus.DUE else DepartureStatus.ON_TIME,
                                isWheelchairAccessible = slot.feature.equals("WAB", ignoreCase = true),
                                occupancyStatus = mapLtaLoadToOccupancy(slot.load),
                                expectedDepartureInstant = target
                            )
                        )
                    }
                }
                if (live.isNotEmpty()) {
                    return Result.success(live.sortedBy { it.timeToStationSeconds })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Fall back cleanly to scheduled cadence
            }
        }
        return Result.success(SgDefaultStations.getFallbackDepartures(stationId, stationName))
    }
}
