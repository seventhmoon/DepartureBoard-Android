package com.androidfung.departureboard.data.network.gb

import com.androidfung.departureboard.data.model.gb.NrBoardResponse
import com.androidfung.departureboard.data.model.gb.NrServiceDetailsResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Rail Data Marketplace REST API interface for Live Departure Boards (NRE Darwin).
 */
interface NationalRailApiService {

    /**
     * Fetches live departure board with detailed calling points for a given 3-letter CRS station code (e.g. "KGX", "WAT", "VIC").
     */
    @GET("1010-live-departure-board-dep1_2/LDBWS/api/20220120/GetDepBoardWithDetails/{crs}")
    suspend fun getDepBoardWithDetails(
        @Path("crs") crs: String,
        @Query("numRows") numRows: Int = 15
    ): NrBoardResponse

    /**
     * Fetches live departure board for a given 3-letter CRS station code (fallback).
     */
    @GET("1010-live-departure-board-dep1_2/LDBWS/api/20220120/GetDepartureBoard/{crs}")
    suspend fun getDepartureBoard(
        @Path("crs") crs: String,
        @Query("numRows") numRows: Int = 15
    ): NrBoardResponse

    /**
     * Fetches live arrival board with detailed calling points for a given 3-letter CRS station code.
     */
    @GET("1010-live-departure-board-dep1_2/LDBWS/api/20220120/GetArrBoardWithDetails/{crs}")
    suspend fun getArrBoardWithDetails(
        @Path("crs") crs: String,
        @Query("numRows") numRows: Int = 15
    ): NrBoardResponse

    /**
     * Fetches detailed train formation, coach loading, and toilet facilities for [serviceId].
     */
    @GET("1010-live-departure-board-dep1_2/LDBWS/api/20220120/GetServiceDetails/{serviceId}")
    suspend fun getServiceDetails(
        @Path("serviceId") serviceId: String
    ): NrServiceDetailsResponse
}
