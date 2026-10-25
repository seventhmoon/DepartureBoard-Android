package com.androidfung.departureboard.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.androidfung.departureboard.data.model.DefaultStations
import com.androidfung.departureboard.data.model.Station
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.androidfung.departureboard.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

import com.androidfung.departureboard.data.model.TransitRegion
import com.androidfung.departureboard.data.model.HkDefaultStations

internal val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "station_preferences")

/**
 * Interface defining station preferences data source operations.
 */
interface StationPreferencesDataSource {
    val savedStationsFlow: Flow<List<Station>>
    val recentStationIdFlow: Flow<String?>
    val departureRowsCountFlow: Flow<Int>
    val themeModeFlow: Flow<AppThemeMode>
    val dynamicColorFlow: Flow<Boolean>
    val selectedRegionFlow: Flow<TransitRegion>
    val isHkRegionEnabledFlow: Flow<Boolean>
    val autoSortByLocationFlow: Flow<Boolean>
    val recentSearchedStationsFlow: Flow<List<Station>>

    suspend fun saveStation(station: Station)
    suspend fun saveStations(stations: List<Station>)
    suspend fun removeStation(stationId: String)
    suspend fun setRecentStationId(stationId: String)
    suspend fun addRecentSearchedStation(station: Station)
    suspend fun removeRecentSearchedStation(stationId: String)
    suspend fun clearRecentSearchedStations()
    suspend fun setDepartureRowsCount(count: Int)
    suspend fun setThemeMode(mode: AppThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setSelectedRegion(region: TransitRegion)
    suspend fun setHkRegionEnabled(enabled: Boolean)
    suspend fun setAutoSortByLocation(enabled: Boolean)
}

/**
 * DataStore repository for persisting saved/favorite stations.
 */
class StationPreferencesDataStore(private val context: Context) : StationPreferencesDataSource {

    // Moshi code-generated adapters (KSP @JsonClass) resolve automatically via ClassIndex;
    // no reflective KotlinJsonAdapterFactory is needed, keeping release (R8) builds safe.
    private val moshi = Moshi.Builder()
        .build()

    private val stationListType = Types.newParameterizedType(List::class.java, StationDto::class.java)
    private val stationListAdapter = moshi.adapter<List<StationDto>>(stationListType)

    companion object {
        private val KEY_SAVED_STATIONS = stringPreferencesKey("saved_stations_json")
        private val KEY_SAVED_STATIONS_HK = stringPreferencesKey("saved_stations_json_hk")
        private val KEY_RECENT_SEARCHED_STATIONS = stringPreferencesKey("recent_searched_stations_json")
        private val KEY_RECENT_STATION_ID = stringPreferencesKey("recent_station_id")
        private val KEY_DEPARTURE_ROWS_COUNT = androidx.datastore.preferences.core.intPreferencesKey("departure_rows_count")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_DYNAMIC_COLOR = androidx.datastore.preferences.core.booleanPreferencesKey("dynamic_color")
        private val KEY_SELECTED_REGION = stringPreferencesKey("selected_region")
        private val KEY_HK_REGION_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("hk_region_enabled")
        private val KEY_AUTO_SORT_BY_LOCATION = androidx.datastore.preferences.core.booleanPreferencesKey("auto_sort_by_location")
        const val DEFAULT_DEPARTURE_ROWS = 5
        const val DEFAULT_DYNAMIC_COLOR = true
        private const val MAX_RECENT_SEARCHES = 10
    }

    override val themeModeFlow: Flow<AppThemeMode> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val modeName = preferences[KEY_THEME_MODE]
            if (modeName != null) {
                try {
                    AppThemeMode.valueOf(modeName)
                } catch (_: IllegalArgumentException) {
                    AppThemeMode.SYSTEM
                }
            } else {
                AppThemeMode.SYSTEM
            }
        }

