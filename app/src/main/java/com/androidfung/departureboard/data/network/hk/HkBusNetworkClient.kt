package com.androidfung.departureboard.data.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.create

/**
 * Singleton Retrofit network clients for Hong Kong Bus services.
 *
 * Shared boilerplate (pool, dispatcher, logging, offline cache) lives in [SharedNetworkConfig].
 */
object HkBusNetworkClient {

    private const val KMB_BASE_URL = "https://data.etabus.gov.hk/"
    private const val CITYBUS_BASE_URL = "https://rt.data.gov.hk/"

    private val okHttpClient: OkHttpClient by lazy {
        SharedNetworkConfig.applyOfflineCache(
            SharedNetworkConfig.newBuilder(
                logTag = "OkHttp-HkBus",
                connectTimeoutSeconds = 15,
                readTimeoutSeconds = 15
            )
        ).build()
    }

    val kmbApiService: HkBusApiService by lazy {
        SharedNetworkConfig.newRetrofit(KMB_BASE_URL, okHttpClient).create()
    }

    val citybusApiService: HkBusApiService by lazy {
        SharedNetworkConfig.newRetrofit(CITYBUS_BASE_URL, okHttpClient).create()
    }
}
