package com.androidfung.departureboard.data.network

import android.content.Context
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.create

/**
 * Singleton network client configuring OkHttp, Moshi, and Retrofit for TfL Unified API.
 *
 * Shared boilerplate (pool, dispatcher, redacted logging, offline cache) lives in
 * [SharedNetworkConfig]; only the TfL-specific pieces are the app_key query injection and
 * the response decorator that force-caches non-live metadata responses for 60s.
 */
object TflNetworkClient {

    private const val BASE_URL = "https://api.tfl.gov.uk/"

    val moshi: Moshi by lazy {
        SharedNetworkConfig.moshi
    }

    /**
     * Registers the application context so the offline HTTP cache can be initialised.
     */
    fun initialize(context: Context) {
        SharedNetworkConfig.initialize(context.applicationContext ?: context)
    }

    private val okHttpClient: OkHttpClient by lazy {
        val builder = SharedNetworkConfig.newBuilder(
            // Redact app_key from logcat so secret API keys are never exposed in logs
            redactRegex = Regex("""([?&]app_key=)[^&\s]+""", RegexOption.IGNORE_CASE)
        )
            .addNetworkInterceptor { chain ->
                // Response interceptor to allow caching if server returns restrictive headers for metadata queries.
                // Live arrival predictions (/Arrivals) must never be force-cached for 60s so 15s-30s polling
                // and pull-to-refresh always fetch fresh live predictions when online.
                val request = chain.request()
                val response = chain.proceed(request)
                val isLiveArrivalsEndpoint = request.url.encodedPath.endsWith("/Arrivals", ignoreCase = true)
                val isCacheable = (request.method == "GET") && response.isSuccessful
                val cacheControl = response.cacheControl
                val isRestrictiveOrUncached = (cacheControl.noStore ||
                        cacheControl.noCache ||
                        cacheControl.mustRevalidate ||
                        (cacheControl.maxAgeSeconds <= 0))

                if (isCacheable && !isLiveArrivalsEndpoint && isRestrictiveOrUncached) {
                    response.newBuilder()
                        .removeHeader("Pragma")
                        .removeHeader("Expires")
                        .header("Cache-Control", "public, max-age=60")
                        .build()
                } else {
                    response
                }
            }
            .addInterceptor { chain ->
                // Attach app_key parameter if available
                val originalRequest = chain.request()
                val originalUrl = originalRequest.url
                val apiKey = com.androidfung.departureboard.BuildConfig.TFL_API_KEY
                val newUrl = if (apiKey.isNotBlank()) {
                    originalUrl.newBuilder()
                        .addQueryParameter("app_key", apiKey)
                        .build()
                } else {
                    originalUrl
                }
                val newRequest = originalRequest.newBuilder()
                    .url(newUrl)
                    .build()

                chain.proceed(newRequest)
            }

        SharedNetworkConfig.applyOfflineCache(builder).build()
    }

    private val retrofit: Retrofit by lazy {
        SharedNetworkConfig.newRetrofit(BASE_URL, okHttpClient)
    }

    val apiService: TflApiService by lazy {
        retrofit.create()
    }
}
