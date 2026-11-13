package com.androidfung.departureboard.data.repository.region.au

import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.network.au.AuTfnswApiService
import com.androidfung.departureboard.data.network.au.AuTfnswDepartureMonResponse
import com.androidfung.departureboard.data.network.au.AuTfnswDestination
import com.androidfung.departureboard.data.network.au.AuTfnswProduct
import com.androidfung.departureboard.data.network.au.AuTfnswStopEvent
import com.androidfung.departureboard.data.network.au.AuTfnswStopLocation
import com.androidfung.departureboard.data.network.au.AuTfnswTransportation
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuDepartureSourceTest {

    @Test
    fun `bus and light rail TfNSW events map to non-rail modes without Waratah train formation`() = runTest {
        val fiveMinsFromNow = Instant.now().plusSeconds(300).toString()
        val fakeApi = object : AuTfnswApiService {
            override suspend fun getDepartures(
                stopId: String,
                outputFormat: String,
                coordOutputFormat: String,
                mode: String,
                typeDm: String,
                tfnswDm: String,
                version: String
            ): AuTfnswDepartureMonResponse {
                return AuTfnswDepartureMonResponse(
                    stopEvents = listOf(
                        AuTfnswStopEvent(
                            departureTimeEstimated = fiveMinsFromNow,
                            location = AuTfnswStopLocation(disassembledName = "Central Station, Eddy Ave, Stand C"),
                            transportation = AuTfnswTransportation(
                                id = "nsw:374",
                                number = "374",
                                disassembledName = "374",
                                product = AuTfnswProduct(productClass = 5, name = "Sydney Buses Network"),
                                destination = AuTfnswDestination(name = "Coogee Beach")
                            )
                        ),
                        AuTfnswStopEvent(
                            departureTimeEstimated = fiveMinsFromNow,
                            location = AuTfnswStopLocation(disassembledName = "Central Grand Concourse, Platform 1"),
                            transportation = AuTfnswTransportation(
                                id = "nsw:L2",
                                number = "L2",
                                disassembledName = "L2",
                                product = AuTfnswProduct(productClass = 4, name = "L2 Randwick Line"),
                                destination = AuTfnswDestination(name = "Randwick")
                            )
                        )
                    )
                )
            }
        }

        val source = AuDepartureSource(
            tfnswApiService = fakeApi,
            apiKeyProvider = { "test-tfnsw-key" }
        )

        val result = source.fetchDepartures(
            stationId = "AU_BUS_200060",
            stationName = "Central Station, Eddy Ave",
            isBusStop = true
        )

        assertTrue(result.isSuccess)
        val departures = result.getOrThrow()
        assertEquals(2, departures.size)

        val busDep = departures[0]
        assertEquals("bus", busDep.modeName)
        assertEquals("374", busDep.lineName)
        assertEquals(TransitMode.BUS, busDep.lineBadge.mode)
        assertEquals("Stand C", busDep.platformName)
        assertNull(busDep.train)

        val lrDep = departures[1]
        assertEquals("tram", lrDep.modeName)
        assertEquals(TransitMode.TRAM, lrDep.lineBadge.mode)
        assertNull(lrDep.train)
    }
}
