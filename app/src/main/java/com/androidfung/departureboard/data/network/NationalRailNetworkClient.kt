package com.androidfung.departureboard.data.network

import com.androidfung.departureboard.BuildConfig
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton network client configuring OkHttp and Retrofit for Rail Data Marketplace API.
 */
object NationalRailNetworkClient {

    private const val BASE_URL = "https://api1.raildata.org.uk/"

    private val moshi: Moshi by lazy {
        Moshi.Builder().build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            val redacted = message.replace(Regex("""(x-apikey:\s*)[^\r\n]+""", RegexOption.IGNORE_CASE), "$1[REDACTED]")
            android.util.Log.d("OkHttp-NR", redacted)
        }.apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val apiKey = BuildConfig.NATIONAL_RAIL_API_KEY

                val newRequestBuilder = originalRequest.newBuilder()
                if (apiKey.isNotBlank()) {
                    newRequestBuilder.addHeader("x-apikey", apiKey)
                }
                chain.proceed(newRequestBuilder.build())
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    val apiService: NationalRailApiService by lazy {
        retrofit.create(NationalRailApiService::class.java)
    }
}
