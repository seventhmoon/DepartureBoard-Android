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

    @Test
    fun northernLine_downstreamCallingPoints_ignoreInboundPredictions() = runTest {
        // Test scenario corresponding to the bug:
        // Train is at Camden Town (300s) -> Finchley Central (1000s) -> Mill Hill East (1200s = 20 mins)
        // From Mill Hill East, downstream trip is Mill Hill East -> Finchley Central -> Camden Town
        // Inbound predictions for Finchley Central (1000s) and Camden Town (300s) are < 1200s and must NOT be used.
        val mockSequence = TflRouteSequenceResponse(
            lineId = "northern",
            stopPointSequences = listOf(
                TflStopPointSequence(
                    branchId = 1,
                    nextBranchIds = emptyList(),
                    stopPoint = listOf(
                        TflMatchedStop("940GZZLUMHL", "Mill Hill East Underground Station"),
                        TflMatchedStop("940GZZLUFYC", "Finchley Central Underground Station"),
                        TflMatchedStop("940GZZLUCTN", "Camden Town Underground Station")
                    )
                )
            )
        )

        val inboundVehicleId = "veh_123"
        val fakeTflApi = com.androidfung.departureboard.test.FakeTflApiService(
            routeSequenceResult = mockSequence,
            lineArrivalsResult = listOf(
                // Inbound leg predictions before reaching Mill Hill East:
                com.androidfung.departureboard.data.model.TflArrivalPrediction(
                    id = "arr1",
                    vehicleId = inboundVehicleId,
                    naptanId = "940GZZLUCTN",
                    stationName = "Camden Town Underground Station",
                    timeToStation = 300 // 5 mins (inbound)
                ),
                com.androidfung.departureboard.data.model.TflArrivalPrediction(
                    id = "arr2",
                    vehicleId = inboundVehicleId,
                    naptanId = "940GZZLUFYC",
                    stationName = "Finchley Central Underground Station",
                    timeToStation = 1000 // ~17 mins (inbound)
                ),
                com.androidfung.departureboard.data.model.TflArrivalPrediction(
                    id = "arr3",
                    vehicleId = inboundVehicleId,
                    naptanId = "940GZZLUMHL",
                    stationName = "Mill Hill East Underground Station",
                    timeToStation = 1200 // 20 mins
                )
            )
        )

        val repository = TransitRepositoryImpl(
            apiService = fakeTflApi,
            nrApiService = com.androidfung.departureboard.test.FakeNrApiService(),
            dataStore = com.androidfung.departureboard.test.FakeStationPreferencesDataSource(emptyList(), null)
        )

        val departure = TestFixtures.createTestDeparture(
            id = "dep_mhe",
            stationId = "940GZZLUMHL",
            stationName = "Mill Hill East",
            lineId = "northern",
            lineName = "Northern",
            platformName = "Platform 1",
            destinationName = "Camden Town",
            towards = "Camden Town",
            timeToStationSeconds = 1200, // 20 mins
            currentLocation = "Between Angel and Kings Cross",
            modeName = "tube",
            lineBadge = LineBadgeInfo("northern", "Northern", androidx.compose.ui.graphics.Color.Black, mode = TransitMode.TUBE),
            vehicleId = inboundVehicleId
        )

        val callingPoints = repository.getCallingPoints(departure)

        assertEquals(3, callingPoints.size)
        assertEquals("Mill Hill East", callingPoints[0].stationName)
        assertEquals("Finchley Central", callingPoints[1].stationName)
        assertEquals("Camden Town", callingPoints[2].stationName)

        // Mill Hill East: 20 mins
        assertEquals("20 mins", callingPoints[0].estimatedTime)

        // Downstream stops must have strictly increasing ETAs, NOT 17 mins or 5 mins from the inbound leg!
        // Finchley Central should be > 20 mins (e.g. 22 mins)
        // Camden Town should be > Finchley Central (e.g. 24 mins)
        val mheMins = callingPoints[0].estimatedTime!!.replace(" mins", "").toInt()
        val finchleyMins = callingPoints[1].estimatedTime!!.replace(" mins", "").toInt()
        val camdenMins = callingPoints[2].estimatedTime!!.replace(" mins", "").toInt()

        assertTrue("Finchley Central ETA ($finchleyMins) must be greater than Mill Hill East ETA ($mheMins)", finchleyMins > mheMins)
        assertTrue("Camden Town ETA ($camdenMins) must be greater than Finchley Central ETA ($finchleyMins)", camdenMins > finchleyMins)
    }

    @Test
    fun northernLine_mordenViaCharingCross_callsAllStopsToMorden() = runTest {
        // Topology reflecting Northern Line branches:
        // Branch 2: Mill Hill East -> Finchley Central, next = [9]
        // Branch 9: Finchley Central -> Camden Town, next = [3, 4]
        // Branch 3: Camden Town -> Mornington Crescent -> Euston, next = [5]
        // Branch 5: Euston -> Charing Cross -> Kennington, next = [7, 8]
        // Branch 4: Camden Town -> Euston (Bank branch), next = [6]
        // Branch 6: Euston -> Bank -> Kennington, next = [8]
        // Branch 8: Kennington -> Morden
        val mockSequence = TflRouteSequenceResponse(
            lineId = "northern",
            stopPointSequences = listOf(
                TflStopPointSequence(
                    branchId = 2,
                    nextBranchIds = listOf(9),
                    stopPoint = listOf(
                        TflMatchedStop("940GZZLUMHL", "Mill Hill East Underground Station"),
                        TflMatchedStop("940GZZLUFYC", "Finchley Central Underground Station")
                    )
                ),
                TflStopPointSequence(
                    branchId = 9,
                    nextBranchIds = listOf(3, 4),
                    stopPoint = listOf(
                        TflMatchedStop("940GZZLUFYC", "Finchley Central Underground Station"),
                        TflMatchedStop("940GZZLUEFY", "East Finchley Underground Station"),
                        TflMatchedStop("940GZZLUCTN", "Camden Town Underground Station")
                    )
                ),
                TflStopPointSequence(
                    branchId = 3,
                    nextBranchIds = listOf(5),
                    stopPoint = listOf(
                        TflMatchedStop("940GZZLUCTN", "Camden Town Underground Station"),
                        TflMatchedStop("940GZZLUMTC", "Mornington Crescent Underground Station"),
                        TflMatchedStop("940GZZLUEUS", "Euston Underground Station")
                    )
                ),
                TflStopPointSequence(
                    branchId = 5,
                    nextBranchIds = listOf(7, 8),
                    stopPoint = listOf(
                        TflMatchedStop("940GZZLUEUS", "Euston Underground Station"),
                        TflMatchedStop("940GZZLUCHX", "Charing Cross Underground Station"),
                        TflMatchedStop("940GZZLUKNG", "Kennington Underground Station")
                    )
                ),
                TflStopPointSequence(
                    branchId = 4,
                    nextBranchIds = listOf(6),
                    stopPoint = listOf(
                        TflMatchedStop("940GZZLUCTN", "Camden Town Underground Station"),
                        TflMatchedStop("940GZZLUEUS", "Euston Underground Station")
                    )
                ),
                TflStopPointSequence(
                    branchId = 6,
                    nextBranchIds = listOf(8),
                    stopPoint = listOf(
                        TflMatchedStop("940GZZLUEUS", "Euston Underground Station"),
                        TflMatchedStop("940GZZLUBNK", "Bank Underground Station"),
                        TflMatchedStop("940GZZLUKNG", "Kennington Underground Station")
                    )
                ),
                TflStopPointSequence(
                    branchId = 8,
                    nextBranchIds = emptyList(),
                    stopPoint = listOf(
                        TflMatchedStop("940GZZLUKNG", "Kennington Underground Station"),
                        TflMatchedStop("940GZZLUMDN", "Morden Underground Station")
                    )
                )
            )
        )

        val fakeTflApi = com.androidfung.departureboard.test.FakeTflApiService(
            routeSequenceResult = mockSequence
        )

        val repository = TransitRepositoryImpl(
            apiService = fakeTflApi,
            nrApiService = com.androidfung.departureboard.test.FakeNrApiService(),
            dataStore = com.androidfung.departureboard.test.FakeStationPreferencesDataSource(emptyList(), null)
        )

        val departure = TestFixtures.createTestDeparture(
            id = "dep_chx",
            stationId = "940GZZLUMHL",
            stationName = "Mill Hill East",
            lineId = "northern",
            lineName = "Northern",
            platformName = "Platform 1",
            destinationName = "Morden via Charing Cross",
            towards = "Morden via Charing Cross",
            destinationStationId = "940GZZLUMDN",
            timeToStationSeconds = 120,
            modeName = "tube",
            lineBadge = LineBadgeInfo("northern", "Northern", androidx.compose.ui.graphics.Color.Black, mode = TransitMode.TUBE)
        )

        val callingPoints = repository.getCallingPoints(departure)

        assertEquals("Mill Hill East", callingPoints.first().stationName)
        assertEquals("Morden", callingPoints.last().stationName)
        assertTrue(callingPoints.any { it.stationName == "Charing Cross" })
        assertTrue(callingPoints.any { it.stationName == "Kennington" })
        assertTrue(callingPoints.last().isDestination)
    }

    @Test
    fun districtLine_wimbledonToBarking_resolvesCallingPointsByStationCode() = runTest {
        // Multi-branch or multi-line routing on District line:
        // Branch 1: Wimbledon -> Earl's Court
        // Branch 2: Earl's Court -> Tower Hill -> Barking
        val mockSequence = TflRouteSequenceResponse(
            lineId = "district",
            stopPointSequences = listOf(
                TflStopPointSequence(
                    branchId = 1,
                    nextBranchIds = listOf(2),
                    stopPoint = listOf(
                        TflMatchedStop("940GZZLUWBR", "Wimbledon Underground Station"),
                        TflMatchedStop("940GZZLUPNY", "Putney Bridge Underground Station"),
                        TflMatchedStop("940GZZLUECT", "Earl's Court Underground Station")
                    )
                ),
                TflStopPointSequence(
                    branchId = 2,
                    nextBranchIds = emptyList(),
                    stopPoint = listOf(
                        TflMatchedStop("940GZZLUECT", "Earl's Court Underground Station"),
                        TflMatchedStop("940GZZLUTWH", "Tower Hill Underground Station"),
                        TflMatchedStop("940GZZLUBKG", "Barking Underground Station")
                    )
                )
            )
        )

        val fakeTflApi = com.androidfung.departureboard.test.FakeTflApiService(
            routeSequenceResult = mockSequence
        )

        val repository = TransitRepositoryImpl(
            apiService = fakeTflApi,
            nrApiService = com.androidfung.departureboard.test.FakeNrApiService(),
            dataStore = com.androidfung.departureboard.test.FakeStationPreferencesDataSource(emptyList(), null)
        )

        val departure = TestFixtures.createTestDeparture(
            id = "dep_district",
            stationId = "940GZZLUWBR",
            stationName = "Wimbledon",
            lineId = "district",
            lineName = "District",
            platformName = "Platform 1",
            destinationName = "Barking",
            towards = "Barking via Tower Hill",
            destinationStationId = "940GZZLUBKG",
            timeToStationSeconds = 60,
            modeName = "tube",
            lineBadge = LineBadgeInfo("district", "District", androidx.compose.ui.graphics.Color.Green, mode = TransitMode.TUBE)
        )

        val callingPoints = repository.getCallingPoints(departure)

        assertEquals("Wimbledon", callingPoints.first().stationName)
        assertEquals("Barking", callingPoints.last().stationName)
        assertEquals(5, callingPoints.size)
        assertTrue(callingPoints.first().isCurrentStation)
        assertTrue(callingPoints.last().isDestination)
    }
}
