package com.androidfung.departureboard.test

import com.androidfung.departureboard.data.network.BodsApiService
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Test double for [BodsApiService].
 */
class FakeBodsApiService(
    var xmlResponse: String = "<Siri></Siri>",
    var shouldThrow: Boolean = false
) : BodsApiService {
    override suspend fun getVehicleDatafeed(boundingBox: String, apiKey: String): ResponseBody {
        if (shouldThrow) throw java.io.IOException("BODS network error")
        return xmlResponse.toResponseBody("application/xml".toMediaTypeOrNull())
    }

    override suspend fun getVehicleDatafeedByLine(lineRef: String, apiKey: String): ResponseBody {
        if (shouldThrow) throw java.io.IOException("BODS network error")
        return xmlResponse.toResponseBody("application/xml".toMediaTypeOrNull())
    }

    override suspend fun getDisruptions(apiKey: String): ResponseBody {
        if (shouldThrow) throw java.io.IOException("BODS network error")
        return xmlResponse.toResponseBody("application/xml".toMediaTypeOrNull())
    }
}
