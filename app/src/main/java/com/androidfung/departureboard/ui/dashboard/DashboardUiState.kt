package com.androidfung.departureboard.ui.dashboard

import androidx.compose.runtime.Immutable
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.util.DepartureFilterHelper

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
     */
    fun getAvailableDirectionsForLine(lineId: String): List<String> {
        return DepartureFilterHelper.getAvailableDirections(departures, lineId)
    }

    /**
     * Filters departures for a specific line and direction.
     */
    fun getFilteredDepartures(selectedLineId: String?, selectedDirection: String?): List<Departure> {
        return DepartureFilterHelper.filterDepartures(
            departures = departures,
            lineId = selectedLineId,
            direction = selectedDirection
        )
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
    val recentSearchedStations: List<Station> = emptyList(),
    val isPro: Boolean = false,
    val userPlan: com.androidfung.departureboard.billing.UserPlan = com.androidfung.departureboard.billing.UserPlan.FREE,
    val paywallPromptReason: String? = null,
    val selectedDetailStation: Station? = null,
    val detailStationsContext: List<Station> = emptyList(),
    val detailDepartures: List<Departure> = emptyList(),
    val isDetailLoading: Boolean = false,
    val selectedJourneyDeparture: Departure? = null,
    val journeyDeparturesContext: List<Departure> = emptyList(),
    val aiModelType: com.androidfung.departureboard.ai.AiModelType = com.androidfung.departureboard.ai.AiModelType.DEFAULT,
    val departureRowsCount: Int = 5,
    val themeMode: com.androidfung.departureboard.ui.theme.AppThemeMode = com.androidfung.departureboard.ui.theme.AppThemeMode.SYSTEM,
    val isDynamicColor: Boolean = true,
    val serviceHoursInfo: com.androidfung.departureboard.data.model.StationServiceHoursInfo? = null,
    val isOnline: Boolean = true,
    val selectedRegion: com.androidfung.departureboard.data.model.TransitRegion = com.androidfung.departureboard.data.model.TransitRegion.GB,
    val isHkRegionEnabled: Boolean = false,
    val lineStatuses: Map<String, com.androidfung.departureboard.data.model.TflLineStatusItem> = emptyMap(),
    val isAutoSortByLocation: Boolean = false
)
