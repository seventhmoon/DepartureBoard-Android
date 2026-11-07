package com.androidfung.departureboard.data.repository.region.gb.direction

import org.junit.Assert.assertEquals
import org.junit.Test

class DestinationResolverTest {

    @Test
    fun testElizabethLineDirectionResolution() {
        // Name-based with viewing station context
        assertEquals("Eastbound", ElizabethLineDirectionResolver.resolve(destinationName = "Abbey Wood", currentStationName = "Paddington")?.displayName)
        assertEquals("Eastbound", ElizabethLineDirectionResolver.resolve(destinationName = "Shenfield", currentStationName = "Paddington")?.displayName)
        assertEquals("Eastbound", ElizabethLineDirectionResolver.resolve(destinationName = "Stratford", currentStationName = "Paddington")?.displayName)
        assertEquals("Westbound", ElizabethLineDirectionResolver.resolve(destinationName = "London Paddington", currentStationId = "SRA")?.displayName)
        assertEquals("Westbound", ElizabethLineDirectionResolver.resolve(destinationName = "Reading", currentStationName = "Paddington")?.displayName)
        assertEquals("Westbound", ElizabethLineDirectionResolver.resolve(destinationName = "Heathrow Terminal 4", currentStationName = "Paddington")?.displayName)

        // O(1) CRS-based (National Rail)
        assertEquals("Eastbound", ElizabethLineDirectionResolver.resolve(destinationCrs = "ABW", currentStationId = "PAD")?.displayName)
        assertEquals("Eastbound", ElizabethLineDirectionResolver.resolve(destinationCrs = "SNF", currentStationId = "PAD")?.displayName)
        assertEquals("Westbound", ElizabethLineDirectionResolver.resolve(destinationCrs = "PAD", currentStationId = "SRA")?.displayName)
        assertEquals("Westbound", ElizabethLineDirectionResolver.resolve(destinationCrs = "RDG", currentStationId = "PAD")?.displayName)
        assertEquals("Westbound", ElizabethLineDirectionResolver.resolve(destinationCrs = "HXX", currentStationId = "PAD")?.displayName)

        // O(1) NaPTAN-based (TfL API)
        assertEquals("Eastbound", ElizabethLineDirectionResolver.resolve(destinationNaptanId = "910GABWD", currentStationId = "910GPADTON")?.displayName)
        assertEquals("Westbound", ElizabethLineDirectionResolver.resolve(destinationNaptanId = "910GREADING", currentStationId = "910GPADTON")?.displayName)

        // Platform-based (explicit hints)
        assertEquals("Eastbound", ElizabethLineDirectionResolver.resolve(platformName = "Platform 1 Eastbound")?.displayName)
        assertEquals("Westbound", ElizabethLineDirectionResolver.resolve(platformName = "Platform 2 Westbound")?.displayName)

        // Relative Route-Position Comparison from intermediate stations:
        // When at Reading (West), a train towards Paddington is Eastbound!
        assertEquals("Eastbound", ElizabethLineDirectionResolver.resolve(destinationCrs = "PAD", currentStationId = "RDG")?.displayName)
        // When at Stratford (East), a train towards Paddington is Westbound!
        assertEquals("Westbound", ElizabethLineDirectionResolver.resolve(destinationCrs = "PAD", currentStationId = "SRA")?.displayName)
    }

    @Test
    fun nonTerminatingTrain_returnsOriginalDestination() {
        val result = DestinationResolver.resolve(
            stationName = "King's Cross St. Pancras",
            itemDestination = "Cockfosters",
            itemTowards = "Cockfosters",
            lineId = "piccadilly",
            platformName = "Platform 1"
        )
        assertEquals("Cockfosters", result)
    }

    @Test
    fun terminatingTrain_victoriaLine_brixtonResolvesToWalthamstow() {
        val result = DestinationResolver.resolve(
            stationName = "Brixton",
            itemDestination = "Brixton",
            itemTowards = null,
            lineId = "victoria",
            platformName = "Platform 1"
        )
        assertEquals("Walthamstow Central", result)
    }

    @Test
    fun terminatingTrain_victoriaLine_walthamstowResolvesToBrixton() {
        val result = DestinationResolver.resolve(
            stationName = "Walthamstow Central",
            itemDestination = "Walthamstow Central",
            itemTowards = null,
            lineId = "victoria",
            platformName = "Platform 2"
        )
        assertEquals("Brixton", result)
    }

    @Test
    fun terminatingTrain_northernLine_withViaBranch() {
        val cxResult = DestinationResolver.resolve(
            stationName = "Edgware",
            itemDestination = "Edgware",
            itemTowards = "via CX",
            lineId = "northern",
            platformName = "Platform 1"
        )
        assertEquals("Morden via Charing Cross", cxResult)

        val bankResult = DestinationResolver.resolve(
            stationName = "Edgware",
            itemDestination = "Edgware",
            itemTowards = "via Bank",
            lineId = "northern",
            platformName = "Platform 1"
        )
        assertEquals("Morden via Bank", bankResult)
    }

