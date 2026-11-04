package com.androidfung.departureboard.data.model.au

import com.androidfung.departureboard.data.model.TransitDirection
import com.androidfung.departureboard.data.model.TransitLineNetwork

import com.androidfung.departureboard.data.repository.direction.DirectionResolver
import com.androidfung.departureboard.data.routes.LoopRoute
import com.androidfung.departureboard.data.routes.TransitRoute

/**
 * Defines the physical routing topology for Sydney Trains, particularly the T2 Inner West & Leppington Line
 * which forms a partial City Circle loop.
 */
object AuSydneyRoutes : TransitLineNetwork {

    private val cityCircleRoute = LoopRoute(
        lineId = "T2",
        stations = listOf(
            "Central", "Town Hall", "Wynyard", "Circular Quay", "St James", "Museum"
        ),
        directionResolver = object : DirectionResolver {
            override fun resolve(
                destinationName: String?, destinationCrs: String?, destinationNaptanId: String?,
                currentStationId: String?, currentStationName: String?, platformName: String?, apiDirection: String?
            ): TransitDirection? {
                if (platformName?.contains("Inner") == true || apiDirection?.equals("inner", ignoreCase = true) == true) {
                    return TransitDirection.ANTI_CLOCKWISE
                }
                if (platformName?.contains("Outer") == true || apiDirection?.equals("outer", ignoreCase = true) == true) {
                    return TransitDirection.CLOCKWISE
                }
                return null
            }
        }
    )

    override val routeConfigs: Map<String, TransitRoute<String>> = mapOf(
        "T2" to cityCircleRoute,
        "T3" to cityCircleRoute,
        "T8" to cityCircleRoute
    )
}
