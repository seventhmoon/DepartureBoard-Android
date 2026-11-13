package com.androidfung.departureboard.data.network

import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * An [Interceptor] that serves cached responses when offline or when requests fail.
 *
 * @param isNetworkAvailable Lambda providing network connectivity state.
 * @param maxStale Duration for which stale cached responses remain acceptable.
 * @param timeUnit The unit of time for [maxStale].
 */
class OfflineCacheInterceptor(
    private val isNetworkAvailable: () -> Boolean,
    maxStale: Int = 7,
    timeUnit: TimeUnit = TimeUnit.DAYS,
) : Interceptor {

    private val offlineCacheControl = CacheControl.Builder()
        .onlyIfCached()
        .maxStale(maxStale, timeUnit)
        .build()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val request = if (!isNetworkAvailable()) {
            originalRequest.toOfflineCachedRequest()
        } else {
            originalRequest
        }

        return try {
            val response = chain.proceed(request)
            if (response.code >= 500 && !request.cacheControl.onlyIfCached) {
                val cachedResponse = chain.proceed(originalRequest.toOfflineCachedRequest())
                if (cachedResponse.isSuccessful) {
                    response.close()
                    cachedResponse
                } else {
                    cachedResponse.close()
                    response
                }
            } else {
                response
            }
        } catch (e: IOException) {
            if (request.cacheControl.onlyIfCached) throw e
            val cachedResponse = chain.proceed(originalRequest.toOfflineCachedRequest())
            if (cachedResponse.isSuccessful) {
                cachedResponse
            } else {
                cachedResponse.close()
                throw e
            }
        }
    }

    private fun Request.toOfflineCachedRequest(): Request =
        newBuilder()
            .cacheControl(offlineCacheControl)
            .build()
}

/**
 * Extension helper to add [OfflineCacheInterceptor] to an [OkHttpClient.Builder].
 */
fun OkHttpClient.Builder.addOfflineCacheSupport(
    isNetworkAvailable: () -> Boolean,
    maxStale: Int = 7,
    timeUnit: TimeUnit = TimeUnit.DAYS,
): OkHttpClient.Builder = addInterceptor(
    OfflineCacheInterceptor(
        isNetworkAvailable = isNetworkAvailable,
        maxStale = maxStale,
        timeUnit = timeUnit,
    ),
)
