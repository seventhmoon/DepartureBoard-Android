package com.androidfung.departureboard.data.repository.search

import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.StationLineInfo
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.model.hk.HkKmbStopDetailItem
import com.androidfung.departureboard.data.network.hk.HkBusApiService
import com.androidfung.departureboard.data.network.hk.HkBusNetworkClient
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Coordinates dynamic route-stop searches for Hong Kong franchised buses (KMB/LWB/Citybus).
 * Queries stop sequences for matched bus routes and resolves bilingual stop details with caching.
 */
class HkBusSearchEngine(
    private val kmbApiService: HkBusApiService = HkBusNetworkClient.kmbApiService
) {
    // In-memory cache for route-stop sequences: "route_dir" -> list of stop IDs
    private val routeStopsCache = ConcurrentHashMap<String, List<String>>()

    // In-memory cache for stop details: "stopId" -> stop metadata
    private val stopDetailCache = ConcurrentHashMap<String, HkKmbStopDetailItem>()

    // Matches standard HK bus routes (e.g. 1, 1A, 104, 280X, 960, 968, E21A, B1, N241, W2, etc.)
    private val routeRegex = Regex("""^(?:[0-9]{1,3}[A-Z]{0,2}|[A-Z]{1,2}[0-9]{1,3}[A-Z]{0,2})$""", RegexOption.IGNORE_CASE)

    fun isRouteQuery(query: String): Boolean {
        val trimmed = query.trim()
        return trimmed.length in 1..6 && routeRegex.matches(trimmed)
    }

    /**
     * Searches bus stops along a route matching [query].
     */
    suspend fun searchRouteStops(query: String): List<Station> = withContext(Dispatchers.IO) {
        val cleanRoute = query.trim().uppercase()
        if (!isRouteQuery(cleanRoute)) return@withContext emptyList()

        try {
            // Check both outbound and inbound stop sequences for this route
            val outboundStopsDeferred = async { fetchRouteStopIds(cleanRoute, "outbound") }
            val inboundStopsDeferred = async { fetchRouteStopIds(cleanRoute, "inbound") }

            val outboundStops = outboundStopsDeferred.await()
            val inboundStops = inboundStopsDeferred.await()

            val combinedStopIds = (outboundStops + inboundStops).distinct()
            if (combinedStopIds.isEmpty()) return@withContext emptyList()

            coroutineScope {
                val detailDeferreds = combinedStopIds.map { stopId ->
                    async { fetchStopDetail(stopId) }
                }
                val stopDetails = detailDeferreds.awaitAll()

                stopDetails.mapNotNull { detail ->
                    val stopId = detail.stop ?: return@mapNotNull null
                    val nameTc = detail.localizedNames["zh-Hant"].orEmpty().trim()
                    val nameEn = detail.localizedNames["en"].orEmpty().trim()
                    val displayName = when {
                        nameTc.isNotBlank() && nameEn.isNotBlank() -> "$nameEn ($nameTc)"
                        nameTc.isNotBlank() -> nameTc
                        nameEn.isNotBlank() -> nameEn
                        else -> "Bus Stop"
                    }

                    val lat = detail.lat?.toDoubleOrNull()
                    val lon = detail.long?.toDoubleOrNull()

                    Station(
                        id = "HK_BUS_KMB_$stopId",
                        name = "$displayName (Route $cleanRoute)",
                        modes = listOf(TransitMode.BUS.id),
                        zone = null,
                        lat = lat,
                        lon = lon,
                        isFavorite = false,
                        lines = listOf(StationLineInfo("KMB_$cleanRoute", cleanRoute, "bus"))
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchRouteStopIds(route: String, direction: String): List<String> {
        val key = "${route}_$direction"
        return routeStopsCache.getOrPut(key) {
            try {
                kmbApiService.getKmbRouteStops(route, direction).data.orEmpty()
                    .mapNotNull { it.stop }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    suspend fun fetchStopDetail(stopId: String): HkKmbStopDetailItem {
        return stopDetailCache.getOrPut(stopId) {
            try {
                kmbApiService.getKmbStopDetail(stopId).data ?: HkKmbStopDetailItem(
                    stop = stopId,
                    jsonNameEn = "Bus Stop",
                    jsonNameTc = "巴士站"
                )
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
