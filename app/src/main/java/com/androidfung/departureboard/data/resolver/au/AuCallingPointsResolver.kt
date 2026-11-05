package com.androidfung.departureboard.data.resolver.au

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.au.AuSydneyRoutes
import com.androidfung.departureboard.data.model.isAuTransitId
import com.androidfung.departureboard.data.topology.LinearRouteCallingPointsResolver

/**
 * Resolves calling points for Australia (Sydney) departures using [AuSydneyRoutes].
 */
class AuCallingPointsResolver : LinearRouteCallingPointsResolver(AuSydneyRoutes) {

    override fun canHandle(departure: Departure): Boolean {
        return departure.stationId.isAuTransitId ||
                departure.id.startsWith("AU_") ||
                departure.id.startsWith("NSW_")
    }
}
