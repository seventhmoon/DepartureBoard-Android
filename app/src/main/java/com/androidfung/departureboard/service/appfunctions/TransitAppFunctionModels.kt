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

/**
 * Walk-adjusted comparison between two nearby or competing concourse stations.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class TwinConcourseComparisonResult(
    /** Name of the first compared station hub. */
    val firstStationName: String,
    /** Name of the second compared station hub. */
    val secondStationName: String,
    /** Recommended station hub to walk to based on catchability. */
    val recommendedStationName: String,
    /** Concise summary of the fastest catchable departure and walk margin. */
    val recommendationSummary: String,
    /** Top upcoming departures from the first station. */
    val firstStationDepartures: List<DepartureSummary>,
    /** Top upcoming departures from the second station. */
    val secondStationDepartures: List<DepartureSummary>
)

/**
 * Represents an active service disruption or line status warning.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class DisruptionSummaryItem(
    /** Line or station affected by the disruption. */
    val targetName: String,
    /** Severity or status label (e.g. "Severe Delays", "Part Suspended"). */
    val statusSeverity: String,
    /** Detailed explanation of the disruption. */
    val reason: String
)

/**
 * Result containing current transit network disruptions.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class LineDisruptionsResult(
    /** Human-readable overview of current service health. */
    val summary: String,
    /** Individual disrupted lines or alerts matching the request. */
    val disruptions: List<DisruptionSummaryItem>
)

/**
 * Result detailing the latest available departure ("last train home") from a station.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class LastTrainResult(
    /** Origin station name. */
    val stationName: String,
    /** Destination of the latest available train or bus. */
    val destination: String,
    /** Transit line or operator name. */
    val line: String,
    /** Minutes until departure from the origin station. */
    val minutesToStation: Int,
    /** Assigned or predicted platform label. */
    val platform: String,
    /** Guidance message including any overnight/Shuden warning. */
    val guidance: String
)

/**
 * Represents a saved Delay Repay / Service Delay Refund eligibility receipt.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class DelayRepayReceiptItem(
    /** Date and time when the delayed or cancelled journey was recorded. */
    val dateLabel: String,
    /** Origin station name. */
    val stationName: String,
    /** Destination station name. */
    val destinationName: String,
    /** Transit line or rail operator name. */
    val lineName: String,
    /** Recorded delay in minutes. */
    val delayMinutes: Int,
    /** Compensation eligibility summary. */
    val compensationSummary: String,
    /** Official operator Delay Repay claim portal URL. */
    val claimUrl: String
)

/**
 * Result containing locally recorded Delay Repay compensation receipts.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class DelayRepayReceiptsResult(
    /** Total number of saved eligible receipts. */
    val count: Int,
    /** Saved Delay Repay receipts ordered most-recent first. */
    val receipts: List<DelayRepayReceiptItem>
)

