package com.androidfung.departureboard.service.appfunctions

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionDeclaration
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import androidx.core.content.edit
import com.androidfung.departureboard.R
import com.androidfung.departureboard.appContainer
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.jp.JpDefaultStations
import com.androidfung.departureboard.service.TrainTrackingService
import com.androidfung.departureboard.ui.dashboard.StationCardUiModel
import com.androidfung.departureboard.util.DelayRepayReceiptLog
import com.androidfung.departureboard.util.LocationHelper
import com.androidfung.departureboard.util.TwinConcourseEvaluator
import com.androidfung.departureboard.widget.DepartureBoardWidget
import com.androidfung.departureboard.widget.DepartureBoardWidgetReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

@RequiresApi(36)
@AppFunctionServiceEntryPoint(
    serviceName = "TransitAppFunctionService",
    appFunctionXmlFileName = "transit_app_function_service"
)
abstract class BaseTransitAppFunctionService : AppFunctionService() {

    private val transitRepository by lazy {
        applicationContext.appContainer.transitRepository
    }

    private fun Departure.toSummary(): DepartureSummary {
        val plat = if (resolvedPlatform.isHistoricalExpected && !resolvedPlatform.isConfirmed) {
            "TBD (Typically Plat ${resolvedPlatform.code})"
        } else {
            platformName
        }
        return DepartureSummary(
            destination = destinationName,
            line = lineName,
            minutesToStation = timeToStationSeconds / 60,
            platform = plat,
            mode = modeName
        )
    }

    private suspend fun resolveStationOrThrow(stationName: String): Station {
        val searchResult = transitRepository.searchStations(stationName)
        return searchResult.getOrNull()?.firstOrNull()
            ?: throw AppFunctionElementNotFoundException("Could not find station: $stationName")
    }

