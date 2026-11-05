package com.androidfung.departureboard.data.repository.region.hk

import com.androidfung.departureboard.data.model.CallingPoint
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.hk.topology.HkMtrRoutes
import com.androidfung.departureboard.data.model.isHkMtr
import com.androidfung.departureboard.data.model.parseHkMtrStationId
import com.androidfung.departureboard.data.topology.LinearRouteCallingPointsResolver
import java.time.Instant

/**
 * Resolves calling points for Hong Kong MTR heavy rail lines using [HkMtrRoutes]
 * and the shared [LinearRouteCallingPointsResolver].
 */
class HkMtrCallingPointsResolver : LinearRouteCallingPointsResolver(HkMtrRoutes) {

    override fun canHandle(departure: Departure): Boolean = departure.isHkMtr

    override suspend fun resolveCallingPoints(departure: Departure): List<CallingPoint> {
        val ref = departure.stationId.parseHkMtrStationId()
        return resolveCallingPointsForTrip(
            lineCode = departure.lineId.ifBlank { ref.lineCode ?: "ISL" }.uppercase(),
            originStationIdentifier = ref.stationCode.ifBlank { departure.stationName },
            destStationIdentifier = departure.destinationName,
            departureEpochSeconds = Instant.now().epochSecond + departure.timeToStationSeconds,
            fallbackCallingPoints = departure.callingPoints
        )
    }
}
