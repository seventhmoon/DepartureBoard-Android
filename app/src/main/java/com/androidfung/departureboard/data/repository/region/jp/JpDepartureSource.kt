package com.androidfung.departureboard.data.repository.region.jp

import com.androidfung.departureboard.BuildConfig
import com.androidfung.departureboard.data.model.DataFreshness
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.TrainFormation
import com.androidfung.departureboard.data.model.TrainInfo
import com.androidfung.departureboard.data.model.isJpTransitId
import com.androidfung.departureboard.data.model.jp.JpDefaultStations
import com.androidfung.departureboard.data.model.jp.JpStationDirectory
import com.androidfung.departureboard.data.model.jp.JpTokyoLine
import com.androidfung.departureboard.data.model.jp.topology.JpTokyoRoutes
import com.androidfung.departureboard.data.network.jp.JpOdptApiService
import com.androidfung.departureboard.data.network.jp.JpOdptNetworkClient
import com.androidfung.departureboard.data.network.jp.JpOdptTrainItem
import com.androidfung.departureboard.data.repository.DepartureSource
import com.androidfung.departureboard.util.CoalescingTimedCache
import com.androidfung.departureboard.util.getOrPutCoalescedSafe
import com.androidfung.departureboard.util.runSuspendCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Departure source for Tokyo transit (ODPT live `odpt:Train` + fallback schedule).
 */
