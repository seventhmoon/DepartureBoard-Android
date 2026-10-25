package com.androidfung.departureboard.data.model

import androidx.annotation.StringRes
import com.androidfung.departureboard.R
import java.time.ZoneId

/**
 * Supported transit regions in EkiBoard following international ISO 3166-1 / UN-LOCODE standards.
 * - [GB]: Great Britain (Nationwide National Rail, London TfL, BODS buses, Tyne & Wear Metro)
 * - [HK]: Hong Kong (MTR heavy rail & franchised buses)
 */
enum class TransitRegion(
    val code: String,
    val flagEmoji: String,
    val timeZone: ZoneId,
    @StringRes val displayNameRes: Int,
    val subtitle: String
) {
    GB(
        code = "GB",
        flagEmoji = "🇬🇧",
        timeZone = ZoneId.of("Europe/London"),
        displayNameRes = R.string.region_uk,
        subtitle = "National Rail & TfL"
    ),
    HK(
        code = "HK",
        flagEmoji = "🇭🇰",
        timeZone = ZoneId.of("Asia/Hong_Kong"),
        displayNameRes = R.string.region_hk,
        subtitle = "MTR & Franchised Buses"
    );

    // Backward-compatibility property so any existing code using `.id` continues to work
    val id: String get() = code

    companion object {
        /**
         * Resolves a TransitRegion by its standard code or legacy alias (e.g. "UK" maps to GB).
         */
        fun fromId(id: String?): TransitRegion {
            return when (id?.uppercase()?.trim()) {
                "GB", "UK", "GBR", "LON" -> GB
                "HK", "HKG" -> HK
                else -> GB
            }
        }

        /**
         * Detects the TransitRegion from a station or departure ID prefix.
         */
        fun fromStationId(stationId: String?): TransitRegion {
            if (stationId == null) return GB
            return when {
                stationId.startsWith("HK_", ignoreCase = true) -> HK
                else -> GB
            }
        }
    }
}
