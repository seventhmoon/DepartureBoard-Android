package com.androidfung.departureboard.data.resolver.gb

import com.androidfung.departureboard.data.model.CallingPoint
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.TflArrivalPrediction
import com.androidfung.departureboard.data.model.TflMatchedStop
import com.androidfung.departureboard.data.model.TflRouteSequenceResponse
import com.androidfung.departureboard.data.model.TflStationIds
import com.androidfung.departureboard.data.network.TflApiService
import com.androidfung.departureboard.data.repository.StationNameFormatter
import com.androidfung.departureboard.data.resolver.CallingPointsResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves journey calling points for Transport for London (TfL) Tube, Overground, Elizabeth Line, DLR, and Buses.
 * Handles single-branch and multi-branch network traversals and live vehicle predictions.
 */
class TflCallingPointsResolver(
    private val apiService: TflApiService,
    private val routeSequenceCache: ConcurrentHashMap<String, TflRouteSequenceResponse> = ConcurrentHashMap()
) : CallingPointsResolver {

    companion object {
        private val VIA_SPLIT_REGEX = Regex("""\s+via\s+""", RegexOption.IGNORE_CASE)
    }

    override fun canHandle(departure: Departure): Boolean {
        // Fallback resolver for TfL lines (Tube, Rail, Overground, DLR, Elizabeth line, London Buses)
        val lineId = departure.lineId.trim()
        return lineId.isNotBlank() &&
                !departure.stationId.startsWith("HK_", ignoreCase = true) &&
                !departure.lineId.startsWith("HK_", ignoreCase = true) &&
                !departure.lineId.startsWith("bods-", ignoreCase = true)
    }

    override suspend fun resolveCallingPoints(departure: Departure): List<CallingPoint> = withContext(Dispatchers.IO) {
        val lineId = departure.lineId.lowercase().trim()
        if (lineId.isBlank()) return@withContext emptyList()

        try {
            val sequenceResponse = routeSequenceCache.getOrPut(lineId) {
                apiService.getLineRouteSequence(lineId, "all")
            }

            val currentStationClean = StationNameFormatter.clean(departure.stationName).lowercase()
            val destClean = StationNameFormatter.clean(departure.destinationName).lowercase()
            // When destination contains routing like "Morden via Charing Cross" or "Morden via Bank",
            // extract the actual terminal station (e.g. "Morden") and the via routing stop (e.g. "Charing Cross" or "Bank").
            val destParts = destClean.split(VIA_SPLIT_REGEX)
            val targetTerminal = destParts[0].trim()
            val targetTerminals = targetTerminal.split("/").asSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
            val viaStation = if (destParts.size > 1) destParts[1].trim() else ""
            val originCode = departure.stationId.lowercase().trim().takeIf { it.isNotBlank() }
            val explicitDestCode = departure.destinationStationId?.lowercase()?.trim()?.takeIf {
                it.isNotBlank() && !it.equals(originCode, ignoreCase = true)
            }
            val candidateDestCodes = buildSet {
                if (explicitDestCode != null) add(explicitDestCode)
                for (target in targetTerminals) {
                    TflStationIds.CANONICAL_TERMINALS_BY_NAME[target]?.let { add(it.lowercase()) }
                }
            }

            fun stationNamesMatch(stopNameClean: String, queryClean: String): Boolean {
                if (stopNameClean.isBlank() || queryClean.isBlank()) return false
                if (stopNameClean == queryClean) return true
                // Guard against distinct stations where one name is a substring of another
                if ((stopNameClean == "edgware" || queryClean == "edgware") &&
                    (stopNameClean.contains("edgware road") || queryClean.contains("edgware road"))
                ) return false
                if ((stopNameClean == "wimbledon" || queryClean == "wimbledon") &&
                    (stopNameClean.contains("wimbledon park") || queryClean.contains("wimbledon park") ||
                     stopNameClean.contains("south wimbledon") || queryClean.contains("south wimbledon"))
                ) return false
                if ((stopNameClean == "aldgate" || queryClean == "aldgate") &&
                    (stopNameClean.contains("aldgate east") || queryClean.contains("aldgate east"))
                ) return false
                return stopNameClean.contains(queryClean) || queryClean.contains(stopNameClean)
            }

            fun matchesOrigin(stop: TflMatchedStop): Boolean {
                if (originCode != null && (stop.id.equals(originCode, ignoreCase = true) || stop.stationId?.equals(originCode, ignoreCase = true) == true)) {
                    return true
                }
                val n = StationNameFormatter.clean(stop.name).lowercase()
                return stationNamesMatch(n, currentStationClean)
            }

            fun matchesDestination(stop: TflMatchedStop): Boolean {
                if (matchesOrigin(stop)) return false
                if (candidateDestCodes.isNotEmpty()) {
                    val stopIdLower = stop.id.lowercase()
                    val stopStationIdLower = stop.stationId?.lowercase()
                    if (candidateDestCodes.contains(stopIdLower) || (stopStationIdLower != null && candidateDestCodes.contains(stopStationIdLower))) {
                        return true
                    }
                }
                val n = StationNameFormatter.clean(stop.name).lowercase()
                return targetTerminals.any { candidate -> stationNamesMatch(n, candidate) }
            }

            val branchMap = sequenceResponse.stopPointSequences.associateBy { it.branchId }

            // 1. Try finding a branch sequence that directly contains both current station and destination
            var resolvedStops: List<TflMatchedStop>? = null

            for ((_, _, _, _, stopPoint) in sequenceResponse.stopPointSequences) {
                val curIndex = stopPoint.indexOfFirst { matchesOrigin(it) }
                val destIndex = stopPoint.indexOfLast { matchesDestination(it) }
                if (curIndex != -1 && destIndex != -1 && destIndex > curIndex) {
                    resolvedStops = stopPoint.subList(curIndex, destIndex + 1)
                    break
                }
            }

            // 2. Multi-branch traversal (e.g. Northern Line, District, Piccadilly, Elizabeth Line, etc.)
            if (resolvedStops == null && branchMap.isNotEmpty()) {
                val startBranches = sequenceResponse.stopPointSequences.filter { seq ->
                    seq.stopPoint.any { matchesOrigin(it) }
                }

                fun findBranchPath(
                    currBranchId: Int,
                    visited: Set<Int>
                ): List<Int>? {
                    if (currBranchId in visited) return null
                    val branch = branchMap[currBranchId] ?: return null
                    val originIdxInBranch = branch.stopPoint.indexOfFirst { matchesOrigin(it) }
                    val destIdxInBranch = branch.stopPoint.indexOfLast { matchesDestination(it) }
                    val containsTargetAfterOrigin = destIdxInBranch != -1 && (originIdxInBranch == -1 || destIdxInBranch > originIdxInBranch)
                    if (containsTargetAfterOrigin) return listOf(currBranchId)

                    val nextVisited = visited + currBranchId
                    val candidatePaths = mutableListOf<List<Int>>()
                    for (nxt in branch.nextBranchIds) {
                        val subPath = findBranchPath(nxt, nextVisited)
                        if (subPath != null) {
                            candidatePaths.add(subPath)
                        }
                    }
                    if (candidatePaths.isEmpty()) return null

                    // If a "via" routing condition is specified, prefer the path whose branches pass through the "via" stop
                    if (viaStation.isNotBlank()) {
                        val matchingPath = candidatePaths.firstOrNull { path ->
                            path.any { bId ->
                                branchMap[bId]?.stopPoint?.any { sp ->
                                    val n = StationNameFormatter.clean(sp.name).lowercase()
                                    n.contains(viaStation) || viaStation.contains(n)
                                } == true
                            }
                        }
                        if (matchingPath != null) {
                            return listOf(currBranchId) + matchingPath
                        }
                    }

                    return listOf(currBranchId) + candidatePaths.first()
                }

                for ((branchId) in startBranches) {
                    val branchPath = findBranchPath(branchId, emptySet())
                    if (branchPath != null) {
                        val combined = mutableListOf<TflMatchedStop>()
                        for (bId in branchPath) {
                            val branch = branchMap[bId] ?: continue
                            for (sp in branch.stopPoint) {
                                if (combined.isEmpty() || combined.last().id != sp.id) {
                                    combined.add(sp)
                                }
                            }
                        }
                        val curIdx = combined.indexOfFirst { matchesOrigin(it) }
                        val destIdx = combined.indexOfLast { matchesDestination(it) }

                        val sIdx = if (curIdx != -1) curIdx else 0
                        val eIdx = if (destIdx != -1 && destIdx > sIdx) destIdx else (combined.size - 1)
                        if (eIdx > sIdx) {
                            resolvedStops = combined.subList(sIdx, eIdx + 1)
                            break
                        }
                    }
                }
            }

            if (!resolvedStops.isNullOrEmpty()) {
                // If we have a vehicleId, query live Line Arrivals across the route to get exact prediction times
                val vehicleArrivalsByStation = mutableMapOf<String, TflArrivalPrediction>()
                val vehicleId = departure.vehicleId
                if (!vehicleId.isNullOrBlank()) {
                    try {
                        val lineArrivals = apiService.getLineArrivals(lineId)
                        // If destination station code is known, filter predictions to ensure they are on this trip
                        lineArrivals.filter { pred ->
                            val matchesTripDest = candidateDestCodes.isEmpty() ||
                                pred.destinationNaptanId == null ||
                                candidateDestCodes.contains(pred.destinationNaptanId.lowercase()) ||
                                (pred.naptanId != null && candidateDestCodes.contains(pred.naptanId.lowercase()))
                            pred.vehicleId == vehicleId && matchesTripDest
                        }.forEach { pred ->
                            val naptan = pred.naptanId?.lowercase()
                            val stName = pred.stationName?.let { StationNameFormatter.clean(it).lowercase() }
                            if (!naptan.isNullOrBlank()) vehicleArrivalsByStation[naptan] = pred
                            if (!stName.isNullOrBlank()) vehicleArrivalsByStation[stName] = pred
                        }
                    } catch (_: Exception) {}
                }

                val baseSeconds = departure.timeToStationSeconds
                val nowMillis = System.currentTimeMillis()
                val isBus = departure.modeName.equals("bus", ignoreCase = true)
                val interStationSeconds = if (isBus) 90 else 120 // 1.5 min per bus stop, 2 min per tube stop
                val londonZone = ZoneId.of("Europe/London")
                val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)
                    .withZone(londonZone)

                var previousArrivalSeconds = baseSeconds

                return@withContext resolvedStops.mapIndexed { idx, stop ->
                    val cleanName = StationNameFormatter.clean(stop.name)
                    val stopKey = cleanName.lowercase()
                    val naptanKey = stop.id.lowercase()

                    // Check if there is a live prediction for this exact vehicle at this calling point
                    val livePrediction = vehicleArrivalsByStation[naptanKey] ?: vehicleArrivalsByStation[stopKey]

                    val isDownstreamPredictionValid = idx > 0 && livePrediction != null &&
                            livePrediction.timeToStation > departure.timeToStationSeconds

                    val arrivalSeconds: Int = when {
                        idx == 0 -> baseSeconds
                        isDownstreamPredictionValid -> {
                            val predSecs = livePrediction.timeToStation
                            if (predSecs > previousArrivalSeconds) predSecs else (previousArrivalSeconds + interStationSeconds)
                        }
                        else -> previousArrivalSeconds + interStationSeconds
                    }

                    if (idx > 0) {
                        previousArrivalSeconds = arrivalSeconds
                    }

                    val formattedTime: String? = when {
                        idx == 0 -> departure.formattedActualClockTime ?: run {
                            if (baseSeconds > 0) {
                                val arrivalMillis = nowMillis + (baseSeconds * 1000L)
                                timeFormatter.format(Instant.ofEpochMilli(arrivalMillis))
                            } else null
                        }
                        arrivalSeconds > 0 -> {
                            val arrivalMillis = nowMillis + (arrivalSeconds * 1000L)
                            timeFormatter.format(Instant.ofEpochMilli(arrivalMillis))
                        }
                        else -> null
                    }

                    val estimatedBadge: String? = when {
                        idx == 0 -> departure.formattedTimeToArrival
                        arrivalSeconds > 0 -> {
                            val mins = (arrivalSeconds / 60).coerceAtLeast(1)
                            "$mins min${if (mins > 1) "s" else ""}"
                        }
                        else -> null
                    }

                    CallingPoint(
                        stationName = cleanName,
                        scheduledTime = formattedTime,
                        estimatedTime = estimatedBadge,
                        isCurrentStation = idx == 0,
                        isDestination = idx == resolvedStops.lastIndex,
                        stationId = stop.stationId ?: stop.id,
                        modes = listOf(departure.modeName)
                    )
                }
            }
        } catch (_: Exception) {
            // Fall through to fallback
        }

        // Minimal fallback: [Current Station] -> [Destination Station]
        listOf(
            CallingPoint(
                stationName = departure.stationName,
                scheduledTime = departure.formattedActualClockTime,
                estimatedTime = departure.formattedTimeToArrival,
                isCurrentStation = true,
                isDestination = false,
                stationId = departure.stationId,
                modes = listOf(departure.modeName)
            ),
            CallingPoint(
                stationName = departure.destinationName,
                isCurrentStation = false,
                isDestination = true,
                stationId = departure.destinationStationId,
                modes = listOf(departure.modeName)
            )
        )
    }
}
