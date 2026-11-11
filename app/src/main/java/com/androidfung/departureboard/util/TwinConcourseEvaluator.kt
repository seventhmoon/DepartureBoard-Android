package com.androidfung.departureboard.util

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.ui.dashboard.StationCardUiModel
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Evaluates competing station hubs ("Twin Concourses") by deducting walking + concourse gate
 * buffer time from each live departure countdown so commuters see whether a train is genuinely
 * catchable rather than sprinting for an uncatchable train.
 */
object TwinConcourseEvaluator {

    /** Average brisk pedestrian speed (~4.8 km/h = 1.33 m/s) plus 60s gate/escalator buffer. */
    private const val WALK_SPEED_METERS_PER_SEC = 1.33
    private const val CONCOURSE_GATE_BUFFER_SECONDS = 60
    private const val COMFORTABLE_MARGIN_SECONDS = 90

    enum class CatchabilityStatus {
        MAKEABLE,
        TIGHT_SPRINT,
        TOO_CLOSE
    }

    data class EvaluatedTwinDeparture(
        val departure: Departure,
        val walkSeconds: Int,
        val marginSeconds: Int,
        val catchability: CatchabilityStatus
    )

    data class TwinRaceComparison(
        val hubA: StationCardUiModel,
        val hubB: StationCardUiModel,
        val walkMinutesA: Int?,
        val walkMinutesB: Int?,
        val evaluatedA: List<EvaluatedTwinDeparture>,
        val evaluatedB: List<EvaluatedTwinDeparture>,
        val winnerCard: StationCardUiModel?,
        val winningDeparture: EvaluatedTwinDeparture?
    )

    private val KNOWN_TWIN_NAME_PAIRS: List<Pair<String, String>> = listOf(
        "King's Cross" to "St Pancras",
        "Euston" to "Euston Square",
        "Waterloo" to "Waterloo East",
        "Paddington" to "Lancaster Gate",
        "Charing Cross" to "Embankment",
        "Bank" to "Monument",
        "Liverpool Street" to "Moorgate",
        "Farringdon" to "City Thameslink",
        "Central" to "Hong Kong",
        "Admiralty" to "Exhibition Centre",
        "Mong Kok" to "Mong Kok East",
        "Tsim Sha Tsui" to "East Tsim Sha Tsui",
        "Raffles Place" to "Downtown",
        "City Hall" to "Esplanade",
        "Shinjuku" to "Seibu-Shinjuku",
        "Tokyo" to "Otemachi",
        "Central" to "Town Hall"
    )

    /**
     * Estimates total walk + concourse traversal seconds from GPS distance (capped to sensible walking range).
     * Returns 0 when distance is unknown so comparisons degrade gracefully to raw departure time.
     */
    fun estimateWalkSeconds(distanceMeters: Double?, minTransferSeconds: Int? = null): Int {
        if (distanceMeters == null || distanceMeters < 0.0 || distanceMeters > 5_000.0) {
            return minTransferSeconds?.coerceAtLeast(0) ?: 0
        }
        if (distanceMeters <= 35.0) {
            return 30 // Already inside concourse
        }
        val rawWalk = ceil(distanceMeters / WALK_SPEED_METERS_PER_SEC).toInt() + CONCOURSE_GATE_BUFFER_SECONDS
        return maxOf(rawWalk, minTransferSeconds ?: 0)
    }

    fun classifyCatchability(
        timeToStationSeconds: Int,
        walkSeconds: Int,
        hasDistance: Boolean
    ): CatchabilityStatus {
        if (!hasDistance && walkSeconds <= 0) {
            return when {
                timeToStationSeconds < 30 -> CatchabilityStatus.TIGHT_SPRINT
                else -> CatchabilityStatus.MAKEABLE
            }
        }
        val margin = timeToStationSeconds - walkSeconds
        return when {
            margin < 0 -> CatchabilityStatus.TOO_CLOSE
            margin < COMFORTABLE_MARGIN_SECONDS -> CatchabilityStatus.TIGHT_SPRINT
            else -> CatchabilityStatus.MAKEABLE
        }
    }

