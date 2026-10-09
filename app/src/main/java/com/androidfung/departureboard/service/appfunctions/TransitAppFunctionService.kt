package com.androidfung.departureboard.service.appfunctions

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionDeclaration
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import com.androidfung.departureboard.data.repository.TransitRepositoryImpl
import com.androidfung.departureboard.service.TrainTrackingService
import com.androidfung.departureboard.widget.DepartureBoardWidgetReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@RequiresApi(36)
@AppFunctionServiceEntryPoint(
    serviceName = "TransitAppFunctionService",
    appFunctionXmlFileName = "transit_app_function_service"
)
abstract class BaseTransitAppFunctionService : AppFunctionService() {

    private val transitRepository by lazy {
        TransitRepositoryImpl(applicationContext)
    }

    /**
     * Checks live train or bus times at a specific station.
     *
     * @param stationName The name of the station to check departures for (e.g. "Waterloo", "Victoria").
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getLiveDepartures(
        stationName: String
    ): LiveDeparturesResult = withContext(Dispatchers.IO) {
        val searchResult = transitRepository.searchStations(stationName)
        val station = searchResult.getOrNull()?.firstOrNull()
            ?: throw AppFunctionElementNotFoundException("Could not find station: $stationName")

        val depResult = transitRepository.getDepartures(station.id, station.name)
        val departures = depResult.getOrNull() ?: emptyList()

        if (departures.isEmpty()) {
            throw AppFunctionElementNotFoundException("No live departures currently available for ${station.name}.")
        }

        val summaries = departures.take(10).map { dep ->
            DepartureSummary(
                destination = dep.destinationName,
                line = dep.lineName,
                minutesToStation = dep.timeToStationSeconds / 60,
                platform = dep.platformName,
                mode = dep.modeName
            )
        }

        LiveDeparturesResult(
            stationName = station.name,
            departures = summaries
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
        val searchResult = transitRepository.searchStations(stationName)
        val station = searchResult.getOrNull()?.firstOrNull()
            ?: throw AppFunctionElementNotFoundException("Could not find station: $stationName")

        val depResult = transitRepository.getDepartures(station.id, station.name)
        val departures = depResult.getOrNull() ?: emptyList()

        // Find the first departure going to the requested destination
        val targetDeparture = departures.firstOrNull { 
            it.destinationName.contains(destinationName, ignoreCase = true) ||
            (it.towards?.contains(destinationName, ignoreCase = true) == true)
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
        val searchResult = transitRepository.searchStations(stationName)
        val station = searchResult.getOrNull()?.firstOrNull()
            ?: throw AppFunctionElementNotFoundException("Could not find station: $stationName")

        transitRepository.saveStation(station)

        ActionResult(
            success = true,
            message = "Successfully pinned ${station.name} to your dashboard."
        )
    }

    /**
     * Prompts the user to add a live departure widget to their device home screen.
     *
     * @param stationName The name of the station to display on the widget.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun requestWidgetPin(
        stationName: String
    ): ActionResult = withContext(Dispatchers.Main) {
        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        val provider = ComponentName(applicationContext, DepartureBoardWidgetReceiver::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appWidgetManager.isRequestPinAppWidgetSupported) {
            // Note: In a real app we'd pass the station details via intent extras to the configure activity
            appWidgetManager.requestPinAppWidget(provider, null, null)
            ActionResult(
                success = true,
                message = "Requested widget pin. Please confirm the system dialog to add it to your home screen."
            )
        } else {
            throw AppFunctionInvalidArgumentException("Widget pinning is not supported on this device.")
        }
    }
}