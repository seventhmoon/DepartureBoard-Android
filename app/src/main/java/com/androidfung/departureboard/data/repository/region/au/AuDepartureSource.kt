package com.androidfung.departureboard.data.repository.region.au

import com.androidfung.departureboard.BuildConfig
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.PlatformInfo
import com.androidfung.departureboard.data.model.TrainInfo
import com.androidfung.departureboard.data.model.TransitOperator
import com.androidfung.departureboard.data.model.au.AuDefaultStations
import com.androidfung.departureboard.data.model.au.AuStationDirectory
import com.androidfung.departureboard.data.model.au.AuSydneyLine
import com.androidfung.departureboard.data.model.isAuTransitId
import com.androidfung.departureboard.data.network.au.AuTfnswApiService
import com.androidfung.departureboard.data.network.au.AuTfnswNetworkClient
import com.androidfung.departureboard.data.repository.DepartureSource
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException

/**
 * Departure source for Sydney / NSW transit (Transport for NSW `departure_mon` + Waratah 8-car formation).
 */
class AuDepartureSource(
    private val tfnswApiService: AuTfnswApiService = AuTfnswNetworkClient.apiService,
    private val apiKeyProvider: () -> String = { BuildConfig.TFNSW_API_KEY }
) : DepartureSource {

    companion object {
        private val TSN_STOP_IDS: Map<String, String> = mapOf(
            "AU_TRAIN_CENTRAL" to "200060",
            "AU_TRAIN_TOWNHALL" to "200070",
            "AU_TRAIN_WYNYARD" to "200080",
            "AU_TRAIN_CIRCULARQUAY" to "200020",
            "AU_TRAIN_CHATSWOOD" to "206710",
            "AU_TRAIN_PARRAMATTA" to "215020",
            "AU_TRAIN_SYDNEYDOMESTIC" to "202020",
            "AU_TRAIN_BONDIJUNCTION" to "202210"
        )
    }

    override fun canHandle(stationId: String, stationName: String, isBusStop: Boolean): Boolean {
        return stationId.isAuTransitId || AuStationDirectory.getStationById(stationId) != null
    }

    override suspend fun fetchDepartures(
        stationId: String,
        stationName: String,
        isBusStop: Boolean
    ): Result<List<Departure>> {
        if (apiKeyProvider().isNotBlank()) {
            val stopId = when {
                stationId.startsWith("AU_BUS_", ignoreCase = true) -> stationId.removePrefix("AU_BUS_").trim()
                else -> TSN_STOP_IDS[stationId.uppercase()]
            }
            if (!stopId.isNullOrBlank()) {
                try {
                    val res = tfnswApiService.getDepartures(stopId = stopId)
                    val now = Instant.now()
                    val live = res.stopEvents.mapIndexedNotNull { idx, ev ->
                        val estIso = ev.departureTimeEstimated ?: ev.departureTimePlanned ?: return@mapIndexedNotNull null
                        val estInstant = runCatching { OffsetDateTime.parse(estIso).toInstant() }.getOrNull() ?: return@mapIndexedNotNull null
                        val plannedInstant = ev.departureTimePlanned?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
                        val waitSec = ChronoUnit.SECONDS.between(now, estInstant).toInt().coerceAtLeast(0)
                        if (waitSec > 7200) return@mapIndexedNotNull null

                        val lineCode = ev.transportation?.disassembledName ?: ev.transportation?.number ?: "T1"
                        val sydneyLine = AuSydneyLine.fromCode(lineCode) ?: AuSydneyLine.T1
                        val isMetro = sydneyLine == AuSydneyLine.M1
                        val platRaw = ev.location?.disassembledName?.substringAfter("Platform ", "")?.takeIf { it.isNotBlank() } ?: "1"
                        val isDelayed = plannedInstant != null && ChronoUnit.SECONDS.between(plannedInstant, estInstant) >= 180
                        val dest = ev.transportation?.destination?.name ?: "Sydney Network"

                        Departure(
                            id = "AU_LIVE_${stopId}_${lineCode}_$idx",
                            stationId = stationId,
                            stationName = stationName,
                            lineId = lineCode,
                            lineName = sydneyLine.lineName,
                            platformName = "Platform $platRaw",
                            destinationName = dest,
                            towards = dest,
                            timeToStationSeconds = waitSec,
                            expectedArrivalIso = estInstant.toString(),
                            currentLocation = when {
                                isDelayed -> "Delayed"
                                waitSec <= 60 -> "Approaching"
                                else -> "Real-Time"
                            },
                            modeName = if (isMetro) "tube" else "national-rail",
                            lineBadge = sydneyLine.toLineBadge(),
                            status = when {
                                isDelayed -> DepartureStatus.DELAYED
                                waitSec <= 60 -> DepartureStatus.DUE
                                else -> DepartureStatus.ON_TIME
                            },
                            platform = PlatformInfo(code = platRaw, isConfirmed = true),
                            train = TrainInfo(
                                serviceId = ev.transportation?.id,
                                operator = TransitOperator(
                                    id = if (isMetro) "SM" else "ST",
                                    name = if (isMetro) "Sydney Metro" else "Sydney Trains"
                                ),
                                formation = AuDefaultStations.buildWaratahFormation(isMetro = isMetro)
                            ),
                            scheduledDepartureInstant = plannedInstant,
                            expectedDepartureInstant = estInstant,
                            isWheelchairAccessible = true
                        )
                    }
                    if (live.isNotEmpty()) {
                        return Result.success(live.sortedBy { it.timeToStationSeconds })
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {}
            }
        }
        return Result.success(AuDefaultStations.getFallbackDepartures(stationId, stationName))
    }
}
