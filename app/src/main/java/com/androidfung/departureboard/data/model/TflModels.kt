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
    @Json(name = "towards") val towards: String? = null,
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
    @Json(name = "indicator") val indicator: String? = null,
    @Json(name = "stopLetter") val stopLetter: String? = null,
    @Json(name = "children") val children: List<TflStopPointChild> = emptyList(),
    @Json(name = "lines") val lines: List<TflStopPointLineIdentifier> = emptyList(),
    @Json(name = "lineModeGroups") val lineModeGroups: List<TflLineModeGroup> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TflLineModeGroup(
    @Json(name = "modeName") val modeName: String? = null,
    @Json(name = "lineIdentifier") val lineIdentifier: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TflStopPointLineIdentifier(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String? = null,
    @Json(name = "modeName") val modeName: String? = null
)

@JsonClass(generateAdapter = true)
data class TflAdditionalProperty(
    @Json(name = "category") val category: String? = null,
    @Json(name = "key") val key: String = "",
    @Json(name = "value") val value: String = ""
)

@JsonClass(generateAdapter = true)
data class TflStopPointChild(
    @Json(name = "id") val id: String,
    @Json(name = "commonName") val commonName: String? = null,
    @Json(name = "indicator") val indicator: String? = null,
    @Json(name = "stopLetter") val stopLetter: String? = null,
    @Json(name = "towards") val towards: String? = null,
    @Json(name = "modes") val modes: List<String> = emptyList(),
    @Json(name = "lat") val lat: Double? = null,
    @Json(name = "lon") val lon: Double? = null,
    @Json(name = "additionalProperties") val additionalProperties: List<TflAdditionalProperty> = emptyList()
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

/**
 * TfL Line Route response for querying line sections and endpoints.
 */
@JsonClass(generateAdapter = true)
data class TflLineRouteResponse(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "modeName") val modeName: String? = null,
    @Json(name = "routeSections") val routeSections: List<TflRouteSection> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TflRouteSection(
    @Json(name = "name") val name: String = "",
    @Json(name = "direction") val direction: String? = null,
    @Json(name = "originationName") val originationName: String? = null,
    @Json(name = "destinationName") val destinationName: String? = null
)

/**
 * TfL Route Sequence response from:
 * https://api.tfl.gov.uk/Line/{id}/Route/Sequence/{direction}
 */
@JsonClass(generateAdapter = true)
data class TflRouteSequenceResponse(
    @Json(name = "lineId") val lineId: String,
    @Json(name = "lineName") val lineName: String? = null,
    @Json(name = "direction") val direction: String? = null,
    @Json(name = "stopPointSequences") val stopPointSequences: List<TflStopPointSequence> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TflStopPointSequence(
    @Json(name = "branchId") val branchId: Int = 0,
    @Json(name = "nextBranchIds") val nextBranchIds: List<Int> = emptyList(),
    @Json(name = "prevBranchIds") val prevBranchIds: List<Int> = emptyList(),
    @Json(name = "direction") val direction: String? = null,
    @Json(name = "stopPoint") val stopPoint: List<TflMatchedStop> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TflMatchedStop(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "stationId") val stationId: String? = null,
    @Json(name = "lat") val lat: Double? = null,
    @Json(name = "lon") val lon: Double? = null
)
