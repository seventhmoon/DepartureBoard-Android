package com.androidfung.departureboard.data.network.au

import com.androidfung.departureboard.BuildConfig
import com.androidfung.departureboard.data.network.SharedNetworkConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.create
import retrofit2.http.GET
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class AuTfnswDepartureMonResponse(
    val stopEvents: List<AuTfnswStopEvent> = emptyList()
)

@JsonClass(generateAdapter = true)
data class AuTfnswStopEvent(
    val departureTimePlanned: String? = null,
    val departureTimeEstimated: String? = null,
    val location: AuTfnswStopLocation? = null,
    val transportation: AuTfnswTransportation? = null,
    val isRealtimeControlled: Boolean = false
)

@JsonClass(generateAdapter = true)
data class AuTfnswStopLocation(
    val id: String? = null,
    val name: String? = null,
    val disassembledName: String? = null
)

@JsonClass(generateAdapter = true)
data class AuTfnswProduct(
    @param:Json(name = "class")
    val productClass: Int? = null,
    val name: String? = null
)

@JsonClass(generateAdapter = true)
data class AuTfnswTransportation(
    val id: String? = null,
    val number: String? = null,
    val disassembledName: String? = null,
    val product: AuTfnswProduct? = null,
    val destination: AuTfnswDestination? = null
)

@JsonClass(generateAdapter = true)
data class AuTfnswDestination(
    val id: String? = null,
    val name: String? = null
)

interface AuTfnswApiService {
    @GET("departure_mon")
    suspend fun getDepartures(
        @Query("name_dm") stopId: String,
        @Query("outputFormat") outputFormat: String = "rapidJSON",
        @Query("coordOutputFormat") coordOutputFormat: String = "EPSG:4326",
        @Query("mode") mode: String = "direct",
        @Query("type_dm") typeDm: String = "stop",
        @Query("TfNSWDM") tfnswDm: String = "true",
        @Query("version") version: String = "10.2.1.42"
    ): AuTfnswDepartureMonResponse
}

object AuTfnswNetworkClient {
    private const val TFNSW_BASE_URL = "https://api.transport.nsw.gov.au/v1/tp/"

    private val okHttpClient: OkHttpClient by lazy {
        SharedNetworkConfig.applyOfflineCache(
            SharedNetworkConfig.newBuilder(
                logTag = "OkHttp-AuTfnsw",
                connectTimeoutSeconds = 15,
                readTimeoutSeconds = 15
            ).addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("Authorization", "apikey ${BuildConfig.TFNSW_API_KEY}")
                    .header("accept", "application/json")
                    .build()
                chain.proceed(req)
            }
        ).build()
    }

    val apiService: AuTfnswApiService by lazy {
        SharedNetworkConfig.newRetrofit(TFNSW_BASE_URL, okHttpClient).create()
    }
}
