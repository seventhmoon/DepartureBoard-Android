package com.androidfung.departureboard.ui.dashboard

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus

/**
 * Manages local 1-second countdown decrements across card models, detail departure lists,
 * and selected journey departures so that live counters tick smoothly in real time.
 * Optimized to reuse identical list and object instances whenever departures are not ticking,
 * minimizing garbage collection allocations and unneeded Compose recompositions.
 */
object CountdownTicker {

    /**
     * Ticks card models, decrementing active departure timeToStationSeconds by 1.
     * Subtracting 1 with coerceAtLeast(0) is order-preserving, avoiding re-sorting on each 1s tick.
     * Returns the same list instance if no cards were modified.
     */
    fun tick(cards: List<StationCardUiModel>): List<StationCardUiModel> {
        if (cards.isEmpty()) return cards
        var anyCardChanged = false
        val newCards = cards.map { card ->
            if (card.departures.isEmpty()) {
                card
            } else {
                val tickedDepartures = tickDepartures(card.departures)
                if (tickedDepartures === card.departures) {
                    card
                } else {
                    anyCardChanged = true
                    card.copy(departures = tickedDepartures)
                }
            }
        }
        return if (anyCardChanged) newCards else cards
    }

    /**
     * Ticks a list of departures, decrementing positive timeToStationSeconds by 1.
     * Returns the original list instance without allocation if no departures had timeToStationSeconds > 0.
     */
    fun tickDepartures(departures: List<Departure>): List<Departure> {
        if (departures.isEmpty()) return departures
        var hasChanged = false
        val result = departures.map { departure ->
            if (departure.timeToStationSeconds > 0) {
                hasChanged = true
                val newSeconds = departure.timeToStationSeconds - 1
                val newStatus = DepartureStatus.fromString(departure.currentLocation, newSeconds)
                departure.copy(
                    timeToStationSeconds = newSeconds,
                    status = newStatus
                )
            } else {
                departure
            }
        }
        return if (hasChanged) result else departures
    }

    /**
     * Ticks a single departure, decrementing positive timeToStationSeconds by 1.
     */
    fun tickDeparture(departure: Departure?): Departure? {
        if (departure == null) return null
        return if (departure.timeToStationSeconds > 0) {
            val newSeconds = departure.timeToStationSeconds - 1
            val newStatus = DepartureStatus.fromString(departure.currentLocation, newSeconds)
            departure.copy(
                timeToStationSeconds = newSeconds,
                status = newStatus
            )
        } else {
            departure
        }
    }
}
