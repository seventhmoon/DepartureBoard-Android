package com.androidfung.departureboard.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.androidfung.departureboard.data.model.Station
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class DeviceCoordinates(
    val latitude: Double,
    val longitude: Double
)

interface LocationDataSource {
    suspend fun getCurrentLocation(): Location?
    suspend fun getCurrentCoordinates(): DeviceCoordinates? = getCurrentLocation()?.let {
        DeviceCoordinates(it.latitude, it.longitude)
    }
}

class LocationDataSourceImpl(
    private val context: Context
) : LocationDataSource {
    override suspend fun getCurrentLocation(): Location? {
        val appContext = try {
            context.applicationContext ?: context
        } catch (_: Exception) {
            context
        }
        return LocationHelper.getCurrentLocation(appContext)
    }
}

object LocationHelper {

    private const val MAX_LAST_LOCATION_AGE_MS = 2 * 60 * 1000L // 2 minutes
    private const val ACTIVE_FIX_TIMEOUT_MS = 4_000L

    private fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        return try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            // First check lastLocation for instant response if fresh
            val lastLoc = fusedClient.lastLocation.await()
            if (lastLoc != null) {
                val ageMs = (SystemClock.elapsedRealtimeNanos() - lastLoc.elapsedRealtimeNanos) / 1_000_000L
                if (ageMs in 0..MAX_LAST_LOCATION_AGE_MS) {
                    return lastLoc
                }
            }
            val cts = CancellationTokenSource()
            val activeFix = try {
                withTimeoutOrNull(ACTIVE_FIX_TIMEOUT_MS) {
                    fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token).await()
                }
            } finally {
                cts.cancel()
            }
            activeFix ?: lastLoc
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
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
     * Finds the nearest station to the specified coordinates.
     */
    fun findNearestStation(lat: Double, lon: Double, stations: List<Station>): Pair<Station, Double>? {
        val validStations = stations.filter { it.lat != null && it.lon != null }
        if (validStations.isEmpty()) return null

        var nearest: Station? = null
        var minDistance = Double.MAX_VALUE

        for (st in validStations) {
            val stationLat = st.lat ?: continue
            val stationLon = st.lon ?: continue
            val dist = calculateDistanceMeters(
                userLat = lat,
                userLon = lon,
                stationLat = stationLat,
                stationLon = stationLon
            )
            if (dist < minDistance) {
                minDistance = dist
                nearest = st
            }
        }

        return nearest?.let { it to minDistance }
    }

    /**
     * Finds the nearest station to user's location.
     */
    fun findNearestStation(userLocation: Location, stations: List<Station>): Pair<Station, Double>? {
        return findNearestStation(userLocation.latitude, userLocation.longitude, stations)
    }

    /**
     * Finds stations sorted by distance to user's location within an optional max distance threshold.
     */
    fun findNearbyStations(
        userLocation: Location,
        stations: List<Station>,
        maxDistanceMeters: Double = 3000.0,
        maxCount: Int = 5
    ): List<Pair<Station, Double>> {
        val validStations = stations.filter { it.lat != null && it.lon != null }
        return validStations.mapNotNull { st ->
            val stationLat = st.lat ?: return@mapNotNull null
            val stationLon = st.lon ?: return@mapNotNull null
            val dist = calculateDistanceMeters(
                userLat = userLocation.latitude,
                userLon = userLocation.longitude,
                stationLat = stationLat,
                stationLon = stationLon
            )
            if (dist <= maxDistanceMeters) {
                st to dist
            } else null
        }
            .sortedBy { it.second }
            .take(maxCount)
    }
}
