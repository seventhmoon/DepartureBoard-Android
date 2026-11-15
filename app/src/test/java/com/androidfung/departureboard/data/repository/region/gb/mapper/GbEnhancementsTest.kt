package com.androidfung.departureboard.data.repository.region.gb.mapper

import com.androidfung.departureboard.data.model.OccupancyStatus
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.StationLineInfo
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.model.WheelchairBoardingStatus
import com.androidfung.departureboard.data.model.gb.BodsVehicleActivity
import com.androidfung.departureboard.data.model.gb.BodsXmlParser
import com.androidfung.departureboard.data.model.gb.NrBoardResponse
import com.androidfung.departureboard.data.model.gb.NrCoach
import com.androidfung.departureboard.data.model.gb.NrFormation
import com.androidfung.departureboard.data.model.gb.NrLocation
import com.androidfung.departureboard.data.model.gb.NrMessage
import com.androidfung.departureboard.data.model.gb.NrToilet
import com.androidfung.departureboard.data.model.gb.NrTrainService
import com.androidfung.departureboard.data.model.gb.TflAdditionalProperty
import com.androidfung.departureboard.data.model.gb.TflStopPointDetail
import com.androidfung.departureboard.data.network.gb.BodsApiService
import com.androidfung.departureboard.data.repository.region.gb.BodsDepartureSource
import com.androidfung.departureboard.data.repository.region.gb.NationalRailDepartureSource
import com.androidfung.departureboard.test.FakeNrApiService
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GbEnhancementsTest {

    @Test
    fun `NationalRailDepartureMapper maps formation coaches, loading, toilets, and split flags`() {
        val service = NrTrainService(
            serviceId = "svc-123",
            std = "10:00",
            etd = "On time",
            operator = "LNER",
            operatorCode = "GR",
            detachFront = true,
            isReverseFormation = true,
            destination = listOf(NrLocation(locationName = "Edinburgh", crs = "EDB")),
            formation = NrFormation(
                avgLoading = 60,
                coaches = listOf(
                    NrCoach(number = "A", coachClass = "Standard", loading = 30, toilet = NrToilet(value = "Accessible")),
                    NrCoach(number = "K", coachClass = "First", loading = 85, toilet = NrToilet(value = "None"))
                )
            )
        )

        val dep = NationalRailDepartureMapper.mapToDeparture(service, "KGX", "London King's Cross")
        assertEquals(OccupancyStatus.FEW_SEATS_AVAILABLE.name, dep.occupancyStatus)
        val formation = dep.train?.formation
        requireNotNull(formation)
        assertEquals(2, formation.coachCount)
        assertEquals(2, formation.coaches.size)
        assertEquals(true, formation.coaches[0].hasToilet)
        assertEquals(true, formation.coaches[0].isWheelchairAccessible)
        assertEquals(OccupancyStatus.MANY_SEATS_AVAILABLE.name, formation.coaches[0].occupancyStatus)
        assertEquals(false, formation.coaches[1].hasToilet)
        assertEquals(OccupancyStatus.STANDING_ROOM_ONLY.name, formation.coaches[1].occupancyStatus)
        assertTrue(dep.associations.any { it.notice?.contains("Front portion detaches", ignoreCase = true) == true })
        assertTrue(dep.associations.any { it.notice?.contains("reverse formation", ignoreCase = true) == true })
    }

    @Test
    fun `NationalRailDepartureMapper falls back to coaches size when length is zero`() {
        val coaches = (1..9).map { NrCoach(number = "$it", coachClass = "Standard", loading = 40) }
        val serviceWithZeroLength = NrTrainService(
            serviceId = "xr-123",
            std = "23:17",
            etd = "Delayed",
            platform = "B",
            operator = "Elizabeth Line",
            operatorCode = "XR",
            length = 0,
            destination = listOf(NrLocation(locationName = "Heathrow Terminal 4", crs = "HAF")),
            formation = NrFormation(coaches = coaches)
        )

        val dep = NationalRailDepartureMapper.mapToDeparture(serviceWithZeroLength, "LST", "Liverpool Street")
        val formation = dep.train?.formation
        requireNotNull(formation)
        assertEquals(9, formation.coachCount)
        assertEquals(9, formation.coaches.size)
        assertEquals("9 coaches", formation.displayCoachCount)

        val emptyZeroLengthService = serviceWithZeroLength.copy(formation = null)
        val depEmpty = NationalRailDepartureMapper.mapToDeparture(emptyZeroLengthService, "LST", "Liverpool Street")
        assertNull(depEmpty.train?.formation)
    }

    @Test
    fun `NationalRailDepartureSource includes rail replacement busServices and caches nrccMessages`() = runTest {
        val fakeNr = FakeNrApiService(
            boardResponse = NrBoardResponse(
                crs = "WAT",
                trainServices = emptyList(),
                busServices = listOf(
                    NrTrainService(
                        serviceId = "bus-1",
                        std = "12:15",
                        etd = "On time",
                        operator = "South Western Railway",
                        operatorCode = "SW",
                        serviceType = "bus",
                        destination = listOf(NrLocation(locationName = "Guildford", crs = "GLD"))
                    )
                ),
                nrccMessages = listOf(NrMessage(value = "<p>Disruption between Woking and Guildford.</p>"))
            )
        )
        val source = NationalRailDepartureSource(fakeNr)
        val result = source.fetchDepartures("WAT", "London Waterloo", isBusStop = false)
        assertTrue(result.isSuccess)
        val deps = result.getOrThrow()
        assertEquals(1, deps.size)
        assertEquals(TransitMode.BUS.id, deps.first().modeName)
        assertEquals("RRB", deps.first().lineBadge.lineCode)

        val alerts = source.getCachedNrccAlerts("WAT", "London Waterloo")
        assertEquals(1, alerts.size)
        assertTrue(alerts.first().descriptionText?.contains("Disruption between Woking and Guildford") == true)
    }

    @Test
    fun `TflStationMapper extracts Zone and accessibility from additionalProperties`() {
        val detail = TflStopPointDetail(
            id = "940GZZLUBND",
            commonName = "Bond Street Underground Station",
            additionalProperties = listOf(
                TflAdditionalProperty(category = "Facility", key = "Zone", value = "1"),
                TflAdditionalProperty(category = "Facility", key = "StepFreeAccess", value = "Step-free with platform ramp"),
                TflAdditionalProperty(category = "Facility", key = "Accessible Toilets", value = "Yes")
            )
        )
        assertEquals("1", detail.propertyValue("Zone"))
        val (wheelchair, accessibility) = TflStationMapper.extractAccessibility(detail)
        assertEquals(WheelchairBoardingStatus.STEP_FREE_WITH_RAMP, wheelchair)
        assertEquals(true, accessibility?.hasAccessibleToilet)
        assertEquals(true, accessibility?.requiresBoardingRamp)
    }

    @Test
    fun `BodsDepartureSource filters by stop lines and prefers GPS ETA over distant terminus time`() = runTest {
        val farTerminusIso = java.time.Instant.now().plusSeconds(3600).toString()
        val sampleXml = """
            <Siri><ServiceDelivery><VehicleMonitoringDelivery>
              <VehicleActivity><MonitoredVehicleJourney>
                <LineRef>310</LineRef><PublishedLineName>310</PublishedLineName>
                <DestinationName>Hertford</DestinationName>
                <DestinationAimedArrivalTime>$farTerminusIso</DestinationAimedArrivalTime>
                <VehicleLocation><Longitude>-0.0500</Longitude><Latitude>51.7550</Latitude></VehicleLocation>
                <VehicleRef>VEH-310</VehicleRef>
              </MonitoredVehicleJourney></VehicleActivity>
              <VehicleActivity><MonitoredVehicleJourney>
                <LineRef>999</LineRef><PublishedLineName>999</PublishedLineName>
                <DestinationName>Elsewhere</DestinationName>
                <VehicleLocation><Longitude>-0.0600</Longitude><Latitude>51.7600</Latitude></VehicleLocation>
                <VehicleRef>VEH-999</VehicleRef>
              </MonitoredVehicleJourney></VehicleActivity>
            </VehicleMonitoringDelivery></ServiceDelivery></Siri>
        """.trimIndent()

        val fakeBods = object : BodsApiService {
            override suspend fun getVehicleDatafeed(boundingBox: String, apiKey: String): ResponseBody =
                sampleXml.toResponseBody()

            override suspend fun getVehicleDatafeedByLine(lineRef: String, apiKey: String): ResponseBody =
                sampleXml.toResponseBody()

            override suspend fun getDisruptions(apiKey: String): ResponseBody =
                "<Siri/>".toResponseBody()
        }

        val station = Station(
            id = "210021000123",
            name = "Ware",
            modes = listOf("bus"),
            lat = 51.7500,
            lon = -0.0500,
            lines = listOf(StationLineInfo("bods-310", "310", "bus"))
        )

        val source = BodsDepartureSource(fakeBods) { station }
        val deps = source.fetchDepartures(station.id, station.name, isBusStop = true).getOrThrow()
        assertEquals(1, deps.size)
        assertEquals("VEH-310", deps.first().vehicleId)
        // ~555m away -> ~100s GPS ETA instead of 3600s distant terminus ETA
        assertTrue(deps.first().timeToStationSeconds in 60..200)
        assertTrue(deps.first().currentLocation?.startsWith("Live GPS") == true)
    }

    @Test
    fun `BodsXmlParser parseStream parses vehicle activity`() {
        val xml = """
            <Siri><ServiceDelivery><VehicleMonitoringDelivery>
              <VehicleActivity><MonitoredVehicleJourney>
                <LineRef>HL1</LineRef><PublishedLineName>HL1</PublishedLineName>
                <VehicleRef>BUS-1</VehicleRef>
              </MonitoredVehicleJourney></VehicleActivity>
            </VehicleMonitoringDelivery></ServiceDelivery></Siri>
        """.trimIndent()
        val activities = BodsXmlParser.parseStream(xml.byteInputStream())
        assertEquals(1, activities.size)
        assertEquals("BUS-1", activities.first().vehicleRef)
    }
}
