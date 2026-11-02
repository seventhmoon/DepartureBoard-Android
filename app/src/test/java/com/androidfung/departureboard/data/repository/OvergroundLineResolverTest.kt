package com.androidfung.departureboard.data.repository

import com.androidfung.departureboard.data.db.cache.CachedDepartureEntity
import com.androidfung.departureboard.data.repository.direction.DestinationResolver
import com.androidfung.departureboard.data.repository.direction.ElizabethLineDirectionResolver
import com.androidfung.departureboard.data.repository.direction.LinearCorridorDirectionResolver
import com.androidfung.departureboard.data.repository.direction.OvergroundLineResolver
import com.androidfung.departureboard.data.repository.direction.ThameslinkDirectionResolver
import com.androidfung.departureboard.data.repository.mapper.NationalRailDepartureMapper
import com.androidfung.departureboard.data.repository.mapper.TflDepartureMapper
import com.androidfung.departureboard.data.repository.source.hk.HkBusDepartureSource
import com.androidfung.departureboard.data.repository.source.hk.HkMtrDepartureSource
import com.androidfung.departureboard.data.resolver.hk.HkBusCallingPointsResolver


import org.junit.Assert.assertEquals
import org.junit.Test

class OvergroundLineResolverTest {

    @Test
    fun resolvesLibertyLineByCrsAndTermini() {
        val byCrs = OvergroundLineResolver.resolve(stationCrs = "ROM", destName = "Upminster")
        assertEquals("liberty", byCrs.id)
        assertEquals("Liberty", byCrs.name)

        val byDest = OvergroundLineResolver.resolve(stationName = "Emerson Park", destName = "Romford")
        assertEquals("liberty", byDest.id)
    }

    @Test
    fun resolvesLionessLineByCrsAndTermini() {
        val byCrs = OvergroundLineResolver.resolve(stationCrs = "EUS", destName = "Watford Junction")
        assertEquals("lioness", byCrs.id)
        assertEquals("Lioness", byCrs.name)

        val byStationName = OvergroundLineResolver.resolve(stationName = "Kenton", destName = "London Euston")
        assertEquals("lioness", byStationName.id)
    }

    @Test
    fun resolvesWeaverLineByCrsAndTermini() {
        val byCrs = OvergroundLineResolver.resolve(stationCrs = "LST", destName = "Chingford")
        assertEquals("weaver", byCrs.id)
        assertEquals("Weaver", byCrs.name)

        val byDest = OvergroundLineResolver.resolve(stationName = "Hackney Downs", destName = "Enfield Town")
        assertEquals("weaver", byDest.id)
    }

    @Test
    fun resolvesSuffragetteLineByCrsAndTermini() {
        val byCrs = OvergroundLineResolver.resolve(stationCrs = "GPO", destName = "Barking Riverside")
        assertEquals("suffragette", byCrs.id)
        assertEquals("Suffragette", byCrs.name)
    }

    @Test
    fun resolvesMildmayLineByCrsAndTermini() {
        val byCrs = OvergroundLineResolver.resolve(stationCrs = "RMD", destName = "Stratford")
        assertEquals("mildmay", byCrs.id)
        assertEquals("Mildmay", byCrs.name)
    }

    @Test
    fun resolvesWindrushLineByCrsAndTermini() {
        val byCrs = OvergroundLineResolver.resolve(stationCrs = "HHY", destName = "Crystal Palace")
        assertEquals("windrush", byCrs.id)
        assertEquals("Windrush", byCrs.name)
    }

    @Test
    fun fallsBackToDefaultOvergroundWhenUnknown() {
        val unknown = OvergroundLineResolver.resolve(stationCrs = "ZZZ", destName = "Unknown Station")
        assertEquals("overground", unknown.id)
        assertEquals("London Overground", unknown.name)
    }
}
