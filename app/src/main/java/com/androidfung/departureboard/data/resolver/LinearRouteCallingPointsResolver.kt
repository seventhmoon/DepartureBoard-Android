package com.androidfung.departureboard.data.resolver

import com.androidfung.departureboard.data.model.CallingPoint
import com.androidfung.departureboard.data.model.Departure
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Base resolver that interpolates journey calling points along a linear or circular/loop station sequence.
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
     * Resolves calling points for a journey trip given origin, destination, and optional departure epoch.
     */
    fun resolveCallingPointsForTrip(
        lineCode: String,
        originStationIdentifier: String,
        destStationIdentifier: String,
        departureEpochSeconds: Long? = Instant.now().epochSecond,
        fallbackCallingPoints: List<CallingPoint> = emptyList()
    ): List<CallingPoint> {
        val cleanOrigin = originStationIdentifier.replace(Regex("\\s*\\(.*\\)"), "").trim()
        val cleanDest = destStationIdentifier.replace(Regex("\\s*\\(.*\\)"), "").trim()
        val line = lineCode.uppercase()
        val seqKey = network.resolveSequenceKey(line, cleanOrigin, cleanDest)

        val rawSeq = network.lineSequences[seqKey] ?: network.lineSequences[line] ?: return fallbackCallingPoints
        val isLoop = network.loopLineCodes.any { it.equals(line, ignoreCase = true) || it.equals(seqKey, ignoreCase = true) }
        val seq = if (isLoop && rawSeq.size > 1 && rawSeq.first().equals(rawSeq.last(), ignoreCase = true)) {
            rawSeq.dropLast(1)
        } else {
            rawSeq
        }

        val originIdx = seq.indexOfFirst {
            val resolved = network.resolveStationName(it)
            val resolvedClean = resolved.replace(Regex("\\s*\\(.*\\)"), "").trim()
            it.equals(cleanOrigin, ignoreCase = true) ||
                resolved.equals(cleanOrigin, ignoreCase = true) ||
                resolvedClean.equals(cleanOrigin, ignoreCase = true) ||
                resolved.contains(cleanOrigin, ignoreCase = true)
        }

        fun matchesDestination(codeOrName: String): Boolean {
            val resolved = network.resolveStationName(codeOrName)
            val resolvedClean = resolved.replace(Regex("\\s*\\(.*\\)"), "").trim()
            return codeOrName.equals(cleanDest, ignoreCase = true) ||
                resolved.equals(cleanDest, ignoreCase = true) ||
                resolvedClean.equals(cleanDest, ignoreCase = true) ||
                cleanDest.contains(codeOrName, ignoreCase = true) ||
                cleanDest.contains(resolved, ignoreCase = true) ||
                resolved.contains(cleanDest, ignoreCase = true)
        }

        // Prefer a downstream match (indexOfLast > originIdx) so spiral lines like London's Circle line
        // (where Edgware Road is both an intermediate stop and the final terminus) slice forward to the terminus.
        val lastDestIdx = seq.indexOfLast { matchesDestination(it) }
        val firstDestIdx = seq.indexOfFirst { matchesDestination(it) }
        val destIdx = if (!isLoop && originIdx != -1 && lastDestIdx > originIdx) {
            lastDestIdx
        } else {
            firstDestIdx
        }

        if (originIdx == -1) {
            return fallbackCallingPoints.ifEmpty {
                if (cleanOrigin.isBlank() || cleanDest.isBlank()) {
                    emptyList()
                } else {
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
        }

        val range = when {
            destIdx == -1 -> seq.subList(originIdx, seq.size)
            isLoop && originIdx != destIdx -> LoopRouteResolver.slice(seq, originIdx, destIdx)
            destIdx >= originIdx -> seq.subList(originIdx, destIdx + 1)
            else -> seq.subList(destIdx, originIdx + 1).reversed()
        }

        return range.mapIndexed { idx, stationIdentifier ->
            val clockTime = departureEpochSeconds?.let { baseEpoch ->
                val stopEpoch = baseEpoch + (idx * network.averageSecondsBetweenStops)
                Instant.ofEpochSecond(stopEpoch)
                    .atZone(network.timeZone)
                    .format(timeFormatter)
            }

            val estBadge = when {
                idx == 0 -> "Due"
                departureEpochSeconds != null -> "${(idx * network.averageSecondsBetweenStops) / 60} mins"
                else -> null
            }

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
