package com.androidfung.departureboard.data.db

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.HkMtrLineRegistry
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.TflLineColors
import com.androidfung.departureboard.data.model.TransitMode
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Room entity storing cached departure predictions for instant offline / cold-start rendering.
 */
@Entity(
    tableName = "cached_departures",
    indices = [
        Index(value = ["stationId", "timeToStationSeconds"]),
        Index(value = ["cachedAtMillis"])
    ]
)
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
    val destinationStationId: String? = null,
    val badgeBgColorArgb: Int? = null,
    val badgeTextColorArgb: Int? = null,
    val badgeLineCode: String? = null,
    val isWheelchairAccessible: Boolean? = null,
    val occupancyStatus: String? = null,
    val cachedAtMillis: Long = System.currentTimeMillis()
) {
    fun toDeparture(nowMillis: Long = System.currentTimeMillis()): Departure {
        // Compute accurate remaining seconds based on expectedArrivalIso or elapsed time since cachedAtMillis
        val adjustedSeconds = computeRemainingSeconds(nowMillis)

        val resolvedBadge = if (badgeBgColorArgb != null && badgeTextColorArgb != null) {
            LineBadgeInfo(
                lineId = lineId,
                displayName = lineName.ifBlank { lineId },
                backgroundColor = Color(badgeBgColorArgb),
                textColor = Color(badgeTextColorArgb),
                mode = TransitMode.fromModeString(modeName),
                lineCode = badgeLineCode ?: lineName.take(3).uppercase()
            )
        } else if (stationId.startsWith("HK_MTR_") || lineId.uppercase() in HkMtrLineRegistry.LINES) {
            HkMtrLineRegistry.getLineBadge(lineId)
        } else {
            TflLineColors.getLineBadge(lineId, lineName, modeName)
        }

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
            timeToStationSeconds = adjustedSeconds,
            expectedArrivalIso = expectedArrivalIso,
            currentLocation = currentLocation,
            modeName = modeName,
            lineBadge = resolvedBadge,
            status = DepartureStatus.fromString(currentLocation, adjustedSeconds),
            destinationStationId = destinationStationId,
            isWheelchairAccessible = isWheelchairAccessible,
            occupancyStatus = occupancyStatus
        )
    }

    /**
     * Returns true if this cached departure has already departed by more than [graceSeconds] ago.
     */
    fun isExpired(nowMillis: Long = System.currentTimeMillis(), graceSeconds: Int = 45): Boolean {
        val elapsedSeconds = ((nowMillis - cachedAtMillis) / 1000L).toInt().coerceAtLeast(0)
        if (!expectedArrivalIso.isNullOrBlank()) {
            try {
                val arrivalInstant = Instant.parse(expectedArrivalIso)
                val diff = ChronoUnit.SECONDS.between(Instant.ofEpochMilli(nowMillis), arrivalInstant).toInt()
                return diff < -graceSeconds
            } catch (_: Exception) {}
        }
        return (timeToStationSeconds - elapsedSeconds) < -graceSeconds
    }

    private fun computeRemainingSeconds(nowMillis: Long): Int {
        if (!expectedArrivalIso.isNullOrBlank()) {
            try {
                val arrivalInstant = Instant.parse(expectedArrivalIso)
                val diff = ChronoUnit.SECONDS.between(Instant.ofEpochMilli(nowMillis), arrivalInstant).toInt()
                return diff.coerceAtLeast(0)
            } catch (_: Exception) {}
        }
        val elapsedSeconds = ((nowMillis - cachedAtMillis) / 1000L).toInt().coerceAtLeast(0)
        return (timeToStationSeconds - elapsedSeconds).coerceAtLeast(0)
    }

    companion object {
        fun fromDeparture(departure: Departure, nowMillis: Long = System.currentTimeMillis()): CachedDepartureEntity {
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
                modeName = departure.modeName,
                destinationStationId = departure.destinationStationId,
                badgeBgColorArgb = departure.lineBadge.backgroundColor.toArgb(),
                badgeTextColorArgb = departure.lineBadge.textColor.toArgb(),
                badgeLineCode = departure.lineBadge.lineCode,
                isWheelchairAccessible = departure.isWheelchairAccessible,
                occupancyStatus = departure.occupancyStatus,
                cachedAtMillis = nowMillis
            )
        }
    }
}
