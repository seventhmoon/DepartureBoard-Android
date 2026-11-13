package com.androidfung.departureboard.data.repository.region.gb

import org.junit.Assert.assertEquals
import org.junit.Test

class StationNameFormatterTest {

    @Test
    fun testHeathrowTerminalsNormalization() {
        assertEquals("Heathrow Terminal 4", StationNameFormatter.clean("Heathrow Airport T4"))
        assertEquals("Heathrow Terminal 4", StationNameFormatter.clean("Heathrow Airport Terminal 4 Rail Station"))
        assertEquals("Heathrow Terminal 4", StationNameFormatter.clean("Heathrow T4"))

        assertEquals("Heathrow Terminal 5", StationNameFormatter.clean("Heathrow Airport T5"))
        assertEquals("Heathrow Terminal 5", StationNameFormatter.clean("Heathrow Airport Terminal 5"))

        assertEquals("Heathrow Terminals 2 & 3", StationNameFormatter.clean("Heathrow Airport T2&3"))
        assertEquals("Heathrow Terminals 2 & 3", StationNameFormatter.clean("Heathrow Airport Terminals 2 and 3"))
        assertEquals("Heathrow Terminals 2 & 3", StationNameFormatter.clean("Heathrow Terminals 2 and 3"))
    }

    @Test
    fun testLondonTerminiPrefixNormalization() {
        assertEquals("Paddington", StationNameFormatter.clean("London Paddington"))
        assertEquals("Liverpool Street", StationNameFormatter.clean("London Liverpool Street"))
        assertEquals("Victoria", StationNameFormatter.clean("London Victoria"))
        assertEquals("Waterloo", StationNameFormatter.clean("London Waterloo"))
        assertEquals("Euston", StationNameFormatter.clean("London Euston"))
        assertEquals("King's Cross", StationNameFormatter.clean("London Kings Cross"))
        assertEquals("King's Cross", StationNameFormatter.clean("London King's Cross"))
        assertEquals("Blackfriars", StationNameFormatter.clean("London Blackfriars"))
        assertEquals("Cannon Street", StationNameFormatter.clean("London Cannon Street"))
        assertEquals("Charing Cross", StationNameFormatter.clean("London Charing Cross"))
        assertEquals("Marylebone", StationNameFormatter.clean("London Marylebone"))
        assertEquals("Fenchurch Street", StationNameFormatter.clean("London Fenchurch Street"))

        // London Bridge and London Fields must preserve "London"
        assertEquals("London Bridge", StationNameFormatter.clean("London Bridge"))
        assertEquals("London Bridge", StationNameFormatter.clean("London Bridge Rail Station"))
    }

    @Test
    fun testAirportAndJunctionNormalization() {
        assertEquals("Gatwick Airport", StationNameFormatter.clean("Gatwick"))
        assertEquals("Gatwick Airport", StationNameFormatter.clean("Gatwick Airport"))
        assertEquals("Stansted Airport", StationNameFormatter.clean("Stansted"))
        assertEquals("Luton Airport Parkway", StationNameFormatter.clean("Luton Airport"))

        assertEquals("Clapham Junction", StationNameFormatter.clean("Clapham Jct"))
        assertEquals("Willesden Junction", StationNameFormatter.clean("Willesden Jct"))

        assertEquals("Stratford", StationNameFormatter.clean("Stratford (London)"))
        assertEquals("Highbury & Islington", StationNameFormatter.clean("Highbury and Islington"))
        assertEquals("Shepherd's Bush", StationNameFormatter.clean("Shepherds Bush"))
    }

    @Test
    fun testPreservesStopLettersAndLandmarks() {
        assertEquals("Euston (Stop D)", StationNameFormatter.clean("Euston (Stop D)"))
        assertEquals("Battersea Power Station", StationNameFormatter.clean("Battersea Power Station Underground Station"))
    }
}
