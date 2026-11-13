package com.androidfung.departureboard.data.model

import com.androidfung.departureboard.data.model.gb.BodsXmlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodsXmlParserTest {

    @Test
    fun parse_extractsVehicleActivityWithAccessibilityAndOccupancy() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Siri xmlns="http://www.siri.org.uk/siri" version="2.0">
                <ServiceDelivery>
                    <VehicleMonitoringDelivery>
                        <VehicleActivity>
                            <RecordedAtTime>2025-05-18T10:00:00Z</RecordedAtTime>
                            <MonitoredVehicleJourney>
                                <LineRef>310</LineRef>
                                <PublishedLineName>310</PublishedLineName>
                                <OperatorRef>ARRIVA</OperatorRef>
                                <OriginName>Hertford</OriginName>
                                <DestinationName>Waltham_Cross</DestinationName>
                                <DestinationAimedArrivalTime>2025-05-18T10:30:00Z</DestinationAimedArrivalTime>
                                <VehicleLocation>
                                    <Longitude>-0.0712</Longitude>
                                    <Latitude>51.7231</Latitude>
                                </VehicleLocation>
                                <Bearing>180.0</Bearing>
                                <WheelchairAccessible>true</WheelchairAccessible>
                                <Occupancy>seatsAvailable</Occupancy>
                                <VehicleRef>BUS-4512</VehicleRef>
                            </MonitoredVehicleJourney>
                        </VehicleActivity>
                    </VehicleMonitoringDelivery>
                </ServiceDelivery>
            </Siri>
        """.trimIndent()

        val results = BodsXmlParser.parse(xml)
        assertEquals(1, results.size)
        val activity = results[0]
        assertEquals("310", activity.lineRef)
        assertEquals("Waltham_Cross", activity.destinationName)
        assertEquals("BUS-4512", activity.vehicleRef)
        assertEquals(true, activity.wheelchairAccessible)
        assertEquals("seatsAvailable", activity.occupancy)
        assertEquals(51.7231, activity.latitude ?: 0.0, 0.001)

        val departure = activity.toDeparture(
            stationId = "210001",
            stationName = "Waltham Cross",
            stationLat = 51.72,
            stationLon = -0.07
        )
        assertNotNull(departure)
        assertEquals("Waltham Cross", departure.destinationName)
        assertTrue(departure.isWheelchairAccessible == true)
        assertEquals("seatsAvailable", departure.occupancyStatus)
        assertEquals("", departure.platformName)

        // Repeated calls across time preserve deterministic departure identity
        val secondDeparture = activity.toDeparture(
            stationId = "210001",
            stationName = "Waltham Cross",
            stationLat = 51.72,
            stationLon = -0.07
        )
        assertEquals(departure.id, secondDeparture.id)
    }

    @Test
    fun `parse accumulates entity text cleanly and rejects DOCTYPE XXE declarations`() {
        val entityXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Siri xmlns="http://www.siri.org.uk/siri" version="2.0">
                <ServiceDelivery><VehicleMonitoringDelivery><VehicleActivity>
                    <MonitoredVehicleJourney>
                        <LineRef>310</LineRef>
                        <PublishedLineName>310 &amp; Express</PublishedLineName>
                        <DestinationName>King&apos;s_Cross &amp; St Pancras</DestinationName>
                    </MonitoredVehicleJourney>
                </VehicleActivity></VehicleMonitoringDelivery></ServiceDelivery>
            </Siri>
        """.trimIndent()

        val parsed = BodsXmlParser.parse(entityXml)
        assertEquals(1, parsed.size)
        assertEquals("310 & Express", parsed[0].publishedLineName)
        assertEquals("King's_Cross & St Pancras", parsed[0].destinationName)

        val xxeXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE Siri [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <Siri><ServiceDelivery><VehicleMonitoringDelivery><VehicleActivity>
                <MonitoredVehicleJourney><LineRef>&xxe;</LineRef></MonitoredVehicleJourney>
            </VehicleActivity></VehicleMonitoringDelivery></ServiceDelivery></Siri>
        """.trimIndent()
        assertTrue(BodsXmlParser.parse(xxeXml).isEmpty())
    }
}
