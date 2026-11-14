package com.androidfung.departureboard.data.repository.region.sg

import com.androidfung.departureboard.BuildConfig
import com.androidfung.departureboard.data.model.AlertCause
import com.androidfung.departureboard.data.model.AlertEffect
import com.androidfung.departureboard.data.model.AlertSeverity
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.InformedEntity
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.StationIdPrefixes
import com.androidfung.departureboard.data.model.TransitAlert
import com.androidfung.departureboard.data.model.TransitRegion
import com.androidfung.departureboard.data.model.isSgTransitId
import com.androidfung.departureboard.data.model.sg.SgDefaultStations
import com.androidfung.departureboard.data.model.sg.SgStationDirectory
import com.androidfung.departureboard.data.network.sg.SgLtaApiService
import com.androidfung.departureboard.data.network.sg.SgLtaFacilitiesMaintenanceResponse
import com.androidfung.departureboard.data.network.sg.SgLtaNetworkClient
import com.androidfung.departureboard.data.network.sg.SgLtaTrainServiceAlertsResponse
import com.androidfung.departureboard.data.repository.DepartureSource
import com.androidfung.departureboard.data.repository.region.RegionalTransitProvider
import com.androidfung.departureboard.data.topology.CallingPointsResolver
import com.androidfung.departureboard.util.CoalescingTimedCache
import com.androidfung.departureboard.util.getOrPutCoalescedSafe
import com.androidfung.departureboard.util.runSuspendCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Singapore (LTA DataMall BusArrivalv2, PCDRealTime Station Crowd & MRT) regional transit provider.
 */
object SgRegionalTransitProvider : RegionalTransitProvider {
    override val region: TransitRegion = TransitRegion.SG

    private val departureSource = SgDepartureSource()
    private val callingPointsResolver = SgCallingPointsResolver()
    private val providerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val crowdAlertsCache = CoalescingTimedCache<String, List<TransitAlert>>(ttlMillis = 5 * 60 * 1000L)

    override val departureSources: List<DepartureSource> = listOf(departureSource)
    override val callingPointsResolvers: List<CallingPointsResolver> = listOf(callingPointsResolver)

    override fun ownsStationId(stationId: String): Boolean = stationId.isSgTransitId

    override suspend fun searchStations(query: String): Result<List<Station>> =
        Result.success(SgStationDirectory.searchStations(query))

    override suspend fun getStationById(stationId: String): Station? =
        SgDefaultStations.POPULAR_STATIONS.firstOrNull { it.id.equals(stationId, ignoreCase = true) }
            ?: SgStationDirectory.getStationById(stationId)

    override suspend fun getDepartures(stationId: String, stationName: String): Result<List<Departure>> {
        val isBusStop = stationId.startsWith(StationIdPrefixes.SG_BUS, ignoreCase = true)
        return departureSource.fetchDepartures(stationId, stationName, isBusStop = isBusStop)
    }

    override suspend fun getTransitAlerts(): List<TransitAlert> {
        if (BuildConfig.LTA_DATAMALL_API_KEY.isBlank()) return emptyList()
        return crowdAlertsCache.getOrPutCoalescedSafe(
            key = "sg_lta_alerts",
            scope = providerScope,
            defaultValue = emptyList()
        ) {
            fetchTransitAlerts(SgLtaNetworkClient.apiService)
        }
    }

    internal suspend fun fetchTransitAlerts(apiService: SgLtaApiService): List<TransitAlert> {
        val alerts = mutableListOf<TransitAlert>()

        runSuspendCatching { apiService.getTrainServiceAlerts() }
            .onSuccess { alerts.addAll(mapTrainServiceAlerts(it)) }

        runSuspendCatching { apiService.getFacilitiesMaintenance() }
            .onSuccess { alerts.addAll(mapFacilitiesMaintenance(it)) }

        val probeLines = listOf("NSL", "EWL", "NEL", "CCL", "DTL", "TEL")
        for (line in probeLines) {
            runSuspendCatching { apiService.getPlatformCrowdDensity(line) }.onSuccess { res ->
                res.value.filter { it.crowdLevel.equals("h", ignoreCase = true) }.forEach { crowd ->
                    alerts.add(
                        TransitAlert(
                            id = "sg_pcd_${line}_${crowd.stationCode}",
                            headerText = "$line ${crowd.stationCode} — High Platform Crowd",
                            descriptionText = "LTA real-time platform crowd density is currently high at ${crowd.stationCode}. Move toward outer train cars for faster boarding.",
                            cause = AlertCause.OTHER_CAUSE,
                            effect = AlertEffect.SIGNIFICANT_DELAYS,
                            severity = AlertSeverity.WARNING,
                            informedEntities = listOf(InformedEntity(agencyId = "LTA", routeId = line))
                        )
                    )
                }
            }
        }
        return alerts
    }

