package com.androidfung.departureboard.ui.dashboard

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.androidfung.departureboard.billing.BillingDataSource
import com.androidfung.departureboard.billing.BillingRepository
import com.androidfung.departureboard.billing.SubscriptionTier
import com.androidfung.departureboard.data.model.DefaultStations
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
 * 30-second background polling for live updates, and Pro subscription quotas.
 */
class DashboardViewModel @JvmOverloads constructor(
    application: Application,
    // Exposed (internal) so the AI assistant sheet can reuse the same repository instance
    // and its in-memory caches instead of constructing a second one.
    internal val repository: TransitRepository = TransitRepositoryImpl(application),
    internal val billingRepository: BillingDataSource = BillingRepository.getInstance(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var autoRefreshJob: Job? = null
    private var countdownTickerJob: Job? = null
    private var recentlyDeletedStation: Station? = null

    init {
        observeSavedStations()
        observeSubscriptionStatus()
        // startAutoRefreshPolling() and startCountdownTicker() are driven lifecycle-aware
        // from DashboardScreen (LifecycleResumeEffect) so the 1s ticker doesn't keep
        // updating StateFlow with the screen off.
    }

    private fun observeSubscriptionStatus() {
        viewModelScope.launch {
            billingRepository.isProFlow.collectLatest { isPro ->
                _uiState.update { it.copy(isPro = isPro) }
            }
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
                                departures.map { it.lineBadge }
                                    .distinctBy { it.lineId }
                                    .sortedWith(LineBadgeInfo.NATURAL_COMPARATOR)
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

    /**
     * Loads calling points for a departure.
     */
    suspend fun getCallingPoints(departure: Departure): List<com.androidfung.departureboard.data.model.CallingPoint> {
        return repository.getCallingPoints(departure)
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
                            if (isWembleyPark && badge.lineId.equals("hammersmith-city", ignoreCase = true)) return@filter false

                            // Guard: Filter out Tube badges for St Pancras or King's Cross National Rail
                            val isStPancrasOrKingsCrossNR = card.station.id == "910GSTPX" || card.station.id == "910GKNGX" ||
                                    card.station.name.lowercase().let { it == "st pancras international" || it == "st pancras" || it == "king's cross" }
                            if (isStPancrasOrKingsCrossNR) {
                                if (badge.mode == com.androidfung.departureboard.data.model.TransitMode.TUBE ||
                                    badge.lineId.lowercase() in TflLineColors.TUBE_LINE_IDS
                                ) {
                                    return@filter false
                                }
                            }

                            true
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

                    val tickedDetailDepartures = state.detailDepartures.map { departure ->
                        val newSeconds = (departure.timeToStationSeconds - 1).coerceAtLeast(0)
                        departure.copy(timeToStationSeconds = newSeconds)
                    }.sortedBy { it.timeToStationSeconds }

                    state.copy(
                        stationCards = tickedCards,
                        detailDepartures = tickedDetailDepartures
                    )
                }
            }
        }
    }

    internal fun stopCountdownTicker() {
        countdownTickerJob?.cancel()
        countdownTickerJob = null
    }

    /**
     * Adds a station to saved preferences, enforcing quota limits:
     * - Free: max 2 stations
     * - Pro: max 10 stations (capped to safeguard API rate limits & battery)
     */
    fun addStation(station: Station) {
        val currentCount = _uiState.value.stationCards.size
        val isPro = _uiState.value.isPro

        // Already contains this station
        if (_uiState.value.stationCards.any { it.station.id == station.id }) {
            _uiState.update { it.copy(userMessage = "${station.name} is already saved") }
            return
        }

        if (!isPro && currentCount >= SubscriptionTier.FREE_MAX_STATIONS) {
            _uiState.update {
                it.copy(
                    paywallPromptReason = "Free plan includes up to ${SubscriptionTier.FREE_MAX_STATIONS} saved stations. Upgrade to Pro for up to ${SubscriptionTier.PRO_MAX_STATIONS} stations."
                )
            }
            return
        }

        if (isPro && currentCount >= SubscriptionTier.PRO_MAX_STATIONS) {
            _uiState.update {
                it.copy(
                    userMessage = "Maximum limit of ${SubscriptionTier.PRO_MAX_STATIONS} stations reached to protect live API limits."
                )
            }
            return
        }

        viewModelScope.launch {
            repository.saveStation(station)
            _uiState.update {
                it.copy(userMessage = "Added ${station.name}")
            }
        }
    }

    fun showPaywall(reason: String? = null) {
        _uiState.update { it.copy(paywallPromptReason = reason ?: "Unlock Prompt Departure Pro") }
    }

    fun dismissPaywall() {
        _uiState.update { it.copy(paywallPromptReason = null) }
    }

    /**
     * Toggles Free and Pro subscription states in debug mode.
     */
    fun toggleDebugPro() {
        val newStatus = !_uiState.value.isPro
        billingRepository.setDebugPro(newStatus)
        _uiState.update {
            it.copy(userMessage = "Debug: Switched to ${if (newStatus) "PRO" else "FREE"} plan")
        }
    }

    /**
     * Updates the AI model selection (Local Nano, Cloud LLM, Logic Fallback).
     */
    fun setAiModelType(model: com.androidfung.departureboard.ai.AiModelType) {
        _uiState.update {
            it.copy(
                aiModelType = model,
                userMessage = "AI engine set to: ${model.title}"
            )
        }
    }

    /**
     * Opens the detail bottom sheet for a station (whether from card click or widget launch),
     * ensuring live departures are always fetched independently of saved dashboard cards.
     */
    fun openStationDetail(station: Station) {
        val existingCard = _uiState.value.stationCards.firstOrNull { it.station.id == station.id }
        val cachedDepartures = existingCard?.departures ?: emptyList()

        _uiState.update {
            it.copy(
                selectedDetailStation = station,
                detailDepartures = cachedDepartures,
                isDetailLoading = cachedDepartures.isEmpty()
            )
        }

        viewModelScope.launch {
            val result = repository.getDepartures(station.id, station.name)
            val freshDepartures = result.getOrDefault(cachedDepartures)

            _uiState.update { state ->
                // Update detail sheet
                val updatedState = if (state.selectedDetailStation?.id == station.id) {
                    state.copy(
                        detailDepartures = freshDepartures,
                        isDetailLoading = false
                    )
                } else {
                    state
                }

                // Also update card in dashboard if present
                val updatedCards = updatedState.stationCards.map { card ->
                    if (card.station.id == station.id) {
                        card.copy(
                            departures = freshDepartures,
                            isLoading = false,
                            errorMessage = null
                        )
                    } else card
                }

                updatedState.copy(stationCards = updatedCards)
            }
        }
    }

    fun closeStationDetail() {
        _uiState.update {
            it.copy(
                selectedDetailStation = null,
                detailDepartures = emptyList(),
                isDetailLoading = false
            )
        }
    }

    /**
     * Resets saved stations back to default popular London transit stations (for debug testing).
     */
    fun resetSavedStationsToDefaults() {
        viewModelScope.launch {
            repository.reorderStations(DefaultStations.POPULAR_STATIONS)
            _uiState.update { it.copy(userMessage = "Debug: Reset to default stations") }
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
     * Evaluates across saved stations and all popular London transit hubs to guarantee
     * the AI assistant and dashboard always have true local spatial context.
     */
    fun updateNearestStation(context: Context) {
        viewModelScope.launch {
            val location = com.androidfung.departureboard.util.LocationHelper.getCurrentLocation(context)
            if (location != null) {
                val savedStations = _uiState.value.stationCards.map { it.station }
                val allCandidateStations = (savedStations + DefaultStations.POPULAR_STATIONS).distinctBy { it.id }
                val nearest = com.androidfung.departureboard.util.LocationHelper.findNearestStation(location, allCandidateStations)

                if (nearest != null) {
                    val (nearestSt, distance) = nearest
                    android.util.Log.d("LocationHelper", "Found true nearest station: ${nearestSt.name} (${distance}m away)")

                    // Check if this nearest station is one of the saved dashboard cards
                    val isSavedOnDashboard = savedStations.any { it.id == nearestSt.id }

                    _uiState.update {
                        it.copy(
                            nearestStation = nearestSt,
                            nearestStationId = if (isSavedOnDashboard) nearestSt.id else null,
                            nearestStationDistanceMeters = distance
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
