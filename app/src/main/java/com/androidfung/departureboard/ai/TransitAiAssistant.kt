package com.androidfung.departureboard.ai

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.repository.TransitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Result returned by the Gemini AI Transit Assistant.
 */
data class AiTransitResult(
    val answer: String,
    val matchedStation: Station? = null,
    val matchedDepartures: List<Departure> = emptyList(),
    val originQuery: String? = null,
    val destinationQuery: String? = null,
    val a2uiResponse: A2UiResponse? = null
)

/**
 * Interface representing the Transit AI Assistant capable of answering commuter queries.
 */
interface AiTransitAssistant {
    suspend fun answerQuery(
        query: String,
        savedStations: List<Station>,
        nearestStation: Station? = null,
        modelType: AiModelType = AiModelType.DEFAULT
    ): AiTransitResult
}

/**
 * Intelligent Transit Assistant that translates natural language commuter queries into
 * real-time departure lookups across saved stations and TfL live lines.
 */
class TransitAiAssistant(
    private val repository: TransitRepository,
    context: android.content.Context? = null
) : AiTransitAssistant {

    private val classifier: LiteRtTransitClassifier? = context?.let {
        runCatching { LiteRtTransitClassifier(it.applicationContext) }.getOrNull()
    }

    private val dynamicAiProvider: DynamicAiAssistantProvider? = context?.let {
        DynamicAiAssistantProvider(it.applicationContext)
    }

    private val queryRouter: AiQueryRouter? = context?.let {
        AiQueryRouter(it.applicationContext, dynamicAiProvider)
    }

    /**
     * Answers queries such as:
     * - "tell me when is the next train to Mill Hill East"
     * - "when does the 221 bus leave?"
     * - "next departure to Morden"
     * - "any trains from King's Cross?"
     */
    override suspend fun answerQuery(
        query: String,
        savedStations: List<Station>,
        nearestStation: Station?,
        modelType: AiModelType
    ): AiTransitResult = withContext(Dispatchers.Default) {
        val classified = classifier?.classifyIntent(query)
        val executionTier = queryRouter?.routeQuery(query, classified?.intent, modelType)
            ?: when (modelType) {
                AiModelType.LOGIC_FALLBACK -> AiExecutionTier.INSTANT_DETERMINISTIC
                AiModelType.LOCAL -> AiExecutionTier.ON_DEVICE_LLM
                AiModelType.CLOUD -> AiExecutionTier.CLOUD_SYNTHESIS
                AiModelType.DEFAULT -> AiExecutionTier.LITERT_LOCAL_CLASSIFIER
            }

        val result = executeQueryInternal(query, savedStations, nearestStation)

        val finalAnswer = when (executionTier) {
            AiExecutionTier.ON_DEVICE_LLM -> {
                val engine = dynamicAiProvider?.loadDynamicEngine()
                if (engine != null && engine.loadModel()) {
                    var generated = ""
                    val prompt = "Commuter asked: \"$query\". Transit context: \"${result.answer}\". Provide a friendly, concise response:"
                    engine.streamReply(prompt).collect { chunk ->
                        generated += chunk
                    }
                    if (generated.isNotBlank()) "🤖 [LiteRT-LM Dynamic] $generated" else "🤖 [Local AI] ${result.answer}"
                } else if (dynamicAiProvider?.isFeatureInstalled == false) {
                    "🤖 [Local AI] On-device generative module not installed. Download it in the banner above."
                } else {
                    "🤖 [Local AI] ${result.answer}"
                }
            }
            AiExecutionTier.CLOUD_SYNTHESIS -> "☁️ [Cloud LLM] ${result.answer}"
            AiExecutionTier.INSTANT_DETERMINISTIC -> "⚡ [Instant Rules] ${result.answer}"
            AiExecutionTier.LITERT_LOCAL_CLASSIFIER -> result.answer
        }

        result.copy(answer = finalAnswer)
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

        // 4. LiteRT-Runtime Intent Classification
        val classified = classifier?.classifyIntent(query)
        val isClassifiedStatus = classified?.intent == TransitQueryIntent.SERVICE_STATUS ||
                classified?.intent == TransitQueryIntent.DISRUPTION_CHECK

        // 5. Named line recognition (e.g. "Central line", "Victoria", "Elizabeth line", "Northern")
        val namedLine = KNOWN_LINES.firstOrNull { alias ->
            alias.keywords.any { kw -> cleanQuery.contains(kw) }
        }

        // 6. Service-status intent (e.g. "Any Tube delays or disruptions?", "Elizabeth line status")
        val isStatusQuery = isClassifiedStatus || STATUS_KEYWORDS.any { kw -> cleanQuery.contains(kw) }
        if (isStatusQuery) {
            return answerStatusQuery(namedLine, defaultOrigin)
        }

        // Candidate origins: prioritize user's physical nearest station first, followed by saved stations
        val baseStationsToSearch = when {
            originStation != null -> listOf(originStation)
            nearestStation != null -> listOf(nearestStation) + savedStations.filter { it.id != nearestStation.id }
            else -> savedStations
        }

        val stationsToSearch = if (isTrainQuery && originStation == null) {
            baseStationsToSearch.sortedBy { it.isBusOnly }
        } else {
            baseStationsToSearch
        }

        // Fetch departures across candidate origins concurrently to avoid sequential network latency
        val departuresByStation = coroutineScope {
            stationsToSearch.map { origin ->
                async {
                    origin to (repository.getDepartures(origin.id, origin.name).getOrNull() ?: emptyList())
                }
            }.awaitAll()
        }

        // If target destination or line is queried (e.g. "to Mill Hill East"), check which candidate origin
        // has departures heading towards that destination/line.
        var matchingDepartures: List<Departure> = emptyList()
        var departureStation: Station? = null

        for ((origin, deps) in departuresByStation) {
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

            val targetStation = departureStation ?: defaultOrigin
            val actions = mutableListOf<A2UiAction>()
            if (targetStation != null) {
                actions.add(A2UiAction.PinStation(targetStation))
            }
            actions.add(A2UiAction.TrackTrain(next))
            if (explicitOriginName != null && targetDestinationName != null) {
                actions.add(A2UiAction.OpenGoogleMaps(explicitOriginName, targetDestinationName))
                actions.add(A2UiAction.OpenTflJourneyPlanner(explicitOriginName, targetDestinationName))
            }

            val blocks = mutableListOf<A2UiBlock>(
                A2UiBlock.TextSummary(answer)
            )
            if (targetStation != null) {
                blocks.add(A2UiBlock.LiveDeparturesCard(targetStation, matchingDepartures.take(4)))
            }
            if (actions.isNotEmpty()) {
                blocks.add(A2UiBlock.ActionButtons(actions))
            }

            AiTransitResult(
                answer = answer,
                matchedStation = targetStation,
                matchedDepartures = matchingDepartures.take(4),
                originQuery = explicitOriginName,
                destinationQuery = targetDestinationName,
                a2uiResponse = A2UiResponse(blocks)
            )
        } else if (departuresByStation.isNotEmpty()) {
            val (bestStation, stationDepartures) = departuresByStation.first()
            if (stationDepartures.isNotEmpty()) {
                val next = stationDepartures.first()
                val prefix = if (nearestStation != null && bestStation.id == nearestStation.id) {
                    "At your nearest station (${bestStation.displayName}), the"
                } else {
                    "At ${bestStation.displayName}, the"
                }
                val answer = "$prefix next upcoming service is ${next.lineName} to ${next.destinationName} (${next.formattedTimeToArrival})."
                val actions = listOf(
                    A2UiAction.PinStation(bestStation),
                    A2UiAction.TrackTrain(next)
                )
                val blocks = listOf(
                    A2UiBlock.TextSummary(answer),
                    A2UiBlock.LiveDeparturesCard(bestStation, stationDepartures.take(3)),
                    A2UiBlock.ActionButtons(actions)
                )
                AiTransitResult(
                    answer = answer,
                    matchedStation = bestStation,
                    matchedDepartures = stationDepartures.take(3),
                    a2uiResponse = A2UiResponse(blocks)
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
            // In TfL Unified API: 10 = Good Service. Lower values (0-9) represent delays, closures, or suspensions.
            if (detail.statusSeverity != 10) {
                // If a specific line was queried, keep the full reason; for general network status, keep it concise
                val statusText = detail.statusSeverityDescription
                if (namedLine != null) {
                    val reason = detail.reason?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
                    "${item.name}: $statusText$reason"
                } else {
                    "${item.name} ($statusText)"
                }
            } else {
                null
            }
        }

        val a2uiBlocks = mutableListOf<A2UiBlock>()

        val answer = when {
            namedLine != null && relevant.isEmpty() -> {
                a2uiBlocks.add(
                    A2UiBlock.LineStatusBanner(
                        lineName = namedLine.keywords.first().replaceFirstChar { it.uppercase() },
                        statusDescription = "Status feed unavailable",
                        severity = A2UiSeverity.WARNING
                    )
                )
                "I couldn't check the live status of that line right now — the service status feed may be unavailable."
            }
            namedLine != null -> {
                val named = relevant.values.firstOrNull()
                val detail = named?.lineStatuses?.firstOrNull()
                if (named != null && detail != null && detail.statusSeverity != 10) {
                    val reason = detail.reason?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
                    a2uiBlocks.add(
                        A2UiBlock.LineStatusBanner(
                            lineName = named.name,
                            statusDescription = detail.statusSeverityDescription,
                            reason = detail.reason,
                            severity = if (detail.statusSeverity <= 5) A2UiSeverity.DISRUPTION else A2UiSeverity.WARNING
                        )
                    )
                    "${named.name} is currently experiencing ${detail.statusSeverityDescription.lowercase()}$reason"
                } else {
                    a2uiBlocks.add(
                        A2UiBlock.LineStatusBanner(
                            lineName = named?.name ?: "Line",
                            statusDescription = "Good Service",
                            severity = A2UiSeverity.GOOD_SERVICE
                        )
                    )
                    "${named?.name ?: "The line"} is running with good service right now."
                }
            }
            relevant.isEmpty() ->
                "I couldn't check live line statuses right now — the service status feed may be unavailable."
            disrupted.isEmpty() -> {
                a2uiBlocks.add(
                    A2UiBlock.LineStatusBanner(
                        lineName = "All Lines",
                        statusDescription = "Good Service Across Network",
                        severity = A2UiSeverity.GOOD_SERVICE
                    )
                )
                "Good service is currently reported across all monitored lines."
            }
            disrupted.size <= 4 -> {
                relevant.values.filter { (it.lineStatuses.firstOrNull()?.statusSeverity ?: 10) != 10 }.forEach { lineItem ->
                    val detail = lineItem.lineStatuses.firstOrNull()
                    a2uiBlocks.add(
                        A2UiBlock.LineStatusBanner(
                            lineName = lineItem.name,
                            statusDescription = detail?.statusSeverityDescription ?: "Disruption",
                            reason = detail?.reason,
                            severity = if ((detail?.statusSeverity ?: 10) <= 5) A2UiSeverity.DISRUPTION else A2UiSeverity.WARNING
                        )
                    )
                }
                "Delays currently reported on: " + disrupted.joinToString("; ") + "."
            }
            else -> {
                val topIssues = disrupted.take(4).joinToString("; ")
                val remainingCount = disrupted.size - 4
                relevant.values.filter { (it.lineStatuses.firstOrNull()?.statusSeverity ?: 10) != 10 }.take(4).forEach { lineItem ->
                    val detail = lineItem.lineStatuses.firstOrNull()
                    a2uiBlocks.add(
                        A2UiBlock.LineStatusBanner(
                            lineName = lineItem.name,
                            statusDescription = detail?.statusSeverityDescription ?: "Disruption",
                            reason = detail?.reason,
                            severity = if ((detail?.statusSeverity ?: 10) <= 5) A2UiSeverity.DISRUPTION else A2UiSeverity.WARNING
                        )
                    )
                }
                "Delays currently reported on: $topIssues, and $remainingCount other lines."
            }
        }

        if (a2uiBlocks.isEmpty() || a2uiBlocks.none { it is A2UiBlock.TextSummary }) {
            a2uiBlocks.add(0, A2UiBlock.TextSummary(answer))
        }

        AiTransitResult(
            answer = answer,
            matchedStation = contextStation,
            a2uiResponse = A2UiResponse(a2uiBlocks)
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
