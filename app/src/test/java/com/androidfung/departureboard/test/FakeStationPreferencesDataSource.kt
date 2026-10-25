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
    private val _selectedRegion = MutableStateFlow(com.androidfung.departureboard.data.model.TransitRegion.GB)
    private val _isHkRegionEnabled = MutableStateFlow(false)
    private val _autoSortByLocation = MutableStateFlow(false)

    override val savedStationsFlow: Flow<List<Station>> = _stations.asStateFlow()
    override val recentStationIdFlow: Flow<String?> = _recentId.asStateFlow()
    override val departureRowsCountFlow: Flow<Int> = _departureRows.asStateFlow()
    override val themeModeFlow: Flow<AppThemeMode> = _themeMode.asStateFlow()
    override val dynamicColorFlow: Flow<Boolean> = _dynamicColor.asStateFlow()
    override val selectedRegionFlow: Flow<com.androidfung.departureboard.data.model.TransitRegion> = _selectedRegion.asStateFlow()
    override val isHkRegionEnabledFlow: Flow<Boolean> = _isHkRegionEnabled.asStateFlow()
    override val autoSortByLocationFlow: Flow<Boolean> = _autoSortByLocation.asStateFlow()

    override suspend fun setAutoSortByLocation(enabled: Boolean) {
        _autoSortByLocation.value = enabled
    }

    override suspend fun setSelectedRegion(region: com.androidfung.departureboard.data.model.TransitRegion) {
        _selectedRegion.value = region
    }

    override suspend fun setHkRegionEnabled(enabled: Boolean) {
        _isHkRegionEnabled.value = enabled
    }

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

    private val _recentSearchedStations = MutableStateFlow<List<Station>>(emptyList())
    override val recentSearchedStationsFlow: Flow<List<Station>> = _recentSearchedStations.asStateFlow()

    override suspend fun addRecentSearchedStation(station: Station) {
        val list = _recentSearchedStations.value.filterNot { it.id == station.id }.toMutableList()
        list.add(0, station)
        _recentSearchedStations.value = list
    }

    override suspend fun removeRecentSearchedStation(stationId: String) {
        _recentSearchedStations.value = _recentSearchedStations.value.filterNot { it.id == stationId }
    }

    override suspend fun clearRecentSearchedStations() {
        _recentSearchedStations.value = emptyList()
    }

    override suspend fun setRecentStationId(stationId: String) {
        _recentId.value = stationId
    }
}
