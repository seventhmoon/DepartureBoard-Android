package com.androidfung.departureboard.data.network

import com.androidfung.departureboard.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * Network client for Department for Transport (DfT) Bus Open Data Service (BODS).
 */
object BodsNetworkClient {

    private const val BASE_URL = "https://data.bus-data.dft.gov.uk/"

    private val okHttpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            val redacted = message.replace(Regex("""([?&]api_key=)[^&\s]+""", RegexOption.IGNORE_CASE), "$1[REDACTED]")
            try {
                android.util.Log.d("OkHttp-BODS", redacted)
            } catch (_: Throwable) {
                // Ignore in unit tests where android.util.Log is unmocked
            }
        }.apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        SharedNetworkConfig.newBuilder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .build()
    }

    val apiService: BodsApiService by lazy {
        retrofit.create(BodsApiService::class.java)
    }
}
