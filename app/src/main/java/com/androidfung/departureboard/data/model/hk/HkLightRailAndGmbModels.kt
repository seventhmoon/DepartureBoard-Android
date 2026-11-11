package com.androidfung.departureboard.data.model.hk

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * MTR Light Rail Next Train response from `https://rt.data.gov.hk/v1/transport/mtr/lrt/getSchedule?station_id={id}`.
 */
@JsonClass(generateAdapter = true)
data class HkLightRailScheduleResponse(
    val status: Int = 0,
    @param:Json(name = "system_time") val systemTime: String? = null,
    @param:Json(name = "platform_list") val platformList: List<HkLightRailPlatform> = emptyList()
)

@JsonClass(generateAdapter = true)
data class HkLightRailPlatform(
    @param:Json(name = "platform_id") val platformId: Int = 1,
    @param:Json(name = "route_list") val routeList: List<HkLightRailRouteArrival> = emptyList()
)

@JsonClass(generateAdapter = true)
data class HkLightRailRouteArrival(
    @param:Json(name = "route_no") val routeNo: String = "",
    @param:Json(name = "train_length") val trainLength: Int? = null,
    @param:Json(name = "dest_en") val destEn: String? = null,
    @param:Json(name = "dest_ch") val destCh: String? = null,
    @param:Json(name = "time_en") val timeEn: String? = null,
    @param:Json(name = "time_ch") val timeCh: String? = null,
    @param:Json(name = "stop") val stopFlag: Int? = null
)

/**
 * HKSAR Green Minibus (GMB) Stop ETA response from `https://data.etagmb.gov.hk/eta/stop/{stop_id}`.
 */
@JsonClass(generateAdapter = true)
data class HkGmbStopEtaResponse(
    val type: String? = null,
    @param:Json(name = "generated_timestamp") val generatedTimestamp: String? = null,
    val data: List<HkGmbRouteStopEta> = emptyList()
)

@JsonClass(generateAdapter = true)
data class HkGmbRouteStopEta(
    @param:Json(name = "route_id") val routeId: Long? = null,
    @param:Json(name = "route_seq") val routeSeq: Int? = null,
    @param:Json(name = "stop_seq") val stopSeq: Int? = null,
    val enabled: Boolean = true,
    val eta: List<HkGmbEtaEntry> = emptyList()
)

@JsonClass(generateAdapter = true)
data class HkGmbEtaEntry(
    @param:Json(name = "eta_seq") val etaSeq: Int = 1,
    val diff: Int? = null,
    val remarks_tc: String? = null,
    val remarks_en: String? = null,
    val timestamp: String? = null
)
