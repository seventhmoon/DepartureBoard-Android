package com.androidfung.departureboard.data.model

import androidx.annotation.StringRes
import com.androidfung.departureboard.R
import com.androidfung.departureboard.data.model.au.AuDefaultStations
import com.androidfung.departureboard.data.model.au.AuStationDirectory
import com.androidfung.departureboard.data.model.au.AuSydneyLine
import com.androidfung.departureboard.data.model.gb.GbDefaultStations
import com.androidfung.departureboard.data.model.gb.GbTransitLine
import com.androidfung.departureboard.data.model.hk.HkDefaultStations
import com.androidfung.departureboard.data.model.hk.HkMtrLine
import com.androidfung.departureboard.data.model.hk.HkStationDirectory
import com.androidfung.departureboard.data.model.jp.JpDefaultStations
import com.androidfung.departureboard.data.model.jp.JpStationDirectory
import com.androidfung.departureboard.data.model.jp.JpTokyoLine
import com.androidfung.departureboard.data.model.sg.SgDefaultStations
import com.androidfung.departureboard.data.model.sg.SgMrtLine
import com.androidfung.departureboard.data.model.sg.SgStationDirectory
import java.time.ZoneId

/**
 * Supported transit regions in EkiBoard following international ISO 3166-1 / UN-LOCODE standards.
 * - [GB]: Great Britain (Nationwide National Rail, London TfL, BODS buses, Tyne & Wear Metro)
 * - [HK]: Hong Kong (MTR heavy rail & franchised buses)
 * - [SG]: Singapore (MRT heavy rail & franchised buses)
 * - [AU]: Sydney, Australia (Sydney Trains & Sydney Metro)
 * - [JP]: Tokyo, Japan (Tokyo Metro & JR East)
 */
/**
 * Regional representation of a selectable transit mode filter (e.g. "MTR" in HK, "Tube" in London).
 */
data class RegionTransitMode(
    val mode: TransitMode?,
    val displayName: String
)

