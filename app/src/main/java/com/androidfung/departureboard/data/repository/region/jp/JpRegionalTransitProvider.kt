package com.androidfung.departureboard.data.repository.region.jp

import com.androidfung.departureboard.BuildConfig
import com.androidfung.departureboard.data.model.AlertCause
import com.androidfung.departureboard.data.model.AlertEffect
import com.androidfung.departureboard.data.model.AlertSeverity
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.InformedEntity
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TransitAlert
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.model.TransitRegion
import com.androidfung.departureboard.data.model.gb.TflLineStatusDetail
import com.androidfung.departureboard.data.model.gb.TflLineStatusItem
import com.androidfung.departureboard.data.model.isJpTransitId
import com.androidfung.departureboard.data.model.jp.JpDefaultStations
import com.androidfung.departureboard.data.model.jp.JpStationDirectory
import com.androidfung.departureboard.data.model.jp.JpTokyoLine
import com.androidfung.departureboard.data.network.jp.JpOdptNetworkClient
import com.androidfung.departureboard.data.network.jp.JpOdptTrainInformationItem
import com.androidfung.departureboard.data.repository.DepartureSource
import com.androidfung.departureboard.data.repository.region.RegionalTransitProvider
import com.androidfung.departureboard.data.topology.CallingPointsResolver
import com.androidfung.departureboard.util.CoalescingTimedCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Japan / Tokyo (ODPT Real-Time TrainInformation, JR East, Tokyo Metro & Toei Subway) regional transit provider.
 */
object JpRegionalTransitProvider : RegionalTransitProvider {
    override val region: TransitRegion = TransitRegion.JP

    private val departureSource = JpDepartureSource()
    private val callingPointsResolver = JpCallingPointsResolver()
    private val providerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val trainInfoCache = CoalescingTimedCache<String, List<JpOdptTrainInformationItem>>(ttlMillis = 3 * 60 * 1000L)

    override val departureSources: List<DepartureSource> = listOf(departureSource)
    override val callingPointsResolvers: List<CallingPointsResolver> = listOf(callingPointsResolver)

    override fun ownsStationId(stationId: String): Boolean = stationId.isJpTransitId

    override suspend fun searchStations(query: String): Result<List<Station>> =
        Result.success(JpStationDirectory.searchStations(query))

    override suspend fun getStationById(stationId: String): Station? =
        JpStationDirectory.getStationById(stationId)
            ?: JpDefaultStations.POPULAR_STATIONS.firstOrNull { it.id.equals(stationId, ignoreCase = true) }

    override suspend fun getDepartures(stationId: String, stationName: String): Result<List<Departure>> {
        val isBusStop = stationId.startsWith("JP_BUS_", ignoreCase = true)
        return departureSource.fetchDepartures(stationId, stationName, isBusStop = isBusStop)
    }

