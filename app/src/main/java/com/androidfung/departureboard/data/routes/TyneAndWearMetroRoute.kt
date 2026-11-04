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
 * Fully migrated to generic [TransitRoute] with [RouteStation].
 */
object TyneAndWearMetroRoute : TransitLineNetwork {

    private val UK_ZONE = ZoneId.of("Europe/London")
    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

    val GREEN_STATIONS = listOf(
        RouteStation("South Hylton"),
        RouteStation("Pallion"),
        RouteStation("Millfield"),
        RouteStation("University"),
        RouteStation("Park Lane"),
        RouteStation("Sunderland"),
        RouteStation("St Peter's", aliases = listOf("st peter's", "st peters")),
        RouteStation("Stadium of Light"),
        RouteStation("Seaburn"),
        RouteStation("East Boldon"),
        RouteStation("Brockley Whins"),
        RouteStation("Fellgate"),
        RouteStation("Pelaw"),
        RouteStation("Heworth"),
        RouteStation("Felling"),
        RouteStation("Gateshead Stadium"),
        RouteStation("Gateshead"),
        RouteStation("Central Station"),
        RouteStation("Monument"),
        RouteStation("Haymarket"),
        RouteStation("Jesmond"),
        RouteStation("West Jesmond"),
        RouteStation("Ilford Road"),
        RouteStation("South Gosforth"),
        RouteStation("Regent Centre"),
        RouteStation("Wansbeck Road"),
        RouteStation("Fawdon"),
        RouteStation("Kingston Park"),
        RouteStation("Bank Foot"),
        RouteStation("Callerton Parkway"),
        RouteStation("Airport", aliases = listOf("airport", "newcastle airport"))
    )

    val YELLOW_STATIONS = listOf(
        RouteStation("St James", aliases = listOf("st james", "st. james")),
        RouteStation("Monument"),
        RouteStation("Manors"),
        RouteStation("Byker"),
        RouteStation("Chillingham Road"),
        RouteStation("Walkergate"),
        RouteStation("Wallsend"),
        RouteStation("Hadrian Road"),
        RouteStation("Howdon"),
        RouteStation("Percy Main"),
        RouteStation("Meadow Well"),
        RouteStation("North Shields"),
        RouteStation("Tynemouth"),
        RouteStation("Cullercoats"),
        RouteStation("Whitley Bay"),
        RouteStation("Monkseaton"),
        RouteStation("West Monkseaton"),
        RouteStation("Shiremoor"),
        RouteStation("PalmersVILLE", aliases = listOf("palmersville")),
        RouteStation("Northumberland Park"),
        RouteStation("Benton"),
        RouteStation("Four Lane Ends"),
        RouteStation("Longbenton"),
        RouteStation("South Gosforth"),
        RouteStation("Ilford Road"),
        RouteStation("West Jesmond"),
        RouteStation("Jesmond"),
        RouteStation("Haymarket"),
        RouteStation("Monument"),
        RouteStation("Central Station"),
        RouteStation("Gateshead"),
        RouteStation("Gateshead Stadium"),
        RouteStation("Felling"),
        RouteStation("Heworth"),
        RouteStation("Pelaw"),
        RouteStation("Hebburn"),
        RouteStation("Jarrow"),
        RouteStation("Bede"),
        RouteStation("Simonside"),
        RouteStation("Tyne Dock"),
        RouteStation("Chichester"),
        RouteStation("South Shields")
    )

    private val greenRoute = LinearRoute(
        lineId = "metro-green",
        stations = GREEN_STATIONS,
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
        stations = YELLOW_STATIONS,
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
        "metro-green" to LinearRoute(
            lineId = "metro-green",
            stations = GREEN_STATIONS.map { it.name },
            directionResolver = greenRoute.directionResolver
        ),
        "metro-yellow" to LoopRoute(
            lineId = "metro-yellow",
            stations = YELLOW_STATIONS.map { it.name },
            directionResolver = yellowRoute.directionResolver
        )
    )

    fun findStation(query: String?): RouteStation? {
        if (query.isNullOrBlank()) return null
        return (GREEN_STATIONS + YELLOW_STATIONS).firstOrNull { it.matches(query) }
    }

    fun resolveCallingPoints(
        lineId: String,
        originName: String,
        destinationName: String,
        departureSeconds: Int = 0
    ): List<CallingPoint> {
        val route = if (lineId.contains("green", ignoreCase = true)) greenRoute else yellowRoute
        val originStation = route.stations.firstOrNull { it.matches(originName) } ?: RouteStation(originName)
        val destStation = route.stations.firstOrNull { it.matches(destinationName) } ?: RouteStation(destinationName)

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
                stationName = station.name,
                scheduledTime = clockTime,
                estimatedTime = estBadge,
                isCurrentStation = idx == 0,
                isDestination = idx == sliced.lastIndex,
                modes = listOf(TransitMode.TUBE.id)
            )
        }
    }

    fun resolveDirection(
        lineId: String,
        originName: String,
        destinationName: String,
        platformName: String? = null
    ): TransitDirection? {
        val route = if (lineId.contains("green", ignoreCase = true)) greenRoute else yellowRoute
        val originStation = route.stations.firstOrNull { it.matches(originName) } ?: return null
        val destStation = route.stations.firstOrNull { it.matches(destinationName) } ?: return null

        val fromPlatform = route.directionResolver.resolve(platformName = platformName)
        if (fromPlatform != null) return fromPlatform

        val resolved = route.resolveDirection(originStation, destStation, platformName)
        if (lineId.contains("green", ignoreCase = true) && resolved != null) {
            return if (resolved == TransitDirection.OUTBOUND) TransitDirection.NORTHBOUND else TransitDirection.SOUTHBOUND
        }
        return resolved
    }
}
