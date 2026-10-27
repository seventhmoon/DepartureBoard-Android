package com.androidfung.departureboard.data.repository.mapper

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.NrTrainService
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.repository.StationNameFormatter
import com.androidfung.departureboard.data.repository.direction.ElizabethLineDirectionResolver
import com.androidfung.departureboard.data.repository.direction.OvergroundLineResolver
import com.androidfung.departureboard.data.repository.direction.ThameslinkDirectionResolver
import java.util.Calendar
import java.util.TimeZone

/**
 * Maps National Rail (NRE Darwin) live train services into domain [Departure] models.
 */
object NationalRailDepartureMapper {

    private val UK_TIME_ZONE = TimeZone.getTimeZone("Europe/London")

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
        stationName: String
    ): Departure {
        val rawDestination = service.destination.firstOrNull()?.locationName ?: "Terminus"
        val destination = StationNameFormatter.clean(rawDestination)

        val operatorName = service.operator ?: "National Rail"
        val operatorCode = service.operatorCode ?: "NR"
        val std = service.std ?: "00:00"
        val etd = service.etd ?: "On time"

        val timeToStationSeconds = calculateSecondsToDeparture(std, etd)
        val platformStr = service.platform?.let { "Platform $it" } ?: "Platform TBD"

        val isElizabeth = operatorCode.equals("XR", ignoreCase = true) || operatorName.contains("Elizabeth", ignoreCase = true)
        val isOverground = operatorCode.equals("LO", ignoreCase = true) || operatorName.contains("Overground", ignoreCase = true)
        val isThameslink = operatorCode.equals("TL", ignoreCase = true) || operatorName.contains("Thameslink", ignoreCase = true)

        val (resolvedLineId, resolvedLineName, badge) = when {
            isElizabeth -> {
                Triple("elizabeth-line", "Elizabeth line", com.androidfung.departureboard.data.model.TflLineColors.getLineBadge("elizabeth-line", "Elizabeth line", "elizabeth-line"))
            }
            isOverground -> {
                val destLocation = service.destination.firstOrNull()
                val resolvedLine = OvergroundLineResolver.resolve(
                    stationCrs = stationId,
                    stationName = stationName,
                    destCrs = destLocation?.crs,
                    destName = destLocation?.locationName
                )
                Triple(
                    resolvedLine.id,
                    resolvedLine.name,
                    com.androidfung.departureboard.data.model.TflLineColors.getLineBadge(
                        resolvedLine.id,
                        resolvedLine.name,
                        "overground"
                    )
                )
            }
            isThameslink -> {
                Triple("thameslink", "Thameslink", com.androidfung.departureboard.data.model.TflLineColors.getLineBadge("thameslink", "Thameslink", "national-rail"))
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
                    mode = TransitMode.NATIONAL_RAIL,
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
            etd.matches(Regex("""^\d{1,2}:\d{2}$""")) -> "Exp $etd"
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

        val modeId = if (isElizabeth) TransitMode.ELIZABETH_LINE.id else if (isOverground) TransitMode.OVERGROUND.id else TransitMode.NATIONAL_RAIL.id

        val subsequentPoints = service.subsequentCallingPoints?.firstOrNull()?.callingPoint?.map { cp ->
            com.androidfung.departureboard.data.model.CallingPoint(
                stationName = cp.locationName,
                scheduledTime = cp.st,
                estimatedTime = cp.et,
                isCurrentStation = false,
                isDestination = cp.locationName.equals(destination, ignoreCase = true),
                stationId = cp.crs,
                modes = listOf(modeId)
            )
        } ?: emptyList()

        val fullCallingPoints = if (subsequentPoints.isNotEmpty()) {
            listOf(
                com.androidfung.departureboard.data.model.CallingPoint(
                    stationName = stationName,
                    scheduledTime = std,
                    estimatedTime = etd,
                    isCurrentStation = true,
                    isDestination = false,
                    stationId = stationId,
                    modes = listOf(modeId)
                )
            ) + subsequentPoints
        } else {
            emptyList()
        }

        return Departure(
            id = service.serviceId,
            stationId = stationId,
            stationName = stationName,
            lineId = resolvedLineId,
            lineName = resolvedLineName,
            platformName = platformStr,
            destinationName = destination,
            towards = service.destination.firstOrNull()?.via?.let { "via $it" },
            direction = resolvedDirection,
            timeToStationSeconds = timeToStationSeconds,
            expectedArrivalIso = null,
            currentLocation = statusDescription,
            modeName = modeId,
            lineBadge = badge,
            destinationStationId = destCrs,
            callingPoints = fullCallingPoints
        )
    }

    private fun calculateSecondsToDeparture(std: String, etd: String): Int {
        val targetTimeStr = if (etd.matches(Regex("""^\d{1,2}:\d{2}$"""))) etd else std
        val parts = targetTimeStr.split(":")
        if (parts.size != 2) return 0

        val targetHour = parts[0].toIntOrNull() ?: return 0
        val targetMinute = parts[1].toIntOrNull() ?: return 0

        val cal = Calendar.getInstance(UK_TIME_ZONE)
        val currentMillis = cal.timeInMillis

        val targetCal = Calendar.getInstance(UK_TIME_ZONE).apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        var diffSeconds = ((targetCal.timeInMillis - currentMillis) / 1000).toInt()
        if (diffSeconds < -3600) {
            targetCal.add(Calendar.DAY_OF_YEAR, 1)
            diffSeconds = ((targetCal.timeInMillis - currentMillis) / 1000).toInt()
        }

        return diffSeconds.coerceAtLeast(0)
    }
}
