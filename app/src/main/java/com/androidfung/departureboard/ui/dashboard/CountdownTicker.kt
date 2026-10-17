package com.androidfung.departureboard.ui.dashboard

import com.androidfung.departureboard.data.model.Departure

/**
 * Manages local 1-second countdown decrements across card models, detail departure lists,
 * and selected journey departures so that live counters tick smoothly in real time.
 */
object CountdownTicker {

    /**
     * Ticks card models, decrementing active departure timeToStationSeconds by 1.
     * Subtracting 1 with coerceAtLeast(0) is order-preserving, avoiding re-sorting on each 1s tick.
     */
    fun tick(cards: List<StationCardUiModel>): List<StationCardUiModel> {
        if (cards.isEmpty()) return cards
        return cards.map { card ->
            if (card.departures.isEmpty()) {
                card
            } else {
                val tickedDepartures = tickDepartures(card.departures)
                card.copy(departures = tickedDepartures)
            }
        }
    }

    /**
     * Ticks a list of departures, decrementing positive timeToStationSeconds by 1.
     */
    fun tickDepartures(departures: List<Departure>): List<Departure> {
        if (departures.isEmpty()) return departures
        return departures.map { departure ->
            if (departure.timeToStationSeconds > 0) {
                departure.copy(timeToStationSeconds = departure.timeToStationSeconds - 1)
            } else {
                departure
            }
        }
    }

    /**
     * Ticks a single departure, decrementing positive timeToStationSeconds by 1.
     */
    fun tickDeparture(departure: Departure?): Departure? {
        if (departure == null) return null
        return if (departure.timeToStationSeconds > 0) {
            departure.copy(timeToStationSeconds = departure.timeToStationSeconds - 1)
        } else {
            departure
        }
    }
}
