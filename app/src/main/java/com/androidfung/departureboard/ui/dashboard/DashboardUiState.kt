package com.androidfung.departureboard.ui.dashboard

import com.androidfung.departureboard.data.model.DataFreshness
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.util.DepartureFilterHelper

/**
 * UI model representing a station departure card on the dashboard.
 */
data class StationCardUiModel(
    val station: Station,
    val departures: List<Departure> = emptyList(),
    val availableLineBadges: List<LineBadgeInfo> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isExpanded: Boolean = true,
    val distanceMeters: Double? = null,
    val isAtConcourseStation: Boolean = false,
    val concourseBadgeLabel: String? = null,
    val serviceHoursInfo: com.androidfung.departureboard.data.model.StationServiceHoursInfo? = null
) {
    val dataFreshness: DataFreshness
        get() = when {
            departures.isEmpty() -> DataFreshness.LIVE
            departures.any { it.dataFreshness == DataFreshness.OFFLINE_SCHEDULED } -> DataFreshness.OFFLINE_SCHEDULED
            departures.any { it.dataFreshness == DataFreshness.CACHED_LIVE } -> DataFreshness.CACHED_LIVE
            else -> DataFreshness.LIVE
        }
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
    val detailAccessibilityAlerts: List<com.androidfung.departureboard.data.model.TransitAlert> = emptyList(),
    val isDetailLoading: Boolean = false,
    val selectedJourneyDeparture: Departure? = null,
    val journeyDeparturesContext: List<Departure> = emptyList(),
    val isStationDetailAboveJourney: Boolean = false,
    val aiModelType: com.androidfung.departureboard.ai.AiModelType = com.androidfung.departureboard.ai.AiModelType.DEFAULT,
    val departureRowsCount: Int = 5,
    val themeMode: com.androidfung.departureboard.ui.theme.AppThemeMode = com.androidfung.departureboard.ui.theme.AppThemeMode.SYSTEM,
    val isDynamicColor: Boolean = true,
    val serviceHoursInfo: com.androidfung.departureboard.data.model.gb.StationServiceHoursInfo? = null,
    val isOnline: Boolean = true,
    val selectedRegion: com.androidfung.departureboard.data.model.TransitRegion = com.androidfung.departureboard.data.model.TransitRegion.GB,
    val detectedRegion: com.androidfung.departureboard.data.model.TransitRegion? = null,
    val detectedCountryCode: String? = null,
    val isLocationUnsupported: Boolean = false,
    val isHkRegionEnabled: Boolean = false,
    val isBilingualEnabled: Boolean = true,
    val isBikeshareEnabled: Boolean = false,
    val lineStatuses: Map<String, com.androidfung.departureboard.data.model.gb.TflLineStatusItem> = emptyMap(),
    val isAutoSortByLocation: Boolean = false,
    val isWidgetDisruptionNotificationsEnabled: Boolean = true,
    val isSmartCommuteEnabled: Boolean = false,
    val deskClockStation: Station? = null,
    val concourseStationId: String? = null,
    val isConcourseSprintActive: Boolean = false,
    val concoursePollIntervalMs: Long = 60_000L
)
