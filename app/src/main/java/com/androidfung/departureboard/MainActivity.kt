package com.androidfung.departureboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.androidfung.departureboard.billing.BillingRepository
import com.androidfung.departureboard.data.model.Station
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.androidfung.departureboard.ui.components.ListDetailScene
import com.androidfung.departureboard.ui.components.SearchStationBottomSheet
import com.androidfung.departureboard.ui.components.StationDetailScreen
import com.androidfung.departureboard.ui.components.TrainJourneyScreen
import com.androidfung.departureboard.ui.components.rememberListDetailSceneStrategy
import com.androidfung.departureboard.ui.dashboard.DashboardContent
import com.androidfung.departureboard.ui.dashboard.DashboardViewModel
import com.androidfung.departureboard.ui.theme.DepartureBoardTheme
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.milliseconds

@Serializable
data object DashboardListRoute : NavKey

@Serializable
data object StationDetailRoute : NavKey

@Serializable
data object TrainJourneyRoute : NavKey

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_STATION_ID = "extra_station_id"
        const val EXTRA_STATION_NAME = "extra_station_name"
        private const val TAG = "InAppUpdate"
    }

    private var initialStation by mutableStateOf<Station?>(null)
    private lateinit var appUpdateManager: AppUpdateManager
    private var installStateUpdatedListener: InstallStateUpdatedListener? = null
    private var debugProReceiver: BroadcastReceiver? = null

    // Modern ActivityResultLauncher for in-app update flows
    @Suppress("UpgradeFragmentVersion")
    private val appUpdateLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            android.util.Log.d(TAG, "In-app update flow started successfully.")
        } else if (result.resultCode == RESULT_CANCELED) {
            android.util.Log.d(TAG, "User cancelled the in-app update flow.")
        } else {
            android.util.Log.w(TAG, "In-app update flow failed with resultCode: ${result.resultCode}")
        }
    }

    @OptIn(ExperimentalSharedTransitionApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        com.androidfung.departureboard.widget.WidgetUpdateWorker.enqueuePeriodicUpdate(applicationContext)

        registerDebugProReceiver()

        // Check for Google Play In-App Updates
        appUpdateManager = AppUpdateManagerFactory.create(this)
        checkForAppUpdate()

        // Track app launch for In-App Review engagement metrics
        lifecycleScope.launch {
            com.androidfung.departureboard.review.InAppReviewManager.getInstance(applicationContext).recordAppLaunch()
        }

        setContent {
            val viewModel: DashboardViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            // Observe persisted theme mode and dynamic color preferences
            DepartureBoardTheme(
                themeMode = uiState.themeMode,
                dynamicColor = uiState.isDynamicColor
            ) {
                com.androidfung.departureboard.ui.theme.ProvideWindowWidthClass {
                    val snackbarHostState = remember { SnackbarHostState() }

                    var showSearchSheet by rememberSaveable { mutableStateOf(false) }
                    var showAiSheet by rememberSaveable { mutableStateOf(false) }
                    var showDebugSheet by rememberSaveable { mutableStateOf(false) }
                    var showHelpSheet by rememberSaveable { mutableStateOf(false) }
                    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
                    var showLineStatusSheet by rememberSaveable { mutableStateOf(false) }

                    LaunchedEffect(initialStation) {
                        if (initialStation != null) {
                            viewModel.openStationDetail(initialStation!!)
                        }
                    }

                    // Contextual smart review trigger for active users:
                    // Checks if conditions are met after the dashboard loads with saved stations
                    // without interrupting the user during active navigation or urgent departure checks.
                    LaunchedEffect(uiState.stationCards.size) {
                        if (uiState.stationCards.isNotEmpty() && !uiState.isInitialLoading && !uiState.isRefreshing) {
                            // Delay slightly after launch so the UI is calm and settled
                            kotlinx.coroutines.delay(3000.milliseconds)
                            // Only trigger when the user is on the top-level dashboard (not deep in station or journey details)
                            if (uiState.selectedDetailStation == null && uiState.selectedJourneyDeparture == null) {
                                com.androidfung.departureboard.review.InAppReviewManager.getInstance(applicationContext)
                                    .launchReviewFlow(
                                        activity = this@MainActivity,
                                        force = false,
                                        savedStationsCount = uiState.stationCards.size,
                                        fallbackToPlayStore = false
                                    )
                            }
                        }
                    }

                    // Register flexible in-app update listener and handle restart prompt via Snackbar
                    LaunchedEffect(Unit) {
                        val listener = InstallStateUpdatedListener { state ->
                            android.util.Log.d(TAG, "InstallState: status=${state.installStatus()}, errorCode=${state.installErrorCode()}")
                            if (state.installStatus() == InstallStatus.DOWNLOADED) {
                                lifecycleScope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "An update has just been downloaded.",
                                        actionLabel = "Restart",
                                        duration = SnackbarDuration.Indefinite
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        appUpdateManager.completeUpdate()
                                    }
                                }
                            }
                        }
                        installStateUpdatedListener = listener
                        appUpdateManager.registerListener(listener)
                    }

                    // Handle user snackbar notifications (e.g., station deletion undo)
                    LaunchedEffect(uiState.userMessage) {
                        val message = uiState.userMessage
                        if (message != null) {
                            val result = snackbarHostState.showSnackbar(
                                message = message,
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                viewModel.undoRemoveStation()
                            }
                            viewModel.clearUserMessage()
                        }
                    }

                    // Automatically pause auto-refresh polling and the 1s countdown ticker when the
                    // screen is backgrounded or inactive, so neither keeps running with the screen off.
                    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
                        viewModel.startAutoRefreshPolling()
                        viewModel.startCountdownTicker()
                        onPauseOrDispose {
                            viewModel.stopAutoRefreshPolling()
                            viewModel.stopCountdownTicker()
                        }
                    }

                    val context = androidx.compose.ui.platform.LocalContext.current

                    // Nearest-station detection requires location permission, which is requested
                    // contextually with a rationale (e.g. when opening search nearby or AI assistant),
                    // rather than abruptly at app launch.
                    var hasLocationPermission by remember {
                        mutableStateOf(
                            ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.ACCESS_COARSE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                        )
                    }

                    val locationPermissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestMultiplePermissions()
                    ) { permissions ->
                        hasLocationPermission = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                            permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
                    }

                    var showLocationRationale by remember { mutableStateOf(false) }

                    val requestLocationWithRationale = {
                        if (!hasLocationPermission) {
                            showLocationRationale = true
                        }
                    }

                    // Re-run the nearest-station lookup whenever permission is granted (or re-granted).
                    // Fetches actual live nearby stations within radius so nearest is accurate even if unsaved.
                    LaunchedEffect(hasLocationPermission) {
                        if (hasLocationPermission) {
                            viewModel.updateNearestStation(fetchApiNearby = true)
                        }
                    }

                    val backStack = rememberNavBackStack(DashboardListRoute as NavKey)

                    LaunchedEffect(uiState.selectedDetailStation != null) {
                        if (uiState.selectedDetailStation != null) {
                             if (!backStack.contains(StationDetailRoute)) {
                                 backStack.addDetail(StationDetailRoute)
                             }
                        } else {
                             val toRemove = backStack.filter { it == StationDetailRoute || it == TrainJourneyRoute }
                             backStack.removeAll(toRemove)
                        }
                    }

                    LaunchedEffect(uiState.selectedJourneyDeparture != null) {
                        val journeyDep = uiState.selectedJourneyDeparture
                        if (journeyDep != null) {
                             if (!backStack.contains(TrainJourneyRoute)) {
                                 backStack.addDetail(TrainJourneyRoute)
                             }
                        } else {
                             val toRemove = backStack.filter { it == TrainJourneyRoute }
                             backStack.removeAll(toRemove)
                        }
                    }

                    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>()

                    val staggeredGridState = androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState()

                    SharedTransitionLayout {
                        NavDisplay(
                            backStack = backStack,
                            onBack = { 
                                if (backStack.last() is TrainJourneyRoute) {
                                    viewModel.closeTrainJourney()
                                } else if (backStack.last() is StationDetailRoute) {
                                    viewModel.closeStationDetail()
                                }
                                val last = backStack.last()
                                backStack.remove(last)
                            },
                            sceneStrategies = listOf(listDetailStrategy),
                            sharedTransitionScope = this,
                            modifier = Modifier.fillMaxSize(),
                            entryProvider = entryProvider {
                                    entry<DashboardListRoute>(
                                        metadata = ListDetailScene.listPane()
                                    ) {
                                        DashboardContent(
                                            uiState = uiState,
                                            snackbarHostState = snackbarHostState,
                                            staggeredGridState = staggeredGridState,
                                            onRefresh = { viewModel.refreshDepartures(isManualPullToRefresh = true) },
                                            onDismissStation = { station -> viewModel.removeStation(station) },
                                            onMoveStation = { fromIndex, toIndex -> viewModel.moveStation(fromIndex, toIndex) },
                                            onAddStationClick = {
                                                if (hasLocationPermission) {
                                                    viewModel.updateNearestStation(fetchApiNearby = true)
                                                }
                                                showSearchSheet = true
                                            },
                                            onStationClick = { station ->
                                                viewModel.openStationDetail(station)
                                            },
                                            onDepartureClick = { departure ->
                                                viewModel.openTrainJourney(departure)
                                            },
                                            onAiAssistantClick = {
                                                if (hasLocationPermission) {
                                                    viewModel.updateNearestStation(fetchApiNearby = true)
                                                }
                                                showAiSheet = true
                                            },
                                            onUpgradeClick = { viewModel.showPaywall("Unlock EkiBoard Pro") },
                                            onOpenLineStatus = {
                                                viewModel.refreshLineStatuses()
                                                showLineStatusSheet = true
                                            },
                                            onOpenSettings = { showSettingsSheet = true },
                                            onOpenDebugSettings = {
                                                android.util.Log.d("DashboardScreen", "onOpenDebugSettings clicked!")
                                                showDebugSheet = true
                                            },
                                            onOpenHelpFaq = { showHelpSheet = true }
                                        )
                                    }
                                    entry<StationDetailRoute>(
                                        metadata = ListDetailScene.detailPane()
                                    ) {
                                        val station = uiState.selectedDetailStation
                                        if (station != null) {
                                            val isSaved = uiState.stationCards.any { it.station.id == station.id }
                                            StationDetailScreen(
                                                station = station,
                                                departures = uiState.detailDepartures,
                                                isLoading = uiState.isDetailLoading,
                                                isSaved = isSaved,
                                                serviceHoursInfo = uiState.serviceHoursInfo,
                                                stations = uiState.detailStationsContext.ifEmpty { listOf(station) },
                                                onStationChange = { nextStation ->
                                                    viewModel.openStationDetail(nextStation, uiState.detailStationsContext)
                                                },
                                                onToggleSaveStation = { st ->
                                                    if (isSaved) {
                                                        viewModel.removeStation(st)
                                                    } else {
                                                        viewModel.addStation(st)
                                                    }
                                                },
                                                onRefresh = { viewModel.openStationDetail(station, uiState.detailStationsContext) },
                                                onDepartureClick = { departure, contextDepartures ->
                                                    viewModel.openTrainJourney(departure, contextDepartures)
                                                },
                                                onBack = { 
                                                    viewModel.closeStationDetail()
                                                    val last = backStack.last()
                                                    backStack.remove(last)
                                                },
                                                isPro = uiState.isPro
                                            )
                                        }
                                    }
                                    entry<TrainJourneyRoute>(
                                        metadata = ListDetailScene.supportingPane()
                                    ) {
                                        val journeyDep = uiState.selectedJourneyDeparture
                                        if (journeyDep != null) {
                                            TrainJourneyScreen(
                                                departure = journeyDep,
                                                departures = uiState.journeyDeparturesContext.ifEmpty { listOf(journeyDep) },
                                                onDepartureChange = { nextDep ->
                                                    viewModel.openTrainJourney(nextDep, uiState.journeyDeparturesContext)
                                                },
                                                onCallingPointClick = { cp, cps ->
                                                    viewModel.openStationDetailFromCallingPoint(cp, cps)
                                                },
                                                onBack = { 
                                                    viewModel.closeTrainJourney()
                                                    val last = backStack.last()
                                                    backStack.remove(last)
                                                },
                                                onLoadCallingPoints = { dep -> viewModel.getCallingPoints(dep) },
                                                isPro = uiState.isPro
                                            )
                                        }
                                    }
                                }
                            )
                        }

                    // Bottom sheets for other overlay actions
                    if (showSearchSheet) {
                        @OptIn(ExperimentalMaterial3Api::class)
                        SearchStationBottomSheet(
                            onDismissRequest = { showSearchSheet = false },
                            onStationSelected = { station ->
                                showSearchSheet = false
                                viewModel.addRecentSearchedStation(station)
                                viewModel.openStationDetail(station)
                            },
                            savedStationIds = remember(uiState.stationCards) {
                                uiState.stationCards.asSequence().map { it.station.id }.toSet()
                            },
                            nearbyStations = uiState.nearbyStations,
                            recentSearchedStations = uiState.recentSearchedStations,
                            onRemoveRecentSearch = { stationId -> viewModel.removeRecentSearchedStation(stationId) },
                            onClearRecentSearches = { viewModel.clearRecentSearchedStations() },
                            hasLocationPermission = hasLocationPermission,
                            onRequestLocation = requestLocationWithRationale,
                            selectedRegion = uiState.selectedRegion,
                            onSearchQuery = { query -> viewModel.searchStations(query) }
                        )
                    }

                    if (showAiSheet) {
                        val nearest = uiState.nearestStation
                            ?: uiState.nearbyStations.firstOrNull()?.first
                            ?: uiState.stationCards.firstOrNull { it.station.id == uiState.nearestStationId }?.station
                        com.androidfung.departureboard.ui.components.AiTransitSheet(
                            onDismissRequest = { showAiSheet = false },
                            repository = viewModel.repository,
                            savedStations = uiState.stationCards.map { it.station },
                            nearestStation = nearest,
                            initialTriggerSpeech = false,
                            billingRepository = viewModel.billingRepository,
                            aiModelType = uiState.aiModelType,
                            onUpgradeClick = { viewModel.showPaywall("Unlock unlimited EkiBoard AI queries with Pro") }
                        )
                    }

                    if (showLocationRationale) {
                        com.androidfung.departureboard.ui.components.LocationRationaleDialog(
                            onConfirm = {
                                showLocationRationale = false
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            onDismiss = {
                                showLocationRationale = false
                            }
                        )
                    }

                    if (uiState.paywallPromptReason != null) {
                        com.androidfung.departureboard.ui.components.PaywallBottomSheet(
                            onDismissRequest = { viewModel.dismissPaywall() },
                            billingRepository = viewModel.billingRepository,
                            reasonMessage = uiState.paywallPromptReason
                        )
                    }

                    if (showDebugSheet && BuildConfig.DEBUG) {
                        com.androidfung.departureboard.ui.components.DebugSettingsBottomSheet(
                            onDismissRequest = { showDebugSheet = false },
                            billingRepository = viewModel.billingRepository,
                            isPro = uiState.isPro,
                            userPlan = uiState.userPlan,
                            onSelectPlan = { plan -> viewModel.setDebugPlan(plan) },
                            onTogglePro = { viewModel.toggleDebugPro() },
                            onResetStations = { viewModel.resetSavedStationsToDefaults() },
                            selectedAiModel = uiState.aiModelType,
                            onSelectAiModel = { model -> viewModel.setAiModelType(model) },
                            selectedRegion = uiState.selectedRegion,
                            onSelectRegion = { region -> viewModel.setSelectedRegion(region) },
                            isHkRegionEnabled = uiState.isHkRegionEnabled,
                            onToggleHkRegionEnabled = { enabled -> viewModel.setHkRegionEnabled(enabled) },
                            onTriggerInAppReview = {
                                lifecycleScope.launch {
                                    com.androidfung.departureboard.review.InAppReviewManager.getInstance(applicationContext)
                                        .launchReviewFlow(
                                            activity = this@MainActivity,
                                            force = true,
                                            fallbackToPlayStore = false
                                        )
                                }
                            },
                            onResetInAppReviewData = {
                                lifecycleScope.launch {
                                    com.androidfung.departureboard.review.InAppReviewManager.getInstance(applicationContext)
                                        .resetReviewDataForTesting()
                                    Toast.makeText(applicationContext, "In-app review tracking reset", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }

                    if (showHelpSheet) {
                        com.androidfung.departureboard.ui.components.HelpFaqBottomSheet(
                            onDismissRequest = { showHelpSheet = false },
                            onRateApp = {
                                lifecycleScope.launch {
                                    com.androidfung.departureboard.review.InAppReviewManager.getInstance(applicationContext)
                                        .launchReviewFlow(
                                            activity = this@MainActivity,
                                            force = true,
                                            fallbackToPlayStore = true
                                        )
                                }
                            }
                        )
                    }

                    if (showLineStatusSheet) {
                        val relevantLineIds = remember(uiState.stationCards) {
                            uiState.stationCards.flatMap { card ->
                                card.availableLineBadges.map { it.lineId.lowercase() }
                            }.toSet()
                        }
                        com.androidfung.departureboard.ui.components.LineStatusBottomSheet(
                            lineStatuses = uiState.lineStatuses,
                            selectedRegion = uiState.selectedRegion,
                            relevantLineIds = relevantLineIds,
                            onRefresh = { viewModel.refreshLineStatuses() },
                            onDismiss = { showLineStatusSheet = false }
                        )
                    }

                    if (showSettingsSheet) {
                        com.androidfung.departureboard.ui.components.SettingsBottomSheet(
                            onDismissRequest = { showSettingsSheet = false },
                            departureRowsCount = uiState.departureRowsCount,
                            onDepartureRowsChange = { count -> viewModel.setDepartureRowsCount(count) },
                            themeMode = uiState.themeMode,
                            onThemeModeChange = { mode -> viewModel.setThemeMode(mode) },
                            isDynamicColor = uiState.isDynamicColor,
                            onDynamicColorChange = { enabled -> viewModel.setDynamicColor(enabled) },
                            isAutoSortByLocation = uiState.isAutoSortByLocation,
                            onAutoSortByLocationChange = { enabled ->
                                if (enabled && !hasLocationPermission) {
                                    requestLocationWithRationale()
                                }
                                viewModel.setAutoSortByLocation(enabled)
                            },
                            isPro = uiState.isPro,
                            billingRepository = viewModel.billingRepository,
                            onOpenHelpFaq = { showHelpSheet = true },
                            onOpenDebugSettings = {
                                showDebugSheet = true
                            },
                            onUpgradeClick = {
                                showSettingsSheet = false
                                viewModel.showPaywall("Unlock EkiBoard Pro")
                            },
                            onRateApp = {
                                lifecycleScope.launch {
                                    com.androidfung.departureboard.review.InAppReviewManager.getInstance(applicationContext)
                                        .launchReviewFlow(
                                            activity = this@MainActivity,
                                            force = true,
                                            fallbackToPlayStore = true
                                        )
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun NavBackStack<NavKey>.addDetail(detailRoute: NavKey) {
        if (detailRoute == StationDetailRoute) {
            // When navigating to a new station, clear previous station & train journey details
            val toRemove = this.filter { it == StationDetailRoute || it == TrainJourneyRoute }
            this.removeAll(toRemove)
            this.add(detailRoute)
        } else if (detailRoute == TrainJourneyRoute) {
            // Keep the StationDetailRoute in the backstack so on desktop/tablet both or all 3 can coexist!
            val toRemove = this.filter { it == TrainJourneyRoute }
            this.removeAll(toRemove)
            this.add(detailRoute)
        } else {
            this.add(detailRoute)
        }
    }

    private fun checkForAppUpdate() {
        try {
            val appUpdateInfoTask = appUpdateManager.appUpdateInfo
            appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
                val availability = appUpdateInfo.updateAvailability()
                val isFlexibleAllowed = appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                val availableVersion = appUpdateInfo.availableVersionCode()
                android.util.Log.d(
                    TAG,
                    "AppUpdateInfo: availability=$availability, isFlexibleAllowed=$isFlexibleAllowed, availableVersionCode=$availableVersion"
                )

                if (availability == UpdateAvailability.UPDATE_AVAILABLE && isFlexibleAllowed) {
                    try {
                        appUpdateManager.startUpdateFlowForResult(
                            appUpdateInfo,
                            appUpdateLauncher,
                            AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
                        )
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Failed to start in-app update flow", e)
                    }
                }
            }.addOnFailureListener { error ->
                android.util.Log.w(TAG, "Failed to check for in-app updates: ${error.message}", error)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error checking for app updates", e)
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
                if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    try {
                        appUpdateManager.startUpdateFlowForResult(
                            info,
                            appUpdateLauncher,
                            AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
                        )
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Failed to resume developer triggered update", e)
                    }
                } else if (info.installStatus() == InstallStatus.DOWNLOADED) {
                    lifecycleScope.launch {
                        Toast.makeText(
                            this@MainActivity,
                            "An update is downloaded. Restart the app to finish installing.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }.addOnFailureListener { error ->
                android.util.Log.d(TAG, "onResume: Failed to check app update info: ${error.message}")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "onResume: Error checking app update info", e)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data
        val stationId = intent?.getStringExtra(EXTRA_STATION_ID)
            ?: if ((uri != null) && (uri.scheme == "mindtheboard") && (uri.host == "station")) uri.lastPathSegment else null
        val stationName = intent?.getStringExtra(EXTRA_STATION_NAME)
            ?: uri?.getQueryParameter("name")

        if (!stationId.isNullOrBlank()) {
            // Reuse full metadata (modes/zone/lines) for known popular stations so a
            // deep-linked card shows correct branding — previously every deep link was
            // hard-coded as a "tube" station, which was wrong for National Rail hubs.
            val known = com.androidfung.departureboard.data.model.DefaultStations.POPULAR_STATIONS
                .firstOrNull { (id) -> id == stationId }
            if (known != null) {
                initialStation = known.copy(
                    name = stationName ?: known.name,
                    isFavorite = true
                )
            } else {
                // Initial placeholder
                initialStation = Station(
                    id = stationId,
                    name = stationName ?: "Station",
                    modes = listOf("tube"),
                    zone = null,
                    lat = null,
                    lon = null,
                    lines = emptyList(),
                    isFavorite = true
                )

                // Asynchronously resolve and hydrate station metadata from repository
                lifecycleScope.launch {
                    val app = application as? DepartureBoardApp
                    val repo = app?.repository
                    val resolved = repo?.getStationById(stationId)
                    if (resolved != null) {
                        initialStation = resolved.copy(
                            name = stationName ?: resolved.name,
                            isFavorite = true
                        )
                    }
                }
            }
        } else {
            initialStation = null
        }
    }

    private fun registerDebugProReceiver() {
        if (!BuildConfig.DEBUG) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val billingRepo = BillingRepository.getInstance(applicationContext)
                val targetPro =
                    intent?.hasExtra("is_pro") != true || intent.getBooleanExtra("is_pro", true)
                billingRepo.setDebugPro(targetPro)
                val msg = "Debug: Switched to ${if (targetPro) "PRO" else "FREE"} plan"
                Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                android.util.Log.d("MainActivity", msg)
            }
        }
        debugProReceiver = receiver
        val filter = IntentFilter("com.androidfung.departureboard.TOGGLE_PRO")
        ContextCompat.registerReceiver(
            this,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        installStateUpdatedListener?.let {
            runCatching { appUpdateManager.unregisterListener(it) }
        }
        debugProReceiver?.let {
            runCatching { unregisterReceiver(it) }
        }
    }
}
