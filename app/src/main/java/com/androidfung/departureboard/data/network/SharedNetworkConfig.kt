package com.androidfung.departureboard.data.network

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Shared HTTP connection and thread pool configuration for all transit API clients.
 * Sharing the connection pool and dispatcher avoids allocating redundant native thread
 * pools and enables HTTP/2 and TCP socket reuse across endpoints.
 */
object SharedNetworkConfig {

    val connectionPool: ConnectionPool by lazy {
        ConnectionPool(
            maxIdleConnections = 8,
            keepAliveDuration = 5,
            timeUnit = TimeUnit.MINUTES
        )
    }

    val dispatcher: Dispatcher by lazy {
        Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 12
        }
    }

    val baseClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(connectionPool)
            .dispatcher(dispatcher)
            .build()
    }

    /**
     * Creates a new [OkHttpClient.Builder] pre-configured with the shared connection pool
     * and request dispatcher.
     */
    fun newBuilder(): OkHttpClient.Builder {
        return baseClient.newBuilder()
    }
}