    private suspend fun fetchLiveOdptTrainInfo(): List<JpOdptTrainInformationItem> {
        return trainInfoCache.getOrPutCoalesced(key = "jp_odpt_train_info", scope = providerScope) {
            val combined = mutableListOf<JpOdptTrainInformationItem>()
            try {
                combined.addAll(JpOdptNetworkClient.publicApiService.getPublicTrainInformation())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}

            if (BuildConfig.ODPT_API_KEY.isNotBlank()) {
                try {
                    combined.addAll(JpOdptNetworkClient.keyedApiService.getKeyedTrainInformation(BuildConfig.ODPT_API_KEY))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {}
            }
            combined
        }
    }

    override suspend fun getLineStatuses(): Map<String, TflLineStatusItem> {
        val items = fetchLiveOdptTrainInfo()
        val result = mutableMapOf<String, TflLineStatusItem>()
        for (item in items) {
            val lineDef = JpTokyoLine.fromOdptRailway(item.railway) ?: continue
            val lineCode = lineDef.code
            val lineName = lineDef.lineName
            val textJa = item.textMap?.get("ja").orEmpty()
            val textEn = item.textMap?.get("en").orEmpty()
            val statusJa = item.statusMap?.get("ja").orEmpty()
            val statusEn = item.statusMap?.get("en").orEmpty()
            val preferJa = com.androidfung.departureboard.util.LocaleHelper.getActiveLocale().language == "ja"
            val isNormal = statusJa.isBlank() &&
                (textJa.contains("平常") || textEn.contains("on time", ignoreCase = true) || textEn.contains("normal", ignoreCase = true))
            val severity = if (isNormal) 10 else 6
            val desc = if (isNormal) {
                "Good Service"
            } else if (preferJa) {
                statusJa.ifBlank { statusEn.ifBlank { "遅延" } }
            } else {
                statusEn.ifBlank { statusJa.ifBlank { "Delays" } }
            }
            val reason = if (isNormal) {
                null
            } else if (preferJa) {
                textJa.ifBlank { textEn }.takeIf { it.isNotBlank() }
            } else {
                textEn.ifBlank { textJa }.takeIf { it.isNotBlank() }
            }
            val modeName = if (JpTokyoLine.fromCode(lineCode)?.mode == TransitMode.RAIL) "rail" else "metro"
            result[lineCode.lowercase()] = TflLineStatusItem(
                id = lineCode.lowercase(),
                name = lineName,
                modeName = modeName,
                lineStatuses = listOf(
                    TflLineStatusDetail(
                        id = 0,
                        statusSeverity = severity,
                        statusSeverityDescription = desc,
                        reason = reason
                    )
                )
            )
        }
        return result
    }

    override suspend fun getTransitAlerts(): List<TransitAlert> {
        return fetchLiveOdptTrainInfo().mapNotNull { item ->
            val railwayTail = item.railway?.substringAfter("odpt.Railway:") ?: return@mapNotNull null
            val lineDef = JpTokyoLine.fromOdptRailway(item.railway)
            val lineCode = lineDef?.code ?: railwayTail
            val lineName = lineDef?.lineName ?: railwayTail
            val textJa = item.textMap?.get("ja").orEmpty()
            val textEn = item.textMap?.get("en").orEmpty()
            val statusJa = item.statusMap?.get("ja").orEmpty()
            val statusEn = item.statusMap?.get("en").orEmpty()
            val preferJa = com.androidfung.departureboard.util.LocaleHelper.getActiveLocale().language == "ja"
            val isNormal = statusJa.isBlank() &&
                (textJa.contains("平常") || textEn.contains("on time", ignoreCase = true) || textEn.contains("normal", ignoreCase = true))
            if (isNormal) return@mapNotNull null

            val body = if (preferJa) {
                when {
                    textJa.isNotBlank() && textEn.isNotBlank() -> "$textJa ($textEn)"
                    textJa.isNotBlank() -> textJa
                    else -> textEn
                }
            } else {
                when {
                    textEn.isNotBlank() && textJa.isNotBlank() -> "$textEn ($textJa)"
                    textEn.isNotBlank() -> textEn
                    else -> textJa
                }
            }
            if (body.isBlank()) return@mapNotNull null
            val statusTitle = if (preferJa) {
                statusJa.ifBlank { statusEn.ifBlank { "運行情報" } }
            } else {
                statusEn.ifBlank { statusJa.ifBlank { "Service Alert" } }
            }
            TransitAlert(
                id = "odpt_${lineCode}_${body.hashCode()}",
                headerText = "$lineName — $statusTitle",
                descriptionText = body,
                cause = AlertCause.OTHER_CAUSE,
                effect = AlertEffect.SIGNIFICANT_DELAYS,
                severity = AlertSeverity.WARNING,
                informedEntities = listOf(InformedEntity(agencyId = "ODPT", routeId = lineCode))
            )
        }
    }

    override suspend fun checkStationServiceHours(
        stationId: String,
        lineId: String?
    ): com.androidfung.departureboard.data.model.StationServiceHoursInfo? =
        com.androidfung.departureboard.util.LastTrainServiceCalculator.evaluateServiceHours(stationId, lineId)
}
