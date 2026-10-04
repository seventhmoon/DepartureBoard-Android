package com.androidfung.departureboard.data.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton network client configuring OkHttp, Moshi, and Retrofit for TfL Unified API.
 */
object TflNetworkClient {

    private const val BASE_URL = "https://api.tfl.gov.uk/"

    val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    private var cacheDir: java.io.File? = null

    fun initialize(context: android.content.Context) {
        if (cacheDir == null) {
            cacheDir = java.io.File(context.applicationContext.cacheDir, "http_cache")
        }
    }

    private val okHttpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val builder = OkHttpClient.Builder()
            .addInterceptor { chain ->
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
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)

        cacheDir?.let { dir ->
            try {
                // 10 MB HTTP response cache for offline access underground
                builder.cache(okhttp3.Cache(dir, 10L * 1024 * 1024))
            } catch (_: Exception) {}
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
        retrofit.create(TflApiService::class.java)
    }
}
