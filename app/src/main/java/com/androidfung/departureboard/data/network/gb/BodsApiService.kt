package com.androidfung.departureboard.data.network

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for UK Department for Transport (DfT) Bus Open Data Service (BODS).
 * Base URL: https://data.bus-data.dft.gov.uk/
 */
interface BodsApiService {

    /**
     * Fetches real-time SIRI-VM vehicle location data within a coordinate bounding box.
     * @param boundingBox Format: minLongitude,minLatitude,maxLongitude,maxLatitude (e.g. "-0.05,51.70,0.05,51.80")
     * @param apiKey The BODS API key
     */
    @GET("api/v1/datafeed/")
    suspend fun getVehicleDatafeed(
        @Query("boundingBox") boundingBox: String,
        @Query("api_key") apiKey: String = com.androidfung.departureboard.BuildConfig.BODS_API_KEY
    ): ResponseBody

    /**
     * Fetches real-time SIRI-VM vehicle location data by line/route reference (e.g. "HL1", "310").
     */
    @GET("api/v1/datafeed/")
    suspend fun getVehicleDatafeedByLine(
        @Query("lineRef") lineRef: String,
        @Query("api_key") apiKey: String = com.androidfung.departureboard.BuildConfig.BODS_API_KEY
    ): ResponseBody

    /**
     * Fetches real-time SIRI-SX disruptions datafeed (e.g. cancellations, diversions, closures).
     */
    @GET("api/v1/disruptions/")
    suspend fun getDisruptions(
        @Query("api_key") apiKey: String = com.androidfung.departureboard.BuildConfig.BODS_API_KEY
    ): ResponseBody
}
