package com.androidfung.departureboard.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Models for Hong Kong Bus open data APIs (KMB / LWB and Citybus via data.gov.hk).
 */
@JsonClass(generateAdapter = true)
data class HkKmbStopEtaResponse(
    @Json(name = "type") val type: String? = null,
    @Json(name = "version") val version: String? = null,
    @Json(name = "generated_timestamp") val generatedTimestamp: String? = null,
    @Json(name = "data") val data: List<HkKmbEtaItem>? = null
)

@JsonClass(generateAdapter = true)
data class HkKmbEtaItem(
    @Json(name = "co") val co: String? = null,
    @Json(name = "route") val route: String? = null,
    @Json(name = "dir") val dir: String? = null,
    @Json(name = "service_type") val serviceType: Int? = null,
    @Json(name = "seq") val seq: Int? = null,
    @Json(name = "stop") val stop: String? = null,
    @Json(name = "dest_tc") val destTc: String? = null,
    @Json(name = "dest_sc") val destSc: String? = null,
    @Json(name = "dest_en") val destEn: String? = null,
    @Json(name = "eta_seq") val etaSeq: Int? = null,
    @Json(name = "eta") val eta: String? = null,
    @Json(name = "rmk_tc") val rmkTc: String? = null,
    @Json(name = "rmk_en") val rmkEn: String? = null,
    @Json(name = "data_timestamp") val dataTimestamp: String? = null
)

@JsonClass(generateAdapter = true)
data class HkCitybusEtaResponse(
    @Json(name = "type") val type: String? = null,
    @Json(name = "version") val version: String? = null,
    @Json(name = "generated_timestamp") val generatedTimestamp: String? = null,
    @Json(name = "data") val data: List<HkCitybusEtaItem>? = null
)

@JsonClass(generateAdapter = true)
data class HkCitybusEtaItem(
    @Json(name = "co") val co: String? = null,
    @Json(name = "route") val route: String? = null,
    @Json(name = "dir") val dir: String? = null,
    @Json(name = "seq") val seq: Int? = null,
    @Json(name = "stop") val stop: String? = null,
    @Json(name = "dest_tc") val destTc: String? = null,
    @Json(name = "dest_sc") val destSc: String? = null,
    @Json(name = "dest_en") val destEn: String? = null,
    @Json(name = "eta_seq") val etaSeq: Int? = null,
    @Json(name = "eta") val eta: String? = null,
    @Json(name = "rmk_tc") val rmkTc: String? = null,
    @Json(name = "rmk_en") val rmkEn: String? = null,
    @Json(name = "data_timestamp") val dataTimestamp: String? = null
)
