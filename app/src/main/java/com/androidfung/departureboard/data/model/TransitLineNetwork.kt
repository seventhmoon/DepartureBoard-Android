package com.androidfung.departureboard.data.model

import com.androidfung.departureboard.data.routes.TransitRoute

/**
 * Interface that defines the route topology (either a linear or a circular/loop line)
 * for a transit network. Used by local resolvers and routing engines to determine
 * direction and calling points for static metro/rail lines without hitting a live backend API.
 */
interface TransitLineNetwork {
    /**
     * Map of Route ID (or line code) to its explicit `TransitRoute` data model.
     * Use `LinearRoute` for point-to-point sequences and `LoopRoute` for circular lines.
     */
    val routeConfigs: Map<String, TransitRoute<String>>

    /**
     * Optional explicit direction resolvers for legacy or hybrid networks.
     * By default, it delegates entirely to [routeConfigs].
     */
    val resolvers: Map<String, com.androidfung.departureboard.data.repository.direction.DirectionResolver>
        get() = routeConfigs.mapValues { it.value.directionResolver }
}