class JpDepartureSource(
    private val publicApiService: JpOdptApiService = JpOdptNetworkClient.publicApiService,
    private val keyedApiService: JpOdptApiService = JpOdptNetworkClient.keyedApiService
) : DepartureSource {

    private val sourceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val trainsCache = CoalescingTimedCache<String, List<JpOdptTrainItem>>(ttlMillis = 30_000L)

    override fun canHandle(stationId: String, stationName: String, isBusStop: Boolean): Boolean {
        return stationId.isJpTransitId ||
            (!isBusStop && JpStationDirectory.getStationById(stationId) != null)
    }

    override suspend fun fetchDepartures(
        stationId: String,
        stationName: String,
        isBusStop: Boolean
    ): Result<List<Departure>> {
        if (isBusStop) {
            return Result.success(JpDefaultStations.getFallbackDepartures(stationId, stationName))
        }

        val liveTrains = fetchLiveOdptTrains()
        val liveDepartures = mapOdptTrainsToDepartures(
            trains = liveTrains,
            stationId = stationId,
            stationName = stationName
        )

        val fallback = JpDefaultStations.getFallbackDepartures(stationId, stationName)
        if (liveDepartures.isEmpty()) {
            return Result.success(fallback)
        }

        // Keep live departures for lines with real-time trains, and fill other lines from fallback.
        val liveLineIds = liveDepartures.mapTo(mutableSetOf()) { it.lineId.uppercase() }
        val merged = liveDepartures + fallback.filter { it.lineId.uppercase() !in liveLineIds }
        return Result.success(merged.sortedBy { it.timeToStationSeconds })
    }

    private suspend fun fetchLiveOdptTrains(): List<JpOdptTrainItem> {
        return trainsCache.getOrPutCoalescedSafe(
            key = "jp_odpt_trains",
            scope = sourceScope,
            defaultValue = emptyList()
        ) {
            val combined = mutableListOf<JpOdptTrainItem>()
            runSuspendCatching { publicApiService.getPublicTrains() }.onSuccess { combined.addAll(it) }
            if (BuildConfig.ODPT_API_KEY.isNotBlank()) {
                runSuspendCatching { keyedApiService.getKeyedTrains(BuildConfig.ODPT_API_KEY) }
                    .onSuccess { combined.addAll(it) }
            }
            combined
        }
    }

    internal fun mapOdptTrainsToDepartures(
        trains: List<JpOdptTrainItem>,
        stationId: String,
        stationName: String,
        now: Instant = Instant.now()
    ): List<Departure> {
        if (trains.isEmpty()) return emptyList()

        val station = JpStationDirectory.getStationById(stationId)
            ?: JpDefaultStations.POPULAR_STATIONS.firstOrNull { it.id.equals(stationId, ignoreCase = true) }
        val targetNormalizedName = normalizeStationToken(
            station?.name?.substringBefore(" (") ?: stationName.substringBefore(" (")
        )
        val stationLineCodes = station?.lines?.mapTo(mutableSetOf()) { it.id.uppercase() } ?: emptySet()

        val departures = mutableListOf<Departure>()
        for (train in trains) {
            val lineDef = JpTokyoLine.fromOdptRailway(train.railway) ?: continue
            val lineCode = lineDef.code
            if (stationLineCodes.isNotEmpty() && lineCode.uppercase() !in stationLineCodes) continue

            val fromToken = train.fromStation?.substringAfterLast(".")?.let(::normalizeStationToken)
            val toToken = train.toStation?.substringAfterLast(".")?.let(::normalizeStationToken)

            val delaySec = (train.delaySeconds ?: 0).coerceAtLeast(0)
            var etaSeconds: Int? = null
            var locationText: String? = null

            when {
                fromToken == targetNormalizedName && toToken == null -> {
                    etaSeconds = 0
                    locationText = "At Platform"
                }
                toToken == targetNormalizedName -> {
                    etaSeconds = (60 + delaySec).coerceAtLeast(30)
                    val fromLabel = train.fromStation?.substringAfterLast(".")?.let(::formatOdptStationName)
                    locationText = if (fromLabel != null) "Approaching from $fromLabel" else "Approaching"
                }
                else -> {
                    val stopsAway = estimateStopsUpstream(
                        lineCode = lineCode,
                        trainFromToken = fromToken,
                        trainToToken = toToken,
                        targetToken = targetNormalizedName,
                        directionUri = train.railDirection
                    )
                    if (stopsAway != null && stopsAway in 1..6) {
                        etaSeconds = stopsAway * JpTokyoRoutes.averageSecondsBetweenStops.toInt() + delaySec
                        val currentLabel = (train.toStation ?: train.fromStation)
                            ?.substringAfterLast(".")
                            ?.let(::formatOdptStationName)
                        locationText = if (currentLabel != null) {
                            "$stopsAway stops away (near $currentLabel)"
                        } else {
                            "$stopsAway stops away"
                        }
                    }
                }
            }

            if (etaSeconds == null) continue

            val destName = train.destinationStations.firstOrNull()
                ?.substringAfterLast(".")
                ?.let(::formatOdptStationName)
                ?: lineDef.lineName
            val directionLabel = train.railDirection?.substringAfterLast(".")?.let(::formatDirectionLabel)
            val trainTypeLabel = train.trainType?.substringAfterLast(".")?.let(::formatTrainType)
            val expectedInstant = now.plusSeconds(etaSeconds.toLong())
            val expectedIso = DateTimeFormatter.ISO_INSTANT.format(expectedInstant)
            val status = when {
                etaSeconds <= 30 -> DepartureStatus.DUE
                delaySec >= 120 -> DepartureStatus.DELAYED
                else -> DepartureStatus.ON_TIME
            }

            departures.add(
                Departure(
                    id = "jp_odpt_${train.sameAs ?: train.trainNumber ?: departures.size}",
                    stationId = stationId,
                    stationName = stationName,
                    lineId = lineCode,
                    lineName = lineDef.lineName,
                    platformName = trainTypeLabel ?: "TBD",
                    destinationName = destName,
                    towards = directionLabel,
                    direction = directionLabel,
                    timeToStationSeconds = etaSeconds,
                    expectedArrivalIso = expectedIso,
                    currentLocation = locationText,
                    modeName = lineDef.mode.id,
                    lineBadge = LineBadgeInfo(
                        lineId = lineCode,
                        displayName = lineDef.lineName,
                        backgroundColor = lineDef.color,
                        textColor = androidx.compose.ui.graphics.Color.White,
                        mode = lineDef.mode,
                        lineCode = lineDef.badgeText
                    ),
                    status = status,
                    vehicleId = train.trainNumber,
                    train = TrainInfo(
                        headcode = train.trainNumber,
                        formation = train.carComposition?.let { TrainFormation(coachCount = it) },
                        delayReason = if (delaySec >= 60) "${delaySec / 60} min delay" else null
                    ),
                    expectedDepartureInstant = expectedInstant,
                    dataFreshness = DataFreshness.LIVE
                )
            )
        }
        return departures.sortedBy { it.timeToStationSeconds }
    }

    private fun estimateStopsUpstream(
        lineCode: String,
        trainFromToken: String?,
        trainToToken: String?,
        targetToken: String,
        directionUri: String?
    ): Int? {
        val sequence = JpTokyoRoutes.lineSequences[lineCode] ?: return null
        val normalizedNames = sequence.map { code ->
            normalizeStationToken(JpTokyoRoutes.STATION_NAMES_BY_CODE[code].orEmpty())
        }
        val targetIdx = normalizedNames.indexOf(targetToken)
        if (targetIdx < 0) return null

        val trainAnchorToken = trainToToken ?: trainFromToken ?: return null
        val trainIdx = normalizedNames.indexOf(trainAnchorToken)
        if (trainIdx < 0 || trainIdx == targetIdx) return null

        val isLoop = lineCode in JpTokyoRoutes.loopLineCodes
        if (isLoop) {
            val isInner = directionUri?.contains("InnerLoop", ignoreCase = true) == true
            val size = normalizedNames.size
            val dist = if (isInner) {
                (trainIdx - targetIdx + size) % size
            } else {
                (targetIdx - trainIdx + size) % size
            }
            return dist.takeIf { it > 0 }
        }

        // Non-loop: infer direction from fromStation -> toStation when available
        val fromIdx = trainFromToken?.let { normalizedNames.indexOf(it) } ?: -1
        val toIdx = trainToToken?.let { normalizedNames.indexOf(it) } ?: -1
        return when {
            fromIdx >= 0 && toIdx >= 0 && toIdx > fromIdx && targetIdx >= toIdx -> targetIdx - toIdx + 1
            fromIdx >= 0 && toIdx >= 0 && toIdx < fromIdx && targetIdx <= toIdx -> toIdx - targetIdx + 1
            else -> null
        }
    }

    private fun normalizeStationToken(raw: String): String =
        raw.lowercase().replace("-", "").replace(" ", "")

    private fun formatOdptStationName(raw: String): String =
        raw.replace(Regex("([a-z])([A-Z])"), "$1-$2")

    private fun formatDirectionLabel(raw: String): String =
        raw.replace(Regex("([a-z])([A-Z])"), "$1 $2")

    private fun formatTrainType(raw: String): String =
        raw.replace(Regex("([a-z])([A-Z])"), "$1 $2")
}
