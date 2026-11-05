package com.androidfung.departureboard.data.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.create

/**
 * Retrofit network client for Hong Kong MTR open data API.
 *
 * Shared boilerplate (pool, dispatcher, logging, offline cache) lives in [SharedNetworkConfig].
 */
object HkMtrNetworkClient {

    private const val MTR_BASE_URL = "https://rt.data.gov.hk/"

    private val okHttpClient: OkHttpClient by lazy {
        SharedNetworkConfig.applyOfflineCache(
            SharedNetworkConfig.newBuilder(
                logTag = "OkHttp-HkMtr",
                connectTimeoutSeconds = 15,
                readTimeoutSeconds = 15
            )
        ).build()
    }

    val apiService: HkMtrApiService by lazy {
        SharedNetworkConfig.newRetrofit(MTR_BASE_URL, okHttpClient).create()
    }
}
