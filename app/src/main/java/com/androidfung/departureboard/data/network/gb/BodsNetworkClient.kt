package com.androidfung.departureboard.data.network

import com.androidfung.departureboard.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.create

/**
 * Network client for Department for Transport (DfT) Bus Open Data Service (BODS).
 *
 * Timed-out, redacted-logging and offline-cache behaviour comes from [SharedNetworkConfig];
 * this class only describes what is specific to BODS (base URL, api-key injection, timeouts).
 */
object BodsNetworkClient {

    private const val BASE_URL = "https://data.bus-data.dft.gov.uk/"

    private val okHttpClient: OkHttpClient by lazy {
        // The api_key is injected per-request via @Query defaults in BodsApiService; the
        // redact regex below just keeps it out of logcat.
        SharedNetworkConfig.applyOfflineCache(
            SharedNetworkConfig.newBuilder(
                logTag = "OkHttp-BODS",
                redactRegex = Regex("""([?&]api_key=)[^\s&]+""", RegexOption.IGNORE_CASE),
                connectTimeoutSeconds = 15,
                readTimeoutSeconds = 20
            )
        ).build()
    }

    private val retrofit: Retrofit by lazy {
        SharedNetworkConfig.newRetrofit(BASE_URL, okHttpClient)
    }

    val apiService: BodsApiService by lazy {
        retrofit.create()
    }
}
