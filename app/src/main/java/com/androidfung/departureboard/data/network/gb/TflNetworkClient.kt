package com.androidfung.departureboard.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Singleton network client configuring OkHttp, Moshi, and Retrofit for TfL Unified API.
 */
object TflNetworkClient {

    private const val BASE_URL = "https://api.tfl.gov.uk/"

    val moshi: Moshi by lazy {
        SharedNetworkConfig.moshi
    }

    private var cacheDir: File? = null
    private var appContext: Context? = null

    fun initialize(context: Context) {
        val app = context.applicationContext ?: context
        appContext = app
        if (cacheDir == null) {
            cacheDir = File(app.cacheDir, "http_cache")
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val context = appContext ?: return true
        val cm = context.getSystemService<ConnectivityManager>() ?: return true
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private val okHttpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            // Redact app_key from logcat so secret API keys are never exposed in logs
            val redacted = message.replace(Regex("""([?&]app_key=)[^&\s]+""", RegexOption.IGNORE_CASE), "$1[REDACTED]")
            android.util.Log.d("OkHttp", redacted)
        }.apply {
            level = if (com.androidfung.departureboard.BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val builder = SharedNetworkConfig.newBuilder()
            .addInterceptor(loggingInterceptor)
            .addOfflineCacheSupport(isNetworkAvailable = ::isNetworkAvailable)
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
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)

        cacheDir?.let { dir ->
            runCatching {
                // 10 MB HTTP response cache for offline access underground
                builder.cache(okhttp3.Cache(dir, 10L * 1024 * 1024))
            }
        }

        builder.build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    val apiService: TflApiService by lazy {
        retrofit.create()
    }
}
