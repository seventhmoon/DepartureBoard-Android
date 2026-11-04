package com.androidfung.departureboard.data.model.au

import com.androidfung.departureboard.data.model.CallingPoint
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.StationLineInfo
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Default popular stations and offline departure generation for Sydney (TfNSW).
 */
object AuDefaultStations {

    val DEFAULT_STATIONS: List<Station> = listOf(
        Station(
            id = "AU_TRAIN_CENTRAL",
            name = "Central",
            modes = listOf("national-rail", "tube"),
            lines = listOf(
                StationLineInfo("M1", "Metro M1"),
                StationLineInfo("T1", "T1 North Shore & Western"),
                StationLineInfo("T2", "T2 Inner West"),
                StationLineInfo("T4", "T4 Eastern Suburbs"),
                StationLineInfo("T8", "T8 Airport & South")
            )
        ),
        Station(
            id = "AU_BUS_200060",
            name = "Central Station, Eddy Ave",
            modes = listOf("bus"),
            lines = listOf(
                StationLineInfo("374", "374", "bus"),
                StationLineInfo("396", "396", "bus"),
                StationLineInfo("440", "440", "bus")
            )
        )
    )

    val POPULAR_STATIONS: List<Station> = listOf(
        Station(
            id = "AU_TRAIN_CENTRAL",
            name = "Central",
            modes = listOf("national-rail", "tube"),
            lines = listOf(
                StationLineInfo("M1", "Metro M1"),
                StationLineInfo("T1", "T1 North Shore & Western"),
                StationLineInfo("T2", "T2 Inner West"),
                StationLineInfo("T4", "T4 Eastern Suburbs"),
                StationLineInfo("T8", "T8 Airport & South")
            )
        ),
        Station(
            id = "AU_TRAIN_TOWNHALL",
            name = "Town Hall",
            modes = listOf("national-rail"),
            lines = listOf(
                StationLineInfo("T1", "T1 North Shore & Western"),
                StationLineInfo("T2", "T2 Inner West"),
                StationLineInfo("T3", "T3 Liverpool"),
                StationLineInfo("T4", "T4 Eastern Suburbs")
            )
        ),
        Station(
            id = "AU_TRAIN_WYNYARD",
            name = "Wynyard",
            modes = listOf("national-rail"),
            lines = listOf(
                StationLineInfo("T1", "T1 North Shore & Western"),
                StationLineInfo("T2", "T2 Inner West"),
                StationLineInfo("T3", "T3 Liverpool"),
                StationLineInfo("T8", "T8 Airport & South")
            )
        ),
        Station(
            id = "AU_TRAIN_CIRCULARQUAY",
            name = "Circular Quay",
            modes = listOf("national-rail"),
            lines = listOf(
                StationLineInfo("T2", "T2 Inner West"),
                StationLineInfo("T3", "T3 Liverpool"),
                StationLineInfo("T8", "T8 Airport & South")
            )
        ),
        Station(
            id = "AU_TRAIN_CHATSWOOD",
            name = "Chatswood",
            modes = listOf("national-rail", "tube"),
            lines = listOf(
                StationLineInfo("M1", "Metro M1"),
                StationLineInfo("T1", "T1 North Shore"),
                StationLineInfo("T9", "T9 Northern Line")
            )
        ),
        Station(
            id = "AU_TRAIN_PARRAMATTA",
            name = "Parramatta",
            modes = listOf("national-rail"),
            lines = listOf(
                StationLineInfo("T1", "T1 Western Line"),
                StationLineInfo("T2", "T2 Leppington"),
                StationLineInfo("T5", "T5 Cumberland")
            )
        ),
        Station(
            id = "AU_TRAIN_SYDNEYDOMESTIC",
            name = "Domestic Airport",
            modes = listOf("national-rail"),
            lines = listOf(
                StationLineInfo("T8", "T8 Airport & South Line")
            )
        ),
        Station(
            id = "AU_TRAIN_BONDIJUNCTION",
            name = "Bondi Junction",
            modes = listOf("national-rail"),
            lines = listOf(
                StationLineInfo("T4", "T4 Eastern Suburbs Line")
            )
        )
    )

