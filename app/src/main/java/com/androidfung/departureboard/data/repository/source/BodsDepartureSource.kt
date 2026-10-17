package com.androidfung.departureboard.data.repository.source

import com.androidfung.departureboard.data.model.BodsXmlParser
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.network.BodsApiService

/**
 * Departure source for Department for Transport (DfT) Bus Open Data Service (BODS) live vehicle monitoring.
 * Strictly queries regional bus stops outside London (prefix 210 or non-TfL bus stops).
 */
class BodsDepartureSource(
    private val bodsApiService: BodsApiService,
    private val stationResolver: suspend (String) -> Station?
) : DepartureSource {

    override fun canHandle(stationId: String, stationName: String, isBusStop: Boolean): Boolean {
        if (!isBusStop) return false
        return stationId.startsWith("210") || !stationId.startsWith("490")
    }

    override suspend fun fetchDepartures(
        stationId: String,
        stationName: String,
        isBusStop: Boolean
    ): Result<List<Departure>> {
        if (!canHandle(stationId, stationName, isBusStop)) {
            return Result.success(emptyList())
        }

        return try {
            val station = stationResolver(stationId)
            val lat = station?.lat
            val lon = station?.lon
            if (lat == null || lon == null) {
                return Result.success(emptyList())
            }

            // Query BODS bounding box around station (+/- 0.03 deg ~ 3.3km)
            val minLat = lat - 0.03
            val maxLat = lat + 0.03
            val minLon = lon - 0.04
            val maxLon = lon + 0.04
            val bbox = "$minLon,$minLat,$maxLon,$maxLat"

            val responseBody = bodsApiService.getVehicleDatafeed(boundingBox = bbox)
            val xmlString = responseBody.string()
            val activities = BodsXmlParser.parse(xmlString)

            val departures = activities.map { activity ->
                activity.toDeparture(stationId, stationName, lat, lon)
            }.sortedBy { it.timeToStationSeconds }

            Result.success(departures)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