enum class TransitRegion(
    val code: String,
    val flagEmoji: String,
    val timeZone: ZoneId,
    @StringRes val displayNameRes: Int,
    val subtitle: String,
    val supportedModes: List<RegionTransitMode>
) {
    GB(
        code = "GB",
        flagEmoji = "🇬🇧",
        timeZone = ZoneId.of("Europe/London"),
        displayNameRes = R.string.region_uk,
        subtitle = "National Rail & TfL",
        supportedModes = listOf(
            RegionTransitMode(null, "All"),
            RegionTransitMode(TransitMode.METRO, "Tube"),
            RegionTransitMode(TransitMode.RAIL, "Elizabeth line"),
            RegionTransitMode(TransitMode.RAIL, "Overground"),
            RegionTransitMode(TransitMode.TRAM, "DLR"),
            RegionTransitMode(TransitMode.RAIL, "National Rail"),
            RegionTransitMode(TransitMode.TRAM, "Tram"),
            RegionTransitMode(TransitMode.BUS, "Bus")
        )
    ),
    HK(
        code = "HK",
        flagEmoji = "🇭🇰",
        timeZone = ZoneId.of("Asia/Hong_Kong"),
        displayNameRes = R.string.region_hk,
        subtitle = "MTR & Franchised Buses",
        supportedModes = listOf(
            RegionTransitMode(null, "All"),
            RegionTransitMode(TransitMode.METRO, "MTR"),
            RegionTransitMode(TransitMode.BUS, "Bus")
        )
    ),
    SG(
        code = "SG",
        flagEmoji = "🇸🇬",
        timeZone = ZoneId.of("Asia/Singapore"),
        displayNameRes = R.string.region_sg,
        subtitle = "MRT & Buses",
        supportedModes = listOf(
            RegionTransitMode(null, "All"),
            RegionTransitMode(TransitMode.METRO, "MRT"),
            RegionTransitMode(TransitMode.BUS, "Bus")
        )
    ),
    AU(
        code = "AU",
        flagEmoji = "🇦🇺",
        timeZone = ZoneId.of("Australia/Sydney"),
        displayNameRes = R.string.region_au,
        subtitle = "Sydney Trains & Metro",
        supportedModes = listOf(
            RegionTransitMode(null, "All"),
            RegionTransitMode(TransitMode.METRO, "Metro"),
            RegionTransitMode(TransitMode.RAIL, "Train"),
            RegionTransitMode(TransitMode.BUS, "Bus")
        )
    ),
    JP(
        code = "JP",
        flagEmoji = "🇯🇵",
        timeZone = ZoneId.of("Asia/Tokyo"),
        displayNameRes = R.string.region_jp,
        subtitle = "Tokyo Metro & JR East",
        supportedModes = listOf(
            RegionTransitMode(null, "All"),
            RegionTransitMode(TransitMode.METRO, "Metro"),
            RegionTransitMode(TransitMode.RAIL, "JR / Rail"),
            RegionTransitMode(TransitMode.BUS, "Bus")
        )
    );

    /**
     * Resolves localized/branded display name for a transit mode in this region.
     */
    fun getModeDisplayName(mode: TransitMode): String {
        return supportedModes.firstOrNull { it.mode == mode }?.displayName
            ?: mode.displayName
    }

    // Backward-compatibility property so any existing code using `.id` continues to work
    val id: String get() = code

    /**
     * Default popular transit stations for this region.
     */
    val defaultStations: List<Station>
        get() = when (this) {
            GB -> GbDefaultStations.POPULAR_STATIONS
            HK -> HkDefaultStations.POPULAR_STATIONS
            SG -> SgDefaultStations.POPULAR_STATIONS
            AU -> AuDefaultStations.POPULAR_STATIONS
            JP -> JpDefaultStations.POPULAR_STATIONS
        }

    /**
     * Regional station directory with catalog entries and search ranking, if available offline.
     */
    val directory: StationDirectory?
        get() = when (this) {
            GB -> null
            HK -> HkStationDirectory
            SG -> SgStationDirectory
            AU -> AuStationDirectory
            JP -> JpStationDirectory
        }

    /**
     * Unified transit line registry for looking up transit lines, colors, and badges.
     */
    val lineRegistry: TransitLineRegistry
        get() = when (this) {
            GB -> GbTransitLine
            HK -> HkMtrLine
            SG -> SgMrtLine
            AU -> AuSydneyLine
            JP -> JpTokyoLine
        }

    /**
     * Localized string resource for "No stations found" query hints.
     */
    @get:StringRes
    val searchNotFoundHintRes: Int
        get() = when (this) {
            GB -> R.string.search_stations_not_found_hint_general
            HK -> R.string.search_stations_not_found_hint_hk
            SG -> R.string.search_stations_not_found_hint_sg
            AU -> R.string.search_stations_not_found_hint_au
            JP -> R.string.search_stations_not_found_hint_jp
        }

    /**
     * Whether this region uses UK statutory Delay Repay / TfL Service Delay Refund rules.
     */
    val supportsUkDelayRepay: Boolean
        get() = this == GB

    /**
     * Whether stations in this region display London TfL / National Rail Peak vs Off-Peak fare pills.
     */
    val supportsGbPeakFareIndicator: Boolean
        get() = this == GB

    /**
     * Default primary rail/metro mode identifier for newly resolved stations in this region.
     */
    val defaultPrimaryModeId: String
        get() = when (this) {
            GB -> "tube"
            HK -> "mtr"
            SG, AU, JP -> "metro"
        }

    /**
     * Suffix used for external map directions queries for stations in this region.
     */
    val mapsSearchRegionSuffix: String
        get() = when (this) {
            GB -> "United Kingdom"
            HK -> "Hong Kong"
            SG -> "Singapore"
            AU -> "Sydney"
            JP -> "Tokyo"
        }

    /**
     * Localized string resource for network/line disruption status sheet title.
     */
    @get:StringRes
    val lineStatusTitleRes: Int
        get() = if (this == HK) R.string.line_status_title_mtr else R.string.line_status_title_live

    /**
     * Localized string resource for network/line disruption status sheet subtitle.
     */
    @get:StringRes
    val lineStatusDescRes: Int
        get() = if (this == HK) R.string.line_status_desc_hk else R.string.line_status_desc_general

    companion object {
        /**
         * Resolves a TransitRegion by its standard code or legacy alias.
         */
        fun fromId(id: String?): TransitRegion {
            return when (id?.uppercase()?.trim()) {
                "GB", "UK", "GBR", "LON" -> GB
                "HK", "HKG" -> HK
                "SG", "SGP", "SIN" -> SG
                "AU", "AUS", "SYD", "NSW" -> AU
                "JP", "JPN", "TYO", "TOKYO" -> JP
                else -> GB
            }
        }

        /**
         * Resolves a TransitRegion by standard ISO 3166-1 alpha-2 country code.
         */
        fun fromCountryCode(countryCode: String?): TransitRegion? {
            return when (countryCode?.uppercase()?.trim()) {
                "GB", "UK" -> GB
                "HK" -> HK
                "SG" -> SG
                "AU" -> AU
                "JP" -> JP
                else -> null
            }
        }

        /**
         * Resolves a TransitRegion based on geographic coordinates (bounding box / geofence).
         */
        fun fromCoordinates(latitude: Double, longitude: Double): TransitRegion? {
            return when {
                // Hong Kong (approx 22.15 to 22.58 N, 113.83 to 114.45 E)
                latitude in 22.15..22.60 && longitude in 113.80..114.50 -> HK

                // Singapore (approx 1.15 to 1.48 N, 103.60 to 104.05 E)
                latitude in 1.15..1.50 && longitude in 103.55..104.10 -> SG

                // Sydney / NSW Metropolitan Area (approx -34.40 to -33.30 S, 150.50 to 151.40 E)
                latitude in -34.40..-33.30 && longitude in 150.50..151.40 -> AU

                // Greater Tokyo Area / Kanto (approx 34.90 to 36.40 N, 138.80 to 140.90 E)
                latitude in 34.90..36.40 && longitude in 138.80..140.90 -> JP

                // Great Britain (approx 49.80 to 60.90 N, -8.60 to 1.80 E)
                latitude in 49.80..60.90 && longitude in -8.60..1.80 -> GB

                else -> null
            }
        }

        /**
         * Resolves a TransitRegion from a system ZoneId.
         */
        fun fromTimeZone(zoneId: ZoneId): TransitRegion? {
            entries.firstOrNull { it.timeZone == zoneId }?.let { return it }
            val id = zoneId.id
            return when {
                id.startsWith("Asia/Hong_Kong") -> HK
                id.startsWith("Asia/Singapore") -> SG
                id.startsWith("Asia/Tokyo") -> JP
                id.startsWith("Australia/") -> AU
                id == "Europe/Belfast" -> GB
                else -> null
            }
        }

        /**
         * Detects the TransitRegion from a station or departure ID prefix.
         */
        fun fromStationId(stationId: String?): TransitRegion {
            if (stationId == null) return GB
            return when {
                stationId.isHkTransitId -> HK
                stationId.isSgTransitId -> SG
                stationId.isAuTransitId -> AU
                stationId.isJpTransitId -> JP
                else -> GB
            }
        }
    }
}
