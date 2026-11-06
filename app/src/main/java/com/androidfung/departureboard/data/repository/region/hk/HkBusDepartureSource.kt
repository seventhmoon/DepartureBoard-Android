package com.androidfung.departureboard.data.repository.region.hk

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.StationIdPrefixes
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.model.hk.extractRawHkBusStopId
import com.androidfung.departureboard.data.model.hk.HkDefaultStations
import com.androidfung.departureboard.data.model.hk.HkKmbEtaItem
import com.androidfung.departureboard.data.model.isHkBusStationId
import com.androidfung.departureboard.data.network.hk.HkBusApiService
import com.androidfung.departureboard.data.network.hk.HkBusNetworkClient
import com.androidfung.departureboard.data.repository.DepartureSource
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Departure source for Hong Kong franchised buses (KMB / LWB and Citybus via data.gov.hk).
 */
class HkBusDepartureSource(
    private val kmbApiService: HkBusApiService = HkBusNetworkClient.kmbApiService,
    private val citybusApiService: HkBusApiService = HkBusNetworkClient.citybusApiService
) : DepartureSource {

    override fun canHandle(stationId: String, stationName: String, isBusStop: Boolean): Boolean {
        val isRawNumeric = stationId.matches(Regex("^[0-9]{6}$"))
        return stationId.isHkBusStationId ||
                isRawNumeric ||
                stationName.contains("海底隧道") ||
                stationName.contains("轉車站") ||
                stationName.contains("Cross Harbour Tunnel")
    }

    override suspend fun fetchDepartures(
        stationId: String,
        stationName: String,
        isBusStop: Boolean
    ): Result<List<Departure>> = coroutineScope {
        try {
            val isCtbOnly = stationId.startsWith(StationIdPrefixes.HK_BUS_CTB, ignoreCase = true) ||
                    stationId.startsWith(StationIdPrefixes.HK_CTB, ignoreCase = true)
            val rawStopId = extractRawHkBusStopId(stationId)

            // Look up known CTB routes configured on this station (if any)
            val stationMeta = HkDefaultStations.POPULAR_STATIONS.firstOrNull {
                it.id.equals(stationId, ignoreCase = true)
            }
            val ctbRoutes = stationMeta?.lines
                ?.mapNotNull { line ->
                    when {
                        line.id.startsWith("CTB_", ignoreCase = true) -> line.id.removePrefix("CTB_")
                        isCtbOnly -> line.name
                        else -> null
                    }
                }
                ?.distinct()
                .orEmpty()

            val kmbDeferred = async {
                if (isCtbOnly && rawStopId.length == 6) {
                    emptyList()
                } else {
                    try {
                        kmbApiService.getKmbStopEta(rawStopId).data.orEmpty()
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }

            val ctbDeferreds = ctbRoutes.map { route ->
                async {
                    try {
                        citybusApiService.getCitybusEta("CTB", rawStopId, route).data.orEmpty().map { ctb ->
                            HkKmbEtaItem(
                                co = ctb.co ?: "CTB",
                                route = ctb.route,
                                dir = ctb.dir,
                                seq = ctb.seq,
                                stop = ctb.stop,
                                destTc = ctb.destTc,
                                destSc = ctb.destSc,
                                destEn = ctb.destEn,
                                etaSeq = ctb.etaSeq,
                                eta = ctb.eta,
                                rmkTc = ctb.rmkTc,
                                rmkEn = ctb.rmkEn,
                                dataTimestamp = ctb.dataTimestamp
                            )
                        }
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }

            val items: List<HkKmbEtaItem> = kmbDeferred.await() + ctbDeferreds.flatMap { it.await() }

            val now = Instant.now()
            val departures = items.mapNotNull { item ->
                val etaStr = item.eta?.trim()
                if (etaStr.isNullOrEmpty()) return@mapNotNull null

                val etaInstant = try {
                    OffsetDateTime.parse(etaStr).toInstant()
                } catch (_: Exception) {
                    try {
                        Instant.parse(etaStr)
                    } catch (_: Exception) {
                        null
                    }
                } ?: return@mapNotNull null

                val diffSeconds = ChronoUnit.SECONDS.between(now, etaInstant).toInt()
                // Drop expired buses older than 1 minute
                if (diffSeconds < -60) return@mapNotNull null

                val timeToStationSeconds = max(0, diffSeconds)
                val routeName = item.route.orEmpty().ifBlank { "Bus" }
                val destTc = item.destTc.orEmpty()
                val destEn = item.destEn.orEmpty()
                val destination = when {
                    destTc.isNotBlank() && destEn.isNotBlank() -> "$destEn ($destTc)"
                    destTc.isNotBlank() -> destTc
                    else -> destEn.ifBlank { "Destination" }
                }

                val operator = item.co?.uppercase() ?: "KMB"
                val badgeBgColor = when (operator) {
                    "CTB", "NWFB" -> HkDefaultStations.ColorCitybusYellow
                    else -> HkDefaultStations.ColorKmbRed
                }
                val badgeTextColor = when (operator) {
                    "CTB", "NWFB" -> Color.Black
                    else -> Color.White
                }

                val remark = item.rmkTc?.takeIf { it.isNotBlank() } ?: item.rmkEn.orEmpty()
                val status = when {
                    timeToStationSeconds <= 45 -> DepartureStatus.DUE
                    remark.contains("預定", ignoreCase = true) || remark.contains("Scheduled", ignoreCase = true) -> DepartureStatus.ON_TIME
                    else -> DepartureStatus.ON_TIME
                }

                val boundDir = when (item.dir?.uppercase()) {
                    "I" -> "inbound"
                    else -> "outbound"
                }

                Departure(
                    id = "HK_BUS_${rawStopId}_${routeName}_${item.etaSeq ?: 1}_${etaInstant.epochSecond}",
                    stationId = stationId,
                    stationName = stationName,
                    lineId = "HK_BUS_$routeName",
                    lineName = routeName,
                    platformName = "",
                    destinationName = destination,
                    towards = destEn.ifBlank { destTc },
                    direction = boundDir,
                    timeToStationSeconds = timeToStationSeconds,
                    expectedArrivalIso = DateTimeFormatter.ISO_INSTANT.format(etaInstant),
                    currentLocation = if (remark.isNotBlank()) remark else if (timeToStationSeconds <= 60) "Approaching" else "In Transit",
                    modeName = "bus",
                    lineBadge = LineBadgeInfo(
                        lineId = routeName,
                        displayName = routeName,
                        backgroundColor = badgeBgColor,
                        textColor = badgeTextColor,
                        mode = TransitMode.BUS
                    ),
                    status = status
                )
            }.sortedBy { it.timeToStationSeconds }

            if (departures.isEmpty()) {
                Result.success(HkDefaultStations.getFallbackDepartures(stationId, stationName))
            } else {
                Result.success(departures)
            }
        } catch (_: Exception) {
            Result.success(HkDefaultStations.getFallbackDepartures(stationId, stationName))
        }
    }

    companion object {
        private val HK_ZONE = java.time.ZoneId.of("Asia/Hong_Kong")

        fun checkStationServiceHours(stationId: String, stationName: String? = null): com.androidfung.departureboard.data.model.gb.StationServiceHoursInfo? {
            if (!stationId.isHkBusStationId && (stationName?.contains("海底隧道") != true) && (stationName?.contains("轉車站") != true)) {
                return null
            }

            val nowHk = java.time.ZonedDateTime.now(HK_ZONE)
            val hour = nowHk.hour
            val minute = nowHk.minute

            val isLateNight = (hour == 1 && minute >= 15) || (hour in 2..4) || (hour == 5 && minute < 15)

            return if (isLateNight) {
                com.androidfung.departureboard.data.model.gb.StationServiceHoursInfo(
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
}