    private val STATION_DEPARTURE_TEMPLATES = mapOf(
        "Central" to listOf(
            DepartureTemplate("M1", "Tallawong", "1", listOf(180, 420)),
            DepartureTemplate("M1", "Sydenham", "2", listOf(240, 480)),
            DepartureTemplate("T1", "Berowra via Gordon", "16", listOf(120, 360)),
            DepartureTemplate("T1", "Penrith / Emu Plains", "18", listOf(150, 450)),
            DepartureTemplate("T4", "Bondi Junction", "24", listOf(90, 330)),
            DepartureTemplate("T4", "Cronulla", "25", listOf(210, 510)),
            DepartureTemplate("T8", "Macarthur via Airport", "23", listOf(180, 480))
        ),
        "Town Hall" to listOf(
            DepartureTemplate("T1", "Hornsby", "1", listOf(120, 360)),
            DepartureTemplate("T1", "Blacktown", "2", listOf(150, 390)),
            DepartureTemplate("T4", "Bondi Junction", "4", listOf(90, 270)),
            DepartureTemplate("T4", "Waterfall", "5", listOf(180, 420))
        ),
        "Circular Quay" to listOf(
            DepartureTemplate("T2", "Leppington via Museum", "1", listOf(120, 360)),
            DepartureTemplate("T8", "Macarthur via Airport", "2", listOf(180, 420))
        ),
        "Chatswood" to listOf(
            DepartureTemplate("M1", "Sydenham via Central", "1", listOf(120, 360)),
            DepartureTemplate("M1", "Tallawong", "2", listOf(150, 390)),
            DepartureTemplate("T1", "City Circle", "3", listOf(180, 420)),
            DepartureTemplate("T9", "Hornsby", "4", listOf(240, 540))
        ),
        "Parramatta" to listOf(
            DepartureTemplate("T1", "City via Strathfield", "1", listOf(120, 360)),
            DepartureTemplate("T1", "Penrith", "2", listOf(180, 480)),
            DepartureTemplate("T2", "City Circle via Granville", "3", listOf(240, 540))
        ),
        "Domestic Airport" to listOf(
            DepartureTemplate("T8", "City Circle", "1", listOf(150, 390, 630)),
            DepartureTemplate("T8", "Macarthur", "2", listOf(210, 450))
        ),
        "Bondi Junction" to listOf(
            DepartureTemplate("T4", "Cronulla via Central", "1", listOf(120, 360, 600)),
            DepartureTemplate("T4", "Waterfall via Central", "2", listOf(240, 540))
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
                DepartureTemplate("T1", "City Circle", "1", listOf(180, 480)),
                DepartureTemplate("T1", "Hornsby", "2", listOf(240, 540))
            )

        val now = Instant.now()
        val departures = mutableListOf<Departure>()

        templates.forEach { template ->
            val sydneyLine = AuSydneyLine.fromCode(template.lineCode) ?: AuSydneyLine.T1
            template.headwaysSeconds.forEachIndexed { idx, waitSec ->
                val departureInstant = now.plusSeconds(waitSec.toLong())
                val formattedArrival = DateTimeFormatter.ISO_INSTANT.format(departureInstant)

                departures.add(
                    Departure(
                        id = "AU_TRAIN_${template.lineCode}_${cleanName}_${template.platform}_${departureInstant.epochSecond}_$idx",
                        stationId = stationId,
                        stationName = stationName,
                        lineId = template.lineCode,
                        lineName = sydneyLine.lineName,
                        platformName = "Platform ${template.platform}",
                        destinationName = template.destination,
                        towards = template.destination,
                        timeToStationSeconds = waitSec,
                        expectedArrivalIso = formattedArrival,
                        currentLocation = if (waitSec <= 60) "Approaching" else "In Transit",
                        modeName = if (sydneyLine == AuSydneyLine.M1) "tube" else "national-rail",
                        lineBadge = sydneyLine.toLineBadge(),
                        status = if (waitSec <= 60) DepartureStatus.DUE else DepartureStatus.ON_TIME,
                        callingPoints = listOf(
                            CallingPoint(cleanName, "Dep"),
                            CallingPoint(template.destination, "${waitSec / 60 + 12} min")
                        )
                    )
                )
            }
        }

        return departures.sortedBy { it.timeToStationSeconds }
    }
}