    override val dynamicColorFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_DYNAMIC_COLOR] ?: DEFAULT_DYNAMIC_COLOR
        }

    override suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[KEY_THEME_MODE] = mode.name
        }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DYNAMIC_COLOR] = enabled
        }
    }

    override val departureRowsCountFlow: Flow<Int> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_DEPARTURE_ROWS_COUNT] ?: DEFAULT_DEPARTURE_ROWS
        }

    override suspend fun setDepartureRowsCount(count: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DEPARTURE_ROWS_COUNT] = count
        }
    }

    override val isHkRegionEnabledFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_HK_REGION_ENABLED] ?: false
        }

    override suspend fun setHkRegionEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_HK_REGION_ENABLED] = enabled
            if (!enabled && preferences[KEY_SELECTED_REGION] == TransitRegion.HK.id) {
                preferences[KEY_SELECTED_REGION] = TransitRegion.GB.id
            }
        }
    }

    override val autoSortByLocationFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_AUTO_SORT_BY_LOCATION] ?: false
        }

    override suspend fun setAutoSortByLocation(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTO_SORT_BY_LOCATION] = enabled
        }
    }

    override val selectedRegionFlow: Flow<TransitRegion> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val regionId = preferences[KEY_SELECTED_REGION]
            TransitRegion.fromId(regionId)
        }

    override suspend fun setSelectedRegion(region: TransitRegion) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SELECTED_REGION] = region.id
        }
    }

    private fun getSavedStationsKey(region: TransitRegion): Preferences.Key<String> {
        return when (region) {
            TransitRegion.HK -> KEY_SAVED_STATIONS_HK
            TransitRegion.GB -> KEY_SAVED_STATIONS
        }
    }

    private fun getDefaultStationsForRegion(region: TransitRegion): List<Station> {
        return when (region) {
            TransitRegion.HK -> HkDefaultStations.POPULAR_STATIONS
            TransitRegion.GB -> DefaultStations.POPULAR_STATIONS
        }
    }

    /**
     * Flow of saved/favorite stations. If empty, provides popular defaults for the active region.
     */
    override val savedStationsFlow: Flow<List<Station>> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val region = TransitRegion.fromId(preferences[KEY_SELECTED_REGION])
            val key = getSavedStationsKey(region)
            val defaults = getDefaultStationsForRegion(region)
            val json = preferences[key]
            if (json.isNullOrBlank()) {
                defaults
            } else {
                try {
                    val dtos = stationListAdapter.fromJson(json)
                    dtos?.map { it.toStation() } ?: defaults
                } catch (_: Exception) {
                    defaults
                }
            }
        }

    /**
     * Flow of the most recently selected station ID.
     */
    override val recentStationIdFlow: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val region = TransitRegion.fromId(preferences[KEY_SELECTED_REGION])
            val defaults = getDefaultStationsForRegion(region)
            preferences[KEY_RECENT_STATION_ID] ?: defaults.firstOrNull()?.id
        }

    /**
     * Saves a station or toggles favorite status.
     */
    override suspend fun saveStation(station: Station) {
        context.dataStore.edit { preferences ->
            val region = TransitRegion.fromId(preferences[KEY_SELECTED_REGION])
            val key = getSavedStationsKey(region)
            val currentList = loadCurrentList(preferences, key, region)
            val updated = currentList.toMutableList()
            val existingIndex = updated.indexOfFirst { it.id == station.id }
            if (existingIndex >= 0) {
                updated[existingIndex] = station.toDto()
            } else {
                updated.add(station.toDto())
            }
            preferences[key] = stationListAdapter.toJson(updated)
        }
    }

    override suspend fun saveStations(stations: List<Station>) {
        context.dataStore.edit { preferences ->
            val region = TransitRegion.fromId(preferences[KEY_SELECTED_REGION])
            val key = getSavedStationsKey(region)
            preferences[key] = stationListAdapter.toJson(stations.map { it.toDto() })
        }
    }

    override suspend fun removeStation(stationId: String) {
        context.dataStore.edit { preferences ->
            val region = TransitRegion.fromId(preferences[KEY_SELECTED_REGION])
            val key = getSavedStationsKey(region)
            val currentList = loadCurrentList(preferences, key, region)
            val updated = currentList.filterNot { it.id == stationId }
            preferences[key] = stationListAdapter.toJson(updated)
        }
    }

    override suspend fun setRecentStationId(stationId: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_RECENT_STATION_ID] = stationId
        }
    }

    override val recentSearchedStationsFlow: Flow<List<Station>> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val json = preferences[KEY_RECENT_SEARCHED_STATIONS]
            if (json.isNullOrBlank()) {
                emptyList()
            } else {
                try {
                    val dtos = stationListAdapter.fromJson(json) ?: emptyList()
                    dtos.map { it.toStation() }
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }

    override suspend fun addRecentSearchedStation(station: Station) {
        context.dataStore.edit { preferences ->
            val json = preferences[KEY_RECENT_SEARCHED_STATIONS]
            val currentList = if (!json.isNullOrBlank()) {
                try {
                    stationListAdapter.fromJson(json)?.toMutableList() ?: mutableListOf()
                } catch (_: Exception) {
                    mutableListOf()
                }
            } else {
                mutableListOf()
            }

            // Remove if already exists so we can move it to the top
            currentList.removeAll { it.id == station.id }
            currentList.add(0, station.toDto())

            val trimmed = if (currentList.size > MAX_RECENT_SEARCHES) {
                currentList.subList(0, MAX_RECENT_SEARCHES)
            } else {
                currentList
            }

            preferences[KEY_RECENT_SEARCHED_STATIONS] = stationListAdapter.toJson(trimmed)
        }
    }

    override suspend fun removeRecentSearchedStation(stationId: String) {
        context.dataStore.edit { preferences ->
            val json = preferences[KEY_RECENT_SEARCHED_STATIONS] ?: return@edit
            val currentList = try {
                stationListAdapter.fromJson(json)?.toMutableList() ?: return@edit
            } catch (_: Exception) {
                return@edit
            }

            currentList.removeAll { it.id == stationId }
            preferences[KEY_RECENT_SEARCHED_STATIONS] = stationListAdapter.toJson(currentList)
        }
    }

    override suspend fun clearRecentSearchedStations() {
        context.dataStore.edit { preferences ->
            preferences.remove(KEY_RECENT_SEARCHED_STATIONS)
        }
    }

    private fun loadCurrentList(
        preferences: Preferences,
        key: Preferences.Key<String> = KEY_SAVED_STATIONS,
        region: TransitRegion = TransitRegion.GB
    ): List<StationDto> {
        val json = preferences[key]
        val defaults = getDefaultStationsForRegion(region)
        if (json.isNullOrBlank()) {
            return defaults.map { it.toDto() }
        }
        return try {
            stationListAdapter.fromJson(json) ?: defaults.map { it.toDto() }
        } catch (_: Exception) {
            defaults.map { it.toDto() }
        }
    }
}

