package com.androidfung.departureboard.data.network

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class OfflineCacheInterceptorTest {

    @Test
    fun offline_504_cache_miss_throws_IOException_and_closes_response() {
        val interceptor = OfflineCacheInterceptor(
            isNetworkAvailable = { false },
            maxStale = 7,
            timeUnit = TimeUnit.DAYS
        )

        var closed = false
        val chain = fakeChain { req ->
            assertTrue(req.cacheControl.onlyIfCached)
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(504)
                .message("Unsatisfiable Request (only-if-cached)")
                .body(trackingBody("{}", onClose = { closed = true }))
                .build()
        }

        try {
            interceptor.intercept(chain)
            fail("Expected IOException on 504 cache miss")
        } catch (e: IOException) {
            assertTrue(closed)
            assertTrue(e.message.orEmpty().contains("504"))
        }
    }

    @Test
    fun offline_200_cache_hit_returns_cached_response() {
        val interceptor = OfflineCacheInterceptor(isNetworkAvailable = { false })
        val chain = fakeChain { req ->
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{\"ok\":true}".toResponseBody("application/json".toMediaType()))
                .build()
        }

        val response = interceptor.intercept(chain)
        assertEquals(200, response.code)
        assertEquals("{\"ok\":true}", response.body?.string())
    }

    @Test
    fun online_IOException_falls_back_to_cache_and_rethrows_when_fallback_is_504() {
        val interceptor = OfflineCacheInterceptor(isNetworkAvailable = { true })
        var fallbackClosed = false
        val chain = fakeChain { req ->
            if (!req.cacheControl.onlyIfCached) {
                throw IOException("Simulated socket timeout")
            }
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(504)
                .message("Unsatisfiable Request")
                .body(trackingBody("{}", onClose = { fallbackClosed = true }))
                .build()
        }

        try {
            interceptor.intercept(chain)
            fail("Expected original IOException to be rethrown when cache fallback is 504")
        } catch (e: IOException) {
            assertTrue(fallbackClosed)
            assertEquals("Simulated socket timeout", e.message)
        }
    }

    private fun trackingBody(content: String, onClose: () -> Unit): okhttp3.ResponseBody {
        val delegate = content.toResponseBody("application/json".toMediaType())
        return object : okhttp3.ResponseBody() {
            override fun contentType() = delegate.contentType()
            override fun contentLength() = delegate.contentLength()
            override fun source() = delegate.source()
            override fun close() {
                onClose()
                delegate.close()
            }
        }
    }

    private fun fakeChain(handler: (Request) -> Response): Interceptor.Chain {
        val initialRequest = Request.Builder().url("https://example.com/api/departures").build()
        return java.lang.reflect.Proxy.newProxyInstance(
            Interceptor.Chain::class.java.classLoader,
            arrayOf(Interceptor.Chain::class.java)
        ) { _, method, args ->
            when (method.name) {
                "request" -> initialRequest
                "proceed" -> handler(args[0] as Request)
                "connectTimeoutMillis", "readTimeoutMillis", "writeTimeoutMillis" -> 10_000
                "connection" -> null
                else -> throw UnsupportedOperationException("Unexpected method ${method.name}")
            }
        } as Interceptor.Chain
    }
}
