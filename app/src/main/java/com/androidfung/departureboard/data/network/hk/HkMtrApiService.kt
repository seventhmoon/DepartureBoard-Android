package com.androidfung.departureboard.data.network.hk

import com.androidfung.departureboard.data.model.hk.HkLightRailScheduleResponse
import com.androidfung.departureboard.data.model.hk.HkMtrScheduleResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for Hong Kong MTR & Light Rail open data schedule endpoints.
 * Base URL: https://rt.data.gov.hk/
 */
interface HkMtrApiService {

    @GET("v1/transport/mtr/getSchedule.php")
    suspend fun getSchedule(
        @Query("line") line: String,
        @Query("sta") station: String,
        @Query("lang") lang: String = "en"
    ): HkMtrScheduleResponse
}

interface HkLightRailApiService {
    @GET("v1/transport/mtr/lrt/getSchedule")
    suspend fun getLightRailSchedule(
        @Query("station_id") stationId: String
    ): HkLightRailScheduleResponse
}