/**
 * Lightweight DTO for serializing stations to JSON.
 * Code-generated Moshi adapter (no runtime reflection) so R8/minified release builds
 * keep the DataStore JSON round-trip intact.
 */
@JsonClass(generateAdapter = true)
data class StationDto(
    val id: String,
    val name: String,
    val modes: List<String> = emptyList(),
    val zone: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val isFavorite: Boolean = false,
    val lines: List<StationLineDto> = emptyList()
) {
    fun toStation(): Station = Station(
        id = id,
        name = name,
        modes = modes,
        zone = zone,
        lat = lat,
        lon = lon,
        isFavorite = isFavorite,
        lines = lines.map { it.toStationLineInfo() }
    )
}

@JsonClass(generateAdapter = true)
data class StationLineDto(
    val id: String,
    val name: String,
    val mode: String? = null
) {
    fun toStationLineInfo(): com.androidfung.departureboard.data.model.StationLineInfo =
        com.androidfung.departureboard.data.model.StationLineInfo(
            id = id,
            name = name,
            mode = mode
        )
}

fun com.androidfung.departureboard.data.model.StationLineInfo.toDto(): StationLineDto = StationLineDto(
    id = id,
    name = name,
    mode = mode
)

fun Station.toDto(): StationDto = StationDto(
    id = id,
    name = name,
    modes = modes,
    zone = zone,
    lat = lat,
    lon = lon,
    isFavorite = isFavorite,
    lines = lines.map { it.toDto() }
)

