package com.androidfung.departureboard.ui.dashboard

import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.test.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CountdownTickerTest {

    private val sampleDeparture = TestFixtures.createTestDeparture(
        id = "dep_1",
        timeToStationSeconds = 10
    )

    private val sampleStation = Station(
        id = "station_1",
        name = "Victoria",
        modes = listOf("tube")
    )

    @Test
    fun testTick_decrementsTimeToStationSeconds() {
        val card = StationCardUiModel(
            station = sampleStation,
            departures = listOf(sampleDeparture)
        )

        val ticked = CountdownTicker.tick(listOf(card))
        assertEquals(9, ticked[0].departures[0].timeToStationSeconds)
    }

    @Test
    fun testTick_doesNotDecrementBelowZero() {
        val zeroDep = sampleDeparture.copy(timeToStationSeconds = 0)
        val card = StationCardUiModel(
            station = sampleStation,
            departures = listOf(zeroDep)
        )

        val ticked = CountdownTicker.tick(listOf(card))
        assertEquals(0, ticked[0].departures[0].timeToStationSeconds)
    }

    @Test
    fun testTickDeparture_nullReturnsNull() {
        assertNull(CountdownTicker.tickDeparture(null))
    }

    @Test
    fun testTick_preservesCancelledAndDelayedStatusNearZeroSeconds() {
        val cancelledDep = sampleDeparture.copy(
            id = "dep_cancelled",
            timeToStationSeconds = 15,
            currentLocation = "At Platform",
            status = com.androidfung.departureboard.data.model.DepartureStatus.CANCELLED
        )
        val delayedDep = sampleDeparture.copy(
            id = "dep_delayed",
            timeToStationSeconds = 20,
            currentLocation = "Approaching",
            status = com.androidfung.departureboard.data.model.DepartureStatus.DELAYED
        )
        val card = StationCardUiModel(
            station = sampleStation,
            departures = listOf(cancelledDep, delayedDep)
        )

        val ticked = CountdownTicker.tick(listOf(card))
        assertEquals(com.androidfung.departureboard.data.model.DepartureStatus.CANCELLED, ticked[0].departures[0].status)
        assertEquals(com.androidfung.departureboard.data.model.DepartureStatus.DELAYED, ticked[0].departures[1].status)
        assertEquals(19, ticked[0].departures[1].timeToStationSeconds)
    }
}
