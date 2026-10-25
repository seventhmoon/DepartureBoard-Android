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
import kotlin.time.Duration.Companion.milliseconds

/**
 * ViewModel for the Departure Board Dashboard.
 * Manages saved stations, live TfL departure predictions, 1-second countdown ticker,
 * 30-second background polling for live updates, and Pro subscription quotas.
 */
class DashboardViewModel @JvmOverloads constructor(
    application: Application,
    // Exposed (internal) so the AI assistant sheet can reuse the same repository instance
    // and its in-memory caches instead of constructing a second one.
    internal val repository: TransitRepository = (application as? com.androidfung.departureboard.DepartureBoardApp)?.repository ?: TransitRepositoryImpl(application),
    internal val billingRepository: BillingDataSource = BillingRepository.getInstance(application),
    internal val networkConnectivityObserver: com.androidfung.departureboard.util.NetworkConnectivityObserver =
        com.androidfung.departureboard.util.NetworkConnectivityObserverImpl(application),
    internal val locationDataSource: com.androidfung.departureboard.util.LocationDataSource =
        com.androidfung.departureboard.util.LocationDataSourceImpl(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var autoRefreshJob: Job? = null
    private var countdownTickerJob: Job? = null
    private var recentlyDeletedStation: Station? = null
    private var isCurrentlyOnline = true

    init {
        observeSavedStations()
        observeSubscriptionStatus()
        observeDepartureRowsCount()
        observeThemePreferences()
        observeRegionPreferences()
        observeAutoSortPreference()
        observeRecentSearchedStations()
        observeNetworkConnectivity()
        // startAutoRefreshPolling() and startCountdownTicker() are driven lifecycle-aware
        // from DashboardScreen (LifecycleResumeEffect) so the 1s ticker doesn't keep
        // updating StateFlow with the screen off.
    }

    private fun observeRegionPreferences() {
        viewModelScope.launch {
            repository.selectedRegionFlow.collectLatest { region ->
                _uiState.update { it.copy(selectedRegion = region) }
            }
        }
        viewModelScope.launch {
            repository.isHkRegionEnabledFlow.collectLatest { enabled ->
                _uiState.update { it.copy(isHkRegionEnabled = enabled) }
            }
        }
    }

    fun setSelectedRegion(region: com.androidfung.departureboard.data.model.TransitRegion) {
        viewModelScope.launch {
            repository.setSelectedRegion(region)
        }
    }

    fun setHkRegionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setHkRegionEnabled(enabled)
        }
    }

    private fun observeAutoSortPreference() {
        viewModelScope.launch {
            repository.autoSortByLocationFlow.collectLatest { autoSort ->
                _uiState.update { it.copy(isAutoSortByLocation = autoSort) }
                if (autoSort) {
                    updateNearestStation(fetchApiNearby = false)
                }
            }
        }
    }

    private fun observeRecentSearchedStations() {
        viewModelScope.launch {
            repository.recentSearchedStationsFlow.collectLatest { recents ->
                _uiState.update { it.copy(recentSearchedStations = recents) }
            }
        }
    }

    fun addRecentSearchedStation(station: Station) {
        viewModelScope.launch {
            repository.addRecentSearchedStation(station)
        }
    }

    fun removeRecentSearchedStation(stationId: String) {
        viewModelScope.launch {
            repository.removeRecentSearchedStation(stationId)
        }
    }

    fun clearRecentSearchedStations() {
        viewModelScope.launch {
            repository.clearRecentSearchedStations()
        }
    }

    fun setAutoSortByLocation(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoSortByLocation(enabled)
            if (enabled) {
                updateNearestStation(fetchApiNearby = false)
            }
        }
    }

    fun refreshLineStatuses() {
        viewModelScope.launch {
            val statuses = repository.getLineStatuses()
            _uiState.update { it.copy(lineStatuses = statuses) }
        }
    }

    private fun observeNetworkConnectivity() {
        viewModelScope.launch {
            networkConnectivityObserver.isOnline.collectLatest { online ->
                val wasOffline = !isCurrentlyOnline
                isCurrentlyOnline = online
                _uiState.update { it.copy(isOnline = online) }

                if (online) {
                    // Transitioned from offline to online: trigger refresh and restart auto-polling
                    if (wasOffline) {
                        val stations = _uiState.value.stationCards.map { it.station }
                        if (stations.isNotEmpty()) {
                            refreshDeparturesForStations(stations)
                        }
                    }
                } else {
                    // Paused when offline to preserve battery while in tube tunnels
                    stopAutoRefreshPolling()
                }
            }
        }
    }

    private fun observeThemePreferences() {
        viewModelScope.launch {
            repository.themeModeFlow.collectLatest { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
        viewModelScope.launch {
            repository.dynamicColorFlow.collectLatest { dynamicColor ->
                _uiState.update { it.copy(isDynamicColor = dynamicColor) }
            }
        }
    }

    fun setThemeMode(mode: com.androidfung.departureboard.ui.theme.AppThemeMode) {
        viewModelScope.launch {
            repository.setThemeMode(mode)
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            repository.setDynamicColor(enabled)
        }
    }

    private fun observeDepartureRowsCount() {
        viewModelScope.launch {
            repository.departureRowsCountFlow.collectLatest { count ->
                _uiState.update { it.copy(departureRowsCount = count) }
            }
        }
    }

    fun setDepartureRowsCount(count: Int) {
        viewModelScope.launch {
            repository.setDepartureRowsCount(count)
        }
    }

    private fun observeSubscriptionStatus() {
        viewModelScope.launch {
            billingRepository.userPlanFlow.collectLatest { plan ->
                _uiState.update { it.copy(isPro = plan.isPaidOrAbove, userPlan = plan) }
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
                    val existingCards = _uiState.value.stationCards
                    val existingMap = existingCards.associateBy { it.station.id }
                    
                    // Check if this is just a reorder operation or if we have new stations
                    val isJustReorder = existingCards.size == stations.size && 
                                        existingCards.map { it.station.id }.containsAll(stations.map { it.id })
                    
                    val newCards = stations.map { station ->
                        val existing = existingMap[station.id]
                        if (existing != null && existing.departures.isNotEmpty()) {
                            existing.copy(station = station)
                        } else {
                            val cachedDepartures = repository.getCachedDepartures(station.id)
                            StationCardUiModel(
                                station = station,
                                departures = cachedDepartures,
                                availableLineBadges = if (cachedDepartures.isNotEmpty()) {
                                    cachedDepartures.map { it.lineBadge }.distinctBy { it.displayName }
                                } else {
                                    inferLineBadges(station)
                                },
                                isLoading = cachedDepartures.isEmpty()
                            )
                        }
                    }
                    
                    _uiState.update {
                        it.copy(
                            stationCards = newCards,
                            isInitialLoading = false
                        )
                    }
                    
                    // Only fetch fresh arrivals if it's NOT just a simple reorder, or if we have new stations without departures
                    if (!isJustReorder || newCards.any { it.departures.isEmpty() }) {
                        refreshDeparturesForStations(stations)
                    }
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
            val lineStatusesDeferred = async { repository.getLineStatuses() }
            val departuresDeferred = async { repository.getDepartures(station.id, station.name) }
            val lineStatuses = lineStatusesDeferred.await()
            val result = departuresDeferred.await()

            _uiState.update { state ->
                val updatedCards = state.stationCards.map { card ->
                    if (card.station.id == station.id) {
                        if (result.isSuccess) {
                            val departures = result.getOrDefault(emptyList())
                            val badges = StationBadgeSynthesizer.buildStationLineBadges(card.station, departures, lineStatuses)
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
                state.copy(
                    stationCards = updatedCards,
                    lineStatuses = if (lineStatuses.isNotEmpty()) lineStatuses else state.lineStatuses
                )
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
                    val badgesWithStatus = StationBadgeSynthesizer.buildStationLineBadges(card.station, departures, lineStatuses)

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
                lineStatuses = if (lineStatuses.isNotEmpty()) lineStatuses else state.lineStatuses,
                isRefreshing = false,
                lastUpdatedTimestamp = System.currentTimeMillis()
            )
        }
    }

    /**
     * Auto-refreshes live departures every 30 seconds.
     */
    internal fun startAutoRefreshPolling() {
        if (!isCurrentlyOnline) return
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            // Adaptive polling backed off from 30s to save battery and network:
            // Fetch immediately on screen entry, then back off to a more reasonable 60s
            // for dashboard-level macro polling.
            while (isActive) {
                if (isCurrentlyOnline) {
                    val stations = _uiState.value.stationCards.map { it.station }
                    if (stations.isNotEmpty()) {
                        refreshDeparturesForStations(stations)
                    }
                }
                delay(60_000L.milliseconds)
            }
        }
    }

    internal fun stopAutoRefreshPolling() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    /**
     * 1-second countdown ticker decrements timeToStationSeconds locally so that live counters tick in real time.
     * Note: Subtracting 1 with coerceAtLeast(0) is order-preserving, so re-sorting every list on each 1s tick is avoided.
     */
    internal fun startCountdownTicker() {
        countdownTickerJob?.cancel()
        countdownTickerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L.milliseconds)
                _uiState.update { state ->
                    val hasAnyActiveDepartures = state.stationCards.any { card -> card.departures.any { it.timeToStationSeconds > 0 } } ||
                        state.detailDepartures.any { it.timeToStationSeconds > 0 } ||
                        (state.selectedJourneyDeparture?.timeToStationSeconds ?: 0) > 0
                    if (!hasAnyActiveDepartures) return@update state

                    val tickedCards = CountdownTicker.tick(state.stationCards)
                    val tickedDetailDepartures = CountdownTicker.tickDepartures(state.detailDepartures)
                    val tickedJourneyDeparture = CountdownTicker.tickDeparture(state.selectedJourneyDeparture)

                    if (tickedCards === state.stationCards &&
                        tickedDetailDepartures === state.detailDepartures &&
                        tickedJourneyDeparture === state.selectedJourneyDeparture
                    ) {
                        return@update state
                    }

                    state.copy(
                        stationCards = tickedCards,
                        detailDepartures = tickedDetailDepartures,
                        selectedJourneyDeparture = tickedJourneyDeparture
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

        // Already contains this station
        if (_uiState.value.stationCards.any { it.station.id == station.id }) {
            _uiState.update { it.copy(userMessage = "${station.name} is already saved") }
            return
        }

        val currentPlan = _uiState.value.userPlan
        if (!currentPlan.isPaidOrAbove && currentCount >= currentPlan.maxStations) {
            _uiState.update {
                it.copy(
                    paywallPromptReason = "Free plan includes up to ${currentPlan.maxStations} saved stations. Upgrade to Pro for up to ${SubscriptionTier.PRO_MAX_STATIONS} stations."
                )
            }
            return
        }

        if (currentPlan.isPaidOrAbove && currentCount >= currentPlan.maxStations) {
            _uiState.update {
                it.copy(
                    userMessage = "Maximum limit of ${currentPlan.maxStations} stations reached to protect live API limits."
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
        _uiState.update { it.copy(paywallPromptReason = reason ?: "Unlock EkiBoard Pro") }
    }

    fun dismissPaywall() {
        _uiState.update { it.copy(paywallPromptReason = null) }
    }

    /**
     * Cycles between FREE -> PRO -> MAX plans in debug mode.
     */
    fun cycleDebugPlan() {
        val current = _uiState.value.userPlan
        val next = when (current) {
            com.androidfung.departureboard.billing.UserPlan.FREE -> com.androidfung.departureboard.billing.UserPlan.PRO
            com.androidfung.departureboard.billing.UserPlan.PRO -> com.androidfung.departureboard.billing.UserPlan.MAX
            com.androidfung.departureboard.billing.UserPlan.MAX -> com.androidfung.departureboard.billing.UserPlan.FREE
        }
        billingRepository.setDebugPlan(next)
        _uiState.update {
            it.copy(userMessage = "Debug: Switched to ${next.name} plan")
        }
    }

    /**
     * Toggles Free and Pro subscription states in debug mode.
     */
    fun toggleDebugPro() {
        cycleDebugPlan()
    }

    fun setDebugPlan(plan: com.androidfung.departureboard.billing.UserPlan) {
        billingRepository.setDebugPlan(plan)
        _uiState.update {
            it.copy(userMessage = "Debug: Switched to ${plan.name} plan")
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
     *
     * @param stationsContext The list of stations to allow swiping between (defaults to saved stations).
     */
    fun openStationDetail(station: Station, stationsContext: List<Station>? = null) {
        val existingCard = _uiState.value.stationCards.firstOrNull { it.station.id == station.id }
        val cachedDepartures = existingCard?.departures ?: emptyList()

        val resolvedStationsContext = stationsContext
            ?: _uiState.value.stationCards.map { it.station }.takeIf { it.isNotEmpty() }
            ?: listOf(station)

        _uiState.update {
            it.copy(
                selectedDetailStation = station,
                detailStationsContext = resolvedStationsContext,
                detailDepartures = cachedDepartures,
                isDetailLoading = cachedDepartures.isEmpty(),
                selectedJourneyDeparture = null,
                journeyDeparturesContext = emptyList()
            )
        }

        viewModelScope.launch {
            val lineStatusesDeferred = async { repository.getLineStatuses() }
            val departuresDeferred = async { repository.getDepartures(station.id, station.name) }

            val lineStatuses = lineStatusesDeferred.await()
            val result = departuresDeferred.await()
            val rawDepartures = result.getOrDefault(cachedDepartures)

            // Enrich departures with live line statuses so badges carry disruption info
            val freshDepartures = if (lineStatuses.isNotEmpty()) {
                rawDepartures.map { dep ->
                    val statusItem = lineStatuses[dep.lineBadge.lineId.lowercase()]
                    val firstDetail = statusItem?.lineStatuses?.firstOrNull()
                    if (firstDetail != null) {
                        dep.copy(
                            lineBadge = dep.lineBadge.copy(
                                statusSeverity = firstDetail.statusSeverity,
                                statusDescription = firstDetail.statusSeverityDescription,
                                disruptionReason = firstDetail.reason
                            )
                        )
                    } else {
                        dep
                    }
                }
            } else {
                rawDepartures
            }

            // If no departures returned, check whether the station is closed for the night
            val serviceHours = if (freshDepartures.isEmpty()) {
                repository.checkStationServiceHours(station.id)
            } else {
                null
            }

            _uiState.update { state ->
                // Update detail sheet
                val updatedState = if (state.selectedDetailStation?.id == station.id) {
                    state.copy(
                        detailDepartures = freshDepartures,
                        isDetailLoading = false,
                        serviceHoursInfo = serviceHours
                    )
                } else {
                    state
                }

                // Also update card in dashboard if present
                val updatedCards = updatedState.stationCards.map { card ->
                    if (card.station.id == station.id) {
                        val badgesWithStatus = StationBadgeSynthesizer.buildStationLineBadges(card.station, freshDepartures, lineStatuses)
                        card.copy(
                            departures = freshDepartures,
                            availableLineBadges = badgesWithStatus,
                            isLoading = false,
                            errorMessage = null
                        )
                    } else card
                }

                updatedState.copy(
                    stationCards = updatedCards,
                    lineStatuses = if (lineStatuses.isNotEmpty()) lineStatuses else updatedState.lineStatuses
                )
            }
        }
    }

    fun closeStationDetail() {
        _uiState.update {
            it.copy(
                selectedDetailStation = null,
                detailStationsContext = emptyList(),
                detailDepartures = emptyList(),
                isDetailLoading = false,
                selectedJourneyDeparture = null,
                journeyDeparturesContext = emptyList(),
                serviceHoursInfo = null
            )
        }
    }

    fun openTrainJourney(departure: Departure, departuresContext: List<Departure>? = null) {
        val resolvedDepartures = departuresContext
            ?: _uiState.value.detailDepartures.takeIf { it.isNotEmpty() }
            ?: _uiState.value.stationCards.firstOrNull { it.station.id == departure.stationId }?.departures
            ?: listOf(departure)

        _uiState.update {
            it.copy(
                selectedJourneyDeparture = departure,
                journeyDeparturesContext = resolvedDepartures
            )
        }
    }

    fun closeTrainJourney() {
        _uiState.update {
            it.copy(
                selectedJourneyDeparture = null,
                journeyDeparturesContext = emptyList()
            )
        }
    }

    fun openStationDetailFromCallingPoint(callingPoint: com.androidfung.departureboard.data.model.CallingPoint, callingPoints: List<com.androidfung.departureboard.data.model.CallingPoint>) {
        val targetStationId = callingPoint.stationId ?: callingPoint.stationName
        viewModelScope.launch {
            val station = repository.getStationById(targetStationId) ?: Station(
                id = targetStationId,
                name = callingPoint.stationName,
                modes = callingPoint.modes
            )

            // Convert all calling points into Station instances for the context-aware swipe pager
            val callingStations = callingPoints.map { cp ->
                val id = cp.stationId ?: cp.stationName
                Station(
                    id = id,
                    name = cp.stationName,
                    modes = cp.modes
                )
            }

            openStationDetail(station, callingStations)
        }
    }

    fun resolveDepartureForJourney(departureId: String): Departure? {
        val state = _uiState.value
        return state.selectedJourneyDeparture?.takeIf { it.id == departureId }
            ?: state.detailDepartures.firstOrNull { it.id == departureId }
            ?: state.stationCards.asSequence().flatMap { it.departures }.firstOrNull { it.id == departureId }
            ?: state.selectedJourneyDeparture
    }

    /**
     * Resets saved stations back to default popular transit stations for active region (for debug testing).
     */
    fun resetSavedStationsToDefaults() {
        viewModelScope.launch {
            val defaults = when (_uiState.value.selectedRegion) {
                com.androidfung.departureboard.data.model.TransitRegion.HK -> com.androidfung.departureboard.data.model.HkDefaultStations.POPULAR_STATIONS
                com.androidfung.departureboard.data.model.TransitRegion.GB -> DefaultStations.POPULAR_STATIONS
            }
            repository.reorderStations(defaults)
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
     * Evaluates across saved stations and all popular London transit hubs.
     *
     * @param fetchApiNearby If true, queries the TfL StopPoint radius API (used when opening Search).
     *                        If false, computes distance quickly against local candidates without extra HTTP traffic.
     */
    @Suppress("UNUSED_PARAMETER")
    fun updateNearestStation(context: Context, fetchApiNearby: Boolean = false) {
        updateNearestStation(fetchApiNearby)
    }

    fun updateNearestStation(fetchApiNearby: Boolean = false) {
        viewModelScope.launch {
            val location = locationDataSource.getCurrentLocation()
            if (location != null) {
                val apiNearbyStations = if (fetchApiNearby) {
                    repository.getNearbyStationsFromApi(
                        lat = location.latitude,
                        lon = location.longitude,
                        radiusMeters = 2000
                    )
                } else emptyList()

                val savedStations = _uiState.value.stationCards.map { it.station }
                val regionDefaults = when (_uiState.value.selectedRegion) {
                    com.androidfung.departureboard.data.model.TransitRegion.HK -> com.androidfung.departureboard.data.model.HkDefaultStations.POPULAR_STATIONS + com.androidfung.departureboard.data.model.HkMtrStationRegistry.STATIONS.values.map { com.androidfung.departureboard.data.model.HkStationDirectory.mtrToStation(it) }
                    com.androidfung.departureboard.data.model.TransitRegion.GB -> DefaultStations.POPULAR_STATIONS
                }
                val allCandidateStations: List<Station> = (apiNearbyStations + savedStations + regionDefaults)
                    .distinctBy { it.id }

                val nearest = com.androidfung.departureboard.util.LocationHelper.findNearestStation(location, allCandidateStations)
                val nearbyList = com.androidfung.departureboard.util.LocationHelper.findNearbyStations(
                    userLocation = location,
                    stations = allCandidateStations,
                    maxDistanceMeters = 5000.0,
                    maxCount = 10
                )

                if (nearest != null) {
                    val (nearestSt, distance) = nearest
                    try {
                        android.util.Log.d("LocationHelper", "Found true nearest station: ${nearestSt.name} (${distance}m away)")
                    } catch (_: Throwable) {}

                    // Check if this nearest station is one of the saved dashboard cards
                    val isSavedOnDashboard = savedStations.any { it.id == nearestSt.id }

                    _uiState.update { state ->
                        val sortedCards = if (state.isAutoSortByLocation && state.stationCards.size > 1) {
                            state.stationCards.sortedBy { card ->
                                val stLat = card.station.lat
                                val stLon = card.station.lon
                                if (stLat != null && stLon != null) {
                                    com.androidfung.departureboard.util.LocationHelper.calculateDistanceMeters(
                                        location.latitude,
                                        location.longitude,
                                        stLat,
                                        stLon
                                    )
                                } else {
                                    Double.MAX_VALUE
                                }
                            }
                        } else {
                            state.stationCards
                        }
                        state.copy(
                            stationCards = sortedCards,
                            nearestStation = nearestSt,
                            nearestStationId = if (isSavedOnDashboard) nearestSt.id else null,
                            nearestStationDistanceMeters = distance,
                            nearbyStations = if (fetchApiNearby || state.nearbyStations.isEmpty()) nearbyList else state.nearbyStations
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(nearbyStations = if (fetchApiNearby || it.nearbyStations.isEmpty()) nearbyList else it.nearbyStations)
                    }
                }
            } else {
                try {
                    android.util.Log.w("LocationHelper", "Location was null")
                } catch (_: Throwable) {}
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
