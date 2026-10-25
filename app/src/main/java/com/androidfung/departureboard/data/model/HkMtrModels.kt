package com.androidfung.departureboard.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Models for Hong Kong MTR Real-Time API (data.gov.hk / getSchedule.php).
 */
@JsonClass(generateAdapter = true)
data class HkMtrScheduleResponse(
    @Json(name = "status") val status: Int? = null,
    @Json(name = "message") val message: String? = null,
    @Json(name = "sys_time") val sysTime: String? = null,
    @Json(name = "isdelay") val isDelay: String? = null,
    @Json(name = "data") val data: Map<String, HkMtrStationScheduleData>? = null
)

@JsonClass(generateAdapter = true)
data class HkMtrStationScheduleData(
    @Json(name = "curr_time") val currTime: String? = null,
    @Json(name = "sys_time") val sysTime: String? = null,
    @Json(name = "UP") val upDepartures: List<HkMtrTrainScheduleItem>? = null,
    @Json(name = "DOWN") val downDepartures: List<HkMtrTrainScheduleItem>? = null
)

@JsonClass(generateAdapter = true)
data class HkMtrTrainScheduleItem(
    @Json(name = "ttnt") val ttnt: String? = null, // Time to next train (minutes)
    @Json(name = "valid") val valid: String? = null,
    @Json(name = "plat") val plat: String? = null,
    @Json(name = "time") val time: String? = null, // e.g. "2026-03-31 16:35:00"
    @Json(name = "source") val source: String? = null,
    @Json(name = "dest") val dest: String? = null, // Destination station code (e.g. "CHW", "TSW")
    @Json(name = "seq") val seq: String? = null
)
