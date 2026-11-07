package com.androidfung.departureboard.data.topology

import com.androidfung.departureboard.data.model.TransitDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class BranchedRouteTest {

    private val dummyDirectionResolver = object : DirectionResolver {
        override fun resolve(
            destinationName: String?,
            destinationCrs: String?,
            destinationNaptanId: String?,
            currentStationId: String?,
            currentStationName: String?,
            platformName: String?,
            apiDirection: String?
        ): TransitDirection? = null
    }

    @Test
    fun branchedRoute_slicesCorrectBranchPattern_forNorthernLine() {
        // High Barnet to Morden via Bank
        val patternBank = RoutePattern(
            patternId = "northern_bank",
            name = "Via Bank",
            stations = listOf("HBT", "EFY", "CTN", "KXX", "OLD", "BNK", "LBG", "KNG", "STK", "MDN")
        )

        // Edgware to Battersea via Charing Cross
        val patternCxBattersea = RoutePattern(
            patternId = "northern_cx_battersea",
            name = "Via Charing Cross to Battersea",
            stations = listOf("EGW", "HMP", "CTN", "EUS", "TCR", "CHX", "WLO", "KNG", "NEL", "BPS")
        )

        val northernRoute = BranchedRoute(
            lineId = "NORTHERN",
            patterns = listOf(patternBank, patternCxBattersea),
            directionResolver = dummyDirectionResolver
        )

        // Slicing on the Bank branch
        val sliceBank = northernRoute.slice("HBT", "MDN")
        assertEquals(listOf("HBT", "EFY", "CTN", "KXX", "OLD", "BNK", "LBG", "KNG", "STK", "MDN"), sliceBank)

        // Slicing on the Charing Cross to Battersea branch
        val sliceBattersea = northernRoute.slice("EGW", "BPS")
        assertEquals(listOf("EGW", "HMP", "CTN", "EUS", "TCR", "CHX", "WLO", "KNG", "NEL", "BPS"), sliceBattersea)

        // Slicing intermediate stops: Camden Town to Charing Cross
        val sliceCamdenToChx = northernRoute.slice("CTN", "CHX")
        assertEquals(listOf("CTN", "EUS", "TCR", "CHX"), sliceCamdenToChx)

        // Inbound slicing (Battersea to Euston)
        val sliceInbound = northernRoute.slice("BPS", "EUS")
        assertEquals(listOf("BPS", "NEL", "KNG", "WLO", "CHX", "TCR", "EUS"), sliceInbound)
    }

    @Test
    fun branchedRoute_resolvesDirectionCorrectly() {
        val pattern = RoutePattern(
            patternId = "main",
            stations = listOf("A", "B", "C", "D")
        )
        val route = BranchedRoute(
            lineId = "TEST",
            patterns = listOf(pattern),
            directionResolver = dummyDirectionResolver
        )

        assertEquals(TransitDirection.OUTBOUND, route.resolveDirection("A", "D"))
        assertEquals(TransitDirection.INBOUND, route.resolveDirection("D", "A"))
    }
}
