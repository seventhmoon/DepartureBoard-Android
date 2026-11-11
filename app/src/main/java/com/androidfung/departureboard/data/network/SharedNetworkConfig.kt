package com.androidfung.departureboard.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import com.squareup.moshi.Moshi
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Shared HTTP configuration for all transit API clients.
 *
 * Centralizes the connection pool, dispatcher, Moshi instance, debug logging (with API-key
 * redaction), timeouts, and offline response caching so individual clients stay tiny and all
 * transit endpoints get identical behaviour (including offline fallback) instead of each
 * re-implementing the boilerplate.
 */
object SharedNetworkConfig {

    /** Application context, registered once from [DepartureBoardApp] for the HTTP cache directory. */
    @Volatile
    private var appContext: Context? = null

    /**
     * Registers the application context so [applyOfflineCache] can resolve the cache directory.
     * Safe to call multiple times; no-op if the context is already registered.
     */
    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

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
     * Shared [Moshi] instance across all clients to reuse cached reflective/codegen adapters
     * and avoid allocating duplicate Moshi instances.
     */
    val moshi: Moshi by lazy {
        Moshi.Builder().build()
    }

    /**
     * Creates a new [OkHttpClient.Builder] pre-configured with the shared connection pool,
     * request dispatcher, a debug logging interceptor (redacting the given pattern so secrets
     * never land in logcat), and the provided timeouts.
     *
     * @param logTag Logcat tag used by the logging interceptor.
     * @param redactRegex Optional regex matched against each log line to mask secrets.
     */
    fun newBuilder(
        logTag: String = "OkHttp",
        redactRegex: Regex? = null,
        connectTimeoutSeconds: Long = 15,
        readTimeoutSeconds: Long = 15
    ): OkHttpClient.Builder {
        val logging = HttpLoggingInterceptor { message ->
            val redacted = redactRegex?.let { message.replace(it, "[REDACTED]") } ?: message
            try {
                android.util.Log.d(logTag, redacted)
            } catch (_: Throwable) {
                // Ignore in unit tests where android.util.Log is unmocked
            }
        }.apply {
            level = if (com.androidfung.departureboard.BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        return baseClient.newBuilder()
            .addInterceptor(logging)
            .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
    }

    private val cacheLock = Any()
    private val cachesByDir = mutableMapOf<String, Cache>()

    /**
     * Attaches a disk-backed HTTP response cache plus the [OfflineCacheInterceptor] so clients
     * can serve the last known response while offline or when a request fails.
     *
     * Reuses a single [Cache] instance per [cacheDirName] across OkHttpClients so concurrent
     * clients never contend on the same DiskLruCache journal file.
     *
     * Only active once [initialize] has registered an application context; otherwise the
     * builder is returned unchanged (e.g. in unit tests).
     */
    fun applyOfflineCache(
        builder: OkHttpClient.Builder,
        cacheSizeBytes: Long = 10L * 1024 * 1024,
        cacheDirName: String = "http_cache"
    ): OkHttpClient.Builder {
        val context = appContext ?: return builder
        return try {
            val sharedCache = synchronized(cacheLock) {
                cachesByDir.getOrPut(cacheDirName) {
                    Cache(File(context.cacheDir, cacheDirName), cacheSizeBytes)
                }
            }
            builder
                .cache(sharedCache)
                .addOfflineCacheSupport(isNetworkAvailable = ::isNetworkAvailable)
        } catch (_: Throwable) {
            // Cache is an optimisation — never let it break client construction
            builder
        }
    }

    /**
     * Whether the device currently has a validated internet connection.
     * Defaults to `true` when no context is registered (unit tests) so caching stays permissive.
     */
    fun isNetworkAvailable(): Boolean {
        val context = appContext ?: return true
        val cm = context.getSystemService<ConnectivityManager>() ?: return true
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Creates a [Retrofit] instance wired to [client] with a shared Moshi JSON converter.
     */
    fun newRetrofit(baseUrl: String, client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }
}