    /**
     * Checks live train or bus times at a specific station.
     *
     * @param stationName The name of the station to check departures for (e.g. "Waterloo", "Shinjuku", "Admiralty").
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getLiveDepartures(
        stationName: String
    ): LiveDeparturesResult = withContext(Dispatchers.IO) {
        val station = resolveStationOrThrow(stationName)

        val depResult = transitRepository.getDepartures(station.id, station.name)
        val departures = depResult.getOrNull() ?: emptyList()

        if (departures.isEmpty()) {
            throw AppFunctionElementNotFoundException("No live departures currently available for ${station.name}.")
        }

        LiveDeparturesResult(
            stationName = station.name,
            departures = departures.take(10).map { it.toSummary() }
        )
    }

    /**
     * Compares two nearby or competing station concourses (e.g. "King's Cross" vs "St Pancras",
     * "Central" vs "Hong Kong") factoring in walking distance and catchability margin.
     *
     * @param firstStationName The first station hub to compare.
     * @param secondStationName The second station hub to compare.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun compareTwinConcourses(
        firstStationName: String,
        secondStationName: String
    ): TwinConcourseComparisonResult = withContext(Dispatchers.IO) {
        val stationA = resolveStationOrThrow(firstStationName)
        val stationB = resolveStationOrThrow(secondStationName)

        val userLoc = runCatching { LocationHelper.getCurrentLocation(applicationContext) }.getOrNull()
        val distA = if (userLoc != null && stationA.lat != null && stationA.lon != null) {
            LocationHelper.calculateDistanceMeters(userLoc.latitude, userLoc.longitude, stationA.lat, stationA.lon)
        } else null
        val distB = if (userLoc != null && stationB.lat != null && stationB.lon != null) {
            LocationHelper.calculateDistanceMeters(userLoc.latitude, userLoc.longitude, stationB.lat, stationB.lon)
        } else null

        val (depsA, depsB) = coroutineScope {
            val defA = async { transitRepository.getDepartures(stationA.id, stationA.name).getOrDefault(emptyList()) }
            val defB = async { transitRepository.getDepartures(stationB.id, stationB.name).getOrDefault(emptyList()) }
            defA.await() to defB.await()
        }

        val cardA = StationCardUiModel(station = stationA, departures = depsA, isLoading = false, distanceMeters = distA)
        val cardB = StationCardUiModel(station = stationB, departures = depsB, isLoading = false, distanceMeters = distB)
        val comparison = TwinConcourseEvaluator.compareHubs(cardA, cardB, maxRows = 4)

        val recommendedName = comparison.winnerCard?.station?.name ?: stationA.name
        val summary = comparison.winningDeparture?.let { win ->
            val mins = (win.departure.timeToStationSeconds / 60).coerceAtLeast(0)
            "Head to $recommendedName: ${win.departure.lineName} to ${win.departure.destinationName} in ${mins}m (${win.catchability.name.lowercase().replace('_', ' ')})."
        } ?: "No immediately catchable departures found across ${stationA.name} and ${stationB.name}."

        TwinConcourseComparisonResult(
            firstStationName = stationA.name,
            secondStationName = stationB.name,
            recommendedStationName = recommendedName,
            recommendationSummary = summary,
            firstStationDepartures = depsA.take(5).map { it.toSummary() },
            secondStationDepartures = depsB.take(5).map { it.toSummary() }
        )
    }

    /**
     * Checks live transit line disruptions, delays, suspensions, and station alerts.
     *
     * @param query Optional line name or keyword filter (leave empty for all active disruptions).
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun checkLineDisruptions(
        query: String? = null
    ): LineDisruptionsResult = withContext(Dispatchers.IO) {
        val statuses = transitRepository.getLineStatuses()
        val alerts = transitRepository.getTransitAlerts()
        val normalizedQuery = query?.trim().orEmpty()

        val items = buildList {
            for ((_, item) in statuses) {
                val disruptedStatuses = item.lineStatuses.filter { it.statusSeverity != 10 }
                if (disruptedStatuses.isEmpty()) continue
                if (normalizedQuery.isNotEmpty() &&
                    !item.name.contains(normalizedQuery, ignoreCase = true) &&
                    !item.id.contains(normalizedQuery, ignoreCase = true)
                ) {
                    continue
                }
                val top = disruptedStatuses.minByOrNull { it.statusSeverity } ?: disruptedStatuses.first()
                add(
                    DisruptionSummaryItem(
                        targetName = item.name,
                        statusSeverity = top.statusSeverityDescription,
                        reason = top.reason?.trim()?.ifEmpty { top.statusSeverityDescription } ?: top.statusSeverityDescription
                    )
                )
            }
            for (alert in alerts) {
                val affectedLines = alert.informedEntities.mapNotNull { it.lineId ?: it.routeId }
                val label = affectedLines.firstOrNull()?.uppercase() ?: alert.headerText
                val desc = alert.descriptionText.orEmpty()
                if (normalizedQuery.isNotEmpty() &&
                    !alert.headerText.contains(normalizedQuery, ignoreCase = true) &&
                    !desc.contains(normalizedQuery, ignoreCase = true) &&
                    affectedLines.none { it.contains(normalizedQuery, ignoreCase = true) }
                ) {
                    continue
                }
                add(
                    DisruptionSummaryItem(
                        targetName = label,
                        statusSeverity = alert.severity.name,
                        reason = desc.ifBlank { alert.headerText }
                    )
                )
            }
        }.distinctBy { "${it.targetName}:${it.statusSeverity}" }

        val summary = if (items.isEmpty()) {
            if (normalizedQuery.isEmpty()) {
                "Good service across all monitored transit lines."
            } else {
                "No active disruptions reported matching '$normalizedQuery'."
            }
        } else {
            "${items.size} active disruption(s) reported."
        }

        LineDisruptionsResult(
            summary = summary,
            disruptions = items.take(15)
        )
    }

    /**
     * Finds the latest available departure ("last train home") from a station, optionally filtered by destination.
     *
     * @param stationName Origin station name.
     * @param destinationName Optional destination station or direction filter.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getLastTrainHome(
        stationName: String,
        destinationName: String? = null
    ): LastTrainResult = withContext(Dispatchers.IO) {
        val station = resolveStationOrThrow(stationName)
        val departures = transitRepository.getDepartures(station.id, station.name).getOrDefault(emptyList())
            .filter { it.status != DepartureStatus.CANCELLED }

        val cleanDest = destinationName?.trim().orEmpty()
        val matching = if (cleanDest.isEmpty()) {
            departures
        } else {
            departures.filter {
                it.destinationName.contains(cleanDest, ignoreCase = true) ||
                    (it.towards?.contains(cleanDest, ignoreCase = true) == true)
            }
        }

        val latest = matching.maxByOrNull { it.timeToStationSeconds }
            ?: throw AppFunctionElementNotFoundException(
                if (cleanDest.isEmpty()) {
                    "No upcoming active departures found at ${station.name}."
                } else {
                    "No upcoming active departures found from ${station.name} towards $cleanDest."
                }
            )

        val mins = (latest.timeToStationSeconds / 60).coerceAtLeast(0)
        val isJpShuden = station.id.startsWith("JP_", ignoreCase = true) && JpDefaultStations.isShudenWindow()
        val guidance = when {
            latest.isLastTrain || isJpShuden ->
                "Final late-night window: board ${latest.lineName} to ${latest.destinationName} in ${mins}m — do not miss this service."
            mins <= 15 ->
                "Latest currently listed departure is in ${mins}m; check station boards for later scheduled services."
            else ->
                "Latest listed ${latest.lineName} service to ${latest.destinationName} departs in ${mins}m."
        }

        LastTrainResult(
            stationName = station.name,
            destination = latest.destinationName,
            line = latest.lineName,
            minutesToStation = mins,
            platform = latest.platformName,
            guidance = guidance
        )
    }

    /**
     * Retrieves saved UK National Rail Delay Repay and TfL Service Delay Refund eligibility receipts.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getSavedDelayRepayReceipts(): DelayRepayReceiptsResult = withContext(Dispatchers.IO) {
        val receipts = DelayRepayReceiptLog.loadReceipts(applicationContext)
        DelayRepayReceiptsResult(
            count = receipts.size,
            receipts = receipts.map { r ->
                DelayRepayReceiptItem(
                    dateLabel = r.dateLabel,
                    stationName = r.stationName,
                    destinationName = r.destinationName,
                    lineName = r.lineName,
                    delayMinutes = r.delayMinutes,
                    compensationSummary = r.compensationSummary,
                    claimUrl = r.claimUrl
                )
            }
        )
    }

    /**
     * Starts a live countdown notification for a specific journey.
     *
     * @param stationName The starting station name (e.g. "Waterloo").
     * @param destinationName The destination station name to track towards (e.g. "Vauxhall").
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun trackDeparture(
        stationName: String,
        destinationName: String
    ): ActionResult = withContext(Dispatchers.IO) {
        val station = resolveStationOrThrow(stationName)

        val depResult = transitRepository.getDepartures(station.id, station.name)
        val departures = depResult.getOrNull() ?: emptyList()

        // Find the first non-cancelled departure going to the requested destination
        val targetDeparture = departures.firstOrNull {
            it.status != DepartureStatus.CANCELLED && (
                it.destinationName.contains(destinationName, ignoreCase = true) ||
                    (it.towards?.contains(destinationName, ignoreCase = true) == true)
                )
        }

        if (targetDeparture == null) {
            throw AppFunctionElementNotFoundException("No upcoming departures found from ${station.name} to $destinationName.")
        }

        TrainTrackingService.startTracking(applicationContext, targetDeparture)

        ActionResult(
            success = true,
            message = "Started tracking the next ${targetDeparture.lineName} to ${targetDeparture.destinationName}. You'll see a live countdown in your notifications."
        )
    }

    /**
     * Pins a station to the user's home dashboard in the app.
     *
     * @param stationName The name of the station to pin (e.g. "King's Cross").
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun pinStation(
        stationName: String
    ): ActionResult = withContext(Dispatchers.IO) {
        val station = resolveStationOrThrow(stationName)

        transitRepository.saveStation(station)

        ActionResult(
            success = true,
            message = "Successfully pinned ${station.name} to your dashboard."
        )
    }

    /**
     * Prompts the user to add a pre-configured live departure widget to their device home screen.
     * Pass "Smart Commute" to pin an auto-switching Morning Home / Evening Work widget.
     *
     * @param stationName The name of the station to display on the widget (or "Smart Commute").
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun requestWidgetPin(
        stationName: String
    ): ActionResult {
        val trimmed = stationName.trim()
        val (resolvedId, resolvedName) = withContext(Dispatchers.IO) {
            if (trimmed.equals("smart commute", ignoreCase = true) ||
                trimmed.equals("auto", ignoreCase = true)
            ) {
                DepartureBoardWidget.SMART_COMMUTE_WIDGET_STATION_ID to
                    applicationContext.getString(R.string.widget_smart_commute_title)
            } else {
                val st = resolveStationOrThrow(trimmed)
                st.id to st.displayName
            }
        }

        withContext(Dispatchers.IO) {
            val sp = applicationContext.getSharedPreferences("widget_config", Context.MODE_PRIVATE)
            sp.edit(commit = true) {
                putString(DepartureBoardWidget.PREF_PENDING_PIN_STATION_ID, resolvedId)
                putString(DepartureBoardWidget.PREF_PENDING_PIN_STATION_NAME, resolvedName)
                putLong(DepartureBoardWidget.PREF_PENDING_PIN_TIMESTAMP_MS, System.currentTimeMillis())
            }
        }

        return withContext(Dispatchers.Main) {
            val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
            val provider = ComponentName(applicationContext, DepartureBoardWidgetReceiver::class.java)

            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                appWidgetManager.requestPinAppWidget(provider, null, null)
                ActionResult(
                    success = true,
                    message = "Requested home screen widget for $resolvedName. Confirm the system prompt to place it."
                )
            } else {
                throw AppFunctionInvalidArgumentException("Widget pinning is not supported on this device.")
            }
        }
    }
}
