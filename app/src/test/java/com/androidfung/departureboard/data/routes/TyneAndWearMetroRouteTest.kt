package com.androidfung.departureboard.data.routes

import com.androidfung.departureboard.data.model.TransitDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TyneAndWearMetroRouteTest {

    @Test
    fun greenLineCallingPoints_fromPelawToAirport() {
        val points = TyneAndWearMetroRoute.resolveCallingPoints(
            lineId = "metro-green",
            originName = "Pelaw",
            destinationName = "Airport",
            departureSeconds = 120
        )

        assertTrue(points.isNotEmpty())
        assertEquals("Pelaw", points.first().stationName)
        assertEquals("Airport", points.last().stationName)
        assertTrue(points.first().isCurrentStation)
        assertTrue(points.last().isDestination)
    }

    @Test
    fun greenLineDirection_northboundAndSouthbound() {
        val nb = TyneAndWearMetroRoute.resolveDirection(
            lineId = "metro-green",
            originName = "Sunderland",
            destinationName = "Airport"
        )
        assertEquals(TransitDirection.NORTHBOUND, nb)

        val sb = TyneAndWearMetroRoute.resolveDirection(
            lineId = "metro-green",
            originName = "Haymarket",
            destinationName = "South Hylton"
        )
        assertEquals(TransitDirection.SOUTHBOUND, sb)
    }

    @Test
    fun yellowLineDirection_clockwiseAndAntiClockwise() {
        // Forward loop: St James -> North Shields -> South Gosforth -> Monument -> South Shields
        val cw = TyneAndWearMetroRoute.resolveDirection(
            lineId = "metro-yellow",
            originName = "Monument",
            destinationName = "North Shields"
        )
        assertEquals(TransitDirection.CLOCKWISE, cw)

        val ccw = TyneAndWearMetroRoute.resolveDirection(
            lineId = "metro-yellow",
            originName = "North Shields",
            destinationName = "St James"
        )
        assertEquals(TransitDirection.ANTI_CLOCKWISE, ccw)
    }

    @Test
    fun transitDirection_parsesClockwiseAndAntiClockwise() {
        assertEquals(TransitDirection.CLOCKWISE, TransitDirection.fromString("Clockwise"))
        assertEquals(TransitDirection.CLOCKWISE, TransitDirection.fromString("CW"))
        assertEquals(TransitDirection.CLOCKWISE, TransitDirection.fromString("Inner"))

        assertEquals(TransitDirection.ANTI_CLOCKWISE, TransitDirection.fromString("Anti-Clockwise"))
        assertEquals(TransitDirection.ANTI_CLOCKWISE, TransitDirection.fromString("Counter-Clockwise"))
        assertEquals(TransitDirection.ANTI_CLOCKWISE, TransitDirection.fromString("CCW"))
        assertEquals(TransitDirection.ANTI_CLOCKWISE, TransitDirection.fromString("Outer"))
    }

    @Test
    fun stationEnum_matchesColloquialNames() {
        val monument = TyneAndWearMetroStation.fromString("Monument Metro Station")
        assertNotNull(monument)
        assertEquals(TyneAndWearMetroStation.MONUMENT, monument)

        val airport = TyneAndWearMetroStation.fromString("Newcastle Airport")
        assertNotNull(airport)
        assertEquals(TyneAndWearMetroStation.AIRPORT, airport)
    }
}
