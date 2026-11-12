package com.androidfung.departureboard.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.androidfung.departureboard.R
import com.androidfung.departureboard.billing.BillingDataSource
import com.androidfung.departureboard.billing.BillingRepository
import com.androidfung.departureboard.billing.SubscriptionTier
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TransitRegion
import com.androidfung.departureboard.data.repository.TransitRepository
import com.androidfung.departureboard.data.repository.TransitRepositoryImpl
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    internal val repository: TransitRepository = (application as? com.androidfung.departureboard.DepartureBoardApp)?.container?.transitRepository
        ?: (application as? com.androidfung.departureboard.DepartureBoardApp)?.repository ?: TransitRepositoryImpl(application),
    internal val billingRepository: BillingDataSource = (application as? com.androidfung.departureboard.DepartureBoardApp)?.container?.billingRepository
        ?: BillingRepository.getInstance(application),
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
        observeWidgetDisruptionNotificationsPreference()
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
        viewModelScope.launch {
            repository.isBilingualEnabledFlow.collectLatest { enabled ->
                _uiState.update { it.copy(isBilingualEnabled = enabled) }
            }
        }
        observeConcourseHardwareMotion()
    }

    private fun observeConcourseHardwareMotion() {
        com.androidfung.departureboard.service.ConcourseMotionTracker.ensureRegistered(getApplication())
        viewModelScope.launch {
            com.androidfung.departureboard.service.ConcourseMotionTracker.motionUpdatesFlow.collectLatest { activityType ->
                val wasIntervalMs = _uiState.value.concoursePollIntervalMs
                refreshNearestStationInternal(fetchApiNearby = false)
                val newIntervalMs = _uiState.value.concoursePollIntervalMs
                if (newIntervalMs < wasIntervalMs && isCurrentlyOnline &&
                    (activityType == com.androidfung.departureboard.util.ConcourseActivityGate.ACTIVITY_WALKING ||
                        activityType == com.androidfung.departureboard.util.ConcourseActivityGate.ACTIVITY_RUNNING ||
                        activityType == com.androidfung.departureboard.util.ConcourseActivityGate.ACTIVITY_ON_FOOT)
                ) {
                    startAutoRefreshPolling()
                }
            }
        }
    }

    fun setSelectedRegion(region: com.androidfung.departureboard.data.model.TransitRegion) {
        viewModelScope.launch {
            repository.setSelectedRegion(region)
            refreshDepartures()
            updateNearestStation(fetchApiNearby = true)
        }
    }

    fun setHkRegionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setHkRegionEnabled(enabled)
            if (!enabled) {
                repository.setSelectedRegion(TransitRegion.GB)
                _uiState.update { it.copy(selectedRegion = TransitRegion.GB) }
                refreshDepartures()
                updateNearestStation(fetchApiNearby = true)
            }
        }
    }

    fun setBilingualEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setBilingualEnabled(enabled)
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

    private fun observeWidgetDisruptionNotificationsPreference() {
        viewModelScope.launch {
            repository.widgetDisruptionNotificationsEnabledFlow.collectLatest { enabled ->
                _uiState.update { it.copy(isWidgetDisruptionNotificationsEnabled = enabled) }
            }
        }
    }

    fun setWidgetDisruptionNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setWidgetDisruptionNotificationsEnabled(enabled)
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
                    // Paused when offline to preserve battery while in tube tunnels,
                    // while populating any empty station cards from Underground Tunnel Mode GTFS schedules
                    stopAutoRefreshPolling()
                    val emptyStations = _uiState.value.stationCards
                        .filter { it.departures.isEmpty() }
                        .map { it.station }
                    if (emptyStations.isNotEmpty()) {
                        refreshDeparturesForStations(emptyStations)
                    }
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
                            val (enrichedCached, serviceHours) = com.androidfung.departureboard.util.LastTrainServiceCalculator
                                .enrichDeparturesAndServiceHours(station, cachedDepartures)
                            StationCardUiModel(
                                station = station,
                                departures = enrichedCached,
                                availableLineBadges = if (enrichedCached.isNotEmpty()) {
                                    enrichedCached.map { it.lineBadge }.distinctBy { it.displayName }
                                } else {
                                    inferLineBadges(station)
                                },
                                isLoading = enrichedCached.isEmpty(),
                                distanceMeters = existing?.distanceMeters,
                                serviceHoursInfo = serviceHours
                            )
                        }
                    }
                    
                    _uiState.update { state ->
                        val updatedDetailContext = if (state.selectedDetailStation != null) {
                            val savedList = newCards.map { it.station }
                            val currentContext = state.detailStationsContext
                            // Preserve any unsaved station (e.g. opened from a widget or search) at the front
                            // of the detail pager context while syncing the rest with saved stations.
                            val unsavedInContext = currentContext.filter { ctxSt ->
                                savedList.none { it.id == ctxSt.id }
                            }
                            val base = if (unsavedInContext.isNotEmpty()) {
                                unsavedInContext + savedList
                            } else if (savedList.none { it.id == state.selectedDetailStation.id }) {
                                listOf(state.selectedDetailStation) + savedList
                            } else {
                                savedList
                            }
                            base.distinctBy { it.id }
                        } else {
                            state.detailStationsContext
                        }
                        state.copy(
                            stationCards = newCards,
                            detailStationsContext = updatedDetailContext,
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
            val verifiedPair = if (result.isSuccess) {
                resolveVerifiedServiceHours(station, result.getOrDefault(emptyList()))
            } else null

            _uiState.update { state ->
                val updatedCards = state.stationCards.map { card ->
                    if (card.station.id == station.id) {
                        if (result.isSuccess && verifiedPair != null) {
                            val (departures, serviceHours) = verifiedPair
                            val badges = StationBadgeSynthesizer.buildStationLineBadges(card.station, departures, lineStatuses)
                            card.copy(
                                departures = departures,
                                availableLineBadges = badges,
                                isLoading = false,
                                errorMessage = null,
                                serviceHoursInfo = serviceHours
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

    private suspend fun resolveVerifiedServiceHours(
        station: Station,
        departures: List<Departure>
    ): Pair<List<Departure>, com.androidfung.departureboard.data.model.StationServiceHoursInfo?> {
        val region = com.androidfung.departureboard.data.model.TransitRegion.fromStationId(station.id)
        val nowHour = java.time.ZonedDateTime.now(region.timeZone).hour
        val isLateNightOrEarlyMorning = nowHour >= 22 || nowHour < 6
        val candidateLineId = departures.firstOrNull { !it.isBus }?.lineId
            ?: station.lines.firstOrNull()?.id
            ?: departures.firstOrNull()?.lineId

        return if (departures.isEmpty() || isLateNightOrEarlyMorning) {
            val apiServiceHours = try {
                repository.checkStationServiceHours(station.id, candidateLineId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            com.androidfung.departureboard.util.LastTrainServiceCalculator.enrichDeparturesAndServiceHours(
                station = station,
                departures = departures,
                existingServiceHours = apiServiceHours,
                timetableChecked = true
            )
        } else {
            com.androidfung.departureboard.util.LastTrainServiceCalculator.enrichDeparturesAndServiceHours(
                station = station,
                departures = departures
            )
        }
    }

    private suspend fun refreshDeparturesForStations(stations: List<Station>) {
        val (lineStatuses, results) = coroutineScope {
            val lineStatusesDeferred = async { repository.getLineStatuses() }
            val batchDeparturesDeferred = async { repository.getBatchDepartures(stations) }
            lineStatusesDeferred.await() to batchDeparturesDeferred.await()
        }
        val stationsById = stations.associateBy { it.id }
        val verifiedByStationId = results.mapNotNull { (stationId, res) ->
            val st = stationsById[stationId] ?: _uiState.value.stationCards.firstOrNull { it.station.id == stationId }?.station
            if (res.isSuccess && st != null) {
                stationId to resolveVerifiedServiceHours(st, res.getOrDefault(emptyList()))
            } else null
        }.toMap()

        _uiState.update { state ->
            val updatedCards = state.stationCards.map { card ->
                val res = results[card.station.id]
                val verified = verifiedByStationId[card.station.id]
                if (res != null && res.isSuccess && verified != null) {
                    val (departures, serviceHours) = verified
                    val badgesWithStatus = StationBadgeSynthesizer.buildStationLineBadges(card.station, departures, lineStatuses)

                    card.copy(
                        departures = departures,
                        availableLineBadges = badgesWithStatus,
                        isLoading = false,
                        errorMessage = null,
                        serviceHoursInfo = serviceHours
                    )
                } else {
                    card.copy(
                        isLoading = false,
                        errorMessage = if (card.departures.isEmpty()) resolveString(R.string.departures_load_error) else null
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
        com.androidfung.departureboard.wear.WearCompanionSyncPublisher.publishStationBoards(
            getApplication(),
            _uiState.value.stationCards
        )
    }

    /**
     * Auto-refreshes live departures:
     * - 15s Concourse Sprint cadence when within 350m of a station AND actively approaching on foot.
     * - 60s normal cadence when outside the concourse geofence, passing through in a vehicle, or stationary dwell (> 3m).
     */
    internal fun startAutoRefreshPolling() {
        if (!isCurrentlyOnline) return
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                val pollIntervalMs = _uiState.value.concoursePollIntervalMs.coerceIn(15_000L, 60_000L)
                delay(pollIntervalMs.milliseconds)
                if (isCurrentlyOnline) {
                    val stations = _uiState.value.stationCards.map { it.station }
                    if (stations.isNotEmpty()) {
                        refreshDeparturesForStations(stations)
                    }
                    // If the user is viewing an unsaved station in StationDetailScreen (e.g. launched from widget),
                    // keep its departures refreshed during auto-polling as well.
                    val detailSt = _uiState.value.selectedDetailStation
                    if (detailSt != null && stations.none { it.id == detailSt.id }) {
                        openStationDetail(
                            station = detailSt,
                            stationsContext = _uiState.value.detailStationsContext,
                            preserveActiveJourney = true
                        )
                    }
                    if (_uiState.value.isAutoSortByLocation || _uiState.value.isConcourseSprintActive) {
                        refreshNearestStationInternal(fetchApiNearby = false)
                    }
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
            _uiState.update { it.copy(userMessage = resolveString(R.string.station_already_saved, station.displayName)) }
            return
        }

        val currentPlan = _uiState.value.userPlan
        if (!currentPlan.isPaidOrAbove && currentCount >= currentPlan.maxStations) {
            _uiState.update {
                it.copy(
                    paywallPromptReason = resolveString(
                        R.string.paywall_prompt_station_limit,
                        currentPlan.maxStations,
                        SubscriptionTier.PRO_MAX_STATIONS
                    )
                )
            }
            return
        }

        if (currentPlan.isPaidOrAbove && currentCount >= currentPlan.maxStations) {
            _uiState.update {
                it.copy(
                    userMessage = resolveString(
                        R.string.station_max_limit_reached,
                        currentPlan.maxStations
                    )
                )
            }
            return
        }

        viewModelScope.launch {
            repository.saveStation(station)
            _uiState.update {
                it.copy(userMessage = resolveString(R.string.station_added, station.displayName))
            }
        }
    }

    fun showPaywall(reason: String? = null) {
        _uiState.update { it.copy(paywallPromptReason = reason ?: resolveString(R.string.paywall_prompt_default)) }
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
     * Opens the detail bottom sheet for a station (whether from card click, widget launch, or notification tap),
     * ensuring live departures are always fetched independently of saved dashboard cards.
     *
     * @param stationsContext The list of stations to allow swiping between (defaults to saved stations).
     * @param targetDepartureId Optional departure ID to automatically open in [TrainJourneyScreen] once resolved.
     */
    @JvmOverloads
    fun openStationDetail(
        station: Station,
        stationsContext: List<Station>? = null,
        targetDepartureId: String? = null,
        preserveActiveJourney: Boolean = false
    ) {
        val existingCard = _uiState.value.stationCards.firstOrNull { it.station.id == station.id }
        val cachedDepartures = existingCard?.departures ?: emptyList()

        val savedStations = _uiState.value.stationCards.map { it.station }
        val baseContext = stationsContext
            ?.takeIf { it.isNotEmpty() }
            ?: savedStations.takeIf { it.isNotEmpty() }
            ?: listOf(station)
        // Always include the target station in detailStationsContext (even if it is an unsaved widget/search station)
        // so swiping to a saved station in HorizontalPager never drops the unsaved station from the pager list.
        val resolvedStationsContext = if (baseContext.none { it.id == station.id }) {
            (listOf(station) + baseContext).distinctBy { it.id }
        } else {
            baseContext.distinctBy { it.id }
        }

        val initialMatchedJourney = targetDepartureId?.takeIf { it.isNotBlank() }?.let { depId ->
            cachedDepartures.firstOrNull { it.id == depId }
        }

        _uiState.update { current ->
            val keptJourney = initialMatchedJourney
                ?: current.selectedJourneyDeparture.takeIf { preserveActiveJourney }
            val keptJourneyContext = when {
                initialMatchedJourney != null -> cachedDepartures
                preserveActiveJourney && keptJourney != null -> current.journeyDeparturesContext
                else -> emptyList()
            }
            current.copy(
                selectedDetailStation = station,
                detailStationsContext = resolvedStationsContext,
                detailDepartures = cachedDepartures,
                detailAccessibilityAlerts = if (current.selectedDetailStation?.id == station.id) {
                    current.detailAccessibilityAlerts
                } else {
                    emptyList()
                },
                isDetailLoading = cachedDepartures.isEmpty(),
                selectedJourneyDeparture = keptJourney,
                journeyDeparturesContext = keptJourneyContext
            )
        }

        viewModelScope.launch {
            val lineStatusesDeferred = async { repository.getLineStatuses() }
            val departuresDeferred = async { repository.getDepartures(station.id, station.name) }
            val enrichedStationDeferred = async { runCatching { repository.getStationById(station.id) }.getOrNull() }
            val a11yAlertsDeferred = async { runCatching { repository.getStationAccessibilityAlerts(station.id) }.getOrDefault(emptyList()) }

            val lineStatuses = lineStatusesDeferred.await()
            val result = departuresDeferred.await()
            val enrichedStation = enrichedStationDeferred.await()
            val a11yAlerts = a11yAlertsDeferred.await()
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

            // Check whether the station is closed for the night, approaching last train, or verified Night Tube
            val (enrichedDepartures, serviceHours) = resolveVerifiedServiceHours(station, freshDepartures)

            _uiState.update { state ->
                // Update detail sheet
                val updatedState = if (state.selectedDetailStation?.id == station.id) {
                    val resolvedTargetJourney = targetDepartureId?.takeIf { it.isNotBlank() }?.let { depId ->
                        enrichedDepartures.firstOrNull { it.id == depId }
                    } ?: state.selectedJourneyDeparture
                    val derivedLines = enrichedStation?.lines?.takeIf { it.isNotEmpty() }
                        ?: station.lines.takeIf { it.isNotEmpty() }
                        ?: enrichedDepartures
                            .filter { !it.isBus }
                            .map { dep ->
                                com.androidfung.departureboard.data.model.StationLineInfo(
                                    id = dep.lineBadge.lineId,
                                    name = dep.lineBadge.displayName,
                                    mode = dep.modeName
                                )
                            }
                            .distinctBy { it.id }
                    val mergedStation = if (enrichedStation != null || derivedLines.isNotEmpty()) {
                        station.copy(
                            name = enrichedStation?.name?.takeIf { it.isNotBlank() } ?: station.name,
                            modes = enrichedStation?.modes?.takeIf { it.isNotEmpty() } ?: station.modes,
                            zone = station.zone ?: enrichedStation?.zone,
                            lines = derivedLines.ifEmpty { station.lines },
                            micromobilityHubs = enrichedStation?.micromobilityHubs?.ifEmpty { station.micromobilityHubs } ?: station.micromobilityHubs,
                            lat = station.lat ?: enrichedStation?.lat,
                            lon = station.lon ?: enrichedStation?.lon,
                            wheelchairStatus = if (station.wheelchairStatus != com.androidfung.departureboard.data.model.WheelchairBoardingStatus.UNKNOWN) {
                                station.wheelchairStatus
                            } else {
                                enrichedStation?.wheelchairStatus ?: station.wheelchairStatus
                            }
                        )
                    } else {
                        state.selectedDetailStation ?: station
                    }
                    val updatedDetailContext = state.detailStationsContext.map { ctxSt ->
                        if (ctxSt.id == mergedStation.id) mergedStation else ctxSt
                    }
                    state.copy(
                        selectedDetailStation = mergedStation,
                        detailStationsContext = updatedDetailContext,
                        detailDepartures = enrichedDepartures,
                        detailAccessibilityAlerts = a11yAlerts,
                        isDetailLoading = false,
                        serviceHoursInfo = serviceHours,
                        selectedJourneyDeparture = resolvedTargetJourney,
                        journeyDeparturesContext = if (resolvedTargetJourney != null) enrichedDepartures else state.journeyDeparturesContext
                    )
                } else {
                    state
                }

                // Also update card in dashboard if present
                val updatedCards = updatedState.stationCards.map { card ->
                    if (card.station.id == station.id) {
                        val badgesWithStatus = StationBadgeSynthesizer.buildStationLineBadges(card.station, enrichedDepartures, lineStatuses)
                        card.copy(
                            departures = enrichedDepartures,
                            availableLineBadges = badgesWithStatus,
                            isLoading = false,
                            errorMessage = null,
                            serviceHoursInfo = serviceHours
                        )
                    } else card
                }

                updatedState.copy(
                    stationCards = updatedCards,
                    lineStatuses = if (lineStatuses.isNotEmpty()) lineStatuses else updatedState.lineStatuses
                )
            }

            // If this station is in Recent Searches with missing line metadata, persist the hydrated lines
            val hydratedDetail = _uiState.value.selectedDetailStation
            if (hydratedDetail != null && hydratedDetail.id == station.id && hydratedDetail.lines.isNotEmpty()) {
                val existingRecent = _uiState.value.recentSearchedStations.firstOrNull { it.id == station.id }
                if (existingRecent != null && existingRecent.lines.isEmpty()) {
                    repository.addRecentSearchedStation(hydratedDetail)
                }
            }
        }
    }

    fun openDeskClock(station: Station? = null) {
        val targetStation = station
            ?: _uiState.value.selectedDetailStation
            ?: _uiState.value.nearestStation?.takeIf { nearest -> _uiState.value.stationCards.any { it.station.id == nearest.id } }
            ?: _uiState.value.stationCards.firstOrNull()?.station
            ?: _uiState.value.selectedRegion.defaultStations.firstOrNull()
        if (targetStation != null) {
            _uiState.update { it.copy(deskClockStation = targetStation) }
        }
    }

    fun closeDeskClock() {
        _uiState.update { it.copy(deskClockStation = null) }
    }

    fun closeStationDetail() {
        _uiState.update {
            it.copy(
                selectedDetailStation = null,
                detailStationsContext = emptyList(),
                detailDepartures = emptyList(),
                isDetailLoading = false,
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
        val fallbackStation = Station(
            id = targetStationId,
            name = callingPoint.stationName,
            modes = callingPoint.modes
        )
        val fallbackCallingStations = callingPoints.map { cp ->
            val id = cp.stationId ?: cp.stationName
            Station(
                id = id,
                name = cp.stationName,
                modes = cp.modes
            )
        }

        // Keep the current train journey open so on 3-pane it becomes the middle pane,
        // and on 1-pane/2-pane it remains in the backstack beneath the tapped calling point station.
        val currentJourney = _uiState.value.selectedJourneyDeparture
        val currentJourneyContext = _uiState.value.journeyDeparturesContext

        openStationDetail(fallbackStation, fallbackCallingStations)

        if (currentJourney != null) {
            _uiState.update { current ->
                current.copy(
                    selectedJourneyDeparture = currentJourney,
                    journeyDeparturesContext = currentJourneyContext
                )
            }
        }

        // Enrich the station models asynchronously in the background if repository has more metadata
        viewModelScope.launch {
            val enrichedStation = repository.getStationById(targetStationId) ?: fallbackStation
            val enrichedCallingStations = callingPoints.map { cp ->
                val id = cp.stationId ?: cp.stationName
                repository.getStationById(id) ?: Station(
                    id = id,
                    name = cp.stationName,
                    modes = cp.modes
                )
            }

            if (enrichedStation != fallbackStation || enrichedCallingStations != fallbackCallingStations) {
                _uiState.update { current ->
                    if (current.selectedDetailStation?.id == targetStationId) {
                        current.copy(
                            selectedDetailStation = enrichedStation,
                            detailStationsContext = enrichedCallingStations
                        )
                    } else {
                        current
                    }
                }
            }
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
            val defaults = _uiState.value.selectedRegion.defaultStations
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
                it.copy(userMessage = resolveString(R.string.station_removed, station.displayName))
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
                it.copy(userMessage = resolveString(R.string.station_restored, stationToRestore.displayName))
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
    fun updateNearestStation(fetchApiNearby: Boolean = false) = viewModelScope.launch {
        refreshNearestStationInternal(fetchApiNearby)
    }

    suspend fun refreshNearestStationInternal(fetchApiNearby: Boolean = false): Station? {
            val location = locationDataSource.getCurrentLocation()
            val currentRegion = _uiState.value.selectedRegion
            if (location != null) {
                // Polymorphically query nearby stations from the active regional transit provider
                val apiNearbyStations = if (fetchApiNearby) {
                    repository.getNearbyStationsFromApi(
                        lat = location.latitude,
                        lon = location.longitude,
                        radiusMeters = 2000
                    )
                } else emptyList()

                val savedStations = _uiState.value.stationCards.map { it.station }
                val regionDefaults = when (currentRegion) {
                    com.androidfung.departureboard.data.model.TransitRegion.HK -> currentRegion.defaultStations + com.androidfung.departureboard.data.model.hk.HkMtrStationRegistry.STATIONS.values.map { com.androidfung.departureboard.data.model.hk.HkStationDirectory.mtrToStation(it) }
                    else -> currentRegion.defaultStations
                }
                val allCandidateStations: List<Station> = (apiNearbyStations + savedStations + regionDefaults)
                    .distinctBy { it.id }
                    .filter { com.androidfung.departureboard.data.model.TransitRegion.fromStationId(it.id) == currentRegion }

                // Check whether user's location is in the selected region or within reasonable proximity (< 100km)
                val isLocationInRegion = com.androidfung.departureboard.data.model.TransitRegion.fromCoordinates(location.latitude, location.longitude) == currentRegion

                val nearest = if (isLocationInRegion) {
                    com.androidfung.departureboard.util.LocationHelper.findNearestStation(location, allCandidateStations)
                        ?.takeIf { it.second <= 100_000.0 }
                } else null

                val nearbyList = if (isLocationInRegion) {
                    com.androidfung.departureboard.util.LocationHelper.findNearbyStations(
                        userLocation = location,
                        stations = allCandidateStations,
                        maxDistanceMeters = 5000.0,
                        maxCount = 10
                    )
                } else emptyList()

                if (nearest != null) {
                    val (nearestSt, distance) = nearest
                    val speedMps = if (location.hasSpeed()) location.speed else null
                    val concourseDecision = com.androidfung.departureboard.util.ConcourseActivityGate.evaluateConcourseProximity(
                        station = nearestSt,
                        distanceMeters = distance,
                        speedMetersPerSecond = speedMps
                    )
                    try {
                        android.util.Log.d(
                            "LocationHelper",
                            "Found nearest station: ${nearestSt.name} (${distance.toInt()}m, concourseActive=${concourseDecision.isConcourseSprintActive}, mobility=${concourseDecision.mobilityState})"
                        )
                    } catch (_: Throwable) {}

                    _uiState.update { state ->
                        val updatedCardsWithDistance = state.stationCards.map { card ->
                            val stLat = card.station.lat
                            val stLon = card.station.lon
                            val dist = if (stLat != null && stLon != null) {
                                com.androidfung.departureboard.util.LocationHelper.calculateDistanceMeters(
                                    location.latitude,
                                    location.longitude,
                                    stLat,
                                    stLon
                                ).takeIf { it <= 100_000.0 }
                            } else null
                            val isCardAtConcourse = concourseDecision.isConcourseSprintActive &&
                                card.station.id.equals(nearestSt.id, ignoreCase = true)
                            card.copy(
                                distanceMeters = dist,
                                isAtConcourseStation = isCardAtConcourse,
                                concourseBadgeLabel = if (isCardAtConcourse) concourseDecision.concourseBadgeLabel else null
                            )
                        }

                        val sortedCards = when {
                            concourseDecision.isConcourseSprintActive && updatedCardsWithDistance.any { it.isAtConcourseStation } -> {
                                updatedCardsWithDistance.sortedWith(
                                    compareByDescending<StationCardUiModel> { it.isAtConcourseStation }
                                        .thenBy { if (state.isAutoSortByLocation) (it.distanceMeters ?: Double.MAX_VALUE) else 0.0 }
                                )
                            }
                            state.isAutoSortByLocation && updatedCardsWithDistance.size > 1 -> {
                                updatedCardsWithDistance.sortedBy { it.distanceMeters ?: Double.MAX_VALUE }
                            }
                            else -> updatedCardsWithDistance
                        }
                        state.copy(
                            stationCards = sortedCards,
                            nearestStation = nearestSt,
                            nearestStationId = nearestSt.id,
                            nearestStationDistanceMeters = distance,
                            nearbyStations = nearbyList,
                            concourseStationId = if (concourseDecision.isConcourseSprintActive) nearestSt.id else null,
                            isConcourseSprintActive = concourseDecision.isConcourseSprintActive,
                            concoursePollIntervalMs = concourseDecision.recommendedPollIntervalMs
                        )
                    }
                    com.androidfung.departureboard.wear.WearCompanionSyncPublisher.publishStationBoards(
                        getApplication(),
                        _uiState.value.stationCards
                    )
                } else {
                    _uiState.update { state ->
                        val updatedCardsWithoutDistance = state.stationCards.map { card ->
                            card.copy(
                                distanceMeters = null,
                                isAtConcourseStation = false,
                                concourseBadgeLabel = null
                            )
                        }
                        state.copy(
                            stationCards = updatedCardsWithoutDistance,
                            nearestStation = null,
                            nearestStationId = null,
                            nearestStationDistanceMeters = null,
                            nearbyStations = nearbyList,
                            concourseStationId = null,
                            isConcourseSprintActive = false,
                            concoursePollIntervalMs = 60_000L
                        )
                    }
                }
            } else {
                try {
                    android.util.Log.w("LocationHelper", "Location was null")
                } catch (_: Throwable) {}
            }

            // Multi-tier region detection (GPS -> Telephony carrier -> System timezone -> Device locale)
            val detectionResult = com.androidfung.departureboard.util.RegionDetectionHelper.detectRegion(
                context = getApplication(),
                location = location
            )

            _uiState.update { state ->
                state.copy(
                    detectedRegion = detectionResult.matchedRegion,
                    detectedCountryCode = detectionResult.detectedCountryCode,
                    isLocationUnsupported = detectionResult.isUnsupportedLocation
                )
            }

            // Zero-Tap Default Auto-Switch:
            // If experimental multi-region is enabled and user is physically in a supported region
            // that is different from currently selected region, auto-switch to it.
            if (_uiState.value.isHkRegionEnabled && detectionResult.matchedRegion != null) {
                val matched = detectionResult.matchedRegion
                if (matched != _uiState.value.selectedRegion) {
                    try {
                        android.util.Log.d("RegionDetection", "Auto-switching region to: ${matched.code} via ${detectionResult.detectionSource}")
                    } catch (_: Throwable) {}
                    repository.setSelectedRegion(matched)
                }
            }
            return _uiState.value.nearestStation
    }

    /**
     * Handles errors in the ViewModel with proper user feedback
     */
    private fun handleViewModelError(error: Throwable, message: String? = null) {
        // Log the error for debugging
        try {
            android.util.Log.e("DashboardViewModel", "Error occurred: ${error.message}", error)
        } catch (_: Throwable) {}
        
        // Update UI state with error message if available
        if (message != null) {
            _uiState.update { state ->
                state.copy(
                    userMessage = message,
                    isInitialLoading = false
                )
            }
        }
    }

    /**
     * Updates the UI state with a user-friendly error message
     */
    private fun showUserError(message: String) {
        _uiState.update { state ->
            state.copy(
                userMessage = message,
                isInitialLoading = false
            )
        }
    }

    /**
     * Clears any current user message
     */
    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    /**
     * Clears any current user error messages (alias for backwards compatibility)
     */
    fun clearUserError() {
        clearUserMessage()
    }

    fun toggleSmartCommute() {
        _uiState.update { state ->
            val newEnabled = !state.isSmartCommuteEnabled
            val currentCards = state.stationCards
            val regionalNow = java.time.LocalTime.now(state.selectedRegion.timeZone)
            val activePeriod = com.androidfung.departureboard.util.SmartCommuteHelper.getCommutePeriod(regionalNow)
            val reordered = if (newEnabled) {
                com.androidfung.departureboard.util.SmartCommuteHelper.reorderForCommute(
                    items = currentCards,
                    tagSelector = { it.station.commuteTag },
                    period = activePeriod
                )
            } else {
                currentCards
            }
            state.copy(
                isSmartCommuteEnabled = newEnabled,
                stationCards = reordered,
                userMessage = if (newEnabled) "Smart Commute active: Prioritizing ${activePeriod.name.lowercase()} stations" else "Smart Commute disabled"
            )
        }
    }

    /**
     * Helper to infer line badges when departures haven't loaded yet.
     * Excludes generic "National-rail" or unused "Bus" badges.
     */
    private fun inferLineBadges(station: Station): List<LineBadgeInfo> {
        return com.androidfung.departureboard.data.model.StationLineInferrer.infer(station)
    }

    private fun resolveString(resId: Int, vararg formatArgs: Any?): String {
        return try {
            val app = getApplication<Application>()
            val str = if (formatArgs.isEmpty()) app.getString(resId) else app.getString(resId, *formatArgs)
            if (str.isNullOrBlank()) fallbackString(resId, *formatArgs) else str
        } catch (_: Exception) {
            fallbackString(resId, *formatArgs)
        }
    }

    private fun fallbackString(resId: Int, vararg formatArgs: Any?): String {
        return when (resId) {
            R.string.station_already_saved -> "${formatArgs.getOrNull(0)} is already saved"
            R.string.paywall_prompt_station_limit ->
                "Free plan includes up to ${formatArgs.getOrNull(0)} saved stations. Upgrade to Pro for up to ${formatArgs.getOrNull(1)} stations."
            R.string.station_max_limit_reached ->
                "Maximum limit of ${formatArgs.getOrNull(0)} stations reached to protect live API limits."
            R.string.station_added -> "Added ${formatArgs.getOrNull(0)}"
            R.string.station_removed -> "Removed ${formatArgs.getOrNull(0)}"
            R.string.station_restored -> "Restored ${formatArgs.getOrNull(0)}"
            R.string.paywall_prompt_default -> "Unlock EkiBoard Pro"
            R.string.departures_load_error -> "Unable to load departures"
            else -> ""
        }
    }

    override fun onCleared() {
        stopCountdownTicker()
        stopAutoRefreshPolling()
    }
}
