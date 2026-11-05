package com.androidfung.departureboard.data.resolver

import com.androidfung.departureboard.data.model.TransitDirection
import com.androidfung.departureboard.data.repository.direction.DirectionResolver
import com.androidfung.departureboard.data.routes.LinearRoute
import com.androidfung.departureboard.data.routes.LoopRoute
import com.androidfung.departureboard.data.routes.TransitRoute
import java.time.ZoneId

/**
 * Unified transit network topology interface supporting both linear and circular/loop lines,
 * branching spur key resolution, and station name/ID mapping across all regions.
 */
interface TransitLineNetwork {
    val timeZone: ZoneId
    val defaultMode: String
    val averageSecondsBetweenStops: Long get() = 120L
    val lineSequences: Map<String, List<String>>

    /**
     * Line codes whose station sequence forms a continuous circular loop
     * (e.g. "JY" Yamanote Line in Tokyo, "metro-yellow" in Tyne & Wear).
     */
    val loopLineCodes: Set<String> get() = emptySet()

    /**
     * Map of Route ID (or line code) to its explicit [TransitRoute] data model.
     * Defaults to constructing [LoopRoute] or [LinearRoute] from [lineSequences] and [loopLineCodes].
     */
    val routeConfigs: Map<String, TransitRoute<String>>
        get() = lineSequences.mapValues { (lineCode, stops) ->
            val isLoop = loopLineCodes.any { it.equals(lineCode, ignoreCase = true) }
            val normalizedStops = if (isLoop && stops.size > 1 && stops.first().equals(stops.last(), ignoreCase = true)) {
                stops.dropLast(1)
            } else {
                stops
            }
            val defaultDirectionResolver = object : DirectionResolver {
                override fun resolve(
                    destinationName: String?,
                    destinationCrs: String?,
                    destinationNaptanId: String?,
                    currentStationId: String?,
                    currentStationName: String?,
                    platformName: String?,
                    apiDirection: String?
                ): TransitDirection? = null
            }
            if (isLoop) {
                LoopRoute(lineId = lineCode, stations = normalizedStops, directionResolver = defaultDirectionResolver)
            } else {
                LinearRoute(lineId = lineCode, stations = normalizedStops, directionResolver = defaultDirectionResolver)
            }
        }

    /**
     * Optional explicit direction resolvers for networks that customize direction resolution.
     */
    val resolvers: Map<String, DirectionResolver>
        get() = routeConfigs.mapValues { it.value.directionResolver }

    /**
     * Resolves the sequence map key for a given trip (allows branching lines such as HK EAL -> EAL_RAC).
     */
    fun resolveSequenceKey(lineCode: String, originIdentifier: String, destIdentifier: String): String =
        lineCode.uppercase()

    /**
     * Resolves display station name for a code or raw identifier.
     * Defaults to identity if the sequence already stores display names.
     */
    fun resolveStationName(stationCodeOrName: String): String = stationCodeOrName

    /**
     * Resolves station ID if available.
     */
    fun resolveStationId(lineCode: String, stationCodeOrName: String): String? = null
}
