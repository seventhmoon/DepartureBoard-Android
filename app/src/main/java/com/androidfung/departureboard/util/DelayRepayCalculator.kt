package com.androidfung.departureboard.util

import com.androidfung.departureboard.data.model.CallingPoint
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.TransitMode
import java.time.Duration
import java.time.LocalTime

/**
 * Represents passenger compensation eligibility under UK National Rail "Delay Repay"
 * and TfL "Service Delay Refund" schemes when a train is delayed beyond the operator's threshold.
 */
data class DelayRepayEligibility(
    val delayMinutes: Int,
    val thresholdMinutes: Int,
    val operatorLabel: String,
    val badgeLabel: String,
    val compensationSummary: String
)

object DelayRepayCalculator {

    private val CLOCK_TIME_REGEX = Regex("""(\d{1,2}):(\d{2})""")

    /**
     * Operators whose Delay Repay / Service Delay Refund threshold is 30 minutes
     * (most UK rail operators and TfL Tube/DLR/Overground now use 15 minutes).
     */
    private val THIRTY_MINUTE_THRESHOLD_IDS = setOf(
        "elizabeth",
        "elizabeth-line",
        "elizabeth line",
        "xr",
        "crosscountry",
        "cross-country"
    )

    /**
     * Evaluates whether [departure] (and optional loaded [callingPoints]) qualifies for
     * Delay Repay / Service Delay Refund compensation.
     */
    fun evaluate(
        departure: Departure,
        callingPoints: List<CallingPoint> = departure.callingPoints
    ): DelayRepayEligibility? {
        if (departure.isBus) return null
        val mode = TransitMode.fromModeString(departure.modeName)
        if (mode != TransitMode.RAIL && mode != TransitMode.METRO && mode != TransitMode.TRAM) {
            return null
        }

        val delayMinutes = computeDelayMinutes(departure, callingPoints) ?: return null
        if (delayMinutes < 15) return null

        val lineKey = departure.lineId.trim().lowercase()
        val opName = departure.operator?.name?.trim()?.takeIf { it.isNotEmpty() } ?: departure.lineName
        val opKey = opName.lowercase()

        val threshold = if (lineKey in THIRTY_MINUTE_THRESHOLD_IDS || opKey in THIRTY_MINUTE_THRESHOLD_IDS) {
            30
        } else {
            15
        }

        if (delayMinutes < threshold) return null

        val isTflMetroOrDlr = mode == TransitMode.METRO || lineKey == "dlr"
        val isHeathrowExpress = lineKey.contains("heathrow") || opKey.contains("heathrow express") || opKey == "hx"

        val summary = when {
            isTflMetroOrDlr -> "Eligible for TfL Service Delay Refund (100% single fare for ${delayMinutes}m delay)"
            isHeathrowExpress && delayMinutes >= 30 -> "Eligible for Heathrow Express 100% Delay Repay (${delayMinutes}m delay)"
            isHeathrowExpress -> "Eligible for Heathrow Express 50% Delay Repay (${delayMinutes}m delay)"
            delayMinutes >= 60 -> "Eligible for 100% single / 50–100% return Delay Repay (${delayMinutes}m delay with $opName)"
            delayMinutes >= 30 -> "Eligible for 50% single / 25% return Delay Repay (${delayMinutes}m delay with $opName)"
            else -> "Eligible for 25% single / 12.5% return Delay Repay 15 (${delayMinutes}m delay with $opName)"
        }

        return DelayRepayEligibility(
            delayMinutes = delayMinutes,
            thresholdMinutes = threshold,
            operatorLabel = opName,
            badgeLabel = "Delay Repay (+${delayMinutes}m)",
            compensationSummary = summary
        )
    }

    /**
     * Computes the best-known lateness in minutes for [departure] from:
     * 1. Scheduled vs expected departure instants
     * 2. Calling points (terminus or current station scheduled vs estimated HH:mm clock times)
     * 3. Current location string containing "Exp HH:mm" vs scheduled departure instant
     */
    fun computeDelayMinutes(
        departure: Departure,
        callingPoints: List<CallingPoint> = departure.callingPoints
    ): Int? {
        val candidates = mutableListOf<Int>()

        // 1. Instant comparison
        val schedInstant = departure.scheduledDepartureInstant
        val expInstant = departure.expectedDepartureInstant
        if (schedInstant != null && expInstant != null) {
            val diff = Duration.between(schedInstant, expInstant).toMinutes().toInt()
            if (diff in 1..360) {
                candidates.add(diff)
            }
        }

        // 2. Calling points comparison (check destination first, then current station, then any stop)
        val prioritizedPoints = buildList {
            callingPoints.lastOrNull()?.let { add(it) }
            callingPoints.firstOrNull { it.isCurrentStation }?.let { add(it) }
            addAll(callingPoints)
        }
        for (cp in prioritizedPoints) {
            val diff = computeClockDiffMinutes(cp.scheduledTime, cp.estimatedTime)
            if (diff != null && diff in 1..360) {
                candidates.add(diff)
            }
        }

        return candidates.maxOrNull()
    }

    internal fun computeClockDiffMinutes(scheduledRaw: String?, estimatedRaw: String?): Int? {
        if (scheduledRaw.isNullOrBlank() || estimatedRaw.isNullOrBlank()) return null
        val schedMatch = CLOCK_TIME_REGEX.find(scheduledRaw) ?: return null
        val estMatch = CLOCK_TIME_REGEX.find(estimatedRaw) ?: return null

        val sh = schedMatch.groupValues[1].toIntOrNull() ?: return null
        val sm = schedMatch.groupValues[2].toIntOrNull() ?: return null
        val eh = estMatch.groupValues[1].toIntOrNull() ?: return null
        val em = estMatch.groupValues[2].toIntOrNull() ?: return null

        if (sh !in 0..23 || sm !in 0..59 || eh !in 0..23 || em !in 0..59) return null

        val schedMins = LocalTime.of(sh, sm).toSecondOfDay() / 60
        var estMins = LocalTime.of(eh, em).toSecondOfDay() / 60

        // Handle midnight rollover (e.g. scheduled 23:50, estimated 00:15)
        if (estMins < schedMins - 180) {
            estMins += 24 * 60
        }

        val diff = estMins - schedMins
        return if (diff > 0) diff else null
    }
}
