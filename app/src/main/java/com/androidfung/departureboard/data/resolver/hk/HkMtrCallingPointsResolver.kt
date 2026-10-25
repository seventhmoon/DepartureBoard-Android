package com.androidfung.departureboard.data.resolver.hk

import com.androidfung.departureboard.data.model.CallingPoint
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.HkMtrRoutes
import com.androidfung.departureboard.data.resolver.CallingPointsResolver

/**
 * Resolves calling points for Hong Kong MTR heavy rail lines using [HkMtrRoutes].
 */
class HkMtrCallingPointsResolver : CallingPointsResolver {

    override fun canHandle(departure: Departure): Boolean {
        return departure.stationId.startsWith("HK_MTR_", ignoreCase = true) ||
                departure.id.startsWith("HK_MTR_", ignoreCase = true)
    }

    override suspend fun resolveCallingPoints(departure: Departure): List<CallingPoint> {
        val parts = departure.stationId.removePrefix("HK_MTR_").split("_")
        val sta = parts.getOrNull(1) ?: (if (parts.size == 1) parts[0] else "")
        val line = departure.lineId.uppercase()

        return HkMtrRoutes.resolveCallingPoints(
            lineCode = line,
            originSta = sta,
            destStaOrName = departure.destinationName
        )
    }
}
