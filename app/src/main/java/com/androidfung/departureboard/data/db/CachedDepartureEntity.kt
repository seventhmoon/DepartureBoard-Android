package com.androidfung.departureboard.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.TflLineColors

/**
 * Room entity storing cached departure predictions for instant offline / cold-start rendering.
 */
@Entity(tableName = "cached_departures")
data class CachedDepartureEntity(
    @PrimaryKey
    val id: String,
    val stationId: String,
    val stationName: String,
    val lineId: String,
    val lineName: String,
    val platformName: String,
    val destinationName: String,
    val towards: String?,
    val direction: String?,
    val timeToStationSeconds: Int,
    val expectedArrivalIso: String?,
    val currentLocation: String?,
    val modeName: String,
    val cachedAtMillis: Long = System.currentTimeMillis()
) {
    fun toDeparture(): Departure {
        return Departure(
            id = id,
            stationId = stationId,
            stationName = stationName,
            lineId = lineId,
            lineName = lineName,
            platformName = platformName,
            destinationName = destinationName,
            towards = towards,
            direction = direction,
            timeToStationSeconds = timeToStationSeconds,
            expectedArrivalIso = expectedArrivalIso,
            currentLocation = currentLocation,
            modeName = modeName,
            lineBadge = TflLineColors.getLineBadge(lineId, lineName, modeName)
        )
    }

    companion object {
        fun fromDeparture(departure: Departure): CachedDepartureEntity {
            return CachedDepartureEntity(
                id = departure.id,
                stationId = departure.stationId,
                stationName = departure.stationName,
                lineId = departure.lineId,
                lineName = departure.lineName,
                platformName = departure.platformName,
                destinationName = departure.destinationName,
                towards = departure.towards,
                direction = departure.direction,
                timeToStationSeconds = departure.timeToStationSeconds,
                expectedArrivalIso = departure.expectedArrivalIso,
                currentLocation = departure.currentLocation,
                modeName = departure.modeName
            )
        }
    }
}
