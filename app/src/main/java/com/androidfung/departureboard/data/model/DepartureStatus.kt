package com.androidfung.departureboard.data.model

/**
 * Standardized status for a transit departure.
 */
enum class DepartureStatus {
    ON_TIME,
    DUE,
    DELAYED,
    CANCELLED,
    UNKNOWN;

    companion object {
        fun fromString(statusText: String?, timeToStationSeconds: Int): DepartureStatus {
            if (statusText == null) {
                return if (timeToStationSeconds <= 30) DUE else ON_TIME
            }
            return when {
                statusText.equals("Cancelled", ignoreCase = true) -> CANCELLED
                statusText.contains("Delayed", ignoreCase = true) -> DELAYED
                statusText.equals("On time", ignoreCase = true) -> ON_TIME
                timeToStationSeconds <= 30 -> DUE
                else -> ON_TIME
            }
        }
    }
}
