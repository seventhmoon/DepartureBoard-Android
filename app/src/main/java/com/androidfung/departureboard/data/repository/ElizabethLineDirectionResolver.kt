package com.androidfung.departureboard.data.repository

import com.androidfung.departureboard.data.model.TransitDirection
import com.androidfung.departureboard.data.routes.ElizabethLineRoute

/**
 * Route-sequence based direction resolver for the Elizabeth line (Eastbound vs Westbound).
 * Uses the centralized [ElizabethLineRoute] station sequence.
 */
internal object ElizabethLineDirectionResolver : LinearCorridorDirectionResolver(
    forwardDirection = TransitDirection.EASTBOUND,
    reverseDirection = TransitDirection.WESTBOUND,
    stationsOrder = ElizabethLineRoute.STATIONS
)
