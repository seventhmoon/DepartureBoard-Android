package com.androidfung.departureboard.data.model

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
    @Json(name = "name_en") override val nameEn: String? = null,
    @Json(name = "name_tc") override val nameTc: String? = null,
    @Json(name = "name_sc") override val nameSc: String? = null,
    @Json(name = "lat") val lat: String? = null,
    @Json(name = "long") val long: String? = null
) : com.androidfung.departureboard.data.i18n.LocalizedNameProvider {
    val displayName: String
        get() = getBilingualName(parentheses = false).ifBlank {
            nameTc?.takeIf { it.isNotBlank() } ?: nameEn.orEmpty()
        }
}
