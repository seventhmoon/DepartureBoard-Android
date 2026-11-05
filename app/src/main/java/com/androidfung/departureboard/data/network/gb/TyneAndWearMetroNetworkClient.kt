package com.androidfung.departureboard.data.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.create

/**
 * Singleton client for Nexus RTI API (Tyne & Wear Metro).
 *
 * Shared boilerplate (pool, dispatcher, logging, offline cache) lives in [SharedNetworkConfig].
 */
object TyneAndWearMetroNetworkClient {

    private const val BASE_URL = "https://metro-rti.nexus.org.uk/"

    private val okHttpClient: OkHttpClient by lazy {
        SharedNetworkConfig.applyOfflineCache(
            SharedNetworkConfig.newBuilder(
                logTag = "OkHttp-TyneMetro",
                connectTimeoutSeconds = 10,
                readTimeoutSeconds = 10
            )
        ).build()
    }

    private val retrofit: Retrofit by lazy {
        SharedNetworkConfig.newRetrofit(BASE_URL, okHttpClient)
    }

    val apiService: TyneAndWearMetroApiService by lazy {
        retrofit.create()
    }
}
