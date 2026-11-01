package com.androidfung.departureboard.data.model.sg

import com.androidfung.departureboard.data.model.CallingPoint
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.StationLineInfo
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Default popular stations and offline departure generation for Singapore (MRT).
 */
object SgDefaultStations {

    val POPULAR_STATIONS: List<Station> = listOf(
        Station(
            id = "SG_MRT_NS22_TE14",
            name = "Orchard",
            modes = listOf("tube"),
            lines = listOf(
                StationLineInfo("NSL", "North-South Line"),
                StationLineInfo("TEL", "Thomson-East Coast Line")
            )
        ),
        Station(
            id = "SG_MRT_NS26_EW14",
            name = "Raffles Place",
            modes = listOf("tube"),
            lines = listOf(
                StationLineInfo("NSL", "North-South Line"),
                StationLineInfo("EWL", "East-West Line")
            )
        ),
        Station(
            id = "SG_MRT_NS27_CE2_TE20",
            name = "Marina Bay",
            modes = listOf("tube"),
            lines = listOf(
                StationLineInfo("NSL", "North-South Line"),
                StationLineInfo("CCL", "Circle Line"),
                StationLineInfo("TEL", "Thomson-East Coast Line")
            )
        ),
        Station(
            id = "SG_MRT_NS25_EW13",
            name = "City Hall",
            modes = listOf("tube"),
            lines = listOf(
                StationLineInfo("NSL", "North-South Line"),
                StationLineInfo("EWL", "East-West Line")
            )
        ),
        Station(
            id = "SG_MRT_NS1_EW24",
            name = "Jurong East",
            modes = listOf("tube"),
            lines = listOf(
                StationLineInfo("NSL", "North-South Line"),
                StationLineInfo("EWL", "East-West Line")
            )
        ),
        Station(
            id = "SG_MRT_CG2",
            name = "Changi Airport",
            modes = listOf("tube"),
            lines = listOf(
                StationLineInfo("EWL", "East-West Line")
            )
        ),
        Station(
            id = "SG_MRT_NE4_DT19",
            name = "Chinatown",
            modes = listOf("tube"),
            lines = listOf(
                StationLineInfo("NEL", "North East Line"),
                StationLineInfo("DTL", "Downtown Line")
            )
        ),
        Station(
            id = "SG_MRT_NE1_CC29",
            name = "HarbourFront",
            modes = listOf("tube"),
            lines = listOf(
                StationLineInfo("NEL", "North East Line"),
                StationLineInfo("CCL", "Circle Line")
            )
        )
    )

    private val STATION_DEPARTURE_TEMPLATES = mapOf(
        "Orchard" to listOf(
            DepartureTemplate("NSL", "Jurong East", "1", listOf(120, 360, 600)),
            DepartureTemplate("NSL", "Marina South Pier", "2", listOf(180, 420, 660)),
            DepartureTemplate("TEL", "Woodlands North", "3", listOf(240, 540)),
            DepartureTemplate("TEL", "Bayshore", "4", listOf(300, 600))
        ),
        "Raffles Place" to listOf(
            DepartureTemplate("NSL", "Jurong East", "1", listOf(90, 330, 570)),
            DepartureTemplate("NSL", "Marina South Pier", "2", listOf(150, 390, 630)),
            DepartureTemplate("EWL", "Pasir Ris", "3", listOf(120, 360, 600)),
            DepartureTemplate("EWL", "Tuas Link", "4", listOf(180, 420, 660))
        ),
        "City Hall" to listOf(
            DepartureTemplate("NSL", "Jurong East", "1", listOf(120, 360, 600)),
            DepartureTemplate("NSL", "Marina South Pier", "2", listOf(180, 420, 660)),
            DepartureTemplate("EWL", "Pasir Ris", "3", listOf(90, 330, 570)),
            DepartureTemplate("EWL", "Tuas Link", "4", listOf(150, 390, 630))
        ),
        "Marina Bay" to listOf(
            DepartureTemplate("NSL", "Jurong East", "1", listOf(180, 480)),
            DepartureTemplate("CCL", "Dhoby Ghaut", "2", listOf(240, 540)),
            DepartureTemplate("TEL", "Woodlands North", "3", listOf(150, 450)),
            DepartureTemplate("TEL", "Bayshore", "4", listOf(210, 510))
        ),
        "Jurong East" to listOf(
            DepartureTemplate("NSL", "Marina South Pier", "1", listOf(120, 360, 600)),
            DepartureTemplate("EWL", "Pasir Ris / Changi Airport", "2", listOf(150, 390)),
            DepartureTemplate("EWL", "Tuas Link", "3", listOf(210, 510))
        ),
        "Changi Airport" to listOf(
            DepartureTemplate("EWL", "Tanah Merah", "1", listOf(240, 720, 1200))
        ),
        "Chinatown" to listOf(
            DepartureTemplate("NEL", "Punggol", "1", listOf(150, 390, 630)),
            DepartureTemplate("NEL", "HarbourFront", "2", listOf(180, 420)),
            DepartureTemplate("DTL", "Bukit Panjang", "3", listOf(210, 510)),
            DepartureTemplate("DTL", "Expo", "4", listOf(270, 570))
        ),
        "HarbourFront" to listOf(
            DepartureTemplate("NEL", "Punggol", "1", listOf(120, 360, 600)),
            DepartureTemplate("CCL", "Dhoby Ghaut / Marina Bay", "2", listOf(180, 480))
        )
    )

    private data class DepartureTemplate(
        val lineCode: String,
        val destination: String,
        val platform: String,
        val headwaysSeconds: List<Int>
    )

    fun getFallbackDepartures(stationId: String, stationName: String): List<Departure> {
        val cleanName = stationName.replace(Regex("\\s*\\(.*\\)"), "").trim()
        val templates = STATION_DEPARTURE_TEMPLATES[cleanName]
            ?: listOf(
                DepartureTemplate("NSL", "Jurong East", "1", listOf(180, 480)),
                DepartureTemplate("NSL", "Marina South Pier", "2", listOf(240, 540))
            )

        val now = Instant.now()
        val departures = mutableListOf<Departure>()

        templates.forEach { template ->
            val mrtLine = SgMrtLine.fromCode(template.lineCode) ?: SgMrtLine.NSL
            template.headwaysSeconds.forEachIndexed { idx, waitSec ->
                val departureInstant = now.plusSeconds(waitSec.toLong())
                val formattedArrival = DateTimeFormatter.ISO_INSTANT.format(departureInstant)

                departures.add(
                    Departure(
                        id = "SG_MRT_${template.lineCode}_${cleanName}_${template.platform}_${departureInstant.epochSecond}_$idx",
                        stationId = stationId,
                        stationName = stationName,
                        lineId = template.lineCode,
                        lineName = mrtLine.lineName,
                        platformName = "Platform ${template.platform}",
                        destinationName = template.destination,
                        towards = template.destination,
                        timeToStationSeconds = waitSec,
                        expectedArrivalIso = formattedArrival,
                        currentLocation = if (waitSec <= 60) "Approaching" else "In Transit",
                        modeName = "tube",
                        lineBadge = mrtLine.toLineBadge(),
                        status = if (waitSec <= 60) DepartureStatus.DUE else DepartureStatus.ON_TIME,
                        callingPoints = listOf(
                            CallingPoint(cleanName, "Dep"),
                            CallingPoint(template.destination, "${waitSec / 60 + 8} min")
                        )
                    )
                )
            }
        }

        return departures.sortedBy { it.timeToStationSeconds }
    }
}
