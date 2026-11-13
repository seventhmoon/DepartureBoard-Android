package com.androidfung.departureboard.data.repository.region.hk

import com.androidfung.departureboard.data.model.CallingPoint
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.TransitDirection
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.model.hk.HkKmbStopDetailItem
import com.androidfung.departureboard.data.model.hk.extractRawHkBusStopId
import com.androidfung.departureboard.data.model.isHkBus
import com.androidfung.departureboard.data.network.hk.HkBusApiService
import com.androidfung.departureboard.data.network.hk.HkBusNetworkClient
import com.androidfung.departureboard.data.topology.CallingPointsResolver
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

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
        val rawStopId = extractRawHkBusStopId(departure.stationId)
        val dir = departure.transitDirection ?: TransitDirection.OUTBOUND

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
        direction: String,
        destinationName: String = ""
    ): List<CallingPoint> = resolveCallingPoints(
        route = route,
        currentStopId = currentStopId,
        direction = TransitDirection.fromString(direction) ?: TransitDirection.OUTBOUND,
        destinationName = destinationName
    )

    suspend fun resolveCallingPoints(
        route: String,
        currentStopId: String,
        direction: TransitDirection = TransitDirection.OUTBOUND,
        destinationName: String = ""
    ): List<CallingPoint> = withContext(Dispatchers.IO) {
        val cleanRoute = route.trim().uppercase()
        if (cleanRoute.isBlank()) return@withContext emptyList()

        val apiDirection = if (direction == TransitDirection.INBOUND) "inbound" else "outbound"

        try {
            // 1. Fetch or get cached stop IDs sequence for route
            val cacheKey = "${cleanRoute}_$apiDirection"
            val stopIds = routeSequenceCache.getOrPut(cacheKey) {
                val res = apiService.getKmbRouteStops(cleanRoute, apiDirection)
                val items = res.data.orEmpty()
                if (items.isEmpty() && direction == TransitDirection.OUTBOUND) {
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
                                    jsonNameEn = "Bus Stop",
                                    jsonNameTc = "巴士站"
                                )
                            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                HkKmbStopDetailItem(
                                    stop = stopId,
                                    jsonNameEn = "Bus Stop",
                                    jsonNameTc = "巴士站"
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
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }
}
