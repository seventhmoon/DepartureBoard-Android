package com.androidfung.departureboard.data.network

import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit network client for Hong Kong MTR open data API.
 */
object HkMtrNetworkClient {

    private const val MTR_BASE_URL = "https://rt.data.gov.hk/"

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

    val apiService: HkMtrApiService = Retrofit.Builder()
        .baseUrl(MTR_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(HkMtrApiService::class.java)
}
