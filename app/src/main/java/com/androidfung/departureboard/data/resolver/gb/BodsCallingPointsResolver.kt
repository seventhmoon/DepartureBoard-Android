package com.androidfung.departureboard.data.resolver.gb

import com.androidfung.departureboard.data.model.BodsXmlParser
import com.androidfung.departureboard.data.model.CallingPoint
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.network.BodsApiService
import com.androidfung.departureboard.data.resolver.CallingPointsResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves calling points for UK Department for Transport BODS bus services.
 */
class BodsCallingPointsResolver(
    private val bodsApiService: BodsApiService
) : CallingPointsResolver {

    override fun canHandle(departure: Departure): Boolean {
        return departure.lineId.startsWith("bods-", ignoreCase = true)
    }

    override suspend fun resolveCallingPoints(departure: Departure): List<CallingPoint> = withContext(Dispatchers.IO) {
        val rawLineRef = departure.lineId.removePrefix("bods-").uppercase()
        try {
            val responseBody = bodsApiService.getVehicleDatafeedByLine(lineRef = rawLineRef)
            val xmlString = responseBody.string()
            val activities = BodsXmlParser.parse(xmlString)

            val match = activities.firstOrNull { act ->
                act.destinationName?.replace("_", " ")?.contains(departure.destinationName, ignoreCase = true) == true ||
                        act.lineRef.equals(rawLineRef, ignoreCase = true)
            } ?: activities.firstOrNull()

            val origin = match?.originName?.replace("_", " ")?.trim() ?: departure.stationName
            val dest = match?.destinationName?.replace("_", " ")?.trim() ?: departure.destinationName

            listOf(
                CallingPoint(
                    stationName = origin,
                    scheduledTime = departure.formattedActualClockTime,
                    estimatedTime = departure.formattedTimeToArrival,
                    isCurrentStation = true,
                    isDestination = false,
                    stationId = departure.stationId,
                    modes = listOf(departure.modeName)
                ),
                CallingPoint(
                    stationName = dest,
                    isCurrentStation = false,
                    isDestination = true,
                    stationId = departure.destinationStationId,
                    modes = listOf(departure.modeName)
                )
            )
        } catch (_: Exception) {
            listOf(
                CallingPoint(
                    stationName = departure.stationName,
                    scheduledTime = departure.formattedActualClockTime,
                    estimatedTime = departure.formattedTimeToArrival,
                    isCurrentStation = true,
                    isDestination = false,
                    stationId = departure.stationId,
                    modes = listOf(departure.modeName)
                ),
                CallingPoint(
                    stationName = departure.destinationName,
                    isCurrentStation = false,
                    isDestination = true,
                    stationId = departure.destinationStationId,
                    modes = listOf(departure.modeName)
                )
            )
        }
    }
}
