package com.androidfung.departureboard.data.resolver.hk

import com.androidfung.departureboard.data.model.CallingPoint
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.isHkBus
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.model.HkKmbStopDetailItem
import com.androidfung.departureboard.data.network.HkBusApiService
import com.androidfung.departureboard.data.network.HkBusNetworkClient
import com.androidfung.departureboard.data.resolver.CallingPointsResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves calling points for Hong Kong franchised buses (KMB / Citybus).
 * Caches route sequences and stop details to avoid repeated network calls.
 */
class HkBusCallingPointsResolver(
    private val apiService: HkBusApiService = HkBusNetworkClient.kmbApiService
) : CallingPointsResolver {

    // In-memory cache for route-stop sequences: "route_dir" -> list of stop IDs
    private val routeSequenceCache = ConcurrentHashMap<String, List<String>>()

    // In-memory cache for stop details: "stopId" -> stop metadata
    private val stopDetailCache = ConcurrentHashMap<String, HkKmbStopDetailItem>()

    override fun canHandle(departure: Departure): Boolean {
        return departure.isHkBus
    }

    override suspend fun resolveCallingPoints(departure: Departure): List<CallingPoint> {
        val route = departure.lineName.trim()
        val rawStopId = com.androidfung.departureboard.data.model.extractRawHkBusStopId(departure.stationId)
        val dir = departure.direction ?: "outbound"

        return resolveCallingPoints(
            route = route,
            currentStopId = rawStopId,
            direction = dir,
            destinationName = departure.destinationName
        )
    }

    suspend fun resolveCallingPoints(
        route: String,
        currentStopId: String,
        direction: String = "outbound",
        destinationName: String = ""
    ): List<CallingPoint> = withContext(Dispatchers.IO) {
        val cleanRoute = route.trim().uppercase()
        if (cleanRoute.isBlank()) return@withContext emptyList()

        try {
            // 1. Fetch or get cached stop IDs sequence for route
            val cacheKey = "${cleanRoute}_$direction"
            val stopIds = routeSequenceCache.getOrPut(cacheKey) {
                val res = apiService.getKmbRouteStops(cleanRoute, direction)
                val items = res.data.orEmpty()
                if (items.isEmpty() && direction == "outbound") {
                    // Try inbound fallback if outbound is empty
                    apiService.getKmbRouteStops(cleanRoute, "inbound").data.orEmpty()
                        .mapNotNull { it.stop }
                } else {
                    items.mapNotNull { it.stop }
                }
            }

            if (stopIds.isEmpty()) return@withContext emptyList()

            // 2. Locate current stop index in the sequence
            val curIndex = stopIds.indexOfFirst {
                it.equals(currentStopId, ignoreCase = true) ||
                        currentStopId.contains(it, ignoreCase = true) ||
                        it.contains(currentStopId, ignoreCase = true)
            }

            val downstreamStopIds = if (curIndex >= 0) {
                stopIds.subList(curIndex, stopIds.size)
            } else {
                stopIds
            }

            // 3. Fetch details for stops concurrently (cached)
            coroutineScope {
                val deferredStops = downstreamStopIds.map { stopId ->
                    async {
                        stopDetailCache.getOrPut(stopId) {
                            try {
                                apiService.getKmbStopDetail(stopId).data ?: HkKmbStopDetailItem(
                                    stop = stopId,
                                    nameEn = "Bus Stop",
                                    nameTc = "巴士站"
                                )
                            } catch (_: Exception) {
                                HkKmbStopDetailItem(
                                    stop = stopId,
                                    nameEn = "Bus Stop",
                                    nameTc = "巴士站"
                                )
                            }
                        }
                    }
                }

                val stopDetails = deferredStops.awaitAll()

                stopDetails.mapIndexed { index, item ->
                    CallingPoint(
                        stationName = item.displayName.ifBlank { "Bus Stop 巴士站" },
                        isCurrentStation = index == 0,
                        isDestination = index == stopDetails.lastIndex,
                        stationId = item.stop,
                        modes = listOf(TransitMode.BUS.id)
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
