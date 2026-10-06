package com.androidfung.departureboard.ui.dashboard

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.Station

/**
 * UI model representing a station departure card on the dashboard.
 */
data class StationCardUiModel(
    val station: Station,
    val departures: List<Departure> = emptyList(),
    val availableLineBadges: List<LineBadgeInfo> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isExpanded: Boolean = true
)

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
    val isPro: Boolean = false,
    val paywallPromptReason: String? = null,
    val selectedDetailStation: Station? = null,
    val detailDepartures: List<Departure> = emptyList(),
    val isDetailLoading: Boolean = false,
    val aiModelType: com.androidfung.departureboard.ai.AiModelType = com.androidfung.departureboard.ai.AiModelType.LOGIC_FALLBACK
)
