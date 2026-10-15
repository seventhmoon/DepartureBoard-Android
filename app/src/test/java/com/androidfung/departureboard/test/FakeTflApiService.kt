package com.androidfung.departureboard.test

import com.androidfung.departureboard.data.model.TflArrivalPrediction
import com.androidfung.departureboard.data.model.TflLineRouteResponse
import com.androidfung.departureboard.data.model.TflLineStatusItem
import com.androidfung.departureboard.data.model.TflRouteSequenceResponse
import com.androidfung.departureboard.data.model.TflSearchResponse
import com.androidfung.departureboard.data.model.TflStopPointChild
import com.androidfung.departureboard.data.model.TflStopPointDetail
import com.androidfung.departureboard.data.model.TflStopPointsResponse
import com.androidfung.departureboard.data.model.TflTimetableResponse
import com.androidfung.departureboard.data.network.TflApiService
import java.io.IOException

/**
 * Complete, configurable test double for [TflApiService].
 */
open class FakeTflApiService(
    var searchResult: TflSearchResponse = TflSearchResponse(total = 0, matches = emptyList()),
    var arrivalsResult: List<TflArrivalPrediction> = emptyList(),
    var lineArrivalsResult: List<TflArrivalPrediction> = emptyList(),
    var lineStatusesResult: List<TflLineStatusItem> = emptyList(),
    var routeSequenceResult: TflRouteSequenceResponse? = null,
    var timetableResponse: TflTimetableResponse = TflTimetableResponse(),
    var stopPointDetailResult: TflStopPointDetail? = null,
    var lineStopPointsResult: List<TflStopPointChild> = emptyList(),
    var nearbyStopPointsResult: TflStopPointsResponse = TflStopPointsResponse(),
    var shouldThrow: Boolean = false,
    var throwException: Exception = IOException("Network error")
) : TflApiService {

    override suspend fun searchStations(query: String, modes: String, maxResults: Int): TflSearchResponse {
        if (shouldThrow) throw throwException
        return searchResult
    }

    override suspend fun getArrivals(stationId: String): List<TflArrivalPrediction> {
        if (shouldThrow) throw throwException
        return arrivalsResult
    }

    override suspend fun getLineArrivals(lineId: String): List<TflArrivalPrediction> {
        if (shouldThrow) throw throwException
        return lineArrivalsResult
    }

    override suspend fun getLineStatuses(modes: String): List<TflLineStatusItem> {
        if (shouldThrow) throw throwException
        return lineStatusesResult
    }

    override suspend fun getStopPointDetail(stopPointId: String): TflStopPointDetail {
        if (shouldThrow) throw throwException
        return stopPointDetailResult ?: TflStopPointDetail(id = stopPointId)
    }

    override suspend fun getLineStopPoints(lineId: String): List<TflStopPointChild> {
        if (shouldThrow) throw throwException
        return lineStopPointsResult
    }

    override suspend fun getLineRoute(lineId: String): TflLineRouteResponse {
        if (shouldThrow) throw throwException
        return TflLineRouteResponse(id = lineId, name = lineId)
    }

    override suspend fun getLineRouteSequence(lineId: String, direction: String): TflRouteSequenceResponse {
        if (shouldThrow) throw throwException
        return routeSequenceResult ?: TflRouteSequenceResponse(lineId = lineId)
    }

    override suspend fun getNearbyStopPoints(
        lat: Double,
        lon: Double,
        stopTypes: String,
        radiusMeters: Int,
        useHierarchy: Boolean
    ): TflStopPointsResponse {
        if (shouldThrow) throw throwException
        return nearbyStopPointsResult
    }

    override suspend fun getLineTimetable(lineId: String, fromStopPointId: String): TflTimetableResponse {
        if (shouldThrow) throw throwException
        return timetableResponse
    }
}
