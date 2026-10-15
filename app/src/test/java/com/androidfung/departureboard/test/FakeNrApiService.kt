package com.androidfung.departureboard.test

import com.androidfung.departureboard.data.model.NrBoardResponse
import com.androidfung.departureboard.data.network.NationalRailApiService
import java.io.IOException

/**
 * Complete, configurable test double for [NationalRailApiService].
 */
open class FakeNrApiService(
    var boardResponse: NrBoardResponse = NrBoardResponse(),
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
}
