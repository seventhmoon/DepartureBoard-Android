package com.androidfung.departureboard.data.network

import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create
import java.util.concurrent.TimeUnit

/**
 * Singleton client for Nexus RTI API (Tyne & Wear Metro).
 */
object TyneAndWearMetroNetworkClient {

    private const val BASE_URL = "https://metro-rti.nexus.org.uk/"

    private val moshi: Moshi by lazy {
        SharedNetworkConfig.moshi
    }

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (com.androidfung.departureboard.BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        SharedNetworkConfig.newBuilder()
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    val apiService: TyneAndWearMetroApiService by lazy {
        retrofit.create()
    }
}
