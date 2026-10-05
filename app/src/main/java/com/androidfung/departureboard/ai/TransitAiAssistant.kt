package com.androidfung.departureboard.ai

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.repository.TransitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Result returned by the Gemini AI Transit Assistant.
 */
data class AiTransitResult(
    val answer: String,
    val matchedStation: Station? = null,
    val matchedDepartures: List<Departure> = emptyList(),
    val originQuery: String? = null,
    val destinationQuery: String? = null
)

/**
 * Intelligent Transit Assistant that translates natural language commuter queries into
 * real-time departure lookups across saved stations and TfL live lines.
 */
class TransitAiAssistant(
    private val repository: TransitRepository
) {

    /**
     * Answers queries such as:
     * - "tell me when is the next train to Mill Hill East"
     * - "when does the 221 bus leave?"
     * - "next departure to Morden"
     * - "any trains from King's Cross?"
     */
    suspend fun answerQuery(
        query: String,
        savedStations: List<Station>,
        nearestStation: Station? = null
    ): AiTransitResult = withContext(Dispatchers.Default) {
        val cleanQuery = query.trim().lowercase()

        // 1. Check if the query asks about a specific bus line (e.g. "221", "SL1", "73")
        val busLineMatch = Regex("""\b(?:bus\s+)?([0-9]{1,3}|sl[0-9]{1,2}|n[0-9]{1,3})\b""", RegexOption.IGNORE_CASE).find(cleanQuery)
        val queriedLine = busLineMatch?.groupValues?.getOrNull(1)?.uppercase()

        // 2. Identify if an explicit origin station was specified with "from <station>"
        val fromStationMatch = Regex("""\bfrom\s+([a-zA-Z0-9\s'.]+?)(?:\s+to|\?|$)""").find(cleanQuery)
        val explicitOriginName = fromStationMatch?.groupValues?.getOrNull(1)?.trim()

        var originStation: Station? = null
        if (!explicitOriginName.isNullOrBlank()) {
            originStation = savedStations.firstOrNull { 
                val b = it.name.lowercase().substringBefore("(").trim()
                b.contains(explicitOriginName) || explicitOriginName.contains(b)
            } ?: repository.searchStations(explicitOriginName).getOrNull()?.firstOrNull()
        }

        // If no explicit origin is stated, context-aware routing prefers the user's nearest station
        val defaultOrigin = originStation ?: nearestStation ?: savedStations.firstOrNull()

        // 3. Identify target destination station
        val toMatch = Regex("""\bto\s+([a-zA-Z0-9\s'.]+?)(?:\s+from|\?|$)""").find(cleanQuery)
        val targetDestinationName = toMatch?.groupValues?.getOrNull(1)?.trim()?.lowercase()

        // Prioritize stations based on user location and query context:
        // If query asks about trains ("train", "tube", "underground"), prioritize tube/rail stations over bus stops
        val isTrainQuery = cleanQuery.contains("train") || cleanQuery.contains("tube") || cleanQuery.contains("underground")

        val sortedSavedStations = if (nearestStation != null) {
            savedStations.sortedWith(
                compareBy<Station> { 
                    if (it.id == nearestStation.id) 0 else 1 
                }.thenBy {
                    if (isTrainQuery && it.modes.any { m -> TransitMode.fromModeString(m).isRail }) 0 else 1
                }
            )
        } else {
            savedStations
        }

        val stationsToSearch = when {
            originStation != null -> listOf(originStation)
            defaultOrigin != null && targetDestinationName == null && queriedLine == null -> {
                // If asking "when is the next train?", use nearest tube/train station if train is requested
                val origin = if (isTrainQuery && defaultOrigin.isBusOnly) {
                    sortedSavedStations.firstOrNull { it.modes.any { m -> TransitMode.fromModeString(m).isRail } } ?: defaultOrigin
                } else defaultOrigin
                listOf(origin)
            }
            else -> sortedSavedStations
        }

        // 4. If destination station was queried (e.g. "to Mill Hill East"):
        // If the query asks for "train", find the tube/rail station; otherwise find matching station
        var destinationStation: Station? = null
        if (!targetDestinationName.isNullOrBlank()) {
            val candidateStations = savedStations.filter { 
                val b = it.name.lowercase().substringBefore("(").trim()
                b.contains(targetDestinationName) || targetDestinationName.contains(b)
            }
            destinationStation = if (isTrainQuery) {
                candidateStations.firstOrNull { it.modes.any { m -> m in listOf("tube", "rail", "overground", "elizabeth-line", "national-rail") } }
                    ?: repository.searchStations("$targetDestinationName Underground").getOrNull()?.firstOrNull()
                    ?: candidateStations.firstOrNull()
            } else {
                candidateStations.firstOrNull() ?: repository.searchStations(targetDestinationName).getOrNull()?.firstOrNull()
            }
        }

        // If the query asks about arrivals arriving at a destination station (e.g. "train to Mill Hill East"):
        // Fetch arrivals directly at that target station so the user gets the exact time the train arrives there!
        if (destinationStation != null && originStation == null) {
            val arrivalsAtDest = repository.getDepartures(destinationStation.id, destinationStation.name).getOrNull() ?: emptyList()
            val filteredByQuery = if (queriedLine != null) {
                arrivalsAtDest.filter { it.lineId.equals(queriedLine, ignoreCase = true) || it.lineName.equals(queriedLine, ignoreCase = true) }
            } else if (isTrainQuery) {
                arrivalsAtDest.filter { !it.modeName.equals("bus", ignoreCase = true) }
            } else arrivalsAtDest

            if (filteredByQuery.isNotEmpty()) {
                val next = filteredByQuery.first()
                val clock = next.formattedActualClockTime ?: ""
                val clockSuffix = if (clock.isNotBlank()) " at $clock" else ""
                val nextMinutes = if (next.timeToStationSeconds <= 30) "due now" else "in ${next.formattedTimeToArrival}"
                val subsequent = filteredByQuery.getOrNull(1)
                val subsequentText = if (subsequent != null) ", followed by another in ${subsequent.formattedTimeToArrival}" else ""

                val answer = "The next ${next.lineName} service arriving at ${destinationStation.displayName} (${next.platformName}) is $nextMinutes$clockSuffix$subsequentText."
                return@withContext AiTransitResult(
                    answer = answer,
                    matchedStation = destinationStation,
                    matchedDepartures = filteredByQuery.take(4)
                )
            }
        }

        val allDepartures = mutableListOf<Departure>()
        for (station in stationsToSearch) {
            val departures = repository.getDepartures(station.id, station.name).getOrNull() ?: emptyList()
            allDepartures.addAll(departures)
        }

        val matchingDepartures = allDepartures.filter { dep ->
            val depDest = dep.destinationName.lowercase()
            val depTowards = dep.towards?.lowercase() ?: ""
            val lineMatches = queriedLine != null && (
                dep.lineId.equals(queriedLine, ignoreCase = true) ||
                dep.lineName.equals(queriedLine, ignoreCase = true)
            )

            val destMatches = if (targetDestinationName != null) {
                depDest.contains(targetDestinationName) || targetDestinationName.contains(depDest) ||
                (depTowards.isNotBlank() && (depTowards.contains(targetDestinationName) || targetDestinationName.contains(depTowards)))
            } else {
                false
            }

            lineMatches || destMatches
        }.sortedWith(
            compareBy<Departure> { 
                if (nearestStation != null && it.stationId == nearestStation.id) 0
                else if (nearestStation?.lat != null && nearestStation.lon != null) {
                    val st = savedStations.firstOrNull { s -> s.id == it.stationId }
                    if (st?.lat != null && st.lon != null) {
                        (com.androidfung.departureboard.util.LocationHelper.calculateDistanceMeters(
                            nearestStation.lat, nearestStation.lon, st.lat, st.lon
                        ) / 1000).toInt()
                    } else 50
                } else 1 
            }.thenBy { it.timeToStationSeconds }
        )

        if (matchingDepartures.isNotEmpty()) {
            val next = matchingDepartures.first()
            val clock = next.formattedActualClockTime ?: ""
            val clockSuffix = if (clock.isNotBlank()) " at $clock" else ""
            val nextMinutes = if (next.timeToStationSeconds <= 30) "due now" else "in ${next.formattedTimeToArrival}"

            val subsequent = matchingDepartures.getOrNull(1)
            val subsequentText = if (subsequent != null) {
                ", followed by another in ${subsequent.formattedTimeToArrival}"
            } else ""

            val answer = "The next ${next.lineName} departure towards ${next.destinationName} leaves from ${next.stationName} (${next.platformName}) $nextMinutes$clockSuffix$subsequentText."

            AiTransitResult(
                answer = answer,
                matchedStation = stationsToSearch.firstOrNull { it.id == next.stationId } ?: defaultOrigin,
                matchedDepartures = matchingDepartures.take(4)
            )
        } else if (allDepartures.isNotEmpty() && stationsToSearch.isNotEmpty()) {
            val bestStation = stationsToSearch.first()
            val stationDepartures = allDepartures.filter { it.stationId == bestStation.id }
            val next = stationDepartures.firstOrNull() ?: allDepartures.first()
            val answer = "At ${bestStation.displayName}, the next upcoming service is ${next.lineName} to ${next.destinationName} (${next.formattedTimeToArrival})."
            AiTransitResult(
                answer = answer,
                matchedStation = bestStation,
                matchedDepartures = (if (stationDepartures.isNotEmpty()) stationDepartures else allDepartures).take(3)
            )
        } else {
            AiTransitResult(
                answer = "I couldn't find live departures matching \"$query\". Try asking for a specific destination or saved station like \"next train to Mill Hill East\" or \"when is the 221 bus?\"."
            )
        }
    }
}
