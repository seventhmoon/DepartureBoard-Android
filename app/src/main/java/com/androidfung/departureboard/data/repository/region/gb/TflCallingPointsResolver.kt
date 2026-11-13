package com.androidfung.departureboard.data.repository.region.gb

import com.androidfung.departureboard.data.model.CallingPoint
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.isAuTransitId
import com.androidfung.departureboard.data.model.isBods
import com.androidfung.departureboard.data.model.isHkDeparture
import com.androidfung.departureboard.data.model.isJpTransitId
import com.androidfung.departureboard.data.model.isSgTransitId
import com.androidfung.departureboard.data.model.gb.TflArrivalPrediction
import com.androidfung.departureboard.data.model.gb.TflMatchedStop
import com.androidfung.departureboard.data.model.gb.TflRouteSequenceResponse
import com.androidfung.departureboard.data.model.gb.TflStationIds
import com.androidfung.departureboard.data.network.gb.TflApiService
import com.androidfung.departureboard.data.topology.CallingPointsResolver
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        if (lineId.isBlank()) return false

        // Exclude non-TfL feeds (BODS) and non-UK regions
        if (departure.isBods || departure.isHkDeparture) return false
        val stationId = departure.stationId
        if (stationId.isJpTransitId || stationId.isSgTransitId || stationId.isAuTransitId) {
            return false
        }

        return true
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
            val candidateOriginCodes = buildSet {
                if (originCode != null) {
                    add(originCode)
                    for (pid in TflStationIds.resolvePlatformNaptanIds(originCode)) {
                        add(pid.lowercase())
                    }
                }
                TflStationIds.CANONICAL_TERMINALS_BY_NAME[currentStationClean]?.let { add(it.lowercase()) }
            }
            val candidateDestCodes = buildSet {
                if (explicitDestCode != null) {
                    add(explicitDestCode)
                    for (pid in TflStationIds.resolvePlatformNaptanIds(explicitDestCode)) {
                        add(pid.lowercase())
                    }
                }
                for (target in targetTerminals) {
                    TflStationIds.CANONICAL_TERMINALS_BY_NAME[target]?.let { add(it.lowercase()) }
                }
            }
            val candidateViaCodes = buildSet {
                if (viaStation.isNotBlank()) {
                    TflStationIds.CANONICAL_TERMINALS_BY_NAME[viaStation]?.let { add(it.lowercase()) }
                }
            }

            fun stationNamesMatch(stopNameClean: String, queryClean: String): Boolean {
                if (stopNameClean.isBlank() || queryClean.isBlank()) return false
                if (stopNameClean == queryClean) return true
                // Guard against distinct stations where one name is a substring of another
                if (StationNameFormatter.isConflictingStationSubstring(stopNameClean, queryClean)) {
                    return false
                }
                return stopNameClean.contains(queryClean) || queryClean.contains(stopNameClean)
            }

            fun matchesOrigin(stop: TflMatchedStop): Boolean {
                if (candidateOriginCodes.isNotEmpty()) {
                    val stopIdLower = stop.id.lowercase()
                    val stopStationIdLower = stop.stationId?.lowercase()
                    if (candidateOriginCodes.contains(stopIdLower) || (stopStationIdLower != null && candidateOriginCodes.contains(stopStationIdLower))) {
                        return true
                    }
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

            fun matchesVia(stop: TflMatchedStop): Boolean {
                if (candidateViaCodes.isNotEmpty()) {
                    val stopIdLower = stop.id.lowercase()
                    val stopStationIdLower = stop.stationId?.lowercase()
                    if (candidateViaCodes.contains(stopIdLower) || (stopStationIdLower != null && candidateViaCodes.contains(stopStationIdLower))) {
                        return true
                    }
                }
                if (viaStation.isBlank()) return false
                val n = StationNameFormatter.clean(stop.name).lowercase()
                return stationNamesMatch(n, viaStation)
            }

            val branchMap = sequenceResponse.stopPointSequences.associateBy { it.branchId }

            // Direction matching logic:
            // 1) Direct check against TfL sequence direction (inbound / outbound)
            // 2) Geographic check when cardinal direction is specified (Eastbound/Westbound via lon diff, Northbound/Southbound via lat diff)
            val depDirLower = departure.direction?.lowercase()?.trim()

            fun sequenceMatchesCardinalDirection(seqStops: List<TflMatchedStop>, originIdx: Int): Boolean? {
                if (depDirLower.isNullOrBlank() || originIdx < 0 || originIdx + 1 >= seqStops.size) return null
                val curStop = seqStops[originIdx]
                val nextStop = seqStops[originIdx + 1]
                val curLat = curStop.lat
                val curLon = curStop.lon
                val nextLat = nextStop.lat
                val nextLon = nextStop.lon

                val isEastWest = depDirLower.contains("eastbound") || depDirLower.contains("westbound")
                val isNorthSouth = depDirLower.contains("northbound") || depDirLower.contains("southbound")

                if (isEastWest) {
                    if (curLon != null && nextLon != null) {
                        val lonDiff = nextLon - curLon
                        return if (depDirLower.contains("eastbound")) lonDiff > 0.0001 else lonDiff < -0.0001
                    }
                }

                if (isNorthSouth) {
                    if (curLat != null && nextLat != null) {
                        val latDiff = nextLat - curLat
                        return if (depDirLower.contains("northbound")) latDiff > 0.0001 else latDiff < -0.0001
                    }
                }

                return null
            }

            fun directionMatches(branchDirection: String?): Boolean {
                if (depDirLower.isNullOrBlank() || branchDirection.isNullOrBlank()) return true
                val branchDirLower = branchDirection.lowercase().trim()
                return branchDirLower == depDirLower
            }

            // 1. Try finding a branch sequence that directly contains both current station and destination.
            // If multiple branches match (e.g. Circle line loop or multi-branch overlaps), prioritize the one
            // moving in the correct cardinal/travel direction from the origin station.
            data class SingleBranchCandidate(
                val stops: List<TflMatchedStop>,
                val matchesCardinal: Boolean?,
                val matchesTflDir: Boolean,
                val matchesVia: Boolean
            )

            val singleBranchMatches = mutableListOf<SingleBranchCandidate>()

            for (seq in sequenceResponse.stopPointSequences) {
                val stopPoint = seq.stopPoint
                val curIndex = stopPoint.indexOfFirst { matchesOrigin(it) }
                val destIndex = stopPoint.indexOfLast { matchesDestination(it) }
                if (curIndex != -1 && destIndex != -1 && destIndex > curIndex) {
                    val candidateStops = stopPoint.subList(curIndex, destIndex + 1)
                    val cardinalMatch = sequenceMatchesCardinalDirection(stopPoint, curIndex)
                    val tflDirMatch = directionMatches(seq.direction)
                    val viaMatch = if (viaStation.isNotBlank()) {
                        candidateStops.any { matchesVia(it) }
                    } else {
                        true
                    }
                    singleBranchMatches.add(
                        SingleBranchCandidate(
                            stops = candidateStops,
                            matchesCardinal = cardinalMatch,
                            matchesTflDir = tflDirMatch,
                            matchesVia = viaMatch
                        )
                    )
                }
            }

            var resolvedStops: List<TflMatchedStop>? = singleBranchMatches
                .sortedWith(
                    compareByDescending<SingleBranchCandidate> { it.matchesVia }
                        .thenByDescending { it.matchesCardinal == true }
                        .thenBy { it.matchesCardinal == false } // false is penalized after null
                        .thenByDescending { it.matchesTflDir }
                )
                .firstOrNull()?.stops

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
                                branchMap[bId]?.stopPoint?.any { sp -> matchesVia(sp) } == true
                            }
                        }
                        if (matchingPath != null) {
                            return listOf(currBranchId) + matchingPath
                        }
                    }

                    // Prefer paths matching departure direction if known
                    val matchingPathByDirection = candidatePaths.firstOrNull { path ->
                        path.all { bId -> directionMatches(branchMap[bId]?.direction) }
                    }

                    return listOf(currBranchId) + (matchingPathByDirection ?: candidatePaths.first())
                }

                // Prioritize start branches that match the departure direction
                val prioritizedStartBranches = startBranches.sortedByDescending {
                    directionMatches(it.direction)
                }

                for ((branchId) in prioritizedStartBranches) {
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
                    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                        throw e
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
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            // Fall through to local GbTransitRoutes fallback
        }

        // Local static topology fallback via GbTransitRoutes before returning minimal 2-stop fallback
        val localRoutePoints = com.androidfung.departureboard.data.model.gb.topology.GbTransitRoutes.resolveLocalCallingPoints(departure)
        if (localRoutePoints.size > 2) {
            return@withContext localRoutePoints
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
