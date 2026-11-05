package com.androidfung.departureboard.data.repository

import com.androidfung.departureboard.data.db.cache.CachedDepartureEntity
import com.androidfung.departureboard.data.repository.direction.DestinationResolver
import com.androidfung.departureboard.data.repository.direction.ElizabethLineDirectionResolver
import com.androidfung.departureboard.data.repository.direction.LinearCorridorDirectionResolver
import com.androidfung.departureboard.data.repository.direction.OvergroundLineResolver
import com.androidfung.departureboard.data.repository.direction.ThameslinkDirectionResolver
import com.androidfung.departureboard.data.repository.mapper.NationalRailDepartureMapper
import com.androidfung.departureboard.data.repository.mapper.TflDepartureMapper
import com.androidfung.departureboard.data.repository.region.hk.HkBusCallingPointsResolver
import com.androidfung.departureboard.data.repository.region.hk.HkBusDepartureSource
import com.androidfung.departureboard.data.repository.region.hk.HkMtrDepartureSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThameslinkDirectionResolverTest {

    @Test
    fun testResolvesSouthboundByCrs() {
        assertEquals("Southbound", ThameslinkDirectionResolver.resolve(destinationCrs = "BTN"))
        assertEquals("Southbound", ThameslinkDirectionResolver.resolve(destinationCrs = "GTW"))
        assertEquals("Southbound", ThameslinkDirectionResolver.resolve(destinationCrs = "TBD"))
        assertEquals("Southbound", ThameslinkDirectionResolver.resolve(destinationCrs = "HRH"))
    }

    @Test
    fun testResolvesNorthboundByCrs() {
        assertEquals("Northbound", ThameslinkDirectionResolver.resolve(destinationCrs = "BED"))
        assertEquals("Northbound", ThameslinkDirectionResolver.resolve(destinationCrs = "LUT"))
        assertEquals("Northbound", ThameslinkDirectionResolver.resolve(destinationCrs = "LTN"))
        assertEquals("Northbound", ThameslinkDirectionResolver.resolve(destinationCrs = "SAC"))
        assertEquals("Northbound", ThameslinkDirectionResolver.resolve(destinationCrs = "CBG"))
        assertEquals("Northbound", ThameslinkDirectionResolver.resolve(destinationCrs = "PBO"))
    }

    @Test
    fun testResolvesByNameFallback() {
        assertEquals("Southbound", ThameslinkDirectionResolver.resolve(destinationName = "Brighton"))
        assertEquals("Southbound", ThameslinkDirectionResolver.resolve(destinationName = "Gatwick Airport"))
        assertEquals("Northbound", ThameslinkDirectionResolver.resolve(destinationName = "Bedford"))
        assertEquals("Northbound", ThameslinkDirectionResolver.resolve(destinationName = "Cambridge"))
    }

    @Test
    fun testUnknownReturnsNull() {
        assertNull(ThameslinkDirectionResolver.resolve(destinationCrs = "XYZ", destinationName = "Unknown Station"))
    }
}
