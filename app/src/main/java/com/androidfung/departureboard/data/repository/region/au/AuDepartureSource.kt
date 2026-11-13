package com.androidfung.departureboard.data.repository.region.au

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.BuildConfig
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.PlatformInfo
import com.androidfung.departureboard.data.model.TrainInfo
import com.androidfung.departureboard.data.model.TransitMode
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

                        val rawLineCode = ev.transportation?.disassembledName?.ifBlank { null }
                            ?: ev.transportation?.number?.ifBlank { null }
                        val productClass = ev.transportation?.product?.productClass
                        val matchedRailLine = rawLineCode?.let { AuSydneyLine.fromCode(it) }
                        val lineCode = rawLineCode ?: matchedRailLine?.code ?: "Service"
                        val isMetro = productClass == 2 ||
                            (productClass == null && !isBusStop && matchedRailLine == AuSydneyLine.M1)
                        val isLightRail = productClass == 4 ||
                            (productClass == null && matchedRailLine == null && lineCode.matches(Regex("^L[1-3]$", RegexOption.IGNORE_CASE)))
                        val isFerry = productClass == 9 ||
                            (productClass == null && matchedRailLine == null && lineCode.matches(Regex("^F\\d{1,2}$", RegexOption.IGNORE_CASE)))
                        val isBus = !isMetro && !isLightRail && !isFerry && (
                            productClass == 5 ||
                                productClass == 11 ||
                                isBusStop ||
                                stationId.startsWith("AU_BUS_", ignoreCase = true) ||
                                matchedRailLine == null
                            )

                        val platRaw = ev.location?.disassembledName
                            ?.substringAfter("Platform ", "")
                            ?.takeIf { it.isNotBlank() }
                        val isDelayed = plannedInstant != null && ChronoUnit.SECONDS.between(plannedInstant, estInstant) >= 180
                        val dest = ev.transportation?.destination?.name ?: "Sydney Network"

                        val (modeName, lineDisplayName, badge, platformText, trainInfo) = when {
                            isBus -> ResolvedAuService(
                                modeName = "bus",
                                lineName = ev.transportation?.number?.ifBlank { null } ?: lineCode,
                                badge = LineBadgeInfo(
                                    lineId = lineCode,
                                    displayName = lineCode,
                                    backgroundColor = Color(0xFF00B5EF),
                                    textColor = Color.White,
                                    mode = TransitMode.BUS,
                                    lineCode = lineCode
                                ),
                                platformName = ev.location?.disassembledName
                                    ?.takeIf { it.contains("Stand", true) || it.contains("Bay", true) }
                                    ?.substringAfterLast(", ")
                                    ?.trim()
                                    ?.ifBlank { null }
                                    ?: "",
                                train = null
                            )
                            isLightRail -> ResolvedAuService(
                                modeName = "tram",
                                lineName = ev.transportation?.product?.name?.ifBlank { null } ?: "$lineCode Light Rail",
                                badge = LineBadgeInfo(
                                    lineId = lineCode,
                                    displayName = lineCode,
                                    backgroundColor = Color(0xFFEE343F),
                                    textColor = Color.White,
                                    mode = TransitMode.TRAM,
                                    lineCode = lineCode
                                ),
                                platformName = platRaw?.let { "Platform $it" } ?: "",
                                train = null
                            )
                            isFerry -> ResolvedAuService(
                                modeName = "river-bus",
                                lineName = ev.transportation?.product?.name?.ifBlank { null } ?: "$lineCode Ferry",
                                badge = LineBadgeInfo(
                                    lineId = lineCode,
                                    displayName = lineCode,
                                    backgroundColor = Color(0xFF5AB031),
                                    textColor = Color.White,
                                    mode = TransitMode.FERRY,
                                    lineCode = lineCode
                                ),
                                platformName = platRaw?.let { "Wharf $it" } ?: "",
                                train = null
                            )
                            else -> {
                                val railLine = matchedRailLine ?: AuSydneyLine.T1
                                ResolvedAuService(
                                    modeName = if (isMetro) "tube" else "national-rail",
                                    lineName = railLine.lineName,
                                    badge = railLine.toLineBadge(),
                                    platformName = "Platform ${platRaw ?: "1"}",
                                    train = TrainInfo(
                                        serviceId = ev.transportation?.id,
                                        operator = TransitOperator(
                                            id = if (isMetro) "SM" else "ST",
                                            name = if (isMetro) "Sydney Metro" else "Sydney Trains"
                                        ),
                                        formation = AuDefaultStations.buildWaratahFormation(isMetro = isMetro)
                                    )
                                )
                            }
                        }

                        Departure(
                            id = "AU_LIVE_${stopId}_${lineCode}_${plannedInstant?.epochSecond ?: estInstant.epochSecond}_$idx",
                            stationId = stationId,
                            stationName = stationName,
                            lineId = lineCode,
                            lineName = lineDisplayName,
                            platformName = platformText,
                            destinationName = dest,
                            towards = dest,
                            timeToStationSeconds = waitSec,
                            expectedArrivalIso = estInstant.toString(),
                            currentLocation = when {
                                isDelayed -> "Delayed"
                                waitSec <= 60 -> "Approaching"
                                else -> "Real-Time"
                            },
                            modeName = modeName,
                            lineBadge = badge,
                            status = when {
                                isDelayed -> DepartureStatus.DELAYED
                                waitSec <= 60 -> DepartureStatus.DUE
                                else -> DepartureStatus.ON_TIME
                            },
                            platform = platRaw?.let { PlatformInfo(code = it, isConfirmed = true) },
                            train = trainInfo,
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

    private data class ResolvedAuService(
        val modeName: String,
        val lineName: String,
        val badge: LineBadgeInfo,
        val platformName: String,
        val train: TrainInfo?
    )
}
