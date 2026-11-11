package com.androidfung.departureboard.data.db.cache

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.PlatformInfo
import com.androidfung.departureboard.data.model.ServiceFrequency
import com.androidfung.departureboard.data.model.StopActivityType
import com.androidfung.departureboard.data.model.TrainFormation
import com.androidfung.departureboard.data.model.TrainInfo
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.model.TransitOperator
import com.androidfung.departureboard.data.model.gb.TflLineColors
import com.androidfung.departureboard.data.model.hk.HkMtrLine
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
    val platformCode: String? = null,
    val isPlatformConfirmed: Boolean? = null,
    val isHistoricalExpectedPlatform: Boolean? = null,
    val expectedPlatformConfidence: Int? = null,
    val operatorCode: String? = null,
    val operatorName: String? = null,
    val coachCount: Int? = null,
    val cancelReason: String? = null,
    val delayReason: String? = null,
    val headwaySeconds: Int? = null,
    val stopActivityName: String? = null,
    val statusName: String? = null,
    val scheduledDepartureEpochMillis: Long? = null,
    val expectedDepartureEpochMillis: Long? = null,
    val cachedAtMillis: Long = System.currentTimeMillis()
) {
    fun toDeparture(nowMillis: Long = System.currentTimeMillis()): Departure {
        val explicitStatus = statusName?.let { runCatching { DepartureStatus.valueOf(it) }.getOrNull() }
        // Compute accurate remaining seconds based on expectedArrivalIso or elapsed time since cachedAtMillis
        val adjustedSeconds = if (explicitStatus == DepartureStatus.CANCELLED) {
            timeToStationSeconds
        } else {
            computeRemainingSeconds(nowMillis)
        }

        val resolvedBadge = if (badgeBgColorArgb != null && badgeTextColorArgb != null) {
            LineBadgeInfo(
                lineId = lineId,
                displayName = lineName.ifBlank { lineId },
                backgroundColor = Color(badgeBgColorArgb),
                textColor = Color(badgeTextColorArgb),
                mode = TransitMode.fromModeString(modeName),
                lineCode = badgeLineCode ?: lineName.take(3).uppercase()
            )
        } else if (stationId.startsWith("HK_MTR_") || lineId.uppercase() in HkMtrLine.LINES) {
            HkMtrLine.getLineBadge(lineId)
        } else {
            TflLineColors.getLineBadge(lineId, lineName, modeName)
        }

        val restoredPlatform = if (!platformCode.isNullOrBlank()) {
            PlatformInfo(
                code = platformCode,
                isConfirmed = isPlatformConfirmed ?: true,
                isHistoricalExpected = isHistoricalExpectedPlatform ?: false,
                expectedConfidencePercent = expectedPlatformConfidence
            )
        } else if (platformName.isNotBlank()) {
            PlatformInfo(
                code = platformName,
                isConfirmed = !platformName.contains("TBD", ignoreCase = true)
            )
        } else null

        val restoredTrain = if (!operatorCode.isNullOrBlank() || !operatorName.isNullOrBlank() || coachCount != null || !cancelReason.isNullOrBlank() || !delayReason.isNullOrBlank()) {
            TrainInfo(
                operator = if (!operatorCode.isNullOrBlank() || !operatorName.isNullOrBlank()) {
                    TransitOperator(
                        id = operatorCode ?: operatorName ?: "",
                        name = operatorName ?: operatorCode ?: ""
                    )
                } else null,
                formation = coachCount?.let { TrainFormation(coachCount = it) },
                cancelReason = cancelReason,
                delayReason = delayReason,
                isWheelchairAccessible = isWheelchairAccessible
            )
        } else null

        val restoredStopActivity = stopActivityName?.let {
            runCatching { StopActivityType.valueOf(it) }.getOrNull()
        } ?: StopActivityType.NORMAL
        val restoredFrequency = headwaySeconds?.let { ServiceFrequency(headwaySeconds = it) }

        val restoredStatus = when (explicitStatus) {
            DepartureStatus.CANCELLED -> DepartureStatus.CANCELLED
            DepartureStatus.DELAYED -> DepartureStatus.DELAYED
            else -> DepartureStatus.fromString(currentLocation, adjustedSeconds)
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
            status = restoredStatus,
            destinationStationId = destinationStationId,
            isWheelchairAccessible = isWheelchairAccessible,
            occupancyStatus = occupancyStatus,
            platform = restoredPlatform,
            train = restoredTrain,
            scheduledDepartureInstant = scheduledDepartureEpochMillis?.let { Instant.ofEpochMilli(it) },
            expectedDepartureInstant = expectedDepartureEpochMillis?.let { Instant.ofEpochMilli(it) },
            stopActivity = restoredStopActivity,
            frequency = restoredFrequency,
            dataFreshness = com.androidfung.departureboard.data.model.DataFreshness.CACHED_LIVE
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
            val platformInfo = departure.platform
            val trainInfo = departure.train
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
                platformCode = platformInfo?.code,
                isPlatformConfirmed = platformInfo?.isConfirmed,
                isHistoricalExpectedPlatform = platformInfo?.isHistoricalExpected,
                expectedPlatformConfidence = platformInfo?.expectedConfidencePercent,
                operatorCode = trainInfo?.operator?.id,
                operatorName = trainInfo?.operator?.name,
                coachCount = trainInfo?.formation?.coachCount,
                cancelReason = trainInfo?.cancelReason ?: departure.cancelReason,
                delayReason = trainInfo?.delayReason ?: departure.delayReason,
                headwaySeconds = departure.frequency?.headwaySeconds,
                stopActivityName = if (departure.stopActivity != StopActivityType.NORMAL) departure.stopActivity.name else null,
                statusName = departure.status.name,
                scheduledDepartureEpochMillis = departure.scheduledDepartureInstant?.toEpochMilli(),
                expectedDepartureEpochMillis = departure.expectedDepartureInstant?.toEpochMilli(),
                cachedAtMillis = nowMillis
            )
        }
    }
}
