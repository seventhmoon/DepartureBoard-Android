package com.androidfung.departureboard.data.network.sg

import com.androidfung.departureboard.BuildConfig
import com.androidfung.departureboard.data.network.SharedNetworkConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.create
import retrofit2.http.GET
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class SgLtaBusArrivalResponse(
    @param:Json(name = "BusStopCode") val busStopCode: String? = null,
    @param:Json(name = "Services") val services: List<SgLtaBusService> = emptyList()
)

@JsonClass(generateAdapter = true)
data class SgLtaBusService(
    @param:Json(name = "ServiceNo") val serviceNo: String = "",
    @param:Json(name = "Operator") val operatorCode: String? = null,
    @param:Json(name = "NextBus") val nextBus: SgLtaNextBus? = null,
    @param:Json(name = "NextBus2") val nextBus2: SgLtaNextBus? = null,
    @param:Json(name = "NextBus3") val nextBus3: SgLtaNextBus? = null
)

@JsonClass(generateAdapter = true)
data class SgLtaNextBus(
    @param:Json(name = "DestinationCode") val destinationCode: String? = null,
    @param:Json(name = "EstimatedArrival") val estimatedArrival: String? = null,
    @param:Json(name = "Load") val load: String? = null, // SEA, SDA, LSD
    @param:Json(name = "Feature") val feature: String? = null, // WAB (Wheelchair Accessible Bus)
    @param:Json(name = "Type") val deckType: String? = null // SD, DD, BD
)

@JsonClass(generateAdapter = true)
data class SgLtaPcdCrowdResponse(
    val value: List<SgLtaStationCrowd> = emptyList()
)

@JsonClass(generateAdapter = true)
data class SgLtaStationCrowd(
    @param:Json(name = "Station") val stationCode: String = "",
    @param:Json(name = "CrowdLevel") val crowdLevel: String? = null // l, m, h
)

@JsonClass(generateAdapter = true)
data class SgLtaTrainServiceAlertsResponse(
    val value: SgLtaTrainServiceAlertValue? = null
)

@JsonClass(generateAdapter = true)
data class SgLtaTrainServiceAlertValue(
    @param:Json(name = "Status") val status: Int = 1,
    @param:Json(name = "AffectedSegments") val affectedSegments: List<SgLtaAffectedSegment> = emptyList(),
    @param:Json(name = "Message") val messages: List<SgLtaAlertMessage> = emptyList()
)

@JsonClass(generateAdapter = true)
data class SgLtaAffectedSegment(
    @param:Json(name = "Line") val line: String? = null,
    @param:Json(name = "Direction") val direction: String? = null,
    @param:Json(name = "Stations") val stations: String? = null,
    @param:Json(name = "From") val from: String? = null,
    @param:Json(name = "To") val to: String? = null,
    @param:Json(name = "FreePublicBus") val freePublicBus: String? = null,
    @param:Json(name = "FreeMRTShuttle") val freeMrtShuttle: String? = null,
    @param:Json(name = "MRTShuttleDirection") val mrtShuttleDirection: String? = null
)

@JsonClass(generateAdapter = true)
data class SgLtaAlertMessage(
    @param:Json(name = "Content") val content: String? = null,
    @param:Json(name = "CreatedDate") val createdDate: String? = null
)

@JsonClass(generateAdapter = true)
data class SgLtaFacilitiesMaintenanceResponse(
    val value: List<SgLtaFacilityMaintenance> = emptyList()
)

@JsonClass(generateAdapter = true)
data class SgLtaFacilityMaintenance(
    @param:Json(name = "StationCode") val stationCode: String? = null,
    @param:Json(name = "StationName") val stationName: String? = null,
    @param:Json(name = "FacilityType") val facilityType: String? = null,
    @param:Json(name = "FacilityCode") val facilityCode: String? = null,
    @param:Json(name = "Location") val location: String? = null,
    @param:Json(name = "Status") val status: String? = null,
    @param:Json(name = "EstimatedCompletionDate") val estimatedCompletionDate: String? = null
)

interface SgLtaApiService {
    @GET("v3/BusArrival")
    suspend fun getBusArrival(
        @Query("BusStopCode") busStopCode: String
    ): SgLtaBusArrivalResponse

    @GET("PCDRealTime")
    suspend fun getPlatformCrowdDensity(
        @Query("TrainLine") trainLine: String
    ): SgLtaPcdCrowdResponse

    @GET("TrainServiceAlerts")
    suspend fun getTrainServiceAlerts(): SgLtaTrainServiceAlertsResponse

    @GET("v2/FacilitiesMaintenance")
    suspend fun getFacilitiesMaintenance(): SgLtaFacilitiesMaintenanceResponse
}

object SgLtaNetworkClient {
    private const val LTA_BASE_URL = "https://datamall2.mytransport.sg/ltaodataservice/"

    private val okHttpClient: OkHttpClient by lazy {
        SharedNetworkConfig.applyOfflineCache(
            SharedNetworkConfig.newBuilder(
                logTag = "OkHttp-SgLta",
                connectTimeoutSeconds = 15,
                readTimeoutSeconds = 15
            ).addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("AccountKey", BuildConfig.LTA_DATAMALL_API_KEY)
                    .header("accept", "application/json")
                    .build()
                chain.proceed(req)
            }
        ).build()
    }

    val apiService: SgLtaApiService by lazy {
        SharedNetworkConfig.newRetrofit(LTA_BASE_URL, okHttpClient).create()
    }
}
