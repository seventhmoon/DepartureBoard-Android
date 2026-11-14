package com.androidfung.departureboard.data.model.gb

import com.squareup.moshi.JsonClass

/**
 * Response from TfL `/BikePoint?lat=...&lon=...&radius=...` returning nearby Santander Cycles docking stations.
 */
@JsonClass(generateAdapter = true)
data class TflBikePointPlacesResponse(
    val places: List<TflBikePointPlace> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TflBikePointPlace(
    val id: String = "",
    val commonName: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val distance: Double? = null,
    val additionalProperties: List<TflAdditionalProperty> = emptyList()
) {
    fun intProperty(key: String): Int =
        additionalProperties.firstOrNull { it.key.equals(key, ignoreCase = true) }
            ?.value?.trim()?.toIntOrNull() ?: 0
}

/**
 * Station-level disruption item returned by TfL `/StopPoint/{ids}/Disruption` (including lift and step-free outages).
 */
@JsonClass(generateAdapter = true)
data class TflStopPointDisruption(
    val atcoCode: String? = null,
    val commonName: String? = null,
    val type: String? = null,
    val description: String? = null,
    val appearance: String? = null
)

/**
 * Station lift outage returned by TfL `/Disruptions/Lifts/v2`.
 */
@JsonClass(generateAdapter = true)
data class TflLiftDisruption(
    val icsCode: String? = null,
    val naptanCode: String? = null,
    val stationName: String? = null,
    val outageStartArea: String? = null,
    val outageEndArea: String? = null,
    val message: String? = null
)

/**
 * Live station crowding response from TfL `/crowding/Live/{naptan}`.
 */
@JsonClass(generateAdapter = true)
data class TflCrowdingResponse(
    val naptan: String? = null,
    val percentageOfBaseline: Double? = null,
    val dataAvailable: Boolean = false,
    val timeUtc: String? = null
)
