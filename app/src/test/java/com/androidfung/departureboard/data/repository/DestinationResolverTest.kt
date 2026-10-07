package com.androidfung.departureboard.data.repository

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
