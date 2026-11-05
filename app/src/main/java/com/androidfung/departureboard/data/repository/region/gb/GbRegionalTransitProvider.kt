package com.androidfung.departureboard.data.repository.region.gb

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TransitRegion
import com.androidfung.departureboard.data.model.gb.GbDefaultStations
import com.androidfung.departureboard.data.model.gb.StationServiceHoursInfo
import com.androidfung.departureboard.data.model.isAuTransitId
import com.androidfung.departureboard.data.model.isHkTransitId
import com.androidfung.departureboard.data.model.isJpTransitId
import com.androidfung.departureboard.data.model.isSgTransitId
import com.androidfung.departureboard.data.model.transitMode
import com.androidfung.departureboard.data.network.gb.BodsApiService
import com.androidfung.departureboard.data.network.gb.NationalRailApiService
import com.androidfung.departureboard.data.network.gb.TflApiService
import com.androidfung.departureboard.data.repository.DepartureSource
import com.androidfung.departureboard.data.repository.StationNameFormatter
import com.androidfung.departureboard.data.repository.TflStopPointUtils
import com.androidfung.departureboard.data.repository.TimetableScheduleResolver
import com.androidfung.departureboard.data.repository.mapper.TflStationMapper
import com.androidfung.departureboard.data.repository.region.RegionalTransitProvider
import com.androidfung.departureboard.data.repository.search.StationSearchEngine
import com.androidfung.departureboard.data.topology.CallingPointsResolver
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * United Kingdom (TfL, National Rail, DfT BODS, Tyne & Wear Metro) regional transit provider.
 * Wraps all UK rail and bus APIs behind a single regional composite interface, applying
 * cross-feed reconciliation (Overground named lines, Elizabeth line, and National Rail).
 */
class GbRegionalTransitProvider(
    private val apiService: TflApiService,
    private val nrApiService: NationalRailApiService,
    private val bodsApiService: BodsApiService,
    private val stationSearchEngine: StationSearchEngine,
    private val timetableScheduleResolver: TimetableScheduleResolver,
    private val stationLookup: (String) -> Station? = { null }
) : RegionalTransitProvider {

    override val region: TransitRegion = TransitRegion.GB

    private val tflDepartureSource = TflDepartureSource(apiService)
    private val nationalRailDepartureSource = NationalRailDepartureSource(nrApiService)
    private val bodsDepartureSource = BodsDepartureSource(bodsApiService, stationLookup)
    private val tyneAndWearMetroSource = TyneAndWearMetroDepartureSource()

    private val tflCallingPointsResolver = TflCallingPointsResolver(apiService)
    private val bodsCallingPointsResolver = BodsCallingPointsResolver(bodsApiService)

    override val departureSources: List<DepartureSource> = listOf(
        tflDepartureSource,
        nationalRailDepartureSource,
        bodsDepartureSource,
        tyneAndWearMetroSource
    )

    override val callingPointsResolvers: List<CallingPointsResolver> = listOf(
        tflCallingPointsResolver,
        bodsCallingPointsResolver
    )

    override fun ownsStationId(stationId: String): Boolean =
        !stationId.isHkTransitId &&
            !stationId.isSgTransitId &&
            !stationId.isAuTransitId &&
            !stationId.isJpTransitId

    override suspend fun searchStations(query: String): Result<List<Station>> =
        stationSearchEngine.searchStations(query)

    override suspend fun getStationById(stationId: String): Station? {
        GbDefaultStations.POPULAR_STATIONS.firstOrNull {
            it.id.equals(stationId, ignoreCase = true)
        }?.let { return it }

        return try {
            val detail = apiService.getStopPointDetail(stationId)
            val lines = TflStationMapper.extractLines(detail)
            val cleaned = StationNameFormatter.clean(detail.commonName ?: stationId)
            val extractedModes = lines.mapNotNull { it.mode }.distinct()
            val modes = extractedModes.ifEmpty { listOf("tube") }
            val firstChild = detail.children.firstOrNull()

            Station(
                id = detail.id,
                name = cleaned,
                modes = modes,
                zone = null,
                lat = firstChild?.lat,
                lon = firstChild?.lon,
                lines = lines,
                isFavorite = false
            )
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun getDepartures(stationId: String, stationName: String): Result<List<Departure>> = coroutineScope {
        val isBusStop = TflStopPointUtils.isBusStop(stationId, stationName)
        val matchingSources = departureSources.filter { it.canHandle(stationId, stationName, isBusStop) }
        if (matchingSources.isEmpty()) {
            return@coroutineScope Result.success(emptyList())
        }

        val deferredList = matchingSources.map { source ->
            async {
                source.fetchDepartures(stationId, stationName, isBusStop)
            }
        }

        val results = deferredList.awaitAll()
        var lastError: Throwable? = null
        val successfulFeeds = mutableListOf<List<Departure>>()

        for (res in results) {
            res.fold(
                onSuccess = { departures ->
                    successfulFeeds.add(departures)
                },
                onFailure = { error ->
                    lastError = error
                }
            )
        }

        // Deduplicate and merge all UK feeds (reconciling TfL + National Rail overlaps)
        var merged = emptyList<Departure>()
        for (feed in successfulFeeds) {
            merged = GbFeedReconciliationEngine.mergeAndDeduplicate(merged, feed)
        }

        val isStPancrasOrKingsCrossNR = TflStopPointUtils.isStPancrasOrKingsCrossNR(stationId, stationName)

        val processedDepartures = merged
            .filter { dep ->
                if (isStPancrasOrKingsCrossNR) {
                    val isUnderground = dep.transitMode == com.androidfung.departureboard.data.model.TransitMode.TUBE
                    !isUnderground
                } else {
                    true
                }
            }
            .sortedBy { it.timeToStationSeconds }

        if (processedDepartures.isEmpty() && lastError != null) {
            Result.failure(lastError!!)
        } else {
            Result.success(processedDepartures)
        }
    }

    override suspend fun checkStationServiceHours(
        stationId: String,
        lineId: String?
    ): StationServiceHoursInfo? =
        timetableScheduleResolver.checkStationServiceHours(stationId, lineId)
}
