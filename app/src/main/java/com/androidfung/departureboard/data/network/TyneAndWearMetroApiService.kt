package com.androidfung.departureboard.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path

/**
 * Nexus RTI API client for Tyne and Wear Metro live platform departures.
 */
interface TyneAndWearMetroApiService {

    @Headers(
        "User-Agent: Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        "Accept: application/json"
    )
    @GET("api/times/{station}/{platform}")
    suspend fun getPlatformTimes(
        @Path("station") stationCode: String,
        @Path("platform") platform: Int
    ): List<NexusMetroDepartureItem>
}

@JsonClass(generateAdapter = true)
data class NexusMetroDepartureItem(
    @Json(name = "trn") val trn: String? = null,
    @Json(name = "dueIn") val dueIn: Int = 0,
    @Json(name = "lastEvent") val lastEvent: String? = null,
    @Json(name = "lastEventLocation") val lastEventLocation: String? = null,
    @Json(name = "lastEventTime") val lastEventTime: String? = null,
    @Json(name = "destination") val destination: String? = null
)
