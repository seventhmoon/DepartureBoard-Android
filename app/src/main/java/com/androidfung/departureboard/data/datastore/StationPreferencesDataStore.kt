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

    suspend fun saveStation(station: Station)
    suspend fun saveStations(stations: List<Station>)
    suspend fun removeStation(stationId: String)
    suspend fun setRecentStationId(stationId: String)
    suspend fun setDepartureRowsCount(count: Int)
    suspend fun setThemeMode(mode: AppThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
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
        private val KEY_RECENT_STATION_ID = stringPreferencesKey("recent_station_id")
        private val KEY_DEPARTURE_ROWS_COUNT = androidx.datastore.preferences.core.intPreferencesKey("departure_rows_count")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_DYNAMIC_COLOR = androidx.datastore.preferences.core.booleanPreferencesKey("dynamic_color")
        const val DEFAULT_DEPARTURE_ROWS = 5
        const val DEFAULT_DYNAMIC_COLOR = true
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

    /**
     * Flow of saved/favorite stations. If empty, provides popular defaults.
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
            val json = preferences[KEY_SAVED_STATIONS]
            if (json.isNullOrBlank()) {
                DefaultStations.POPULAR_STATIONS
            } else {
                try {
                    val dtos = stationListAdapter.fromJson(json)
                    dtos?.// An explicitly empty list is preserved so the dashboard can show
                        // its empty state instead of silently resurrecting the defaults.
                    map { it.toStation() }
                        ?: // JSON is the literal "null" — treat as never-saved → popular defaults
                        DefaultStations.POPULAR_STATIONS
                } catch (_: Exception) {
                    DefaultStations.POPULAR_STATIONS
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
            preferences[KEY_RECENT_STATION_ID] ?: DefaultStations.POPULAR_STATIONS.firstOrNull()?.id
        }

    /**
     * Saves a station or toggles favorite status.
     */
    override suspend fun saveStation(station: Station) {
        context.dataStore.edit { preferences ->
            val currentList = loadCurrentList(preferences)
            val updated = currentList.toMutableList()
            val existingIndex = updated.indexOfFirst { it.id == station.id }
            if (existingIndex >= 0) {
                updated[existingIndex] = station.toDto()
            } else {
                updated.add(station.toDto())
            }
            preferences[KEY_SAVED_STATIONS] = stationListAdapter.toJson(updated)
        }
    }

    /**
     * Saves the entire list of stations preserving order.
     */
    override suspend fun saveStations(stations: List<Station>) {
        context.dataStore.edit { preferences ->
            val dtos = stations.map { it.toDto() }
            preferences[KEY_SAVED_STATIONS] = stationListAdapter.toJson(dtos)
        }
    }

    /**
     * Removes a station from saved list.
     */
    override suspend fun removeStation(stationId: String) {
        context.dataStore.edit { preferences ->
            val currentList = loadCurrentList(preferences)
            val filtered = currentList.filterNot { it.id == stationId }
            preferences[KEY_SAVED_STATIONS] = stationListAdapter.toJson(filtered)
        }
    }

    /**
     * Sets the most recently viewed station ID.
     */
    override suspend fun setRecentStationId(stationId: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_RECENT_STATION_ID] = stationId
        }
    }

    private fun loadCurrentList(preferences: Preferences): List<StationDto> {
        val json = preferences[KEY_SAVED_STATIONS]
        if (json.isNullOrBlank()) {
            return DefaultStations.POPULAR_STATIONS.map { it.toDto() }
        }
        return try {
            stationListAdapter.fromJson(json) ?: DefaultStations.POPULAR_STATIONS.map { it.toDto() }
        } catch (_: Exception) {
            DefaultStations.POPULAR_STATIONS.map { it.toDto() }
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

