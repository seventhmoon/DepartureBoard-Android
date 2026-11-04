package com.androidfung.departureboard.data.resolver

import com.androidfung.departureboard.data.model.CallingPoint
import com.androidfung.departureboard.data.model.Departure
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Base resolver that interpolates journey calling points along a linear station sequence.
 */
abstract class LinearRouteCallingPointsResolver(
    private val network: TransitLineNetwork
) : CallingPointsResolver {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    override suspend fun resolveCallingPoints(departure: Departure): List<CallingPoint> {
        return resolveCallingPointsForTrip(
            lineCode = departure.lineId,
            originStationIdentifier = departure.stationName,
            destStationIdentifier = departure.destinationName,
            departureEpochSeconds = Instant.now().epochSecond + departure.timeToStationSeconds,
            fallbackCallingPoints = departure.callingPoints
        )
    }

    /**
     * Resolves calling points for a journey trip given origin, destination, and departure epoch.
     */
    fun resolveCallingPointsForTrip(
        lineCode: String,
        originStationIdentifier: String,
        destStationIdentifier: String,
        departureEpochSeconds: Long = Instant.now().epochSecond,
        fallbackCallingPoints: List<CallingPoint> = emptyList()
    ): List<CallingPoint> {
        val cleanOrigin = originStationIdentifier.replace(Regex("\\s*\\(.*\\)"), "").trim()
        val cleanDest = destStationIdentifier.replace(Regex("\\s*\\(.*\\)"), "").trim()
        val line = lineCode.uppercase()

        val seq = network.lineSequences[line] ?: return fallbackCallingPoints

        val originIdx = seq.indexOfFirst { 
            val resolved = network.resolveStationName(it)
            it.equals(cleanOrigin, ignoreCase = true) || resolved.equals(cleanOrigin, ignoreCase = true)
        }

        val destIdx = seq.indexOfFirst {
            val resolved = network.resolveStationName(it)
            it.equals(cleanDest, ignoreCase = true) ||
                    resolved.equals(cleanDest, ignoreCase = true) ||
                    cleanDest.contains(it, ignoreCase = true) ||
                    cleanDest.contains(resolved, ignoreCase = true)
        }

        if (originIdx == -1) {
            return fallbackCallingPoints.ifEmpty {
                listOf(
                    CallingPoint(
                        stationName = cleanOrigin,
                        scheduledTime = null,
                        estimatedTime = "Dep",
                        isCurrentStation = true
                    ),
                    CallingPoint(
                        stationName = cleanDest,
                        scheduledTime = null,
                        estimatedTime = null,
                        isDestination = true
                    )
                )
            }
        }

        val range = when {
            destIdx == -1 -> seq.subList(originIdx, seq.size)
            destIdx >= originIdx -> seq.subList(originIdx, destIdx + 1)
            else -> seq.subList(destIdx, originIdx + 1).reversed()
        }

        return range.mapIndexed { idx, stationIdentifier ->
            val stopEpoch = departureEpochSeconds + (idx * network.averageSecondsBetweenStops)
            val clockTime = Instant.ofEpochSecond(stopEpoch)
                .atZone(network.timeZone)
                .format(timeFormatter)

            val estBadge = if (idx == 0) "Due" else "${(idx * network.averageSecondsBetweenStops) / 60} mins"

            CallingPoint(
                stationName = network.resolveStationName(stationIdentifier),
                scheduledTime = clockTime,
                estimatedTime = estBadge,
                isCurrentStation = idx == 0,
                isDestination = idx == range.lastIndex,
                stationId = network.resolveStationId(line, stationIdentifier),
                modes = listOf(network.defaultMode)
            )
        }
    }
}
