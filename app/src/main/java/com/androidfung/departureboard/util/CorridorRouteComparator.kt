package com.androidfung.departureboard.util

import com.androidfung.departureboard.data.model.CallingPoint
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus

/**
 * Service speed category on a corridor.
 */
enum class CorridorServiceType {
    EXPRESS,     // Limited stops / Fast train
    SEMI_FAST,   // Moderate stops
    ALL_STATIONS // Local stopper
}

/**
 * Departure telemetry on a specific corridor from Origin to Destination.
 */
data class CorridorDepartureItem(
    val departure: Departure,
    val targetStationName: String,
    val departureTimeMinutes: Int,
    val targetArrivalTime: String?,
    val stopsCountToDestination: Int,
    val estimatedTravelMinutes: Int?,
    val serviceType: CorridorServiceType
)

/**
 * Alert recommending waiting for a faster train that overtakes a local stopper.
 */
data class FasterDepartureAlert(
    val stopperDeparture: Departure,
    val expressDeparture: Departure,
    val waitExtraMinutes: Int,
    val minutesSavedAtDestination: Int,
    val recommendationMessage: String
)

/**
 * Analyzes live departure boards and downstream calling points between an Origin and Destination
 * to compare service speeds (Express vs. Local Stopper) and detect train overtakes.
 */
object CorridorRouteComparator {

    /**
     * Filters departures from an origin station that serve [targetStationName] and computes
     * corridor telemetry (speed type, stops count, travel duration).
     */
    fun evaluateCorridorDepartures(
        departures: List<Departure>,
        targetStationName: String
    ): List<CorridorDepartureItem> {
        val cleanTarget = targetStationName.trim().lowercase()
        if (cleanTarget.isBlank()) return emptyList()

        val matchedItems = mutableListOf<CorridorDepartureItem>()

        for (dep in departures) {
            if (dep.status == DepartureStatus.CANCELLED) continue

            val isTerminus = dep.destinationName.lowercase().contains(cleanTarget)
            val matchedCallingPointIndex = dep.callingPoints.indexOfFirst { cp ->
                cp.stationName.lowercase().contains(cleanTarget)
            }

            if (!isTerminus && matchedCallingPointIndex < 0) continue

            val stopsCount = if (isTerminus) {
                if (dep.callingPoints.isNotEmpty()) dep.callingPoints.size else 1
            } else {
                matchedCallingPointIndex + 1
            }

            val targetArrivalTime = if (!isTerminus && matchedCallingPointIndex >= 0) {
                val cp = dep.callingPoints[matchedCallingPointIndex]
                cp.estimatedTime ?: cp.scheduledTime
            } else {
                dep.expectedArrivalIso?.substringAfter("T")?.take(5)
            }

            // Estimate travel time: roughly 2-3 mins per stop if not explicitly scheduled
            val travelMinutes = stopsCount * 3 + (dep.timeToStationSeconds / 60)

            val serviceType = when {
                stopsCount <= 3 -> CorridorServiceType.EXPRESS
                stopsCount <= 7 -> CorridorServiceType.SEMI_FAST
                else -> CorridorServiceType.ALL_STATIONS
            }

            matchedItems.add(
                CorridorDepartureItem(
                    departure = dep,
                    targetStationName = targetStationName,
                    departureTimeMinutes = dep.timeToStationSeconds / 60,
                    targetArrivalTime = targetArrivalTime,
                    stopsCountToDestination = stopsCount,
                    estimatedTravelMinutes = travelMinutes,
                    serviceType = serviceType
                )
            )
        }

        return matchedItems.sortedBy { it.estimatedTravelMinutes ?: Int.MAX_VALUE }
    }

    /**
     * Checks if a later departure arrives sooner than an earlier departure (Overtake).
     */
    fun detectFasterOvertake(items: List<CorridorDepartureItem>): FasterDepartureAlert? {
        if (items.size < 2) return null

        val sortedByDeparture = items.sortedBy { it.departureTimeMinutes }

        for (i in 0 until sortedByDeparture.lastIndex) {
            val earlier = sortedByDeparture[i]
            for (j in (i + 1)..sortedByDeparture.lastIndex) {
                val later = sortedByDeparture[j]

                val earlierTravel = earlier.estimatedTravelMinutes ?: continue
                val laterTravel = later.estimatedTravelMinutes ?: continue

                // estimatedTravelMinutes already includes wait time until departure (stopsCount * 3 + dep.timeToStationSeconds / 60)
                val earlierTotal = earlierTravel
                val laterTotal = laterTravel

                if (laterTotal < earlierTotal) {
                    val waitExtra = later.departureTimeMinutes - earlier.departureTimeMinutes
                    val minutesSaved = earlierTotal - laterTotal
                    return FasterDepartureAlert(
                        stopperDeparture = earlier.departure,
                        expressDeparture = later.departure,
                        waitExtraMinutes = waitExtra,
                        minutesSavedAtDestination = minutesSaved,
                        recommendationMessage = "Wait ${waitExtra}m for the ${later.departure.lineName} (${later.serviceType.name.lowercase().replace('_', ' ')}): arrives ${minutesSaved}m earlier at ${later.targetStationName}!"
                    )
                }
            }
        }

        return null
    }
}
