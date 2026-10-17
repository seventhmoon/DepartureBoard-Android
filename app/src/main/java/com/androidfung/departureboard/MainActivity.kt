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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
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
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.serialization.Serializable

@Serializable
data object DashboardListRoute : NavKey

@Serializable
data class StationDetailRoute(val stationId: String) : NavKey

@Serializable
data class TrainJourneyRoute(val departureId: String) : NavKey

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_STATION_ID = "extra_station_id"
        const val EXTRA_STATION_NAME = "extra_station_name"
    }

    private var initialStation by mutableStateOf<Station?>(null)
    private lateinit var appUpdateManager: AppUpdateManager
    private var installStateUpdatedListener: com.google.android.play.core.install.InstallStateUpdatedListener? = null
    private var debugProReceiver: BroadcastReceiver? = null

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

                    LaunchedEffect(initialStation) {
                        if (initialStation != null) {
                            viewModel.openStationDetail(initialStation!!)
                        }
                    }

                    // Register flexible in-app update listener and handle restart prompt via Snackbar
                    LaunchedEffect(Unit) {
                        val listener = com.google.android.play.core.install.InstallStateUpdatedListener { state ->
                            if (state.installStatus() == com.google.android.play.core.install.model.InstallStatus.DOWNLOADED) {
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
                    // Uses local candidate stations at startup without firing extra HTTP queries.
                    LaunchedEffect(hasLocationPermission) {
                        if (hasLocationPermission) {
                            viewModel.updateNearestStation(fetchApiNearby = false)
                        }
                    }

                    val backStack = rememberNavBackStack(DashboardListRoute as NavKey)

                    LaunchedEffect(uiState.selectedDetailStation) {
                        if (uiState.selectedDetailStation != null) {
                             backStack.addDetail(StationDetailRoute(uiState.selectedDetailStation!!.id))
                        } else {
                             val toRemove = backStack.filter { it is StationDetailRoute || it is TrainJourneyRoute }
                             backStack.removeAll(toRemove)
                        }
                    }

                    LaunchedEffect(uiState.selectedJourneyDeparture?.id) {
                        val journeyDep = uiState.selectedJourneyDeparture
                        if (journeyDep != null) {
                             backStack.addDetail(TrainJourneyRoute(journeyDep.id))
                        } else {
                             val toRemove = backStack.filter { it is TrainJourneyRoute }
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
                                            onAiAssistantClick = { showAiSheet = true },
                                            onUpgradeClick = { viewModel.showPaywall("Unlock EkiBoard Pro") },
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
                                    ) { stationDetailRoute ->
                                        val station = uiState.selectedDetailStation
                                        if (station != null && station.id == stationDetailRoute.stationId) {
                                            val isSaved = uiState.stationCards.any { it.station.id == station.id }
                                            StationDetailScreen(
                                                station = station,
                                                departures = uiState.detailDepartures,
                                                isLoading = uiState.isDetailLoading,
                                                isSaved = isSaved,
                                                serviceHoursInfo = uiState.serviceHoursInfo,
                                                onToggleSaveStation = { st ->
                                                    if (isSaved) {
                                                        viewModel.removeStation(st)
                                                    } else {
                                                        viewModel.addStation(st)
                                                    }
                                                },
                                                onRefresh = { viewModel.openStationDetail(station) },
                                                onDepartureClick = { departure ->
                                                    viewModel.openTrainJourney(departure)
                                                },
                                                onBack = { 
                                                    viewModel.closeStationDetail()
                                                    val last = backStack.last()
                                                    backStack.remove(last)
                                                }
                                            )
                                        }
                                    }
                                    entry<TrainJourneyRoute>(
                                        metadata = ListDetailScene.supportingPane()
                                    ) { trainJourneyRoute ->
                                        val journeyDep = viewModel.resolveDepartureForJourney(trainJourneyRoute.departureId)
                                        if (journeyDep != null) {
                                            TrainJourneyScreen(
                                                departure = journeyDep,
                                                onBack = { 
                                                    viewModel.closeTrainJourney()
                                                    val last = backStack.last()
                                                    backStack.remove(last)
                                                },
                                                onLoadCallingPoints = { dep -> viewModel.getCallingPoints(dep) }
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
                                viewModel.openStationDetail(station)
                            },
                            savedStationIds = remember(uiState.stationCards) {
                                uiState.stationCards.asSequence().map { it.station.id }.toSet()
                            },
                            nearbyStations = uiState.nearbyStations,
                            hasLocationPermission = hasLocationPermission,
                            onRequestLocation = requestLocationWithRationale,
                            onSearchQuery = { query -> viewModel.searchStations(query) }
                        )
                    }

                    if (showAiSheet) {
                        val nearest = uiState.nearestStation
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
                            onSelectAiModel = { model -> viewModel.setAiModelType(model) }
                        )
                    }

                    if (showHelpSheet) {
                        com.androidfung.departureboard.ui.components.HelpFaqBottomSheet(
                            onDismissRequest = { showHelpSheet = false }
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
                            isPro = uiState.isPro,
                            billingRepository = viewModel.billingRepository,
                            onOpenHelpFaq = { showHelpSheet = true },
                            onUpgradeClick = {
                                showSettingsSheet = false
                                viewModel.showPaywall("Unlock EkiBoard Pro")
                            }
                        )
                    }
                }
            }
        }
    }

    private fun NavBackStack<NavKey>.addDetail(detailRoute: NavKey) {
        if (detailRoute is StationDetailRoute) {
            // When navigating to a new station, clear previous station & train journey details
            val toRemove = this.filter { it is StationDetailRoute || it is TrainJourneyRoute }
            this.removeAll(toRemove)
            this.add(detailRoute)
        } else if (detailRoute is TrainJourneyRoute) {
            // Keep the StationDetailRoute in the backstack so on desktop/tablet both or all 3 can coexist!
            val toRemove = this.filter { it is TrainJourneyRoute }
            this.removeAll(toRemove)
            this.add(detailRoute)
        } else {
            this.add(detailRoute)
        }
    }

    @Suppress("DEPRECATION")
    private fun checkForAppUpdate() {
        try {
            val appUpdateInfoTask = appUpdateManager.appUpdateInfo
            appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
                if ((appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) &&
                    appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                ) {
                    // Flexible in-app update available
                    try {
                        appUpdateManager.startUpdateFlowForResult(
                            appUpdateInfo,
                            AppUpdateType.FLEXIBLE,
                            this,
                            1001
                        )
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }

    @Suppress("DEPRECATION")
    override fun onResume() {
        super.onResume()
        try {
            appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
                if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    appUpdateManager.startUpdateFlowForResult(
                        info,
                        AppUpdateType.FLEXIBLE,
                        this,
                        1001
                    )
                } else if (info.installStatus() == com.google.android.play.core.install.model.InstallStatus.DOWNLOADED) {
                    // Update has already been downloaded, prompt user to complete update
                    lifecycleScope.launch {
                        Toast.makeText(
                            this@MainActivity,
                            "An update is downloaded. Restart the app to finish installing.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        } catch (_: Exception) {}
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
            try {
                appUpdateManager.unregisterListener(it)
            } catch (_: Exception) {}
        }
        debugProReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
        }
    }
}
