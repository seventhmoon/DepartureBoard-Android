package com.androidfung.departureboard

import android.animation.ObjectAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.res.stringResource
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.androidfung.departureboard.billing.BillingRepository
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TransitRegion
import com.androidfung.departureboard.ui.dashboard.DashboardContent
import com.androidfung.departureboard.ui.dashboard.DashboardViewModel
import com.androidfung.departureboard.ui.feature.detail.ListDetailScene
import com.androidfung.departureboard.ui.feature.detail.StationDetailScreen
import com.androidfung.departureboard.ui.feature.detail.TrainJourneyScreen
import com.androidfung.departureboard.ui.feature.detail.rememberListDetailSceneStrategy
import com.androidfung.departureboard.ui.feature.search.SearchStationBottomSheet
import com.androidfung.departureboard.ui.theme.DepartureBoardTheme
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object DashboardListRoute : NavKey

@Serializable
data class StationDetailRoute(val stationId: String = "") : NavKey

@Serializable
data class TrainJourneyRoute(val departureId: String = "") : NavKey

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_STATION_ID = "extra_station_id"
        const val EXTRA_STATION_NAME = "extra_station_name"
        const val EXTRA_DEPARTURE_ID = "extra_departure_id"
        private const val TAG = "InAppUpdate"
    }

    private var initialStation by mutableStateOf<Station?>(null)
    private var initialDepartureId by mutableStateOf<String?>(null)
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
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Custom smooth exit animation from splash screen to Compose UI:
        // Ensures both the center LED board (avd_splash_screen) and the bottom 5 transport mode
        // icons below EkiBoard (avd_splash_branding) play their sequential one-by-one illumination.
        splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
            val splashScreenView = splashScreenViewProvider.view
            val iconView = splashScreenViewProvider.iconView

            // 1. Start (or restart) the center Concourse LED sequential row animation
            val avdLogo = androidx.appcompat.content.res.AppCompatResources.getDrawable(
                this@MainActivity,
                R.drawable.avd_splash_screen
            )
            if (iconView is android.widget.ImageView && iconView.drawable != null) {
                iconView.setImageDrawable(avdLogo)
            } else {
                iconView.background = avdLogo
            }
            (avdLogo as? android.graphics.drawable.Animatable)?.start()

            // 2. Start the bottom branding 5-mode-icon sequential illumination (API 31+ Branding View)
            val viewGroup = splashScreenView as? android.view.ViewGroup
            if (viewGroup != null) {
                for (i in 0 until viewGroup.childCount) {
                    val child = viewGroup.getChildAt(i)
                    if (child !== iconView && child.background != null) {
                        val avdBranding = androidx.appcompat.content.res.AppCompatResources.getDrawable(
                            this@MainActivity,
                            R.drawable.avd_splash_branding
                        )
                        child.background = avdBranding
                        (avdBranding as? android.graphics.drawable.Animatable)?.start()
                    }
                }
            }

            // 3. Hold for 850ms so all rows and all 5 mode icons light up one by one before fading out
            val fadeOut = ObjectAnimator.ofFloat(splashScreenView, View.ALPHA, 1f, 0f).apply {
                interpolator = DecelerateInterpolator()
                startDelay = 850L
                duration = 300L
                doOnEnd { splashScreenViewProvider.remove() }
            }
            fadeOut.start()
        }

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
                    val windowWidthClass = com.androidfung.departureboard.ui.theme.LocalWindowWidthClass.current
                    val snackbarHostState = remember { SnackbarHostState() }

                    var showSearchSheet by rememberSaveable { mutableStateOf(false) }
                    var showAiSheet by rememberSaveable { mutableStateOf(false) }
                    var showDebugSheet by rememberSaveable { mutableStateOf(false) }
                    var showHelpSheet by rememberSaveable { mutableStateOf(false) }
                    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
                    var showLineStatusSheet by rememberSaveable { mutableStateOf(false) }
                    var showRegionPickerSheet by rememberSaveable { mutableStateOf(false) }
                    var showTwinConcourseSheet by rememberSaveable { mutableStateOf(false) }

                    LaunchedEffect(initialStation, initialDepartureId) {
                        initialStation?.let { station ->
                            viewModel.openStationDetail(
                                station = station,
                                targetDepartureId = initialDepartureId
                            )
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
                    val updateDownloadedText = stringResource(R.string.update_downloaded_message)
                    val restartActionText = stringResource(R.string.update_restart_action)
                    val undoActionText = stringResource(R.string.action_undo)

                    LaunchedEffect(Unit) {
                        val listener = InstallStateUpdatedListener { state ->
                            android.util.Log.d(TAG, "InstallState: status=${state.installStatus()}, errorCode=${state.installErrorCode()}")
                            if (state.installStatus() == InstallStatus.DOWNLOADED) {
                                lifecycleScope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = updateDownloadedText,
                                        actionLabel = restartActionText,
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
                                actionLabel = undoActionText,
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

                    val selectedStationId = uiState.selectedDetailStation?.id
                    val selectedDepartureId = uiState.selectedJourneyDeparture?.id

                    LaunchedEffect(selectedStationId) {
                        if (selectedStationId != null) {
                            val newStationRoute = StationDetailRoute(selectedStationId)
                            val currentTrainRoute = backStack.firstOrNull { it is TrainJourneyRoute }
                            val newStack = mutableListOf<NavKey>(DashboardListRoute)
                            if (currentTrainRoute != null) {
                                newStack.add(currentTrainRoute)
                            }
                            newStack.add(newStationRoute)
                            if (backStack.toList() != newStack) {
                                backStack.clear()
                                backStack.addAll(newStack)
                            }
                        } else {
                            val toRemove = backStack.filter { it is StationDetailRoute }
                            if (toRemove.isNotEmpty()) {
                                backStack.removeAll(toRemove)
                            }
                        }
                    }

                    LaunchedEffect(selectedDepartureId) {
                        if (selectedDepartureId != null) {
                            val newJourneyRoute = TrainJourneyRoute(selectedDepartureId)
                            val currentStationRoute = backStack.firstOrNull { it is StationDetailRoute }
                            val newStack = mutableListOf<NavKey>(DashboardListRoute)
                            if (currentStationRoute != null) {
                                newStack.add(currentStationRoute)
                            }
                            newStack.add(newJourneyRoute)
                            if (backStack.toList() != newStack) {
                                backStack.clear()
                                backStack.addAll(newStack)
                            }
                        } else {
                            val toRemove = backStack.filter { it is TrainJourneyRoute }
                            if (toRemove.isNotEmpty()) {
                                backStack.removeAll(toRemove)
                            }
                        }
                    }

                    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>()

                    val staggeredGridState = androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState()

                    SharedTransitionLayout {
                        NavDisplay(
                            backStack = backStack,
                            onBack = { 
                                val last = backStack.lastOrNull()
                                if (last is TrainJourneyRoute) {
                                    viewModel.closeTrainJourney()
                                } else if (last is StationDetailRoute) {
                                    viewModel.closeStationDetail()
                                }
                                if (backStack.size > 1) {
                                    backStack.remove(backStack.last())
                                }
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
                                                viewModel.closeTrainJourney()
                                                viewModel.openStationDetail(station)
                                            },
                                            onDepartureClick = { departure ->
                                                val parentStation = uiState.stationCards
                                                    .firstOrNull { it.station.id == departure.stationId }?.station
                                                if (windowWidthClass == com.androidfung.departureboard.ui.theme.WindowWidthClass.EXTRA_LARGE && parentStation != null) {
                                                    viewModel.openStationDetail(parentStation)
                                                }
                                                viewModel.openTrainJourney(departure)
                                            },
                                            onAiAssistantClick = {
                                                if (hasLocationPermission) {
                                                    viewModel.updateNearestStation(fetchApiNearby = true)
                                                } else {
                                                    requestLocationWithRationale()
                                                }
                                                showAiSheet = true
                                            },
                                            onUpgradeClick = { viewModel.showPaywall(getString(R.string.paywall_prompt_default)) },
                                            onOpenLineStatus = {
                                                viewModel.refreshLineStatuses()
                                                showLineStatusSheet = true
                                            },
                                            onOpenSettings = { showSettingsSheet = true },
                                            onOpenDebugSettings = {
                                                android.util.Log.d("DashboardScreen", "onOpenDebugSettings clicked!")
                                                showDebugSheet = true
                                            },
                                            onOpenHelpFaq = { showHelpSheet = true },
                                            onOpenDeskClock = { viewModel.openDeskClock() },
                                            onOpenTwinConcourse = { showTwinConcourseSheet = true },
                                            onToggleSmartCommute = { viewModel.toggleSmartCommute() }
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
                                                },
                                                isPro = uiState.isPro,
                                                onOpenDeskClock = { st -> viewModel.openDeskClock(st) }
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
                                                },
                                                onLoadCallingPoints = { dep -> viewModel.getCallingPoints(dep) },
                                                isPro = uiState.isPro
                                            )
                                        }
                                    }
                                }
                            )
                        }

                    // Full-screen Split-Flap Desk Clock & Foldable Tabletop Kiosk Mode
                    val windowPosture = com.androidfung.departureboard.ui.theme.LocalWindowPosture.current
                    var userDismissedTabletop by rememberSaveable { mutableStateOf(false) }

                    // When device unfolds flat again, reset userDismissedTabletop so the next tabletop fold triggers
                    LaunchedEffect(windowPosture.isTabletop) {
                        if (!windowPosture.isTabletop) {
                            userDismissedTabletop = false
                        }
                    }

                    val isTabletopAutoTriggered = windowPosture.isTabletop && !userDismissedTabletop
                    val activeDeskStation = uiState.deskClockStation
                        ?: if (isTabletopAutoTriggered) {
                            uiState.selectedDetailStation
                                ?: uiState.nearestStation?.takeIf { nearest -> uiState.stationCards.any { it.station.id == nearest.id } }
                                ?: uiState.stationCards.firstOrNull()?.station
                                ?: uiState.selectedRegion.defaultStations.firstOrNull()
                        } else null

                    if (activeDeskStation != null) {
                        androidx.activity.compose.BackHandler {
                            if (uiState.deskClockStation != null) {
                                viewModel.closeDeskClock()
                            }
                            userDismissedTabletop = true
                        }
                        val deskCard = uiState.stationCards.firstOrNull { it.station.id == activeDeskStation.id }
                        val deskDepartures = when {
                            deskCard != null && deskCard.departures.isNotEmpty() -> deskCard.departures
                            uiState.selectedDetailStation?.id == activeDeskStation.id -> uiState.detailDepartures
                            else -> emptyList()
                        }
                        val deskServiceHours = deskCard?.serviceHoursInfo
                            ?: uiState.serviceHoursInfo.takeIf { uiState.selectedDetailStation?.id == activeDeskStation.id }
                            ?: com.androidfung.departureboard.util.LastTrainServiceCalculator.evaluateServiceHours(
                                stationId = activeDeskStation.id,
                                departures = deskDepartures
                            )

                        com.androidfung.departureboard.ui.feature.deskclock.DeskClockScreen(
                            station = activeDeskStation,
                            stationCards = uiState.stationCards,
                            departures = deskDepartures,
                            serviceHoursInfo = deskServiceHours,
                            onSelectStation = { nextSt ->
                                viewModel.openDeskClock(nextSt)
                                if (uiState.stationCards.none { it.station.id == nextSt.id }) {
                                    viewModel.openStationDetail(nextSt)
                                }
                            },
                            onRefresh = {
                                viewModel.refreshStation(activeDeskStation)
                            },
                            onClose = {
                                if (uiState.deskClockStation != null) {
                                    viewModel.closeDeskClock()
                                }
                                userDismissedTabletop = true
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
                        com.androidfung.departureboard.ui.feature.ai.AiTransitSheet(
                            onDismissRequest = { showAiSheet = false },
                            repository = viewModel.repository,
                            savedStations = uiState.stationCards.map { it.station },
                            nearestStation = nearest,
                            initialTriggerSpeech = false,
                            billingRepository = viewModel.billingRepository,
                            aiModelType = uiState.aiModelType,
                            selectedRegion = uiState.selectedRegion,
                            onUpgradeClick = { viewModel.showPaywall(getString(R.string.paywall_prompt_ai_limit)) },
                            hasLocationPermission = hasLocationPermission,
                            onRequestLocationPermission = requestLocationWithRationale,
                            onRefreshNearestStation = {
                                viewModel.refreshNearestStationInternal(fetchApiNearby = true)
                            }
                        )
                    }

                    if (showLocationRationale) {
                        com.androidfung.departureboard.ui.designsystem.LocationRationaleDialog(
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
                        com.androidfung.departureboard.ui.feature.paywall.PaywallBottomSheet(
                            onDismissRequest = { viewModel.dismissPaywall() },
                            billingRepository = viewModel.billingRepository,
                            reasonMessage = uiState.paywallPromptReason
                        )
                    }

                    if (showDebugSheet && BuildConfig.DEBUG) {
                        com.androidfung.departureboard.ui.feature.settings.DebugSettingsBottomSheet(
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
                            onOpenRegionPicker = {
                                showDebugSheet = false
                                showRegionPickerSheet = true
                            },
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
                            },
                            onOpenDeskClock = {
                                showDebugSheet = false
                                viewModel.openDeskClock()
                            },
                            onOpenTwinConcourse = {
                                showDebugSheet = false
                                showTwinConcourseSheet = true
                            }
                        )
                    }

                    if (showHelpSheet) {
                        com.androidfung.departureboard.ui.feature.settings.HelpFaqBottomSheet(
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

                    if (showRegionPickerSheet) {
                        com.androidfung.departureboard.ui.feature.settings.RegionPickerBottomSheet(
                            selectedRegion = uiState.selectedRegion,
                            detectedRegion = uiState.detectedRegion,
                            detectedCountryCode = uiState.detectedCountryCode,
                            isLocationUnsupported = uiState.isLocationUnsupported,
                            onSelectRegion = { region ->
                                showRegionPickerSheet = false
                                viewModel.setSelectedRegion(region)
                            },
                            onDismissRequest = { showRegionPickerSheet = false }
                        )
                    }

                    if (showLineStatusSheet) {
                        val relevantLineIds = remember(uiState.stationCards) {
                            uiState.stationCards.flatMap { card ->
                                card.availableLineBadges.map { it.lineId.lowercase() }
                            }.toSet()
                        }
                        com.androidfung.departureboard.ui.feature.settings.LineStatusBottomSheet(
                            lineStatuses = uiState.lineStatuses,
                            selectedRegion = uiState.selectedRegion,
                            relevantLineIds = relevantLineIds,
                            onRefresh = { viewModel.refreshLineStatuses() },
                            onDismiss = { showLineStatusSheet = false }
                        )
                    }

                    if (showSettingsSheet) {
                        com.androidfung.departureboard.ui.feature.settings.SettingsBottomSheet(
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
                            isWidgetDisruptionNotificationsEnabled = uiState.isWidgetDisruptionNotificationsEnabled,
                            onToggleWidgetDisruptionNotifications = { enabled ->
                                viewModel.setWidgetDisruptionNotificationsEnabled(enabled)
                            },
                            isBilingualEnabled = uiState.isBilingualEnabled,
                            onToggleBilingualEnabled = { enabled -> viewModel.setBilingualEnabled(enabled) },
                            isPro = uiState.isPro,
                            billingRepository = viewModel.billingRepository,
                            onOpenHelpFaq = { showHelpSheet = true },
                            onOpenDebugSettings = {
                                showDebugSheet = true
                            },
                            onUpgradeClick = {
                                showSettingsSheet = false
                                viewModel.showPaywall(getString(R.string.paywall_prompt_default))
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

                    if (showTwinConcourseSheet) {
                        com.androidfung.departureboard.ui.feature.twinconcourse.TwinConcourseBottomSheet(
                            onDismissRequest = { showTwinConcourseSheet = false },
                            stationCards = uiState.stationCards,
                            onSelectStation = { st ->
                                showTwinConcourseSheet = false
                                viewModel.openStationDetail(st)
                            },
                            onDepartureClick = { dep ->
                                showTwinConcourseSheet = false
                                viewModel.openTrainJourney(dep)
                            }
                        )
                    }
                }
            }
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
                            getString(R.string.app_update_downloaded_toast),
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
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data
        val stationId = intent?.getStringExtra(EXTRA_STATION_ID)
            ?: if ((uri != null) && (uri.scheme == "mindtheboard") && (uri.host == "station")) uri.lastPathSegment else null
        val stationName = intent?.getStringExtra(EXTRA_STATION_NAME)
            ?: uri?.getQueryParameter("name")
        val departureId = intent?.getStringExtra(EXTRA_DEPARTURE_ID)
            ?: uri?.getQueryParameter("departureId")
        initialDepartureId = departureId?.takeIf { it.isNotBlank() }

        if (!stationId.isNullOrBlank()) {
            val region = TransitRegion.fromStationId(stationId)
            // Reuse full metadata (modes/zone/lines) for known stations across all supported regions
            // so a deep-linked card shows correct branding immediately without awaiting network.
            val known = region.directory?.getStationById(stationId)
                ?: region.defaultStations.firstOrNull { it.id.equals(stationId, ignoreCase = true) }
                ?: com.androidfung.departureboard.data.model.gb.GbDefaultStations.POPULAR_STATIONS
                    .firstOrNull { it.id.equals(stationId, ignoreCase = true) }

            if (known != null) {
                initialStation = known.copy(
                    name = stationName ?: known.name,
                    isFavorite = true
                )
            } else {
                // Initial placeholder with region-appropriate defaults
                initialStation = Station(
                    id = stationId,
                    name = stationName ?: "Station",
                    modes = if (region == TransitRegion.GB) listOf("tube") else emptyList(),
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
