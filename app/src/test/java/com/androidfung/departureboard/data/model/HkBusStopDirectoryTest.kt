package com.androidfung.departureboard.data.model

import com.androidfung.departureboard.data.model.hk.HkBusStopDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HkBusStopDirectoryTest {

    @Before
    fun setup() {
        HkBusStopDirectory.setStopsForTesting(
            listOf(
                HkBusStopDirectory.HkBusStopEntry(
                    stopId = "7613538D58A9C452",
                    nameEn = "HAU TAK ESTATE (TK215)",
                    nameTc = "厚德邨 (TK215)",
                    lat = 22.319436,
                    lon = 114.264290
                ),
                HkBusStopDirectory.HkBusStopEntry(
                    stopId = "1B1A7253CC1D3148",
                    nameEn = "HAU TAK LANE",
                    nameTc = "厚德里",
                    lat = 22.274166,
                    lon = 114.179301
                ),
                HkBusStopDirectory.HkBusStopEntry(
                    stopId = "001476",
                    nameEn = "STAR FERRY",
                    nameTc = "尖沙咀碼頭",
                    lat = 22.2938,
                    lon = 114.1685
                )
            )
        )
    }

    @Test
    fun testDisplayName() {
        val entry = HkBusStopDirectory.HkBusStopEntry("123", "HAU TAK ESTATE", "厚德邨", 22.3, 114.2)
        assertEquals("HAU TAK ESTATE (厚德邨)", entry.displayName)
    }

    @Test
    fun testToStation() {
        val entry = HkBusStopDirectory.HkBusStopEntry("7613538D58A9C452", "HAU TAK ESTATE", "厚德邨", 22.319, 114.264)
        val station = entry.toStation()
        assertEquals("HK_BUS_KMB_7613538D58A9C452", station.id)
        assertTrue(station.modes.contains("bus"))
        assertEquals("HAU TAK ESTATE (厚德邨)", station.name)
    }
}
