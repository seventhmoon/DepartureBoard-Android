package com.androidfung.departureboard.util

import com.androidfung.departureboard.data.db.cache.HistoricalPlatformObservationEntity
import com.androidfung.departureboard.data.db.cache.PlatformPredictionDao
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.PlatformInfo
import com.androidfung.departureboard.data.model.TransitRegion
import java.util.Collections

/**
 * Learns confirmed station platform assignments over time and predicts high-confidence
 * "Typically Plat X (92%)" suggestions while live departures still report `Platform TBD`.
 *
 * Crucially preserves `isConfirmed = false` and `platformName = "Platform TBD"` on enriched
 * departures so [com.androidfung.departureboard.service.TrainTrackingService] and Wear OS
 * still fire real-time `TBD -> Plat X` haptic announcements once the live platform is confirmed.
 */
class PlatformHistoryPredictor(
    private val dao: PlatformPredictionDao?
) {

    companion object {
        const val MIN_BUCKET_OBSERVATIONS = 2
        const val MIN_BUCKET_CONFIDENCE_PERCENT = 60
        const val MIN_ROUTE_OBSERVATIONS = 3
        const val MIN_ROUTE_CONFIDENCE_PERCENT = 65
        private const val RETENTION_NINETY_DAYS_MS = 90L * 24L * 3_600_000L
        private const val MAX_SEEN_SERVICE_KEYS = 512

        private val NUMERIC_PLATFORM_PREFIX_REGEX = Regex("(?i)^(?:platform|plat|plt|stop|stand|bay)\\s+([A-Z0-9]+)$")

        fun normalizePlatformCode(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isBlank()) return ""
            val match = NUMERIC_PLATFORM_PREFIX_REGEX.matchEntire(trimmed)
            return (match?.groupValues?.getOrNull(1) ?: trimmed).uppercase()
        }

        fun computeScheduledFiveMinBucket(departure: Departure): Int {
            val scheduled = departure.scheduledDepartureInstant ?: return -1
            val zone = TransitRegion.fromStationId(departure.stationId).timeZone
            val zdt = scheduled.atZone(zone)
            val minuteOfDay = zdt.hour * 60 + zdt.minute
            return (minuteOfDay / 5).coerceIn(0, 287)
        }

        fun normalizeOperatorKey(departure: Departure): String =
            (departure.train?.operator?.id?.takeIf { it.isNotBlank() } ?: departure.lineId)
                .trim()
                .uppercase()

        fun normalizeDestinationKey(departure: Departure): String =
            (departure.destinationStationId?.takeIf { it.isNotBlank() } ?: departure.destinationName)
                .trim()
                .uppercase()

        /**
         * Pure calculation helper selecting the dominant platform code when observation count
         * and confidence percentage meet the specified thresholds.
         */
        fun evaluateDominantPlatform(
            observations: List<HistoricalPlatformObservationEntity>,
            minTotalObservations: Int,
            minConfidencePercent: Int
        ): Pair<String, Int>? {
            if (observations.isEmpty()) return null
            val countsByPlatform = mutableMapOf<String, Int>()
            var total = 0
            for (obs in observations) {
                if (obs.observationCount <= 0 || obs.platformCode.isBlank()) continue
                countsByPlatform[obs.platformCode] = (countsByPlatform[obs.platformCode] ?: 0) + obs.observationCount
                total += obs.observationCount
            }
            if (total < minTotalObservations) return null
            val topEntry = countsByPlatform.maxByOrNull { it.value } ?: return null
            val confidence = ((topEntry.value * 100.0) / total).toInt().coerceIn(0, 100)
            return if (confidence >= minConfidencePercent) {
                topEntry.key to confidence
            } else {
                null
            }
        }
    }

    // Deduplicates repeated polls of the exact same live service instance within one app session
    private val recordedServiceKeys: MutableSet<String> = Collections.synchronizedSet(
        object : LinkedHashSet<String>() {
            override fun add(element: String): Boolean {
                val added = super.add(element)
                if (size > MAX_SEEN_SERVICE_KEYS) {
                    val it = iterator()
                    if (it.hasNext()) {
                        it.next()
                        it.remove()
                    }
                }
                return added
            }
        }
    )

    suspend fun processLiveDepartures(
        stationId: String,
        departures: List<Departure>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<Departure> {
        val predictionDao = dao ?: return departures
        if (departures.isEmpty()) return departures

        val normalizedStationId = stationId.trim().uppercase()

        // 1. Record newly confirmed platform observations
        for (dep in departures) {
            if (dep.status == DepartureStatus.CANCELLED) continue
            val resolved = dep.resolvedPlatform
            val cleanCode = normalizePlatformCode(resolved.code)
            if (resolved.isConfirmed &&
                !resolved.isHistoricalExpected &&
                cleanCode.isNotBlank() &&
                !cleanCode.equals("TBD", ignoreCase = true)
            ) {
                val bucket = computeScheduledFiveMinBucket(dep)
                val dedupeKey = "${normalizedStationId}|${dep.id}|$bucket|$cleanCode"
                if (recordedServiceKeys.add(dedupeKey)) {
                    predictionDao.incrementObservation(
                        stationId = normalizedStationId,
                        operatorId = normalizeOperatorKey(dep),
                        destinationKey = normalizeDestinationKey(dep),
                        bucket = bucket,
                        platformCode = cleanCode,
                        nowMillis = nowMillis
                    )
                }
            }
        }

        // Occasionally prune observations untouched for > 90 days
        predictionDao.pruneStaleObservations(nowMillis - RETENTION_NINETY_DAYS_MS)

        // 2. Enrich unconfirmed ("Platform TBD") departures with learned historical platform expectations
        return departures.map { dep ->
            val resolved = dep.resolvedPlatform
            val isUnconfirmedTbd = !resolved.isConfirmed ||
                resolved.code.isBlank() ||
                resolved.code.equals("TBD", ignoreCase = true) ||
                dep.platformName.contains("TBD", ignoreCase = true)

            if (!isUnconfirmedTbd || dep.status == DepartureStatus.CANCELLED) {
                dep
            } else {
                val operatorKey = normalizeOperatorKey(dep)
                val destinationKey = normalizeDestinationKey(dep)
                val bucket = computeScheduledFiveMinBucket(dep)

                val windowPrediction = if (bucket >= 0) {
                    val minB = (bucket - 3).coerceAtLeast(0)
                    val maxB = (bucket + 3).coerceAtMost(287)
                    val inWindow = predictionDao.getObservationsInWindow(
                        stationId = normalizedStationId,
                        operatorId = operatorKey,
                        destinationKey = destinationKey,
                        minBucket = minB,
                        maxBucket = maxB
                    )
                    evaluateDominantPlatform(
                        observations = inWindow,
                        minTotalObservations = MIN_BUCKET_OBSERVATIONS,
                        minConfidencePercent = MIN_BUCKET_CONFIDENCE_PERCENT
                    )
                } else null

                val finalPrediction = windowPrediction ?: run {
                    val allRoute = predictionDao.getObservationsForRoute(
                        stationId = normalizedStationId,
                        operatorId = operatorKey,
                        destinationKey = destinationKey
                    )
                    evaluateDominantPlatform(
                        observations = allRoute,
                        minTotalObservations = MIN_ROUTE_OBSERVATIONS,
                        minConfidencePercent = MIN_ROUTE_CONFIDENCE_PERCENT
                    )
                }

                if (finalPrediction != null) {
                    val (predictedCode, confidencePct) = finalPrediction
                    dep.copy(
                        platform = PlatformInfo(
                            code = predictedCode,
                            isConfirmed = false,
                            directionHint = resolved.directionHint,
                            isChanged = false,
                            previousPlatform = null,
                            isHistoricalExpected = true,
                            expectedConfidencePercent = confidencePct
                        )
                    )
                } else {
                    dep
                }
            }
        }
    }
}
