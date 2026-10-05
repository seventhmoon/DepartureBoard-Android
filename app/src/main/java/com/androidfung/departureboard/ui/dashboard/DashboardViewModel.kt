package com.androidfung.departureboard.ui.dashboard

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TflLineColors
import com.androidfung.departureboard.data.repository.TransitRepository
import com.androidfung.departureboard.data.repository.TransitRepositoryImpl
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ViewModel for the Departure Board Dashboard.
 * Manages saved stations, live TfL departure predictions, 1-second countdown ticker,
 * and 30-second background polling for live updates.
 */
class DashboardViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: TransitRepository = TransitRepositoryImpl(application),
    enablePeriodicTasks: Boolean = true
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var autoRefreshJob: Job? = null
    private var countdownTickerJob: Job? = null
    private var recentlyDeletedStation: Station? = null

    init {
        observeSavedStations()
        if (enablePeriodicTasks) {
            startCountdownTicker()
            // startAutoRefreshPolling() will be triggered lifecycle-aware from DashboardScreen
        }
    }

    private fun observeSavedStations() {
        viewModelScope.launch {
            repository.savedStationsFlow.collectLatest { stations ->
                if (stations.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            stationCards = emptyList(),
                            isInitialLoading = false,
                            isRefreshing = false
                        )
                    }
                } else {
                    // Retain existing departures for stations if available, or prefill from offline cache
                    val existingMap = _uiState.value.stationCards.associateBy { it.station.id }
                    val newCards = stations.map { station ->
                        existingMap[station.id]?.copy(station = station)
                            ?: StationCardUiModel(
                                station = station,
                                availableLineBadges = inferLineBadges(station),
                                isLoading = true
                            )
                    }
                    _uiState.update {
                        it.copy(
                            stationCards = newCards,
                            isInitialLoading = false
                        )
                    }
                    // Fetch fresh arrivals using batched multi-station endpoint
                    refreshDeparturesForStations(stations)
                }
            }
        }
    }

    /**
     * Refreshes departure predictions for all currently saved stations.
     */
    fun refreshDepartures(isManualPullToRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isManualPullToRefresh) {
                _uiState.update { it.copy(isRefreshing = true) }
            }
            val currentStations = _uiState.value.stationCards.map { it.station }
            if (currentStations.isNotEmpty()) {
                refreshDeparturesForStations(currentStations)
            }
            _uiState.update {
                it.copy(
                    isRefreshing = false,
                    lastUpdatedTimestamp = System.currentTimeMillis()
                )
            }
        }
    }

    /**
     * Refreshes departures for a single station.
     */
    fun refreshStation(station: Station) {
        viewModelScope.launch {
            val result = repository.getDepartures(station.id, station.name)
            _uiState.update { state ->
                val updatedCards = state.stationCards.map { card ->
                    if (card.station.id == station.id) {
                        if (result.isSuccess) {
                            val departures = result.getOrDefault(emptyList())
                            val badges = if (departures.isNotEmpty()) {
                                departures.map { it.lineBadge }.distinctBy { it.lineId }
                            } else {
                                inferLineBadges(card.station)
                            }
                            card.copy(
                                departures = departures,
                                availableLineBadges = badges,
                                isLoading = false,
                                errorMessage = null
                            )
                        } else {
                            card.copy(
                                isLoading = false,
                                errorMessage = if (card.departures.isEmpty()) "Unable to load departures" else null
                            )
                        }
                    } else {
                        card
                    }
                }
                state.copy(stationCards = updatedCards)
            }
        }
    }

    /**
     * Searches TfL transit stations with query.
     */
    suspend fun searchStations(query: String): List<Station> {
        return repository.searchStations(query).getOrDefault(emptyList())
    }

    private suspend fun refreshDeparturesForStations(stations: List<Station>) {
        val lineStatusesDeferred = viewModelScope.async { repository.getLineStatuses() }
        val batchDeparturesDeferred = viewModelScope.async { repository.getBatchDepartures(stations) }

        val lineStatuses = lineStatusesDeferred.await()
        val results = batchDeparturesDeferred.await()

        _uiState.update { state ->
            val updatedCards = state.stationCards.map { card ->
                val res = results[card.station.id]
                if (res != null && res.isSuccess) {
                    val departures = res.getOrDefault(emptyList())
                    // Extract line badges strictly from active departures, falling back to verified station lines only if departures are empty
                    val departureBadges = departures.map { it.lineBadge }.distinctBy { it.displayName }
                    val rawBadges = if (departureBadges.isNotEmpty()) {
                        departureBadges.filter { badge ->
                            // Guard: Filter out spurious depot movements (e.g. H&C depot run at Wembley Park)
                            val isWembleyPark = card.station.id == "940GZZLUWYP" || "wembley park" in card.station.name.lowercase()
                            !(isWembleyPark && badge.lineId.equals("hammersmith-city", ignoreCase = true))
                        }
                    } else {
                        inferLineBadges(card.station)
                    }

                    // Sort line badges logically: Tube/Rail lines alphabetically, then numeric Bus routes in ascending order
                    val sortedBadges = rawBadges.sortedWith(
                        compareBy<LineBadgeInfo> { badge ->
                            if (badge.displayName.all { it.isDigit() }) 1 else 0
                        }.thenBy { badge ->
                            val num = badge.displayName.filter { it.isDigit() }.toIntOrNull()
                            num ?: Int.MAX_VALUE
                        }.thenBy { it.displayName }
                    )

                    val badgesWithStatus = sortedBadges.map { badge ->
                        val statusItem = lineStatuses[badge.lineId.lowercase()]
                        val firstDetail = statusItem?.lineStatuses?.firstOrNull()
                        if (firstDetail != null) {
                            badge.copy(
                                statusSeverity = firstDetail.statusSeverity,
                                statusDescription = firstDetail.statusSeverityDescription,
                                disruptionReason = firstDetail.reason
                            )
                        } else {
                            badge
                        }
                    }

                    card.copy(
                        departures = departures,
                        availableLineBadges = badgesWithStatus,
                        isLoading = false,
                        errorMessage = null
                    )
                } else {
                    card.copy(
                        isLoading = false,
                        errorMessage = if (card.departures.isEmpty()) "Unable to load departures" else null
                    )
                }
            }
            state.copy(
                stationCards = updatedCards,
                isRefreshing = false,
                lastUpdatedTimestamp = System.currentTimeMillis()
            )
        }
    }

    /**
     * Auto-refreshes live departures every 30 seconds.
     */
    internal fun startAutoRefreshPolling() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(30_000L)
                val stations = _uiState.value.stationCards.map { it.station }
                if (stations.isNotEmpty()) {
                    refreshDeparturesForStations(stations)
                }
            }
        }
    }

    internal fun stopAutoRefreshPolling() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    /**
     * 1-second countdown ticker decrements timeToStationSeconds locally so that live counters tick in real time.
     */
    internal fun startCountdownTicker() {
        countdownTickerJob?.cancel()
        countdownTickerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                _uiState.update { state ->
                    val tickedCards = state.stationCards.map { card ->
                        if (card.departures.isEmpty()) {
                            card
                        } else {
                            val tickedDepartures = card.departures
                                .map { departure ->
                                    val newSeconds = (departure.timeToStationSeconds - 1).coerceAtLeast(0)
                                    departure.copy(timeToStationSeconds = newSeconds)
                                }
                                .sortedBy { it.timeToStationSeconds }
                            card.copy(departures = tickedDepartures)
                        }
                    }
                    state.copy(stationCards = tickedCards)
                }
            }
        }
    }

    internal fun stopCountdownTicker() {
        countdownTickerJob?.cancel()
        countdownTickerJob = null
    }

    /**
     * Adds a station to saved preferences.
     */
    fun addStation(station: Station) {
        viewModelScope.launch {
            repository.saveStation(station)
            _uiState.update {
                it.copy(userMessage = "Added ${station.name}")
            }
        }
    }

    /**
     * Toggles expansion state for an individual station card.
     */
    fun toggleStationExpand(stationId: String) {
        _uiState.update { state ->
            val updated = state.stationCards.map { card ->
                if (card.station.id == stationId) {
                    card.copy(isExpanded = !card.isExpanded)
                } else {
                    card
                }
            }
            state.copy(stationCards = updated)
        }
    }

    /**
     * Expands all station departure cards.
     */
    fun expandAll() {
        _uiState.update { state ->
            val updated = state.stationCards.map { it.copy(isExpanded = true) }
            state.copy(stationCards = updated)
        }
    }

    /**
     * Minimizes (collapses) all station departure cards to compact overview rows.
     */
    fun collapseAll() {
        _uiState.update { state ->
            val updated = state.stationCards.map { it.copy(isExpanded = false) }
            state.copy(stationCards = updated)
        }
    }

    /**
     * Reorders stations when dragged and dropped, updating state immediately and persisting order.
     */
    fun moveStation(fromIndex: Int, toIndex: Int) {
        val currentCards = _uiState.value.stationCards.toMutableList()
        if (fromIndex !in currentCards.indices || toIndex !in currentCards.indices || fromIndex == toIndex) {
            return
        }
        val item = currentCards.removeAt(fromIndex)
        currentCards.add(toIndex, item)
        _uiState.update { it.copy(stationCards = currentCards) }

        viewModelScope.launch {
            repository.reorderStations(currentCards.map { it.station })
        }
    }

    /**
     * Removes a station from saved preferences and offers undo capability.
     */
    fun removeStation(station: Station) {
        recentlyDeletedStation = station
        viewModelScope.launch {
            repository.removeStation(station.id)
            _uiState.update {
                it.copy(userMessage = "Removed ${station.name}")
            }
        }
    }

    /**
     * Restores the recently deleted station.
     */
    fun undoRemoveStation() {
        val stationToRestore = recentlyDeletedStation ?: return
        viewModelScope.launch {
            repository.saveStation(stationToRestore)
            recentlyDeletedStation = null
            _uiState.update {
                it.copy(userMessage = "Restored ${stationToRestore.name}")
            }
        }
    }

    /**
     * Updates nearest station based on device GPS location.
     */
    fun updateNearestStation(context: Context) {
        viewModelScope.launch {
            val location = com.androidfung.departureboard.util.LocationHelper.getCurrentLocation(context)
            if (location != null) {
                val stations = _uiState.value.stationCards.map { it.station }
                val nearest = com.androidfung.departureboard.util.LocationHelper.findNearestStation(location, stations)
                if (nearest != null) {
                    android.util.Log.d("LocationHelper", "Found nearest station: ${nearest.first.name} (${nearest.second}m away)")
                    _uiState.update {
                        it.copy(
                            nearestStationId = nearest.first.id,
                            nearestStationDistanceMeters = nearest.second
                        )
                    }
                }
            } else {
                android.util.Log.w("LocationHelper", "Location was null")
            }
        }
    }

    /**
     * Clears user snackbar message.
     */
    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    /**
     * Helper to infer line badges when departures haven't loaded yet.
     * Excludes generic "National-rail" or unused "Bus" badges.
     */
    private fun inferLineBadges(station: Station): List<LineBadgeInfo> {
        return com.androidfung.departureboard.data.model.StationLineInferrer.infer(station)
    }

    override fun onCleared() {
        stopCountdownTicker()
        stopAutoRefreshPolling()
    }
}
