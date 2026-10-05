package com.androidfung.departureboard.data.network

import com.androidfung.departureboard.data.model.NrBoardResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Rail Data Marketplace REST API interface for Live Departure Boards (NRE Darwin).
 */
interface NationalRailApiService {

    /**
     * Fetches live departure board for a given 3-letter CRS station code (e.g. "KGX", "WAT", "VIC").
     */
    @GET("1010-live-departure-board-dep1_2/LDBWS/api/20220120/GetDepartureBoard/{crs}")
    suspend fun getDepartureBoard(
        @Path("crs") crs: String,
        @Query("numRows") numRows: Int = 15
    ): NrBoardResponse
}
