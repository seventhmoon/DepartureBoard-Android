package com.androidfung.departureboard.test

import com.androidfung.departureboard.data.model.CallingPoint
import com.androidfung.departureboard.data.model.DefaultStations
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.StationServiceHoursInfo
import com.androidfung.departureboard.data.model.TflLineStatusItem
import com.androidfung.departureboard.data.repository.TransitRepository
import com.androidfung.departureboard.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Reusable, fluent in-memory test double for [TransitRepository].
 */
open class FakeTransitRepository(
    initialStations: List<Station> = DefaultStations.POPULAR_STATIONS.take(2),
    initialRecentStationId: String? = "940GZZLUOXC",
    initialDepartureRowsCount: Int = 5,
    initialThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    initialDynamicColor: Boolean = true,
    var departuresResult: (stationId: String, stationName: String) -> Result<List<Departure>> = { id, name ->
        Result.success(DefaultStations.getFallbackDepartures(id, name))
    },
    var lineStatuses: Map<String, TflLineStatusItem> = emptyMap(),
    var callingPointsResult: (departure: Departure) -> List<CallingPoint> = { emptyList() },
    var serviceHoursResult: (stationId: String, lineId: String?) -> StationServiceHoursInfo? = { _, _ -> null },
    var searchResult: (query: String) -> Result<List<Station>> = { query ->
        val matches = DefaultStations.POPULAR_STATIONS.filter {
            it.name.contains(query, ignoreCase = true)
        }
        Result.success(matches)
    },
    var nearbyStationsResult: (lat: Double, lon: Double, radiusMeters: Int) -> List<Station> = { _, _, _ -> emptyList() }
) : TransitRepository {

    val stationsFlow = MutableStateFlow(initialStations)
    val recentStationIdStateFlow = MutableStateFlow(initialRecentStationId)
    val departureRowsCountStateFlow = MutableStateFlow(initialDepartureRowsCount)
    val themeModeStateFlow = MutableStateFlow(initialThemeMode)
    val dynamicColorStateFlow = MutableStateFlow(initialDynamicColor)

    val savedStations = mutableListOf<Station>()
    val removedStationIds = mutableListOf<String>()

    override val savedStationsFlow: Flow<List<Station>> = stationsFlow.asStateFlow()
    override val recentStationIdFlow: Flow<String?> = recentStationIdStateFlow.asStateFlow()
    override val departureRowsCountFlow: Flow<Int> = departureRowsCountStateFlow.asStateFlow()
    override val themeModeFlow: Flow<AppThemeMode> = themeModeStateFlow.asStateFlow()
    override val dynamicColorFlow: Flow<Boolean> = dynamicColorStateFlow.asStateFlow()

    override suspend fun setDepartureRowsCount(count: Int) {
        departureRowsCountStateFlow.value = count
    }

    override suspend fun setThemeMode(mode: AppThemeMode) {
        themeModeStateFlow.value = mode
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        dynamicColorStateFlow.value = enabled
    }

    override suspend fun searchStations(query: String): Result<List<Station>> = searchResult(query)

    override suspend fun getStationById(stationId: String): Station? {
        return DefaultStations.POPULAR_STATIONS.firstOrNull { it.id.equals(stationId, ignoreCase = true) }
            ?: stationsFlow.value.firstOrNull { it.id.equals(stationId, ignoreCase = true) }
    }

    override suspend fun getDepartures(stationId: String, stationName: String): Result<List<Departure>> =
        departuresResult(stationId, stationName)

    override suspend fun getBatchDepartures(stations: List<Station>): Map<String, Result<List<Departure>>> =
        stations.associate { it.id to getDepartures(it.id, it.name) }

    override fun getDeparturesFlow(stationId: String, stationName: String): Flow<Result<List<Departure>>> =
        flowOf(departuresResult(stationId, stationName))

    override suspend fun getCachedDepartures(stationId: String): List<Departure> =
        departuresResult(stationId, "").getOrNull()?.filter { it.stationId == stationId } ?: emptyList()

    override suspend fun getLineStatuses(): Map<String, TflLineStatusItem> = lineStatuses

    override suspend fun getCallingPoints(departure: Departure): List<CallingPoint> =
        callingPointsResult(departure)

    override suspend fun checkStationServiceHours(stationId: String, lineId: String?): StationServiceHoursInfo? =
        serviceHoursResult(stationId, lineId)

    override suspend fun saveStation(station: Station) {
        savedStations.add(station)
        stationsFlow.value = stationsFlow.value + station
    }

    override suspend fun reorderStations(stations: List<Station>) {
        stationsFlow.value = stations
    }

    override suspend fun removeStation(stationId: String) {
        removedStationIds.add(stationId)
        stationsFlow.value = stationsFlow.value.filterNot { it.id == stationId }
    }

    override suspend fun setRecentStationId(stationId: String) {
        recentStationIdStateFlow.value = stationId
    }

    override suspend fun getNearbyStationsFromApi(lat: Double, lon: Double, radiusMeters: Int): List<Station> =
        nearbyStationsResult(lat, lon, radiusMeters)
}
