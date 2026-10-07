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
        nearestStation: Station? = null,
        modelType: AiModelType = AiModelType.LOGIC_FALLBACK
    ): AiTransitResult = withContext(Dispatchers.Default) {
        val result = executeQueryInternal(query, savedStations, nearestStation)
        val prefix = when (modelType) {
            AiModelType.LOCAL -> "🤖 [Local AI] "
            AiModelType.CLOUD -> "☁️ [Cloud LLM] "
            AiModelType.LOGIC_FALLBACK -> ""
        }
        result.copy(answer = "$prefix${result.answer}")
    }

    private suspend fun executeQueryInternal(
        query: String,
        savedStations: List<Station>,
        nearestStation: Station? = null
    ): AiTransitResult {
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

        // 5. Named line recognition (e.g. "Central line", "Victoria", "Elizabeth line", "Northern")
        val namedLine = KNOWN_LINES.firstOrNull { alias ->
            alias.keywords.any { kw -> cleanQuery.contains(kw) }
        }

        // 6. Service-status intent (e.g. "Any Tube delays or disruptions?", "Elizabeth line status")
        val isStatusQuery = STATUS_KEYWORDS.any { kw -> cleanQuery.contains(kw) }
        if (isStatusQuery) {
            return answerStatusQuery(namedLine, defaultOrigin)
        }

        // Candidate origins: prioritize user's physical nearest station first, followed by saved stations
        val allOrigins = (listOfNotNull(nearestStation) + savedStations).distinctBy { it.id }

        val stationsToSearch = when {
            originStation != null -> listOf(originStation)
            nearestStation != null -> listOf(nearestStation) + savedStations.filter { it.id != nearestStation.id }
            else -> savedStations
        }

        // If target destination is queried (e.g. "to Mill Hill East"), first check if our nearest station
        // or candidate origins have departures heading towards that destination.
        var matchingDepartures: List<Departure> = emptyList()
        var departureStation: Station? = null

        for (origin in stationsToSearch) {
            val deps = repository.getDepartures(origin.id, origin.name).getOrNull() ?: emptyList()
            val matches = deps.filter { dep ->
                val depDest = dep.destinationName.lowercase()
                val depTowards = dep.towards?.lowercase() ?: ""

                val lineMatches = queriedLine != null && (
                    dep.lineId.equals(queriedLine, ignoreCase = true) ||
                    dep.lineName.equals(queriedLine, ignoreCase = true)
                )

                val namedLineMatches = namedLine != null && (
                    dep.lineId.equals(namedLine.lineId, ignoreCase = true) ||
                    namedLine.keywords.any { kw ->
                        dep.lineName.contains(kw, ignoreCase = true) || dep.lineId.contains(kw, ignoreCase = true)
                    }
                )

                val destMatches = targetDestinationName != null && (depDest.contains(targetDestinationName) || targetDestinationName.contains(depDest) ||
                        (depTowards.isNotBlank() && (depTowards.contains(targetDestinationName) || targetDestinationName.contains(depTowards))))

                lineMatches || namedLineMatches || destMatches
            }

            if (matches.isNotEmpty()) {
                matchingDepartures = matches.sortedBy { it.timeToStationSeconds }
                departureStation = origin
                break
            }
        }

        return if (matchingDepartures.isNotEmpty()) {
            val next = matchingDepartures.first()
            val clock = next.formattedActualClockTime ?: ""
            val clockSuffix = if (clock.isNotBlank()) " at $clock" else ""
            val nextMinutes = if (next.timeToStationSeconds <= 30) "due now" else "in ${next.formattedTimeToArrival}"

            val subsequent = matchingDepartures.getOrNull(1)
            val subsequentText = if (subsequent != null) {
                ", followed by another in ${subsequent.formattedTimeToArrival}"
            } else ""

            val prefix = if (departureStation != null && nearestStation != null && departureStation.id == nearestStation.id) {
                "From ${departureStation.displayName} (your nearest station), the"
            } else {
                "The"
            }

            val answer = "$prefix next ${next.lineName} departure towards ${next.destinationName} leaves from ${next.stationName} (${next.platformName}) $nextMinutes$clockSuffix$subsequentText."

            AiTransitResult(
                answer = answer,
                matchedStation = departureStation ?: defaultOrigin,
                matchedDepartures = matchingDepartures.take(4)
            )
        } else if (stationsToSearch.isNotEmpty()) {
            val bestStation = stationsToSearch.first()
            val stationDepartures = repository.getDepartures(bestStation.id, bestStation.name).getOrNull() ?: emptyList()
            if (stationDepartures.isNotEmpty()) {
                val next = stationDepartures.first()
                val prefix = if (nearestStation != null && bestStation.id == nearestStation.id) {
                    "At your nearest station (${bestStation.displayName}), the"
                } else {
                    "At ${bestStation.displayName}, the"
                }
                val answer = "$prefix next upcoming service is ${next.lineName} to ${next.destinationName} (${next.formattedTimeToArrival})."
                AiTransitResult(
                    answer = answer,
                    matchedStation = bestStation,
                    matchedDepartures = stationDepartures.take(3)
                )
            } else {
                AiTransitResult(
                    answer = "I couldn't find live departures matching \"$query\". Try asking for a specific destination or saved station like \"next train to Mill Hill East\" or \"when is the 221 bus?\"."
                )
            }
        } else {
            AiTransitResult(
                answer = "I couldn't find live departures matching \"$query\". Try asking for a specific destination or saved station like \"next train to Mill Hill East\" or \"when is the 221 bus?\"."
            )
        }
    }

    /**
     * Summarizes live TfL line statuses for status/disruption queries such as
     * "Any Tube delays or disruptions?" or "Elizabeth line status".
     */
    private suspend fun answerStatusQuery(
        namedLine: LineAlias?,
        contextStation: Station?
    ): AiTransitResult = withContext(Dispatchers.Default) {
        val allStatuses = repository.getLineStatuses()

        // Narrow to the named line if the query mentions one (e.g. "Elizabeth line status").
        val relevant = allStatuses.filter { (lineId, item) ->
            namedLine == null ||
                lineId.contains(namedLine.lineId, ignoreCase = true) ||
                namedLine.keywords.any { kw -> item.name.contains(kw, ignoreCase = true) }
        }

        val disrupted = relevant.values.mapNotNull { item ->
            val detail = item.lineStatuses.firstOrNull() ?: return@mapNotNull null
            if (detail.statusSeverity > 10) {
                val reason = detail.reason?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
                "the ${item.name}: ${detail.statusSeverityDescription.lowercase()}$reason"
            } else {
                null
            }
        }

        val answer = when {
            namedLine != null && relevant.isEmpty() ->
                "I couldn't check the live status of that line right now — the service status feed may be unavailable."
            namedLine != null -> {
                val named = relevant.values.firstOrNull()
                val detail = named?.lineStatuses?.firstOrNull()
                if (named != null && detail != null && detail.statusSeverity > 10) {
                    val reason = detail.reason?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
                    "The ${named.name} is currently ${detail.statusSeverityDescription.lowercase()}$reason."
                } else {
                    "The ${named?.name ?: "line"} is running with good service right now."
                }
            }
            relevant.isEmpty() ->
                "I couldn't check live line statuses right now — the service status feed may be unavailable."
            disrupted.isEmpty() ->
                "All monitored lines (Tube, DLR, Overground, Elizabeth line, National Rail) are running with good service right now."
            else ->
                "Here's the current network picture: " + disrupted.joinToString(", ") + "."
        }

        AiTransitResult(
            answer = answer,
            matchedStation = contextStation
        )
    }

    /**
     * A recognizable TfL line (or mode) with the keywords commuters typically use for it.
     */
    private data class LineAlias(
        val lineId: String,
        val keywords: List<String>
    )

    private val KNOWN_LINES = listOf(
        LineAlias("elizabeth-line", listOf("elizabeth line", "elizabeth")),
        LineAlias("central", listOf("central line", "central")),
        LineAlias("victoria", listOf("victoria line", "victoria")),
        LineAlias("piccadilly", listOf("piccadilly line", "piccadilly")),
        LineAlias("northern", listOf("northern line", "northern")),
        LineAlias("bakerloo", listOf("bakerloo line", "bakerloo")),
        LineAlias("district", listOf("district line", "district")),
        LineAlias("circle", listOf("circle line", "circle")),
        LineAlias("jubilee", listOf("jubilee line", "jubilee")),
        LineAlias("metropolitan", listOf("metropolitan line", "metropolitan")),
        LineAlias("hammersmith-city", listOf("hammersmith")),
        LineAlias("overground", listOf("overground")),
        LineAlias("dlr", listOf("dlr")),
        LineAlias("thameslink", listOf("thameslink"))
    )

    private val STATUS_KEYWORDS = listOf(
        "status", "delays", "delay", "disruption", "disruptions", "running", "good service", "problems"
    )
}
