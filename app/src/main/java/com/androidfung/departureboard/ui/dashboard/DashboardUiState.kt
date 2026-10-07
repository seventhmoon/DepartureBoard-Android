package com.androidfung.departureboard.ui.dashboard

import androidx.compose.runtime.Immutable
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.ui.components.TransitIconHelper

/**
 * UI model representing a station departure card on the dashboard.
 */
@Immutable
data class StationCardUiModel(
    val station: Station,
    val departures: List<Departure> = emptyList(),
    val availableLineBadges: List<LineBadgeInfo> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isExpanded: Boolean = true
) {
    /**
     * Identifies available directions for a specific transit line serving this station.
     * This moves logic (such as inferring "Northbound" from "Inbound" on the Northern Line
     * or extracting direction from platform names) out of the Compose View layer.
     */
    fun getAvailableDirectionsForLine(lineId: String): List<String> {
        val lineDepartures = departures.filter { TransitIconHelper.isSameLine(it.lineId, lineId) }
        return lineDepartures
            .asSequence()
            .mapNotNull {
                it.direction ?: listOf("Eastbound", "Westbound", "Northbound", "Southbound", "Inbound", "Outbound")
                    .firstOrNull { d -> it.platformName.contains(d, ignoreCase = true) }
            }
            .distinct()
            .map {
                if (it.equals("Inbound", ignoreCase = true) && lineId.contains("northern", ignoreCase = true)) "Northbound"
                else if (it.equals("Outbound", ignoreCase = true) && lineId.contains("northern", ignoreCase = true)) "Southbound"
                else it
            }
            .distinct()
            .toList()
    }

    /**
     * Filters departures for a specific line and direction.
     */
    fun getFilteredDepartures(selectedLineId: String?, selectedDirection: String?): List<Departure> {
        if (selectedLineId == null) {
            return departures
        }
        return departures.filter { departure ->
            val lineMatches = TransitIconHelper.isSameLine(departure.lineId, selectedLineId)
            if (!lineMatches) return@filter false
            selectedDirection == null || departure.direction.equals(selectedDirection, ignoreCase = true) ||
                    departure.platformName.contains(selectedDirection, ignoreCase = true)
        }
    }
}

/**
 * Screen state for the Departure Board Dashboard.
 */
@Immutable
data class DashboardUiState(
    val stationCards: List<StationCardUiModel> = emptyList(),
    val isRefreshing: Boolean = false,
    val isInitialLoading: Boolean = true,
    val lastUpdatedTimestamp: Long = System.currentTimeMillis(),
    val userMessage: String? = null,
    val nearestStationId: String? = null,
    val nearestStationDistanceMeters: Double? = null,
    val nearestStation: Station? = null,
    val nearbyStations: List<Pair<Station, Double>> = emptyList(),
    val isPro: Boolean = false,
    val paywallPromptReason: String? = null,
    val selectedDetailStation: Station? = null,
    val detailDepartures: List<Departure> = emptyList(),
    val isDetailLoading: Boolean = false,
    val aiModelType: com.androidfung.departureboard.ai.AiModelType = com.androidfung.departureboard.ai.AiModelType.LOGIC_FALLBACK
)
