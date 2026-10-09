package com.androidfung.departureboard.service.appfunctions

import androidx.appfunctions.AppFunctionSerializable

/** 
 * Represents the result of an operation indicating success or failure with an optional message. 
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class ActionResult(
    /** Indicates if the operation was successful. */
    val success: Boolean,
    /** A message detailing the result or error. */
    val message: String
)

/** 
 * Represents a single public transport departure or arrival event. 
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class DepartureSummary(
    /** The destination of the train or bus. */
    val destination: String,
    /** The transit line or route name (e.g. "Victoria", "134"). */
    val line: String,
    /** The expected time to arrival in minutes. */
    val minutesToStation: Int,
    /** The platform or stop number/letter. */
    val platform: String,
    /** The transport mode (e.g. "tube", "bus", "national-rail"). */
    val mode: String
)

/** 
 * Contains a list of real-time departure predictions for a specific station. 
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class LiveDeparturesResult(
    /** The official name of the station. */
    val stationName: String,
    /** The list of upcoming departures. */
    val departures: List<DepartureSummary>
)
