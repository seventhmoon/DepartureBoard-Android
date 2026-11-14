package com.androidfung.departureboard.data.model.gb

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
    @Json(name = "detachFront") val detachFront: Boolean = false,
    @Json(name = "isReverseFormation") val isReverseFormation: Boolean = false,
    @Json(name = "formation") val formation: NrFormation? = null,
    @Json(name = "origin") val origin: List<NrLocation> = emptyList(),
    @Json(name = "destination") val destination: List<NrLocation> = emptyList(),
    @Json(name = "subsequentCallingPoints") val subsequentCallingPoints: List<NrCallingPointList>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class NrFormation(
    @Json(name = "avgLoading") val avgLoading: Int? = null,
    @Json(name = "coaches") val coaches: List<NrCoach> = emptyList()
)

@JsonClass(generateAdapter = true)
data class NrCoach(
    @Json(name = "number") val number: String? = null,
    @Json(name = "coachClass") val coachClass: String? = null,
    @Json(name = "loading") val loading: Int? = null,
    @Json(name = "toilet") val toilet: NrToilet? = null
)

@JsonClass(generateAdapter = true)
data class NrToilet(
    @Json(name = "status") val status: String? = null,
    @Json(name = "value") val value: String? = null
)

@JsonClass(generateAdapter = true)
data class NrServiceDetailsResponse(
    @Json(name = "locationName") val locationName: String? = null,
    @Json(name = "crs") val crs: String? = null,
    @Json(name = "operator") val operator: String? = null,
    @Json(name = "operatorCode") val operatorCode: String? = null,
    @Json(name = "platform") val platform: String? = null,
    @Json(name = "isCancelled") val isCancelled: Boolean = false,
    @Json(name = "cancelReason") val cancelReason: String? = null,
    @Json(name = "delayReason") val delayReason: String? = null,
    @Json(name = "length") val length: Int? = null,
    @Json(name = "detachFront") val detachFront: Boolean = false,
    @Json(name = "isReverseFormation") val isReverseFormation: Boolean = false,
    @Json(name = "formation") val formation: NrFormation? = null
)

@JsonClass(generateAdapter = true)
data class NrCallingPointList(
    @Json(name = "serviceType") val serviceType: String? = null,
    @Json(name = "callingPoint") val callingPoint: List<NrCallingPoint> = emptyList()
)

@JsonClass(generateAdapter = true)
data class NrCallingPoint(
    @Json(name = "locationName") val locationName: String,
    @Json(name = "crs") val crs: String? = null,
    @Json(name = "st") val st: String? = null, // Scheduled time (e.g. "13:15")
    @Json(name = "et") val et: String? = null, // Estimated time (e.g. "On time", "13:18")
    @Json(name = "isCancelled") val isCancelled: Boolean = false
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
