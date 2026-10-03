package com.androidfung.departureboard.data.network

import com.androidfung.departureboard.data.model.TflArrivalPrediction
import com.androidfung.departureboard.data.model.TflSearchResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for Transport for London (TfL) Unified API.
 * Base URL: https://api.tfl.gov.uk/
 */
interface TflApiService {

    /**
     * Searches for stations/stops matching a query.
     * Modes: tube, bus, dlr, overground, elizabeth-line, national-rail, etc.
     */
    @GET("StopPoint/Search/{query}")
    suspend fun searchStations(
        @Path("query") query: String,
        @Query("modes") modes: String = "tube,bus,dlr,overground,elizabeth-line,national-rail",
        @Query("maxResults") maxResults: Int = 20
    ): TflSearchResponse

    /**
     * Fetches live arrival countdown predictions for a specific stop or station.
     */
    @GET("StopPoint/{id}/Arrivals")
    suspend fun getArrivals(
        @Path("id") stationId: String
    ): List<TflArrivalPrediction>

    /**
     * Fetches StopPoint details, including child stop points for stations/hubs.
     */
    @GET("StopPoint/{id}")
    suspend fun getStopPointDetail(
        @Path("id") stopPointId: String
    ): com.androidfung.departureboard.data.model.TflStopPointDetail

    /**
     * Fetches live line service statuses across rail, underground, and Thameslink modes.
     */
    @GET("Line/Mode/{modes}/Status")
    suspend fun getLineStatuses(
        @Path("modes") modes: String = "tube,dlr,overground,elizabeth-line,national-rail"
    ): List<com.androidfung.departureboard.data.model.TflLineStatusItem>
}
