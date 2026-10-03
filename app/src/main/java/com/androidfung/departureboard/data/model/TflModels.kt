package com.androidfung.departureboard.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * TfL StopPoint Search Response wrapper.
 * https://api.tfl.gov.uk/StopPoint/Search/{query}
 */
@JsonClass(generateAdapter = true)
data class TflSearchResponse(
    @Json(name = "query") val query: String? = null,
    @Json(name = "total") val total: Int = 0,
    @Json(name = "matches") val matches: List<TflStopPointMatch> = emptyList()
)

/**
 * Individual matched stop/station from TfL StopPoint search.
 */
@JsonClass(generateAdapter = true)
data class TflStopPointMatch(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "modes") val modes: List<String> = emptyList(),
    @Json(name = "lat") val lat: Double? = null,
    @Json(name = "lon") val lon: Double? = null,
    @Json(name = "zone") val zone: String? = null,
    @Json(name = "icsId") val icsId: String? = null,
    @Json(name = "children") val children: List<TflStopPointMatch> = emptyList()
)

/**
 * StopPoint detail object from TfL StopPoint/{id}
 */
@JsonClass(generateAdapter = true)
data class TflStopPointDetail(
    @Json(name = "id") val id: String,
    @Json(name = "commonName") val commonName: String? = null,
    @Json(name = "children") val children: List<TflStopPointChild> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TflStopPointChild(
    @Json(name = "id") val id: String,
    @Json(name = "commonName") val commonName: String? = null,
    @Json(name = "indicator") val indicator: String? = null
)

/**
 * TfL Prediction / Arrival item from:
 * https://api.tfl.gov.uk/StopPoint/{id}/Arrivals
 */
@JsonClass(generateAdapter = true)
data class TflArrivalPrediction(
    @Json(name = "id") val id: String,
    @Json(name = "vehicleId") val vehicleId: String? = null,
    @Json(name = "naptanId") val naptanId: String? = null,
    @Json(name = "stationName") val stationName: String? = null,
    @Json(name = "lineId") val lineId: String? = null,
    @Json(name = "lineName") val lineName: String? = null,
    @Json(name = "platformName") val platformName: String? = null,
    @Json(name = "direction") val direction: String? = null,
    @Json(name = "destinationNaptanId") val destinationNaptanId: String? = null,
    @Json(name = "destinationName") val destinationName: String? = null,
    @Json(name = "towards") val towards: String? = null,
    @Json(name = "timeToStation") val timeToStation: Int = 0, // countdown in seconds
    @Json(name = "currentLocation") val currentLocation: String? = null,
    @Json(name = "expectedArrival") val expectedArrival: String? = null,
    @Json(name = "timeToLive") val timeToLive: String? = null,
    @Json(name = "modeName") val modeName: String? = null
)

/**
 * TfL Line Status item from:
 * https://api.tfl.gov.uk/Line/Mode/tube,dlr,overground,elizabeth-line/Status
 */
@JsonClass(generateAdapter = true)
data class TflLineStatusItem(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "modeName") val modeName: String? = null,
    @Json(name = "lineStatuses") val lineStatuses: List<TflLineStatusDetail> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TflLineStatusDetail(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "statusSeverity") val statusSeverity: Int = 10, // 10 = Good Service
    @Json(name = "statusSeverityDescription") val statusSeverityDescription: String = "Good Service",
    @Json(name = "reason") val reason: String? = null
)

