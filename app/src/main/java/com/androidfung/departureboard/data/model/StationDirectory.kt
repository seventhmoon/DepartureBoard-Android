package com.androidfung.departureboard.data.model

/**
 * Universal interface implemented by regional station directories (e.g. HK, JP, SG, AU)
 * providing common query operations and default station sets.
 */
interface StationDirectory {
    val defaultStations: List<Station>
    fun searchStations(query: String): List<Station>
    fun getStationById(stationId: String): Station?
}
