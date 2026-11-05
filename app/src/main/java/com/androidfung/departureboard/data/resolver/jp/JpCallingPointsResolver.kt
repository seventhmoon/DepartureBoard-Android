package com.androidfung.departureboard.data.resolver.jp

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.isJpTransitId
import com.androidfung.departureboard.data.model.jp.JpTokyoRoutes
import com.androidfung.departureboard.data.topology.LinearRouteCallingPointsResolver

/**
 * Resolves calling points for Japan (Tokyo) departures using [JpTokyoRoutes].
 */
class JpCallingPointsResolver : LinearRouteCallingPointsResolver(JpTokyoRoutes) {

    override fun canHandle(departure: Departure): Boolean {
        return departure.stationId.isJpTransitId ||
                departure.id.startsWith("JP_") ||
                departure.id.startsWith("TYO_")
    }
}
