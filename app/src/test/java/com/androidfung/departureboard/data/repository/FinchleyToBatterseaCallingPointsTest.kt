package com.androidfung.departureboard.data.repository

import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.TflMatchedStop
import com.androidfung.departureboard.data.model.TflRouteSequenceResponse
import com.androidfung.departureboard.data.model.TflStopPointSequence
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.test.TestFixtures
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinchleyToBatterseaCallingPointsTest {

    @Test
    fun northernLine_finchleyCentralToBattersea_traversesBranchesCorrectly() = runTest {
        // Multi-branch Northern line topology mimicking TfL response:
        // Branch 9: Finchley Central -> Camden Town, next = [3]
        // Branch 3: Camden Town -> Euston, next = [5]
        // Branch 5: Euston -> Kennington, next = [7]
        // Branch 7: Kennington -> Battersea Power Station
        val mockSequence = TflRouteSequenceResponse(
            lineId = "northern",
            stopPointSequences = listOf(
                TflStopPointSequence(
                    branchId = 9,
                    nextBranchIds = listOf(3),
                    stopPoint = listOf(
                        TflMatchedStop("1", "Finchley Central Underground Station"),
                        TflMatchedStop("2", "East Finchley Underground Station"),
                        TflMatchedStop("3", "Camden Town Underground Station")
                    )
                ),
                TflStopPointSequence(
                    branchId = 3,
                    nextBranchIds = listOf(5),
                    stopPoint = listOf(
                        TflMatchedStop("3", "Camden Town Underground Station"),
                        TflMatchedStop("4", "Euston Underground Station")
                    )
                ),
                TflStopPointSequence(
                    branchId = 5,
                    nextBranchIds = listOf(7),
                    stopPoint = listOf(
                        TflMatchedStop("4", "Euston Underground Station"),
                        TflMatchedStop("5", "Waterloo Underground Station"),
                        TflMatchedStop("6", "Kennington Underground Station")
                    )
                ),
                TflStopPointSequence(
                    branchId = 7,
                    nextBranchIds = emptyList(),
                    stopPoint = listOf(
                        TflMatchedStop("6", "Kennington Underground Station"),
                        TflMatchedStop("7", "Nine Elms Underground Station"),
                        TflMatchedStop("8", "Battersea Power Station Underground Station")
                    )
                )
            )
        )

        val fakeTflApi = com.androidfung.departureboard.test.FakeTflApiService(
            routeSequenceResult = mockSequence
        )

        val fakeNrApi = com.androidfung.departureboard.test.FakeNrApiService()

        val fakeDataStore = com.androidfung.departureboard.test.FakeStationPreferencesDataSource(
            initialStations = emptyList(),
            initialRecentId = null
        )

        val repository = TransitRepositoryImpl(
            apiService = fakeTflApi,
            nrApiService = fakeNrApi,
            dataStore = fakeDataStore
        )

        val departure = TestFixtures.createTestDeparture(
            id = "dep_test",
            stationId = "940GZZLUFYC",
            stationName = "Finchley Central",
            lineId = "northern",
            lineName = "Northern",
            platformName = "Platform 1",
            destinationName = "Battersea Power Station",
            towards = "Battersea via Charing Cross",
            timeToStationSeconds = 120,
            expectedArrivalIso = null,
            currentLocation = "Approaching",
            modeName = "tube",
            lineBadge = LineBadgeInfo("northern", "Northern", androidx.compose.ui.graphics.Color.Black, mode = TransitMode.TUBE)
        )

        val callingPoints = repository.getCallingPoints(departure)

        // Should return the stitched multi-branch calling points sequence
        assertTrue("Calling points should not be empty", callingPoints.isNotEmpty())
        assertEquals("Finchley Central", callingPoints.first().stationName)
        assertEquals("Battersea Power Station", callingPoints.last().stationName)
        assertEquals(8, callingPoints.size)
        assertTrue(callingPoints.first().isCurrentStation)
        assertTrue(callingPoints.last().isDestination)
    }
}