    @Test
    fun terminatingTrain_millHillEast_resolvesFinchleyCentralOrBattersea() {
        // Without outbound towards or with generic inbound, default to Finchley Central
        val defaultResult = DestinationResolver.resolve(
            stationName = "Mill Hill East",
            itemDestination = "Mill Hill East",
            itemTowards = null,
            lineId = "northern",
            platformName = "Platform 1"
        )
        assertEquals("Finchley Central", defaultResult)

        // When TfL provides inbound "Mill Hill East via CX", it should not force Morden via Charing Cross
        val cxInboundResult = DestinationResolver.resolve(
            stationName = "Mill Hill East",
            itemDestination = "Mill Hill East",
            itemTowards = "Mill Hill East via CX",
            lineId = "northern",
            platformName = "Platform 1"
        )
        assertEquals("Battersea Power Station via Charing Cross", cxInboundResult)

        // Outbound train heading to Morden with "Morden via Bank" or "via Bank" retains corridor
        val mordenViaBank = DestinationResolver.resolve(
            stationName = "Mill Hill East",
            itemDestination = "Morden",
            itemTowards = "Morden via Bank",
            lineId = "northern",
            platformName = "Platform 1"
        )
        assertEquals("Morden via Bank", mordenViaBank)

        val mordenViaShort = DestinationResolver.resolve(
            stationName = "Mill Hill East",
            itemDestination = "Morden",
            itemTowards = "via Bank",
            lineId = "northern",
            platformName = "Platform 1"
        )
        assertEquals("Morden via Bank", mordenViaShort)

        // Outbound train with abbreviated "Battersea via CX" normalizes to canonical full name
        val batterseaCxAbbrev = DestinationResolver.resolve(
            stationName = "Mill Hill East",
            itemDestination = "Battersea Power Station",
            itemTowards = "Battersea via CX",
            lineId = "northern",
            platformName = "Platform 1"
        )
        assertEquals("Battersea Power Station via Charing Cross", batterseaCxAbbrev)

        val batterseaCxShort = DestinationResolver.resolve(
            stationName = "Mill Hill East",
            itemDestination = "Battersea",
            itemTowards = "via CX",
            lineId = "northern",
            platformName = "Platform 1"
        )
        assertEquals("Battersea Power Station via Charing Cross", batterseaCxShort)
    }

    @Test
    fun normalizeCorridorDestination_expandsTflAbbreviations() {
        assertEquals("Battersea Power Station via Charing Cross", DestinationResolver.normalizeCorridorDestination("Battersea via CX"))
        assertEquals("Battersea Power Station via Charing Cross", DestinationResolver.normalizeCorridorDestination("Battersea Power Station via CX"))
        assertEquals("Battersea Power Station via Charing Cross", DestinationResolver.normalizeCorridorDestination("BPS via CX"))
        assertEquals("Morden via Charing Cross", DestinationResolver.normalizeCorridorDestination("Morden via CX"))
        assertEquals("Edgware via Charing Cross", DestinationResolver.normalizeCorridorDestination("Edgware via cx"))
        assertEquals("High Barnet via Bank", DestinationResolver.normalizeCorridorDestination("High Barnet via Bnk"))
        assertEquals("Battersea Power Station", DestinationResolver.normalizeCorridorDestination("Battersea"))
        assertEquals("Battersea Power Station", DestinationResolver.normalizeCorridorDestination("BPS"))
    }

    @Test
    fun edgwareRoad_districtCircle_doesNotTriggerNorthernEdgwareRule() {
        val result = DestinationResolver.resolve(
            stationName = "Edgware Road",
            itemDestination = "Edgware Road",
            itemTowards = null,
            lineId = "circle",
            platformName = "Platform 2"
        )
        assertEquals("Hammersmith via Tower Hill", result)
    }

    @Test
    fun checkFrontOfTrain_resolvesUsingTowardsWhenAvailable() {
        val result = DestinationResolver.resolve(
            stationName = "Morden",
            itemDestination = "Check Front of Train",
            itemTowards = "High Barnet via Bank",
            lineId = "northern",
            platformName = "Platform 1"
        )
        assertEquals("High Barnet via Bank", result)
    }

    @Test
    fun terminatingTrain_waterlooAndCity_resolvesOppositeTerminus() {
        val waterlooResult = DestinationResolver.resolve(
            stationName = "Waterloo",
            itemDestination = "Waterloo",
            itemTowards = null,
            lineId = "waterloo-city",
            platformName = "Platform 25"
        )
        assertEquals("Bank", waterlooResult)

        val bankResult = DestinationResolver.resolve(
            stationName = "Bank",
            itemDestination = "Bank",
            itemTowards = null,
            lineId = "waterloo-city",
            platformName = "Platform 1"
        )
        assertEquals("Waterloo", bankResult)
    }

    @Test
    fun unknownLine_fallsBackToPlatformDirection() {
        val result = DestinationResolver.resolve(
            stationName = "Custom Terminus",
            itemDestination = "Custom Terminus",
            itemTowards = null,
            lineId = "custom-line",
            platformName = "Platform 1 (Northbound)"
        )
        assertEquals("Northbound Services", result)
    }
}