    fun selectBestDefaultPair(cards: List<StationCardUiModel>): Pair<Int, Int> {
        if (cards.size < 2) return 0 to 0

        // 1. Check curated twin-concourse hub pairs present on the user's board
        for ((nameA, nameB) in KNOWN_TWIN_NAME_PAIRS) {
            val idxA = cards.indexOfFirst { it.station.name.contains(nameA, ignoreCase = true) }
            val idxB = cards.indexOfFirst {
                it.station.name.contains(nameB, ignoreCase = true) &&
                    !it.station.id.equals(cards.getOrNull(idxA)?.station?.id, ignoreCase = true)
            }
            if (idxA >= 0 && idxB >= 0 && idxA != idxB) {
                return idxA to idxB
            }
        }

        // 2. Otherwise pick the two closest stations when GPS distance is available
        val withDist = cards.mapIndexedNotNull { index, card ->
            card.distanceMeters?.let { index to it }
        }.sortedBy { it.second }

        if (withDist.size >= 2) {
            return withDist[0].first to withDist[1].first
        }

        return 0 to 1
    }

    fun compareHubs(
        cardA: StationCardUiModel,
        cardB: StationCardUiModel,
        maxRows: Int = 3
    ): TwinRaceComparison {
        val hasDistA = cardA.distanceMeters != null && cardA.distanceMeters <= 5_000.0
        val hasDistB = cardB.distanceMeters != null && cardB.distanceMeters <= 5_000.0
        val walkSecA = estimateWalkSeconds(cardA.distanceMeters)
        val walkSecB = estimateWalkSeconds(cardB.distanceMeters)

        val walkMinA = if (hasDistA) (walkSecA / 60.0).roundToInt().coerceAtLeast(1) else null
        val walkMinB = if (hasDistB) (walkSecB / 60.0).roundToInt().coerceAtLeast(1) else null

        val evalA = cardA.departures
            .filter { it.status != DepartureStatus.CANCELLED }
            .take(maxRows)
            .map { dep ->
                EvaluatedTwinDeparture(
                    departure = dep,
                    walkSeconds = walkSecA,
                    marginSeconds = dep.timeToStationSeconds - walkSecA,
                    catchability = classifyCatchability(dep.timeToStationSeconds, walkSecA, hasDistA)
                )
            }

        val evalB = cardB.departures
            .filter { it.status != DepartureStatus.CANCELLED }
            .take(maxRows)
            .map { dep ->
                EvaluatedTwinDeparture(
                    departure = dep,
                    walkSeconds = walkSecB,
                    marginSeconds = dep.timeToStationSeconds - walkSecB,
                    catchability = classifyCatchability(dep.timeToStationSeconds, walkSecB, hasDistB)
                )
            }

        val bestCatchableA = evalA.firstOrNull { it.catchability != CatchabilityStatus.TOO_CLOSE }
        val bestCatchableB = evalB.firstOrNull { it.catchability != CatchabilityStatus.TOO_CLOSE }

        val (winnerCard, winningDep) = when {
            bestCatchableA != null && bestCatchableB != null -> {
                if (bestCatchableA.departure.timeToStationSeconds <= bestCatchableB.departure.timeToStationSeconds) {
                    cardA to bestCatchableA
                } else {
                    cardB to bestCatchableB
                }
            }
            bestCatchableA != null -> cardA to bestCatchableA
            bestCatchableB != null -> cardB to bestCatchableB
            else -> null to null
        }

        return TwinRaceComparison(
            hubA = cardA,
            hubB = cardB,
            walkMinutesA = walkMinA,
            walkMinutesB = walkMinB,
            evaluatedA = evalA,
            evaluatedB = evalB,
            winnerCard = winnerCard,
            winningDeparture = winningDep
        )
    }
}
