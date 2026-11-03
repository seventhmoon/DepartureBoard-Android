package com.androidfung.departureboard.data.routes

import com.androidfung.departureboard.data.model.CallingPoint
import com.androidfung.departureboard.data.model.TransitDirection
import com.androidfung.departureboard.data.model.TransitLineNetwork
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.repository.direction.DirectionResolver
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Station sequence routes for Tyne and Wear Metro (Green Line and Yellow Line).
 * Enables complete calling points timeline reconstruction and direction resolution
 * (including Clockwise / Anti-Clockwise for the Yellow Line loop).
 */
object TyneAndWearMetroRoute : MetroRoute<TyneAndWearMetroStation>, TransitLineNetwork {

    private val UK_ZONE = ZoneId.of("Europe/London")
    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

    private val greenRoute = LinearRoute(
        lineId = "metro-green",
        stations = listOf(
            "South Hylton", "Pallion", "Millfield", "University", "Park Lane",
            "Sunderland", "St Peter's", "Stadium of Light", "Seaburn", "East Boldon",
            "Brockley Whins", "Fellgate", "Pelaw", "Heworth", "Felling",
            "Gateshead Stadium", "Gateshead", "Central Station", "Monument", "Haymarket",
            "Jesmond", "West Jesmond", "Ilford Road", "South Gosforth", "Regent Centre",
            "Wansbeck Road", "Fawdon", "Kingston Park", "Bank Foot", "Callerton Parkway",
            "Airport"
        ),
        directionResolver = object : DirectionResolver {
            override fun resolve(
                destinationName: String?, destinationCrs: String?, destinationNaptanId: String?,
                currentStationId: String?, currentStationName: String?, platformName: String?, apiDirection: String?
            ): TransitDirection? {
                if (platformName?.contains("Platform 2", ignoreCase = true) == true) return TransitDirection.SOUTHBOUND
                if (platformName?.contains("Platform 1", ignoreCase = true) == true) return TransitDirection.NORTHBOUND
                return null
            }
        }
    )

    private val yellowRoute = LoopRoute(
        lineId = "metro-yellow",
        stations = listOf(
            "St James", "Monument", "Manors", "Byker", "Chillingham Road", "Walkergate",
            "Wallsend", "Hadrian Road", "Howdon", "Percy Main", "Meadow Well", "North Shields",
            "Tynemouth", "Cullercoats", "Whitley Bay", "Monkseaton", "West Monkseaton",
            "Shiremoor", "Palmersville", "Northumberland Park", "Benton", "Four Lane Ends",
            "Longbenton", "South Gosforth", "Ilford Road", "West Jesmond", "Jesmond",
            "Haymarket", "Monument", "Central Station", "Gateshead", "Gateshead Stadium",
            "Felling", "Heworth", "Pelaw", "Hebburn", "Jarrow", "Bede", "Simonside",
            "Tyne Dock", "Chichester", "South Shields"
        ),
        directionResolver = object : DirectionResolver {
            override fun resolve(
                destinationName: String?, destinationCrs: String?, destinationNaptanId: String?,
                currentStationId: String?, currentStationName: String?, platformName: String?, apiDirection: String?
            ): TransitDirection? {
                if (platformName?.contains("Platform 2", ignoreCase = true) == true) return TransitDirection.ANTI_CLOCKWISE
                if (platformName?.contains("Platform 1", ignoreCase = true) == true) return TransitDirection.CLOCKWISE
                return null
            }
        }
    )

    override val routeConfigs: Map<String, TransitRoute<String>> = mapOf(
        "metro-green" to greenRoute,
        "metro-yellow" to yellowRoute
    )

    // Legacy overrides (for backward compatibility before full migration)

    val GREEN_LINE: List<TyneAndWearMetroStation>
        get() = greenRoute.stations.mapNotNull { TyneAndWearMetroStation.fromString(it) }
        
    val YELLOW_LINE: List<TyneAndWearMetroStation>
        get() = yellowRoute.stations.mapNotNull { TyneAndWearMetroStation.fromString(it) }

    override val lineSequences: Map<String, List<TyneAndWearMetroStation>> = mapOf(
        "metro-green" to GREEN_LINE,
        "metro-yellow" to YELLOW_LINE
    )

    override fun resolveCallingPoints(
        lineId: String,
        originName: String,
        destinationName: String,
        departureSeconds: Int
    ): List<CallingPoint> {
        val route = routeConfigs[lineId.lowercase()] ?: return emptyList()
        val originStation = TyneAndWearMetroStation.fromString(originName)?.stationName ?: originName
        val destStation = TyneAndWearMetroStation.fromString(destinationName)?.stationName ?: destinationName
        
        val sliced = route.slice(originStation, destStation)
        if (sliced.isEmpty()) return emptyList()

        val baseEpoch = Instant.now().epochSecond + departureSeconds

        return sliced.mapIndexed { idx, station ->
            val stopEpoch = baseEpoch + (idx * 120L)
            val clockTime = Instant.ofEpochSecond(stopEpoch).atZone(UK_ZONE).format(TIME_FORMATTER)
            val estBadge = when {
                idx == 0 -> if (departureSeconds <= 45) "Due" else "${departureSeconds / 60} mins"
                else -> "${(departureSeconds + idx * 120) / 60} mins"
            }

            CallingPoint(
                stationName = station,
                scheduledTime = clockTime,
                estimatedTime = estBadge,
                isCurrentStation = idx == 0,
                isDestination = idx == sliced.lastIndex,
                modes = listOf(TransitMode.TUBE.id)
            )
        }
    }

    override fun resolveDirection(
        lineId: String,
        originName: String,
        destinationName: String,
        platformName: String?
    ): TransitDirection? {
        val route = routeConfigs[lineId.lowercase()] ?: return null
        val originStation = TyneAndWearMetroStation.fromString(originName)?.stationName ?: originName
        val destStation = TyneAndWearMetroStation.fromString(destinationName)?.stationName ?: destinationName

        // Delegate to the route model's specific resolver
        val fromPlatform = route.directionResolver.resolve(platformName = platformName)
        if (fromPlatform != null) return fromPlatform

        val resolved = route.resolveDirection(originStation, destStation, platformName)
        
        // Map abstract Outbound/Inbound to Green Line's Northbound/Southbound if needed.
        if (lineId.contains("green", ignoreCase = true) && resolved != null) {
             return if (resolved == TransitDirection.OUTBOUND) TransitDirection.NORTHBOUND else TransitDirection.SOUTHBOUND
        }
        return resolved
    }
}
