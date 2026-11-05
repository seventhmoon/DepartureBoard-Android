package com.androidfung.departureboard.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Executes [block] and wraps the outcome in a [Result], re-throwing [CancellationException]
 * so structured concurrency is never broken inside suspend functions.
 */
inline fun <R> runSuspendCatching(block: () -> R): Result<R> = try {
    Result.success(block())
} catch (c: CancellationException) {
    throw c
} catch (t: Throwable) {
    Result.failure(t)
}

/**
 * Thread-safe, coroutine-aware timed value cache and in-flight request coalescer.
 *
 * Ensures that:
 * 1. Fresh cached values within [ttlMillis] are returned immediately in O(1).
 * 2. Concurrent callers requesting the same key while a fetch is in flight share a single
 *    underlying network/IO call rather than thundering the backend.
 */
class CoalescingTimedCache<K : Any, V>(
    private val ttlMillis: Long = 0L,
    private val clockMillis: () -> Long = System::currentTimeMillis
) {
    private data class CacheEntry<V>(
        val value: V,
        val timestampMillis: Long
    )

    private val entries = ConcurrentHashMap<K, CacheEntry<V>>()
    private val inFlight = ConcurrentHashMap<K, Deferred<V>>()
    private val lock = Mutex()

    /**
     * Returns a non-expired cached value for [key] if present, or null otherwise.
     */
    fun getIfFresh(key: K): V? {
        if (ttlMillis <= 0L) return null
        val entry = entries[key] ?: return null
        return if ((clockMillis() - entry.timestampMillis) < ttlMillis) {
            entry.value
        } else {
            null
        }
    }

    /**
     * Returns the last cached value for [key] even if expired (useful for offline/stale fallbacks).
     */
    fun getStale(key: K): V? = entries[key]?.value

    /**
     * Explicitly stores [value] for [key].
     */
    fun put(key: K, value: V) {
        entries[key] = CacheEntry(value, clockMillis())
    }

    /**
     * Coalesces concurrent requests for [key] on [scope], optionally serving from the TTL cache
     * and caching successful results when [shouldCache] returns true.
     */
    suspend fun getOrPutCoalesced(
        key: K,
        scope: CoroutineScope,
        shouldCache: (V) -> Boolean = { true },
        loader: suspend () -> V
    ): V {
        getIfFresh(key)?.let { return it }

        val deferred = lock.withLock {
            getIfFresh(key)?.let { fresh ->
                return fresh
            }
            inFlight[key] ?: scope.async {
                try {
                    val loaded = loader()
                    if (ttlMillis > 0L && shouldCache(loaded)) {
                        put(key, loaded)
                    }
                    loaded
                } finally {
                    lock.withLock {
                        inFlight.remove(key)
                    }
                }
            }.also { inFlight[key] = it }
        }
        return deferred.await()
    }

    fun clear() {
        entries.clear()
    }
}
