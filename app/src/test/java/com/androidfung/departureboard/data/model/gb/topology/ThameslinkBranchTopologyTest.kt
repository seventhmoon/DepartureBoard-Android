package com.androidfung.departureboard.data.model.gb.topology

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThameslinkBranchTopologyTest {

    @Test
    fun thameslinkRoute_branchedRouteSlicesBedfordToBrightonWithoutPeterborough() {
        val route = ThameslinkRoute.BRANCHED_ROUTE
        val stations = route.slice("BED", "BTN")

        assertTrue(stations.isNotEmpty())
        assertEquals("BED", stations.first())
        assertEquals("BTN", stations.last())

        // Must include London core stations
        assertTrue(stations.contains("STP"))
        assertTrue(stations.contains("LBG"))
        assertTrue(stations.contains("GTW"))

        // Must NOT include separate branch stations
        assertFalse(stations.contains("PBO")) // Peterborough
        assertFalse(stations.contains("CBG")) // Cambridge
        assertFalse(stations.contains("RNH")) // Rainham
        assertFalse(stations.contains("HRH")) // Horsham
    }

    @Test
    fun thameslinkRoute_branchedRouteSlicesPeterboroughToHorshamWithoutCambridge() {
        val route = ThameslinkRoute.BRANCHED_ROUTE
        val stations = route.slice("PBO", "HRH")

        assertTrue(stations.isNotEmpty())
        assertEquals("PBO", stations.first())
        assertEquals("HRH", stations.last())

        assertTrue(stations.contains("STV")) // Stevenage
        assertTrue(stations.contains("FPK")) // Finsbury Park
        assertTrue(stations.contains("STP")) // St Pancras
        assertTrue(stations.contains("GTW")) // Gatwick

        assertFalse(stations.contains("BED")) // Bedford
        assertFalse(stations.contains("CBG")) // Cambridge
    }

    @Test
    fun gbTransitRoutes_resolvesThameslinkSequenceKeyCorrectly() {
        assertEquals(
            "THAMESLINK_PETERBOROUGH_HORSHAM",
            GbTransitRoutes.resolveSequenceKey("THAMESLINK", "Peterborough", "Horsham")
        )
        assertEquals(
            "THAMESLINK_CAMBRIDGE_BRIGHTON",
            GbTransitRoutes.resolveSequenceKey("THAMESLINK", "Cambridge", "Brighton")
        )
        assertEquals(
            "THAMESLINK_LUTON_RAINHAM",
            GbTransitRoutes.resolveSequenceKey("THAMESLINK", "Luton", "Rainham")
        )
        assertEquals(
            "THAMESLINK_BEDFORD_BRIGHTON",
            GbTransitRoutes.resolveSequenceKey("THAMESLINK", "Bedford", "Brighton")
        )
    }
}
