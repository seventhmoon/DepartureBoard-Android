package com.androidfung.departureboard.data.model

import com.androidfung.departureboard.data.repository.direction.DirectionResolver
import com.androidfung.departureboard.data.routes.LoopRoute
import com.androidfung.departureboard.data.routes.TransitRoute

/**
 * Defines the physical routing topology for Tokyo lines, primarily focusing on loops like the Yamanote.
 */
object JpTokyoRoutes : TransitLineNetwork {

    // Yamanote Line is a Loop Route
    private val yamanoteRoute = LoopRoute(
        lineId = "JY",
        stations = listOf(
            "JY01", "JY02", "JY03", "JY04", "JY05", "JY06", "JY07", "JY08", "JY09", "JY10",
            "JY11", "JY12", "JY13", "JY14", "JY15", "JY16", "JY17", "JY18", "JY19", "JY20",
            "JY21", "JY22", "JY23", "JY24", "JY25", "JY26", "JY27", "JY28", "JY29", "JY30"
        ),
        directionResolver = object : DirectionResolver {
            override fun resolve(
                destinationName: String?, destinationCrs: String?, destinationNaptanId: String?,
                currentStationId: String?, currentStationName: String?, platformName: String?, apiDirection: String?
            ): TransitDirection? {
                if (platformName?.contains("内回り") == true || apiDirection?.equals("inner", ignoreCase = true) == true) {
                    return TransitDirection.ANTI_CLOCKWISE
                }
                if (platformName?.contains("外回り") == true || apiDirection?.equals("outer", ignoreCase = true) == true) {
                    return TransitDirection.CLOCKWISE
                }
                return null
            }
        }
    )

    override val routeConfigs: Map<String, TransitRoute<String>> = mapOf(
        "JY" to yamanoteRoute
    )
}
