package com.androidfung.departureboard.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class CoroutineResultUtilsTest {

    @Test
    fun `runSuspendCatching rethrows CancellationException`() {
        try {
            runSuspendCatching {
                throw CancellationException("cancelled")
            }
            fail("Expected CancellationException to be rethrown")
        } catch (e: CancellationException) {
            assertEquals("cancelled", e.message)
        }
    }

    @Test
    fun `runSuspendCatching captures regular exceptions`() {
        val res = runSuspendCatching {
            throw IllegalStateException("network failure")
        }
        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `CoalescingTimedCache coalesces concurrent requests and recovers after scope cancellation`() = runBlocking {
        val cache = CoalescingTimedCache<String, String>(ttlMillis = 0L)
        val loaderCalls = AtomicInteger(0)
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        val caller1 = async {
            cache.getOrPutCoalesced("940GZZLUKSX", workerScope) {
                loaderCalls.incrementAndGet()
                started.complete(Unit)
                gate.await()
                "first"
            }
        }

        started.await()

        // Cancel the worker scope's children while the loader is suspended (simulates mid-flight cancellation)
        workerScope.coroutineContext.cancelChildren()

        try {
            caller1.await()
            fail("Expected caller1 to fail with CancellationException")
        } catch (_: CancellationException) {
            // Expected
        }

        // Subsequent request for the same key must NOT be wedged on a dead cancelled Deferred
        val recovered = cache.getOrPutCoalesced("940GZZLUKSX", workerScope) {
            loaderCalls.incrementAndGet()
            "recovered-after-cancel"
        }

        assertEquals("recovered-after-cancel", recovered)
        assertEquals(2, loaderCalls.get())
        workerScope.coroutineContext.cancelChildren()
    }
}
