package com.androidfung.departureboard.data.model

import com.androidfung.departureboard.data.repository.direction.DirectionResolver
import com.androidfung.departureboard.data.routes.LoopRoute
import com.androidfung.departureboard.data.routes.TransitRoute

/**
 * Defines the physical routing topology for Singapore MRT lines, primarily focusing on loops like the Circle Line.
 */
object SgMrtRoutes : TransitLineNetwork {

    private val cclRoute = LoopRoute(
        lineId = "CC",
        stations = listOf(
            "CC1", "CC2", "CC3", "CC4", "CC5", "CC6", "CC7", "CC8", "CC9", "CC10",
            "CC11", "CC12", "CC13", "CC14", "CC15", "CC16", "CC17", "CC18", "CC19", "CC20",
            "CC21", "CC22", "CC23", "CC24", "CC25", "CC26", "CC27", "CC28", "CC29"
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
        "CC" to cclRoute
    )
}
