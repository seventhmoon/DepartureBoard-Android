package com.androidfung.departureboard.data.repository.region.gb

import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TransitRegion
import com.androidfung.departureboard.data.model.gb.GbDefaultStations
import com.androidfung.departureboard.data.model.gb.StationServiceHoursInfo
import com.androidfung.departureboard.data.model.isAuTransitId
import com.androidfung.departureboard.data.model.isHkTransitId
import com.androidfung.departureboard.data.model.isJpTransitId
import com.androidfung.departureboard.data.model.isSgTransitId
import com.androidfung.departureboard.data.network.gb.TflApiService
import com.androidfung.departureboard.data.repository.StationNameFormatter
import com.androidfung.departureboard.data.repository.TimetableScheduleResolver
import com.androidfung.departureboard.data.repository.mapper.TflStationMapper
import com.androidfung.departureboard.data.repository.region.RegionalTransitProvider
import com.androidfung.departureboard.data.repository.search.StationSearchEngine

/**
 * United Kingdom (TfL, National Rail, DfT BODS, Tyne & Wear Metro) regional transit provider.
 */
class GbRegionalTransitProvider(
    private val apiService: TflApiService,
    private val stationSearchEngine: StationSearchEngine,
    private val timetableScheduleResolver: TimetableScheduleResolver
) : RegionalTransitProvider {

    override val region: TransitRegion = TransitRegion.GB

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

    override suspend fun checkStationServiceHours(
        stationId: String,
        lineId: String?
    ): StationServiceHoursInfo? =
        timetableScheduleResolver.checkStationServiceHours(stationId, lineId)
}
