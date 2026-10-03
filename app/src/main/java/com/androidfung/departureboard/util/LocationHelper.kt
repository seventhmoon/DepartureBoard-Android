package com.androidfung.departureboard.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.androidfung.departureboard.data.model.Station
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object LocationHelper {

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): Location? {
        return try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val cts = CancellationTokenSource()
            fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token).await()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Calculates distance in meters between user location and a station.
     */
    fun calculateDistanceMeters(userLat: Double, userLon: Double, stationLat: Double, stationLon: Double): Double {
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(stationLat - userLat)
        val dLon = Math.toRadians(stationLon - userLon)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(userLat)) * cos(Math.toRadians(stationLat)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    /**
     * Finds the nearest station to user's location.
     */
    fun findNearestStation(userLocation: Location, stations: List<Station>): Pair<Station, Double>? {
        val validStations = stations.filter { it.lat != null && it.lon != null }
        if (validStations.isEmpty()) return null

        var nearest: Station? = null
        var minDistance = Double.MAX_VALUE

        for (st in validStations) {
            val dist = calculateDistanceMeters(
                userLat = userLocation.latitude,
                userLon = userLocation.longitude,
                stationLat = st.lat!!,
                stationLon = st.lon!!
            )
            if (dist < minDistance) {
                minDistance = dist
                nearest = st
            }
        }

        return nearest?.let { it to minDistance }
    }
}
