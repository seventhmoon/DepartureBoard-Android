package com.androidfung.departureboard.data.repository.region.au

import com.androidfung.departureboard.data.gtfs.GtfsCallingPointsResolver
import com.androidfung.departureboard.data.model.CallingPoint
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.au.topology.AuSydneyRoutes
import com.androidfung.departureboard.data.model.isAuTransitId
import com.androidfung.departureboard.data.topology.LinearRouteCallingPointsResolver

/**
 * Resolves calling points for Australia (Sydney) departures using GTFS trips when available,
 * with fallback to [AuSydneyRoutes].
 */
class AuCallingPointsResolver(
    private val gtfsResolver: GtfsCallingPointsResolver? = null
) : LinearRouteCallingPointsResolver(AuSydneyRoutes) {

    override fun canHandle(departure: Departure): Boolean {
        return departure.stationId.isAuTransitId ||
                departure.id.startsWith("AU_") ||
                departure.id.startsWith("NSW_")
    }

    override suspend fun resolveCallingPoints(departure: Departure): List<CallingPoint> {
        if (gtfsResolver != null && gtfsResolver.canHandle(departure)) {
            val points = gtfsResolver.resolveCallingPoints(departure)
            if (points.isNotEmpty()) {
                return points
            }
        }
        return super.resolveCallingPoints(departure)
    }
}
