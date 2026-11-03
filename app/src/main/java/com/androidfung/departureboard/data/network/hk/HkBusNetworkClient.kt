package com.androidfung.departureboard.data.network

import com.androidfung.departureboard.BuildConfig
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create
import java.util.concurrent.TimeUnit

/**
 * Singleton Retrofit network clients for Hong Kong Bus services.
 */
object HkBusNetworkClient {

    private const val KMB_BASE_URL = "https://data.etabus.gov.hk/"
    private const val CITYBUS_BASE_URL = "https://rt.data.gov.hk/"

    private val moshi: Moshi by lazy { SharedNetworkConfig.moshi }

    private val okHttpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        SharedNetworkConfig.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    val kmbApiService: HkBusApiService by lazy {
        Retrofit.Builder()
            .baseUrl(KMB_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create()
    }

    val citybusApiService: HkBusApiService by lazy {
        Retrofit.Builder()
            .baseUrl(CITYBUS_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create()
    }
}
