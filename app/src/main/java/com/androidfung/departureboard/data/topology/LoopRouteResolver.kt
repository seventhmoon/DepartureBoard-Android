package com.androidfung.departureboard.data.topology

import com.androidfung.departureboard.data.model.TransitDirection

/**
 * Resolves station slicing and direction for circular/loop transit lines
 * (e.g. Tokyo Yamanote line, Tyne and Wear Metro Yellow line, Singapore Circle line).
 */
object LoopRouteResolver {

    /**
     * Slices an ordered circular station sequence from [originIdx] to [destIdx].
     *
     * @param stations Canonical circular station sequence without repeating the start station at the end.
     * @param originIdx Index of the starting station (0 <= originIdx < N).
     * @param destIdx Index of the destination station (0 <= destIdx < N).
     * @param preferredDirection Explicit direction (CLOCKWISE / ANTI_CLOCKWISE), or null to pick the shortest route.
     * @return Ordered list of stations from origin to destination along the loop.
     */
    fun slice(
        stations: List<String>,
        originIdx: Int,
        destIdx: Int,
        preferredDirection: TransitDirection? = null
    ): List<String> {
        val n = stations.size
        if (n == 0) return emptyList()
        if (originIdx !in stations.indices) return emptyList()
        if (destIdx !in stations.indices || originIdx == destIdx) return listOf(stations[originIdx])

        val clockwiseDist = (destIdx - originIdx + n) % n
        val counterClockwiseDist = (originIdx - destIdx + n) % n

        val goClockwise = when (preferredDirection) {
            TransitDirection.CLOCKWISE -> true
            TransitDirection.ANTI_CLOCKWISE -> false
            else -> clockwiseDist <= counterClockwiseDist // Take shortest path
        }

        val stepCount = if (goClockwise) clockwiseDist else counterClockwiseDist
        val stepDelta = if (goClockwise) 1 else -1

        return (0..stepCount).map { step ->
            val idx = Math.floorMod(originIdx + step * stepDelta, n)
            stations[idx]
        }
    }

    /**
     * Resolves whether the trip from [originIdx] to [destIdx] is Clockwise or Anti-Clockwise.
     *
     * @param totalStops Number of stations on the ring.
     * @param originIdx Index of departure station.
     * @param destIdx Index of destination station.
     * @param rawDirection Optional raw direction / platform string hint.
     */
    fun resolveDirection(
        totalStops: Int,
        originIdx: Int,
        destIdx: Int,
        rawDirection: String? = null
    ): TransitDirection? {
        if (totalStops <= 1 || originIdx !in 0 until totalStops || destIdx !in 0 until totalStops || originIdx == destIdx) {
            return null
        }

        // Check if raw direction hint is provided (e.g. "inner", "outer", "clockwise", "内回り", "外回り")
        TransitDirection.fromString(rawDirection)?.let { return it }

        val cwDist = (destIdx - originIdx + totalStops) % totalStops
        val ccwDist = (originIdx - destIdx + totalStops) % totalStops
        return if (cwDist <= ccwDist) TransitDirection.CLOCKWISE else TransitDirection.ANTI_CLOCKWISE
    }
}
