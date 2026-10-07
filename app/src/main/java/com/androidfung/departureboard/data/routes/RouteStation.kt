package com.androidfung.departureboard.data.routes

/**
 * Domain representation of a station along a linear transit corridor route sequence.
 *
 * @param crs 3-letter National Rail CRS station code (e.g. "BED", "STP", "PAD").
 * @param naptanId TfL / NaPTAN station identifier (e.g. "910GSTPX", "910GPADTON").
 * @param name Official primary station display name.
 * @param aliases Normalized lowercase search aliases and alternative colloquial names.
 */
data class RouteStation(
    val crs: String,
    val naptanId: String? = null,
    val name: String,
    val aliases: List<String> = listOf(name.lowercase())
)
