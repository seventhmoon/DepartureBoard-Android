package com.androidfung.departureboard.test

import com.androidfung.departureboard.data.model.gb.NrBoardResponse
import com.androidfung.departureboard.data.model.gb.NrServiceDetailsResponse
import com.androidfung.departureboard.data.network.gb.NationalRailApiService
import java.io.IOException

/**
 * Complete, configurable test double for [NationalRailApiService].
 */
open class FakeNrApiService(
    var boardResponse: NrBoardResponse = NrBoardResponse(),
    var serviceDetailsResponse: NrServiceDetailsResponse = NrServiceDetailsResponse(),
    var shouldThrow: Boolean = false,
    var throwException: Exception = IOException("NR Network error")
) : NationalRailApiService {

    override suspend fun getDepBoardWithDetails(crs: String, numRows: Int): NrBoardResponse {
        if (shouldThrow) throw throwException
        return boardResponse
    }

    override suspend fun getDepartureBoard(crs: String, numRows: Int): NrBoardResponse {
        if (shouldThrow) throw throwException
        return boardResponse
    }

    override suspend fun getArrBoardWithDetails(crs: String, numRows: Int): NrBoardResponse {
        if (shouldThrow) throw throwException
        return boardResponse
    }

    override suspend fun getServiceDetails(serviceId: String): NrServiceDetailsResponse {
        if (shouldThrow) throw throwException
        return serviceDetailsResponse
    }
}
