package com.androidfung.departureboard.data.repository.region.gb.mapper

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.model.CallingPoint
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.JourneyDestination
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.PlatformInfo
import com.androidfung.departureboard.data.model.StopScheduleRelationship
import com.androidfung.departureboard.data.model.TrainFormation
import com.androidfung.departureboard.data.model.TrainInfo
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.model.TransitOperator
import com.androidfung.departureboard.data.model.TransitRegion
import com.androidfung.departureboard.data.model.gb.NrTrainService
import com.androidfung.departureboard.data.repository.region.gb.StationNameFormatter
import com.androidfung.departureboard.data.repository.region.gb.direction.ElizabethLineDirectionResolver
import com.androidfung.departureboard.data.repository.region.gb.direction.OvergroundLineResolver
import com.androidfung.departureboard.data.repository.region.gb.direction.ThameslinkDirectionResolver
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Maps National Rail (NRE Darwin) live train services into domain [Departure] models.
 */
object NationalRailDepartureMapper {

    private val UK_ZONE = ZoneId.of("Europe/London")
    private val TIME_FORMAT_REGEX = Regex("""^\d{1,2}:\d{2}$""")

    private val OPERATOR_COLORS = mapOf(
        "LNER" to Color(0xFFC70826),
        "GR" to Color(0xFFC70826),
        "Avanti West Coast" to Color(0xFF004354),
        "VT" to Color(0xFF004354),
        "GWR" to Color(0xFF003F2D),
        "GW" to Color(0xFF003F2D),
        "Great Northern" to Color(0xFF003882),
        "GN" to Color(0xFF003882),
        "Thameslink" to Color(0xFFC70066),
        "TL" to Color(0xFFC70066),
        "Southeastern" to Color(0xFF005DAA),
        "SE" to Color(0xFF005DAA),
        "Southern" to Color(0xFF8CC63F),
        "SN" to Color(0xFF8CC63F),
        "South Western Railway" to Color(0xFF23297A),
        "SW" to Color(0xFF23297A),
        "Chiltern Railways" to Color(0xFF00BFFF),
        "CH" to Color(0xFF00BFFF),
        "Greater Anglia" to Color(0xFFD61837),
        "LE" to Color(0xFFD61837),
        "c2c" to Color(0xFF990099),
        "CC" to Color(0xFF990099),
        "London Overground" to Color(0xFFEE7C0E),
        "LO" to Color(0xFFEE7C0E),
        "Elizabeth line" to Color(0xFF7156A5),
        "XR" to Color(0xFF7156A5),
        "CrossCountry" to Color(0xFF660F25),
        "XC" to Color(0xFF660F25),
        "TransPennine Express" to Color(0xFF0969A2),
        "TP" to Color(0xFF0969A2),
        "Northern" to Color(0xFF1E285A),
        "NT" to Color(0xFF1E285A),
        "ScotRail" to Color(0xFF1E3A8A),
        "SR" to Color(0xFF1E3A8A),
        "Transport for Wales" to Color(0xFFD32F2F),
        "AW" to Color(0xFFD32F2F)
    )

