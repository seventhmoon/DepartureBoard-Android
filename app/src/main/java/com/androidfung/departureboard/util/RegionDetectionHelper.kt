package com.androidfung.departureboard.util

import android.content.Context
import android.location.Location
import android.telephony.TelephonyManager
import androidx.core.content.getSystemService
import com.androidfung.departureboard.data.model.TransitRegion
import java.time.ZoneId
import java.util.Locale

/**
 * Result of the multi-tier region detection pipeline.
 */
data class RegionDetectionResult(
    /** The resolved supported TransitRegion, or null if outside supported regions. */
    val matchedRegion: TransitRegion?,
    /** Country ISO code if identified (e.g. "US", "FR", "JP"). */
    val detectedCountryCode: String?,
    /** Whether the user is confirmed to be in an unsupported region. */
    val isUnsupportedLocation: Boolean,
    /** Which tier provided the match (GPS, CARRIER, TIMEZONE, or LOCALE). */
    val detectionSource: DetectionSource
)

enum class DetectionSource {
    GPS,
    CARRIER,
    TIMEZONE,
    LOCALE,
    NONE
}

object RegionDetectionHelper {

    /**
     * Executes the multi-tier detection pipeline:
     * 1. GPS Location coordinates (highest precision bounding box)
     * 2. Telephony Network Country ISO (cellular tower country)
     * 3. Telephony SIM Country ISO (fallback if roaming or network country unavailable)
     * 4. System TimeZone ID
     * 5. Device System Locale Country
     */
    fun detectRegion(
        context: Context,
        location: Location? = null
    ): RegionDetectionResult {
        // Tier 1: GPS location bounding boxes
        if (location != null) {
            val gpsRegion = TransitRegion.fromCoordinates(location.latitude, location.longitude)
            if (gpsRegion != null) {
                return RegionDetectionResult(
                    matchedRegion = gpsRegion,
                    detectedCountryCode = gpsRegion.code,
                    isUnsupportedLocation = false,
                    detectionSource = DetectionSource.GPS
                )
            }
        }

        // Tier 2 & 3: Telephony network and SIM country ISO
        val telephonyManager = try {
            context.getSystemService<TelephonyManager>()
        } catch (_: Throwable) {
            null
        }

        val networkCountryIso = telephonyManager?.networkCountryIso
            ?.takeIf { it.isNotBlank() }
            ?.uppercase(Locale.ROOT)

        if (networkCountryIso != null) {
            val carrierRegion = TransitRegion.fromCountryCode(networkCountryIso)
            if (carrierRegion != null) {
                return RegionDetectionResult(
                    matchedRegion = carrierRegion,
                    detectedCountryCode = networkCountryIso,
                    isUnsupportedLocation = false,
                    detectionSource = DetectionSource.CARRIER
                )
            } else {
                // Known country code from carrier, but unsupported by EkiBoard (e.g. "FR", "US", "DE")
                return RegionDetectionResult(
                    matchedRegion = null,
                    detectedCountryCode = networkCountryIso,
                    isUnsupportedLocation = true,
                    detectionSource = DetectionSource.CARRIER
                )
            }
        }

        val simCountryIso = telephonyManager?.simCountryIso
            ?.takeIf { it.isNotBlank() }
            ?.uppercase(Locale.ROOT)

        if (simCountryIso != null) {
            val simRegion = TransitRegion.fromCountryCode(simCountryIso)
            if (simRegion != null) {
                return RegionDetectionResult(
                    matchedRegion = simRegion,
                    detectedCountryCode = simCountryIso,
                    isUnsupportedLocation = false,
                    detectionSource = DetectionSource.CARRIER
                )
            }
        }

        // Tier 4: System TimeZone mapping
        val zoneId = try {
            ZoneId.systemDefault()
        } catch (_: Throwable) {
            null
        }

        if (zoneId != null) {
            val timezoneRegion = TransitRegion.fromTimeZone(zoneId)
            if (timezoneRegion != null) {
                return RegionDetectionResult(
                    matchedRegion = timezoneRegion,
                    detectedCountryCode = timezoneRegion.code,
                    isUnsupportedLocation = false,
                    detectionSource = DetectionSource.TIMEZONE
                )
            }
        }

        // Tier 5: Device Locale Country
        val effectiveLocale = LocaleHelper.getAppLocale() ?: Locale.getDefault()
        val localeCountry = effectiveLocale.country.uppercase(Locale.ROOT)
        if (localeCountry.isNotBlank()) {
            val localeRegion = TransitRegion.fromCountryCode(localeCountry)
            if (localeRegion != null) {
                return RegionDetectionResult(
                    matchedRegion = localeRegion,
                    detectedCountryCode = localeCountry,
                    isUnsupportedLocation = false,
                    detectionSource = DetectionSource.LOCALE
                )
            } else if (localeCountry != "ZZ" && localeCountry.length == 2) {
                return RegionDetectionResult(
                    matchedRegion = null,
                    detectedCountryCode = localeCountry,
                    isUnsupportedLocation = true,
                    detectionSource = DetectionSource.LOCALE
                )
            }
        }

        return RegionDetectionResult(
            matchedRegion = null,
            detectedCountryCode = null,
            isUnsupportedLocation = false,
            detectionSource = DetectionSource.NONE
        )
    }
}
