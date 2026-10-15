package com.androidfung.departureboard.test

import com.androidfung.departureboard.data.datastore.StationPreferencesDataSource
import com.androidfung.departureboard.data.model.DefaultStations
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Complete, in-memory test double for [StationPreferencesDataSource].
 */
open class FakeStationPreferencesDataSource(
    initialStations: List<Station> = DefaultStations.POPULAR_STATIONS,
    initialRecentId: String? = DefaultStations.POPULAR_STATIONS.firstOrNull()?.id,
    initialDepartureRows: Int = 5,
    initialThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    initialDynamicColor: Boolean = true
) : StationPreferencesDataSource {

    private val _stations = MutableStateFlow(initialStations)
    private val _recentId = MutableStateFlow(initialRecentId)
    private val _departureRows = MutableStateFlow(initialDepartureRows)
    private val _themeMode = MutableStateFlow(initialThemeMode)
    private val _dynamicColor = MutableStateFlow(initialDynamicColor)

    override val savedStationsFlow: Flow<List<Station>> = _stations.asStateFlow()
    override val recentStationIdFlow: Flow<String?> = _recentId.asStateFlow()
    override val departureRowsCountFlow: Flow<Int> = _departureRows.asStateFlow()
    override val themeModeFlow: Flow<AppThemeMode> = _themeMode.asStateFlow()
    override val dynamicColorFlow: Flow<Boolean> = _dynamicColor.asStateFlow()

    override suspend fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        _dynamicColor.value = enabled
    }

    override suspend fun setDepartureRowsCount(count: Int) {
        _departureRows.value = count
    }

    override suspend fun saveStation(station: Station) {
        val list = _stations.value.toMutableList()
        val idx = list.indexOfFirst { it.id == station.id }
        if (idx >= 0) {
            list[idx] = station
        } else {
            list.add(station)
        }
        _stations.value = list
    }

    override suspend fun saveStations(stations: List<Station>) {
        _stations.value = stations
    }

    override suspend fun removeStation(stationId: String) {
        _stations.value = _stations.value.filterNot { it.id == stationId }
    }

    override suspend fun setRecentStationId(stationId: String) {
        _recentId.value = stationId
    }
}
