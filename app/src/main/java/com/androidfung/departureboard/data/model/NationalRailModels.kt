package com.androidfung.departureboard.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Rail Data Marketplace / NRE OpenLDBWS GetDepartureBoard response models.
 */
@JsonClass(generateAdapter = true)
data class NrBoardResponse(
    @Json(name = "locationName") val locationName: String? = null,
    @Json(name = "crs") val crs: String? = null,
    @Json(name = "platformAvailable") val platformAvailable: Boolean = true,
    @Json(name = "areServicesAvailable") val areServicesAvailable: Boolean = true,
    @Json(name = "trainServices") val trainServices: List<NrTrainService>? = emptyList(),
    @Json(name = "busServices") val busServices: List<NrTrainService>? = emptyList(),
    @Json(name = "nrccMessages") val nrccMessages: List<NrMessage>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class NrTrainService(
    @Json(name = "serviceID") val serviceId: String,
    @Json(name = "std") val std: String? = null, // Scheduled time of departure (e.g. "13:00")
    @Json(name = "etd") val etd: String? = null, // Estimated time (e.g. "On time", "13:05", "Cancelled", "Delayed")
    @Json(name = "platform") val platform: String? = null,
    @Json(name = "operator") val operator: String? = null,
    @Json(name = "operatorCode") val operatorCode: String? = null,
    @Json(name = "isCancelled") val isCancelled: Boolean = false,
    @Json(name = "cancelReason") val cancelReason: String? = null,
    @Json(name = "delayReason") val delayReason: String? = null,
    @Json(name = "serviceType") val serviceType: String? = "train",
    @Json(name = "length") val length: Int? = null,
    @Json(name = "origin") val origin: List<NrLocation> = emptyList(),
    @Json(name = "destination") val destination: List<NrLocation> = emptyList()
)

@JsonClass(generateAdapter = true)
data class NrLocation(
    @Json(name = "locationName") val locationName: String,
    @Json(name = "crs") val crs: String? = null,
    @Json(name = "via") val via: String? = null
)

@JsonClass(generateAdapter = true)
data class NrMessage(
    @Json(name = "value") val value: String? = null
)
