package com.androidfung.departureboard.data.network.hk

import com.androidfung.departureboard.data.model.hk.HkGmbRouteResponse
import com.androidfung.departureboard.data.model.hk.HkGmbRouteStopResponse
import com.androidfung.departureboard.data.model.hk.HkGmbStopEtaResponse
import com.androidfung.departureboard.data.network.SharedNetworkConfig
import okhttp3.OkHttpClient
import retrofit2.create
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Retrofit interface & network client for HKSAR Green Minibus (GMB) Real-Time Arrival Open Data (`data.etagmb.gov.hk`).
 */
interface HkGmbApiService {
    @GET("eta/stop/{stop_id}")
    suspend fun getStopEta(
        @Path("stop_id") stopId: String
    ): HkGmbStopEtaResponse

    @GET("route/{region}/{route_code}")
    suspend fun getRouteByRegionAndCode(
        @Path("region") region: String,
        @Path("route_code") routeCode: String
    ): HkGmbRouteResponse

    @GET("route-stop/{route_id}/{route_seq}")
    suspend fun getRouteStops(
        @Path("route_id") routeId: Long,
        @Path("route_seq") routeSeq: Int = 1
    ): HkGmbRouteStopResponse
}

object HkGmbNetworkClient {
    private const val GMB_BASE_URL = "https://data.etagmb.gov.hk/"

    private val okHttpClient: OkHttpClient by lazy {
        SharedNetworkConfig.applyOfflineCache(
            SharedNetworkConfig.newBuilder(
                logTag = "OkHttp-HkGmb",
                connectTimeoutSeconds = 15,
                readTimeoutSeconds = 15
            )
        ).build()
    }

    val apiService: HkGmbApiService by lazy {
        SharedNetworkConfig.newRetrofit(GMB_BASE_URL, okHttpClient).create()
    }
}
