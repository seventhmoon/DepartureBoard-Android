package com.androidfung.departureboard.data.network

import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton Retrofit network clients for Hong Kong Bus services.
 */
object HkBusNetworkClient {

    private const val KMB_BASE_URL = "https://data.etabus.gov.hk/"
    private const val CITYBUS_BASE_URL = "https://rt.data.gov.hk/"

    private val moshi: Moshi = Moshi.Builder().build()

    private val okHttpClient: OkHttpClient = SharedNetworkConfig.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        )
        .build()

    val kmbApiService: HkBusApiService = Retrofit.Builder()
        .baseUrl(KMB_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(HkBusApiService::class.java)

    val citybusApiService: HkBusApiService = Retrofit.Builder()
        .baseUrl(CITYBUS_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(HkBusApiService::class.java)
}
