package com.androidfung.departureboard.data.resolver.sg

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.isSgTransitId
import com.androidfung.departureboard.data.model.sg.SgMrtRoutes
import com.androidfung.departureboard.data.topology.LinearRouteCallingPointsResolver

/**
 * Resolves calling points for Singapore MRT departures using [SgMrtRoutes].
 */
class SgCallingPointsResolver : LinearRouteCallingPointsResolver(SgMrtRoutes) {

    override fun canHandle(departure: Departure): Boolean {
        return departure.stationId.isSgTransitId ||
                departure.id.startsWith("SG_MRT_")
    }
}
