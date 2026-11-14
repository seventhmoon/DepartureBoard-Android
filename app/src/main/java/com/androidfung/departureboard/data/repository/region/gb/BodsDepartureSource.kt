package com.androidfung.departureboard.data.repository.region.gb

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.gb.BodsVehicleActivity
import com.androidfung.departureboard.data.model.gb.BodsXmlParser
import com.androidfung.departureboard.data.model.isBodsTransitId
import com.androidfung.departureboard.data.network.gb.BodsApiService
import com.androidfung.departureboard.data.repository.DepartureSource
import com.androidfung.departureboard.util.runSuspendCatching

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
        val id = stationId.uppercase()
        // Handle all DfT regional bus prefixes outside London (or explicit BODS queries)
        return com.androidfung.departureboard.data.model.gb.AtcoAreaRegistry.isRegionalBusStop(id) ||
                stationId.isBodsTransitId ||
                (!com.androidfung.departureboard.data.model.gb.AtcoAreaRegistry.isLondonBusStop(id) && !id.startsWith("9"))
    }

    override suspend fun fetchDepartures(
        stationId: String,
        stationName: String,
        isBusStop: Boolean
    ): Result<List<Departure>> {
        if (!canHandle(stationId, stationName, isBusStop)) {
            return Result.success(emptyList())
        }

        return runSuspendCatching {
            val station = stationResolver(stationId)
            val lat = station?.lat
            val lon = station?.lon
            if (lat == null || lon == null) {
                return@runSuspendCatching emptyList()
            }

            // Query BODS bounding box around station (+/- 0.03 deg ~ 3.3km)
            val minLat = lat - 0.03
            val maxLat = lat + 0.03
            val minLon = lon - 0.04
            val maxLon = lon + 0.04
            val bbox = "$minLon,$minLat,$maxLon,$maxLat"

            val activities = bodsApiService.getVehicleDatafeed(boundingBox = bbox)
                .byteStream()
                .use { stream -> BodsXmlParser.parseStream(stream) }

            filterActivitiesForStation(activities, station)
                .map { activity -> activity.toDeparture(stationId, stationName, lat, lon) }
                .sortedBy { it.timeToStationSeconds }
        }
    }

    internal fun filterActivitiesForStation(
        activities: List<BodsVehicleActivity>,
        station: Station
    ): List<BodsVehicleActivity> {
        val knownLineTokens = station.lines
            .flatMap { listOf(it.id.removePrefix("bods-"), it.name) }
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()

        if (knownLineTokens.isEmpty()) return activities
        val matched = activities.filter { activity ->
            val ref = activity.lineRef.trim().lowercase()
            val pub = activity.publishedLineName.trim().lowercase()
            ref in knownLineTokens || pub in knownLineTokens
        }
        return matched.ifEmpty { activities }
    }
}
