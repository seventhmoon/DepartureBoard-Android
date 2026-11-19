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
            if (request.cacheControl.onlyIfCached && (response.code == 504 || !response.isSuccessful)) {
                response.close()
                throw IOException("Offline and no cached response available (HTTP ${response.code})")
            }
            response
        } catch (e: IOException) {
            if (request.cacheControl.onlyIfCached) {
                throw e
            }
            val cachedResponse = try {
                chain.proceed(originalRequest.toOfflineCachedRequest())
            } catch (_: IOException) {
                throw e
            }
            if (cachedResponse.code == 504 || !cachedResponse.isSuccessful) {
                cachedResponse.close()
                throw e
            }
            cachedResponse
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