    fun mapToDeparture(
        service: NrTrainService,
        stationId: String,
        stationName: String,
        nowZdt: ZonedDateTime = ZonedDateTime.now(UK_ZONE)
    ): Departure {
        val rawDestination = service.destination.firstOrNull()?.locationName ?: "Terminus"
        val destination = StationNameFormatter.clean(rawDestination)

        val operatorName = service.operator ?: "National Rail"
        val operatorCode = service.operatorCode ?: "NR"
        val std = service.std ?: "00:00"
        val etd = service.etd ?: "On time"

        val (timeToStationSeconds, expectedInstant, scheduledInstant) = calculateDepartureTimes(std, etd, nowZdt)
        val platformStr = service.platform?.let { "Platform $it" } ?: "Platform TBD"
        val platformInfo = service.platform?.let {
            PlatformInfo(
                code = it,
                isConfirmed = true,
                directionHint = null
            )
        } ?: PlatformInfo(
            code = "TBD",
            isConfirmed = false,
            directionHint = null
        )

        val isElizabeth = operatorCode.equals("XR", ignoreCase = true) || com.androidfung.departureboard.data.model.gb.GbTransitLine.ELIZABETH.matches(operatorCode, operatorName)
        val isOverground = operatorCode.equals("LO", ignoreCase = true) || com.androidfung.departureboard.data.model.gb.GbTransitLine.OVERGROUND.matches(operatorCode, operatorName)
        val isThameslink = operatorCode.equals("TL", ignoreCase = true) || com.androidfung.departureboard.data.model.gb.GbTransitLine.THAMESLINK.matches(operatorCode, operatorName)

        val (resolvedLineId, resolvedLineName, badge) = when {
            isElizabeth -> {
                val line = com.androidfung.departureboard.data.model.gb.GbTransitLine.ELIZABETH
                Triple(line.code, line.lineName, line.toLineBadge())
            }
            isOverground -> {
                val destLocation = service.destination.firstOrNull()
                val overgroundLine = OvergroundLineResolver.resolveLine(
                    stationCrs = stationId,
                    stationName = stationName,
                    destCrs = destLocation?.crs,
                    destName = destLocation?.locationName
                )
                Triple(
                    overgroundLine.code,
                    overgroundLine.lineName,
                    overgroundLine.toLineBadge()
                )
            }
            isThameslink -> {
                val line = com.androidfung.departureboard.data.model.gb.GbTransitLine.THAMESLINK
                Triple(line.code, line.lineName, line.toLineBadge())
            }
            else -> {
                val badgeColor = OPERATOR_COLORS[operatorName] ?: OPERATOR_COLORS[operatorCode] ?: Color(0xFF1E3561)
                val textColor = if (operatorCode.equals("ME", ignoreCase = true) || operatorName.contains("Merseyrail", ignoreCase = true)) Color.Black else Color.White
                val displayLabel = when {
                    operatorCode.equals("AW", ignoreCase = true) || operatorName.contains("Wales", ignoreCase = true) -> "TfW"
                    else -> operatorName
                }
                val lineId = "nr-${operatorCode.lowercase()}"
                val lineBadge = LineBadgeInfo(
                    lineId = lineId,
                    displayName = displayLabel,
                    backgroundColor = badgeColor,
                    textColor = textColor,
                    mode = TransitMode.RAIL,
                    lineCode = when {
                        operatorCode.equals("AW", ignoreCase = true) -> "TfW"
                        else -> operatorCode.take(3).uppercase()
                    }
                )
                Triple(lineId, operatorName, lineBadge)
            }
        }

        val statusDescription = when {
            service.isCancelled -> "Cancelled"
            etd.equals("Delayed", ignoreCase = true) -> "Delayed"
            etd.equals("On time", ignoreCase = true) -> "On time"
            etd.matches(TIME_FORMAT_REGEX) -> "Exp $etd"
            else -> etd
        }

        val destCrs = service.destination.firstOrNull()?.crs

        val resolvedDirection = when {
            isElizabeth -> ElizabethLineDirectionResolver.resolve(
                destinationCrs = destCrs,
                platformName = platformStr,
                destinationName = destination
            )?.displayName
            isThameslink -> ThameslinkDirectionResolver.resolve(
                destinationCrs = destCrs,
                destinationName = destination
            )
            else -> null
        }

        val modeId = TransitMode.RAIL.id

        val subsequentPoints = service.subsequentCallingPoints?.firstOrNull()?.callingPoint?.map { cp ->
            val isSkipped = cp.isCancelled || cp.et.equals("Cancelled", ignoreCase = true)
            CallingPoint(
                stationName = cp.locationName,
                scheduledTime = cp.st,
                estimatedTime = if (isSkipped) "Cancelled" else cp.et,
                isCurrentStation = false,
                isDestination = cp.locationName.equals(destination, ignoreCase = true),
                stationId = cp.crs,
                modes = listOf(modeId),
                scheduleRelationship = if (isSkipped) StopScheduleRelationship.SKIPPED else StopScheduleRelationship.SCHEDULED
            )
        } ?: emptyList()

        val fullCallingPoints = if (subsequentPoints.isNotEmpty()) {
            val isOriginSkipped = service.isCancelled || etd.equals("Cancelled", ignoreCase = true)
            listOf(
                CallingPoint(
                    stationName = stationName,
                    scheduledTime = std,
                    estimatedTime = if (isOriginSkipped) "Cancelled" else etd,
                    isCurrentStation = true,
                    isDestination = false,
                    stationId = stationId,
                    modes = listOf(modeId),
                    scheduleRelationship = if (isOriginSkipped) StopScheduleRelationship.SKIPPED else StopScheduleRelationship.SCHEDULED
                )
            ) + subsequentPoints
        } else {
            emptyList()
        }

        val operatorInfo = TransitOperator(
            id = operatorCode,
            name = operatorName,
            region = TransitRegion.GB
        )

        // Darwin train associations: detect train splitting/dividing into multiple destination portions
        val associations = if (service.destination.size > 1) {
            val primaryDest = service.destination.first().locationName
            val secondaryDest = service.destination.drop(1).joinToString(", ") { it.locationName }
            val junctionName = service.subsequentCallingPoints?.firstOrNull()?.callingPoint?.lastOrNull { cp1 ->
                service.subsequentCallingPoints.drop(1).any { otherList ->
                    otherList.callingPoint.any { cp2 -> cp2.locationName.equals(cp1.locationName, ignoreCase = true) }
                }
            }?.locationName ?: primaryDest

            listOf(
                com.androidfung.departureboard.data.model.TrainAssociation(
                    type = com.androidfung.departureboard.data.model.TrainAssociationType.DIVIDES,
                    junctionStationName = junctionName,
                    portionDestination = secondaryDest,
                    notice = "Divides at $junctionName for $secondaryDest"
                )
            )
        } else emptyList()

        val formationInfo = service.length?.let {
            TrainFormation(coachCount = it, associations = associations)
        } ?: if (associations.isNotEmpty()) {
            TrainFormation(coachCount = 8, associations = associations)
        } else null

        val trainInfo = TrainInfo(
            serviceId = service.serviceId,
            operator = operatorInfo,
            formation = formationInfo,
            cancelReason = service.cancelReason,
            delayReason = service.delayReason,
            associations = associations
        )
        val rawVia = service.destination.firstOrNull()?.via
        val normalizedVia = rawVia?.removePrefix("via ")?.removePrefix("Via ")?.trim()
        val journeyDest = JourneyDestination(
            terminusName = destination,
            tripHeadsign = if (!normalizedVia.isNullOrBlank()) "$destination via $normalizedVia" else destination,
            via = normalizedVia,
            terminusStationId = destCrs
        )

        val isDelayedNumericEtd = scheduledInstant != null &&
            expectedInstant != null &&
            java.time.Duration.between(scheduledInstant, expectedInstant).toMinutes() >= 1L
        val resolvedStatus = when {
            service.isCancelled || etd.equals("Cancelled", ignoreCase = true) ->
                com.androidfung.departureboard.data.model.DepartureStatus.CANCELLED
            etd.equals("Delayed", ignoreCase = true) || isDelayedNumericEtd ->
                com.androidfung.departureboard.data.model.DepartureStatus.DELAYED
            timeToStationSeconds <= 30 ->
                com.androidfung.departureboard.data.model.DepartureStatus.DUE
            else ->
                com.androidfung.departureboard.data.model.DepartureStatus.ON_TIME
        }

        return Departure(
            id = service.serviceId,
            stationId = stationId,
            stationName = stationName,
            lineId = resolvedLineId,
            lineName = resolvedLineName,
            platformName = platformStr,
            destinationName = destination,
            towards = normalizedVia?.let { "via $it" },
            direction = resolvedDirection,
            timeToStationSeconds = timeToStationSeconds,
            expectedArrivalIso = expectedInstant?.toString(),
            currentLocation = statusDescription,
            modeName = modeId,
            lineBadge = badge,
            status = resolvedStatus,
            destinationStationId = destCrs,
            callingPoints = fullCallingPoints,
            platform = platformInfo.copy(directionHint = resolvedDirection),
            train = trainInfo,
            scheduledDepartureInstant = scheduledInstant,
            expectedDepartureInstant = expectedInstant,
            journeyDestination = journeyDest
        )
    }

