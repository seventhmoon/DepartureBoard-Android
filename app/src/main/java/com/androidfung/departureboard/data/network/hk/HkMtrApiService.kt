package com.androidfung.departureboard.data.network

import com.androidfung.departureboard.data.model.HkMtrScheduleResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for Hong Kong MTR open data schedule endpoint.
 * Base URL: https://rt.data.gov.hk/
 * Endpoint: https://rt.data.gov.hk/v1/transport/mtr/getSchedule.php?line=AEL&sta=HOK
 */
interface HkMtrApiService {

    @GET("v1/transport/mtr/getSchedule.php")
    suspend fun getSchedule(
        @Query("line") line: String,
        @Query("sta") station: String,
        @Query("lang") lang: String = "en"
    ): HkMtrScheduleResponse
}
