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

    private val HH_MM_REGEX = Regex("""^([01]?\d|2[0-3]):([0-5]\d)$""")

    /**
     * Filters departures from an origin station that serve [targetStationName] and computes
     * corridor telemetry (speed type, stops count, travel duration).
     */
    fun evaluateCorridorDepartures(
        departures: List<Departure>,
        targetStationName: String,
        nowLocalTime: java.time.LocalTime? = null
    ): List<CorridorDepartureItem> {
        val cleanTarget = targetStationName.trim().lowercase()
        if (cleanTarget.isBlank()) return emptyList()

        val matchedItems = mutableListOf<CorridorDepartureItem>()

        for (dep in departures) {
            if (dep.status == DepartureStatus.CANCELLED) continue

            val isTerminus = dep.destinationName.lowercase().contains(cleanTarget)
            val matchedCallingPointIndex = dep.callingPoints.indexOfFirst { cp ->
                !cp.isCurrentStation && cp.stationName.lowercase().contains(cleanTarget)
            }

            if (!isTerminus && matchedCallingPointIndex < 0) continue

            val hasOriginAtZero = dep.callingPoints.firstOrNull()?.isCurrentStation == true
            val hasLoadedCallingPoints = dep.callingPoints.isNotEmpty()
            val stopsCount = when {
                matchedCallingPointIndex >= 0 -> {
                    val rawCount = if (hasOriginAtZero) matchedCallingPointIndex else matchedCallingPointIndex + 1
                    rawCount.coerceAtLeast(1)
                }
                isTerminus && hasLoadedCallingPoints -> {
                    val rawCount = if (hasOriginAtZero) dep.callingPoints.size - 1 else dep.callingPoints.size
                    rawCount.coerceAtLeast(1)
                }
                else -> 0 // Calling points not yet loaded; avoid misclassifying as 1-stop Express
            }

            val targetArrivalTime = if (matchedCallingPointIndex >= 0) {
                val cp = dep.callingPoints[matchedCallingPointIndex]
                val est = cp.estimatedTime?.takeIf { HH_MM_REGEX.matches(it.trim()) }
                est ?: cp.scheduledTime?.takeIf { HH_MM_REGEX.matches(it.trim()) }
            } else {
                dep.expectedArrivalIso?.substringAfter("T")?.take(5)?.takeIf { HH_MM_REGEX.matches(it) }
            }

            val regionZone = com.androidfung.departureboard.data.model.TransitRegion.fromStationId(dep.stationId).timeZone
            val referenceNow = nowLocalTime ?: java.time.LocalTime.now(regionZone)
            val waitMinutes = (dep.timeToStationSeconds / 60).coerceAtLeast(0)

            // Prefer actual arrival clock time when available; otherwise fall back to stop-count heuristic
            val travelMinutes = computeMinutesUntilClockTime(referenceNow, targetArrivalTime, minMinutes = waitMinutes + 1)
                ?: if (stopsCount > 0) (stopsCount * 3 + waitMinutes) else null

            val serviceType = when {
                !hasLoadedCallingPoints && matchedCallingPointIndex < 0 -> CorridorServiceType.SEMI_FAST
                stopsCount <= 3 -> CorridorServiceType.EXPRESS
                stopsCount <= 7 -> CorridorServiceType.SEMI_FAST
                else -> CorridorServiceType.ALL_STATIONS
            }

            matchedItems.add(
                CorridorDepartureItem(
                    departure = dep,
                    targetStationName = targetStationName,
                    departureTimeMinutes = waitMinutes,
                    targetArrivalTime = targetArrivalTime,
                    stopsCountToDestination = stopsCount,
                    estimatedTravelMinutes = travelMinutes,
                    serviceType = serviceType
                )
            )
        }

        return matchedItems.sortedWith(
            compareBy<CorridorDepartureItem> { it.estimatedTravelMinutes ?: Int.MAX_VALUE }
                .thenBy { it.departureTimeMinutes }
        )
    }

    private fun computeMinutesUntilClockTime(
        now: java.time.LocalTime,
        hhMm: String?,
        minMinutes: Int
    ): Int? {
        if (hhMm.isNullOrBlank()) return null
        val match = HH_MM_REGEX.matchEntire(hhMm.trim()) ?: return null
        val h = match.groupValues[1].toIntOrNull() ?: return null
        val m = match.groupValues[2].toIntOrNull() ?: return null
        val nowMins = now.hour * 60 + now.minute
        var diff = (h * 60 + m) - nowMins
        if (diff < -180) {
            diff += 24 * 60 // Next-day arrival after midnight
        }
        return if (diff >= minMinutes) diff else null
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