    internal fun calculateDepartureTimes(
        std: String,
        etd: String,
        nowZdt: ZonedDateTime = ZonedDateTime.now(UK_ZONE)
    ): Triple<Int, Instant?, Instant?> {
        val scheduledZdt = parseTimeToZonedDateTime(std, nowZdt)
        val expectedZdt = if (etd.matches(TIME_FORMAT_REGEX)) {
            val anchor = scheduledZdt ?: nowZdt
            val rawExpected = parseTimeToZonedDateTime(etd, anchor)
            // If scheduled is late evening (e.g. 23:50) and expected rolled into early morning (e.g. 00:15),
            // ensure expectedZdt stays after scheduledZdt rather than 23h earlier.
            if (scheduledZdt != null && rawExpected != null && rawExpected.isBefore(scheduledZdt.minusHours(6))) {
                rawExpected.plusDays(1)
            } else {
                rawExpected
            }
        } else {
            scheduledZdt
        }

        val diffSeconds = if (expectedZdt != null) {
            ChronoUnit.SECONDS.between(nowZdt, expectedZdt).toInt().coerceAtLeast(0)
        } else {
            0
        }
        return Triple(diffSeconds, expectedZdt?.toInstant(), scheduledZdt?.toInstant())
    }

    internal fun parseTimeToZonedDateTime(timeStr: String, referenceZdt: ZonedDateTime): ZonedDateTime? {
        val parts = timeStr.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null

        var target = referenceZdt.with(LocalTime.of(hour, minute, 0))
        // Forward midnight rollover: e.g. reference is 23:45 and train is 00:15 next day
        if (target.isBefore(referenceZdt.minusHours(6))) {
            target = target.plusDays(1)
        } else if (target.isAfter(referenceZdt.plusHours(18))) {
            // Backward midnight rollover: e.g. reference is 00:05 and train was scheduled 23:50 previous day
            target = target.minusDays(1)
        }
        return target
    }
}
