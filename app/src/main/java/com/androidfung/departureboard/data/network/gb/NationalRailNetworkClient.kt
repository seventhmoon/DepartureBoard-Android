package com.androidfung.departureboard.data.network

import com.androidfung.departureboard.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.create

/**
 * Singleton network client configuring OkHttp and Retrofit for Rail Data Marketplace API.
 *
 * Shared boilerplate (pool, dispatcher, redacted logging, offline cache) lives in
 * [SharedNetworkConfig]; only the x-apikey header injection and base URL are specific here.
 */
object NationalRailNetworkClient {

    private const val BASE_URL = "https://api1.raildata.org.uk/"

    private val okHttpClient: OkHttpClient by lazy {
        val builder = SharedNetworkConfig.newBuilder(
            logTag = "OkHttp-NR",
            // Redact the x-apikey request header so secrets never land in logcat
            redactRegex = Regex("""(x-apikey:\s*)[^\r\n]+""", RegexOption.IGNORE_CASE)
        ).addInterceptor { chain ->
            val originalRequest = chain.request()
            val apiKey = BuildConfig.NATIONAL_RAIL_API_KEY

            val newRequestBuilder = originalRequest.newBuilder()
            if (apiKey.isNotBlank()) {
                newRequestBuilder.addHeader("x-apikey", apiKey)
            }
            chain.proceed(newRequestBuilder.build())
        }
        SharedNetworkConfig.applyOfflineCache(builder).build()
    }

    private val retrofit: Retrofit by lazy {
        SharedNetworkConfig.newRetrofit(BASE_URL, okHttpClient)
    }

    val apiService: NationalRailApiService by lazy {
        retrofit.create()
    }
}
