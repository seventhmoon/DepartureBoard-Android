package com.androidfung.departureboard.data.model.hk

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Route-Stop mapping and Stop details models for KMB/Citybus.
 */
@JsonClass(generateAdapter = true)
data class HkKmbRouteStopResponse(
    @Json(name = "type") val type: String? = null,
    @Json(name = "data") val data: List<HkKmbRouteStopItem>? = null
)

@JsonClass(generateAdapter = true)
data class HkKmbRouteStopItem(
    @Json(name = "route") val route: String? = null,
    @Json(name = "bound") val bound: String? = null,
    @Json(name = "service_type") val serviceType: String? = null,
    @Json(name = "seq") val seq: Int? = null,
    @Json(name = "stop") val stop: String? = null
)

@JsonClass(generateAdapter = true)
data class HkKmbStopDetailResponse(
    @Json(name = "type") val type: String? = null,
    @Json(name = "data") val data: HkKmbStopDetailItem? = null
)

@JsonClass(generateAdapter = true)
data class HkKmbStopDetailItem(
    @Json(name = "stop") val stop: String? = null,
    @Json(name = "name_en") val jsonNameEn: String? = null,
    @Json(name = "name_tc") val jsonNameTc: String? = null,
    @Json(name = "name_sc") val jsonNameSc: String? = null,
    @Json(name = "lat") val lat: String? = null,
    @Json(name = "long") val long: String? = null
) : com.androidfung.departureboard.data.i18n.LocalizedNameProvider {
    override val localizedNames: Map<String, String> get() = buildMap {
        jsonNameEn?.takeIf { it.isNotBlank() }?.let { put("en", it) }
        jsonNameTc?.takeIf { it.isNotBlank() }?.let { put("zh-Hant", it) }
        jsonNameSc?.takeIf { it.isNotBlank() }?.let { put("zh-Hans", it) }
    }

    val displayName: String
        get() = getBilingualName(parentheses = false).ifBlank {
            jsonNameTc?.takeIf { it.isNotBlank() } ?: jsonNameEn.orEmpty()
        }
}
