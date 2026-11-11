package com.androidfung.departureboard.data.db.cache

import androidx.room.Entity
import androidx.room.Index

/**
 * Aggregated historical observation of a confirmed departure platform at a station.
 *
 * Used by [com.androidfung.departureboard.util.PlatformHistoryPredictor] to suggest high-confidence
 * "Typically Plat X (92%)" badges while live rail services still show `Platform TBD`.
 */
@Entity(
    tableName = "historical_platform_observations",
    primaryKeys = [
        "stationId",
        "operatorId",
        "destinationKey",
        "scheduledMinuteBucket",
        "platformCode"
    ],
    indices = [
        Index(value = ["stationId", "operatorId", "destinationKey"])
    ]
)
data class HistoricalPlatformObservationEntity(
    val stationId: String,
    val operatorId: String,
    val destinationKey: String,
    val scheduledMinuteBucket: Int,
    val platformCode: String,
    val observationCount: Int,
    val lastObservedEpochMillis: Long
)
