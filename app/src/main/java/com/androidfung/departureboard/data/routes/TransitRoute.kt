package com.androidfung.departureboard.data.routes

import com.androidfung.departureboard.data.model.TransitDirection
import com.androidfung.departureboard.data.repository.direction.DirectionResolver
import com.androidfung.departureboard.data.resolver.LoopRouteResolver

/**
 * Base abstraction for a transit route, containing the sequence of stations.
 * Subclasses handle linear versus loop topologies.
 */
sealed class TransitRoute<T> {
    abstract val lineId: String
    abstract val stations: List<T>
    abstract val directionResolver: DirectionResolver

    /**
     * Slices the route from origin to destination.
     */
    abstract fun slice(origin: T, dest: T, preferredDirection: TransitDirection? = null): List<T>

    /**
     * Resolves the direction (e.g. INBOUND/OUTBOUND or CLOCKWISE/ANTI_CLOCKWISE) between two stations.
     */
    abstract fun resolveDirection(
        origin: T,
        dest: T,
        rawDirection: String? = null
    ): TransitDirection?
}

/**
 * A standard point-to-point transit route.
 */
data class LinearRoute<T>(
    override val lineId: String,
    override val stations: List<T>,
    override val directionResolver: DirectionResolver
) : TransitRoute<T>() {

    override fun slice(origin: T, dest: T, preferredDirection: TransitDirection?): List<T> {
        val startIdx = stations.indexOf(origin)
        val endIdx = stations.indexOf(dest)
        if (startIdx == -1 || endIdx == -1) return emptyList()
        
        return if (startIdx <= endIdx) {
            stations.subList(startIdx, endIdx + 1)
        } else {
            stations.subList(endIdx, startIdx + 1).reversed()
        }
    }

    override fun resolveDirection(
        origin: T,
        dest: T,
        rawDirection: String?
    ): TransitDirection? {
        val startIdx = stations.indexOf(origin)
        val endIdx = stations.indexOf(dest)
        if (startIdx == -1 || endIdx == -1 || startIdx == endIdx) return null
        
        return if (startIdx < endIdx) TransitDirection.OUTBOUND else TransitDirection.INBOUND
    }
}

/**
 * A circular transit route (e.g. Yamanote Line).
 */
data class LoopRoute<T>(
    override val lineId: String,
    override val stations: List<T>,
    override val directionResolver: DirectionResolver
) : TransitRoute<T>() {

    override fun slice(origin: T, dest: T, preferredDirection: TransitDirection?): List<T> {
        val startIdx = stations.indexOf(origin)
        val endIdx = stations.indexOf(dest)
        if (startIdx == -1 || endIdx == -1) return emptyList()

        // Slices circular string sequence based on shortest path or forced direction
        val stringStations = stations.map { it.toString() }
        val slicedStrings = LoopRouteResolver.slice(stringStations, startIdx, endIdx, preferredDirection)
        
        // Map back to original generic type T (usually enum or String)
        return slicedStrings.mapNotNull { strId -> 
            stations.find { it.toString() == strId } 
        }
    }

    override fun resolveDirection(
        origin: T,
        dest: T,
        rawDirection: String?
    ): TransitDirection? {
        val startIdx = stations.indexOf(origin)
        val endIdx = stations.indexOf(dest)
        return LoopRouteResolver.resolveDirection(stations.size, startIdx, endIdx, rawDirection)
    }
}
