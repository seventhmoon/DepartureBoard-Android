package com.androidfung.departureboard.data.network.jp

import com.androidfung.departureboard.data.network.SharedNetworkConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.create
import retrofit2.http.GET
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class JpOdptTrainInformationItem(
    @param:Json(name = "odpt:railway") val railway: String? = null,
    @param:Json(name = "odpt:operator") val operator: String? = null,
    @param:Json(name = "odpt:trainInformationStatus") val statusMap: Map<String, String>? = null,
    @param:Json(name = "odpt:trainInformationText") val textMap: Map<String, String>? = null
)

interface JpOdptApiService {
    @GET("api/v4/odpt:TrainInformation")
    suspend fun getPublicTrainInformation(
        @Query("odpt:operator") operator: String = "odpt.Operator:Toei"
    ): List<JpOdptTrainInformationItem>

    @GET("api/v4/odpt:TrainInformation")
    suspend fun getKeyedTrainInformation(
        @Query("acl:consumerKey") consumerKey: String
    ): List<JpOdptTrainInformationItem>
}

object JpOdptNetworkClient {
    private const val ODPT_PUBLIC_BASE_URL = "https://api-public.odpt.org/"
    private const val ODPT_KEYED_BASE_URL = "https://api.odpt.org/"

    private val okHttpClient: OkHttpClient by lazy {
        SharedNetworkConfig.applyOfflineCache(
            SharedNetworkConfig.newBuilder(
                logTag = "OkHttp-JpOdpt",
                redactRegex = Regex("acl:consumerKey=[^&\\s]+"),
                connectTimeoutSeconds = 15,
                readTimeoutSeconds = 15
            )
        ).build()
    }

    val publicApiService: JpOdptApiService by lazy {
        SharedNetworkConfig.newRetrofit(ODPT_PUBLIC_BASE_URL, okHttpClient).create()
    }

    val keyedApiService: JpOdptApiService by lazy {
        SharedNetworkConfig.newRetrofit(ODPT_KEYED_BASE_URL, okHttpClient).create()
    }
}
