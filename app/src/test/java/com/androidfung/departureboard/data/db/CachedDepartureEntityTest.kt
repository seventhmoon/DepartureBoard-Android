package com.androidfung.departureboard.data.db

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.HkDefaultStations
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.TflLineStatusDetail
import com.androidfung.departureboard.data.model.TflLineStatusItem
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.model.TransitRegion
import com.androidfung.departureboard.data.repository.source.gb.TyneAndWearMetroSource
import com.androidfung.departureboard.ui.components.buildDisplayLineStatusItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CachedDepartureEntityTest {

    @Test
    fun `fromDeparture and toDeparture preserve custom badge colors and decay time accurately`() {
        val customBadge = LineBadgeInfo(
            lineId = "HK_BUS_104",
            displayName = "104",
            backgroundColor = HkDefaultStations.ColorKmbRed,
            textColor = Color.White,
            mode = TransitMode.BUS,
            lineCode = "KMB"
        )
        val cachedAt = 1_700_000_000_000L
        val original = Departure(
            id = "dep_1",
            stationId = "HK_BUS_KMB_001554",
            stationName = "Cross Harbour Tunnel",
            lineId = "HK_BUS_104",
            lineName = "104",
            platformName = "",
            destinationName = "Pak Tin",
            towards = "Pak Tin",
            timeToStationSeconds = 300,
            expectedArrivalIso = null,
            currentLocation = "In Transit",
            modeName = "bus",
            lineBadge = customBadge,
            status = DepartureStatus.ON_TIME,
            isWheelchairAccessible = true,
            occupancyStatus = "FEW_SEATS_AVAILABLE"
        )

        val entity = CachedDepartureEntity.fromDeparture(original, cachedAt)

        // 120 seconds later: 300 - 120 = 180s remaining, not expired
        val twoMinsLater = cachedAt + 120_000L
        assertFalse(entity.isExpired(twoMinsLater))
        val restored = entity.toDeparture(twoMinsLater)
        assertEquals(180, restored.timeToStationSeconds)
        assertEquals(HkDefaultStations.ColorKmbRed, restored.lineBadge.backgroundColor)
        assertEquals("KMB", restored.lineBadge.lineCode)
        assertEquals(true, restored.isWheelchairAccessible)
        assertEquals("FEW_SEATS_AVAILABLE", restored.occupancyStatus)

        // 400 seconds later (> 300 + 45s grace): expired
        val expiredTime = cachedAt + 400_000L
        assertTrue(entity.isExpired(expiredTime))
    }

    @Test
    fun `TyneAndWearMetroSource searches expanded Metro stations`() {
        val jesmondMatches = TyneAndWearMetroSource.searchMetroStations("Jesmond")
        assertTrue(jesmondMatches.any { it.id == "JES" })

        val gatesheadMatches = TyneAndWearMetroSource.searchMetroStations("Gateshead")
        assertTrue(gatesheadMatches.any { it.id == "GAT" })
    }

    @Test
    fun `buildDisplayLineStatusItems sorts disrupted lines first for GB and lists MTR lines for HK`() {
        val statuses = mapOf(
            "northern" to TflLineStatusItem(
                id = "northern",
                name = "Northern",
                modeName = "tube",
                lineStatuses = listOf(
                    TflLineStatusDetail(
                        statusSeverity = 6,
                        statusSeverityDescription = "Severe Delays",
                        reason = "Track fault at Camden Town"
                    )
                )
            ),
            "central" to TflLineStatusItem(
                id = "central",
                name = "Central",
                modeName = "tube",
                lineStatuses = listOf(
                    TflLineStatusDetail(
                        statusSeverity = 10,
                        statusSeverityDescription = "Good Service",
                        reason = null
                    )
                )
            )
        )
        // Check short form badgeCode
        val gbItems = buildDisplayLineStatusItems(statuses, TransitRegion.GB)
        assertTrue(gbItems.isNotEmpty())
        assertEquals("northern", gbItems.first().id)
        assertTrue(gbItems.first().isDisrupted)
        assertEquals("NOR", gbItems.first().badgeCode)

        // When user has relevant lines, they are prioritized first
        val relevantItems = buildDisplayLineStatusItems(statuses, TransitRegion.GB, relevantLineIds = setOf("central"))
        assertEquals("central", relevantItems.first().id)
        assertTrue(relevantItems.first().isRelevantToUser)

        val hkItems = buildDisplayLineStatusItems(emptyMap(), TransitRegion.HK)
        assertEquals(10, hkItems.size)
        assertTrue(hkItems.any { it.id == "ISL" })
        assertEquals("ISL", hkItems.first { it.id == "ISL" }.badgeCode)
    }
}