    internal fun mapTrainServiceAlerts(response: SgLtaTrainServiceAlertsResponse): List<TransitAlert> {
        val value = response.value ?: return emptyList()
        if (value.status == 1 && value.affectedSegments.isEmpty() && value.messages.isEmpty()) {
            return emptyList()
        }

        val alerts = mutableListOf<TransitAlert>()
        val combinedMessage = value.messages
            .mapNotNull { it.content?.trim()?.takeIf(String::isNotEmpty) }
            .joinToString("\n")

        if (value.affectedSegments.isNotEmpty()) {
            value.affectedSegments.forEachIndexed { idx, segment ->
                val line = segment.line?.trim().orEmpty()
                val rangeText = listOfNotNull(
                    segment.from?.trim()?.takeIf(String::isNotEmpty),
                    segment.to?.trim()?.takeIf(String::isNotEmpty)
                ).joinToString(" – ").ifBlank { segment.stations?.trim().orEmpty() }
                val header = buildString {
                    append(line.ifBlank { "MRT/LRT" })
                    if (rangeText.isNotBlank()) append(" ($rangeText)")
                    append(" — Service Disruption")
                }
                val shuttleHints = buildList {
                    if (segment.freePublicBus.equals("Yes", ignoreCase = true)) add("Free public bus available")
                    if (segment.freeMrtShuttle.equals("Yes", ignoreCase = true)) {
                        val dir = segment.mrtShuttleDirection?.takeIf(String::isNotBlank)?.let { " ($it)" }.orEmpty()
                        add("Free MRT shuttle bus available$dir")
                    }
                }.joinToString(". ")
                val description = listOf(combinedMessage, shuttleHints)
                    .filter { it.isNotBlank() }
                    .joinToString("\n\n")
                    .ifBlank { null }

                val entities = buildList {
                    if (line.isNotBlank()) add(InformedEntity(agencyId = "LTA", routeId = line, lineId = line))
                    segment.from?.trim()?.takeIf(String::isNotEmpty)?.let {
                        add(InformedEntity(agencyId = "LTA", stationId = it))
                    }
                    segment.to?.trim()?.takeIf(String::isNotEmpty)?.let {
                        add(InformedEntity(agencyId = "LTA", stationId = it))
                    }
                }

                alerts.add(
                    TransitAlert(
                        id = "sg_tsa_${line.ifBlank { "mrt" }}_$idx",
                        cause = AlertCause.TECHNICAL_PROBLEM,
                        effect = AlertEffect.SIGNIFICANT_DELAYS,
                        severity = AlertSeverity.SEVERE,
                        headerText = header,
                        descriptionText = description,
                        informedEntities = entities
                    )
                )
            }
        } else if (combinedMessage.isNotBlank()) {
            alerts.add(
                TransitAlert(
                    id = "sg_tsa_general",
                    cause = AlertCause.TECHNICAL_PROBLEM,
                    effect = AlertEffect.SIGNIFICANT_DELAYS,
                    severity = AlertSeverity.SEVERE,
                    headerText = "Singapore MRT/LRT — Service Alert",
                    descriptionText = combinedMessage,
                    informedEntities = listOf(InformedEntity(agencyId = "LTA"))
                )
            )
        }
        return alerts
    }

    internal fun mapFacilitiesMaintenance(response: SgLtaFacilitiesMaintenanceResponse): List<TransitAlert> {
        return response.value.mapIndexedNotNull { idx, item ->
            val stationCode = item.stationCode?.trim()?.takeIf(String::isNotEmpty)
            val stationName = item.stationName?.trim()?.takeIf(String::isNotEmpty)
            val facilityType = item.facilityType?.trim()?.takeIf(String::isNotEmpty) ?: "Facility"
            val location = item.location?.trim()?.takeIf(String::isNotEmpty)
            val status = item.status?.trim()?.takeIf(String::isNotEmpty) ?: "Under Maintenance"
            if (stationCode == null && stationName == null) return@mapIndexedNotNull null

            val stationLabel = listOfNotNull(stationName, stationCode?.let { "($it)" }).joinToString(" ")
            val description = buildList {
                location?.let { add("Location: $it") }
                item.facilityCode?.trim()?.takeIf(String::isNotEmpty)?.let { add("Unit: $it") }
                item.estimatedCompletionDate?.trim()?.takeIf(String::isNotEmpty)?.let { add("Est. completion: $it") }
            }.joinToString(" • ").ifBlank { null }

            TransitAlert.accessibilityOutage(
                id = "sg_fac_${stationCode ?: "unk"}_${item.facilityCode ?: idx}",
                stationLabel = stationLabel,
                facilityLabel = "$facilityType $status",
                description = description,
                agencyId = "LTA",
                stationId = stationCode
            )
        }
    }

    override suspend fun checkStationServiceHours(
        stationId: String,
        lineId: String?
    ): com.androidfung.departureboard.data.model.StationServiceHoursInfo? =
        com.androidfung.departureboard.util.LastTrainServiceCalculator.evaluateServiceHours(stationId, lineId)
}
