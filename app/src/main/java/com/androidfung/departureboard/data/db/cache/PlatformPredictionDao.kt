package com.androidfung.departureboard.data.db.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class PlatformPredictionDao {

    @Query(
        """
        SELECT * FROM historical_platform_observations
        WHERE stationId = :stationId
          AND operatorId = :operatorId
          AND destinationKey = :destinationKey
          AND scheduledMinuteBucket BETWEEN :minBucket AND :maxBucket
        """
    )
    abstract suspend fun getObservationsInWindow(
        stationId: String,
        operatorId: String,
        destinationKey: String,
        minBucket: Int,
        maxBucket: Int
    ): List<HistoricalPlatformObservationEntity>

    @Query(
        """
        SELECT * FROM historical_platform_observations
        WHERE stationId = :stationId
          AND operatorId = :operatorId
          AND destinationKey = :destinationKey
        """
    )
    abstract suspend fun getObservationsForRoute(
        stationId: String,
        operatorId: String,
        destinationKey: String
    ): List<HistoricalPlatformObservationEntity>

    @Query(
        """
        SELECT * FROM historical_platform_observations
        WHERE stationId = :stationId
          AND operatorId = :operatorId
          AND destinationKey = :destinationKey
          AND scheduledMinuteBucket = :bucket
          AND platformCode = :platformCode
        LIMIT 1
        """
    )
    abstract suspend fun getSingleObservation(
        stationId: String,
        operatorId: String,
        destinationKey: String,
        bucket: Int,
        platformCode: String
    ): HistoricalPlatformObservationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(entity: HistoricalPlatformObservationEntity)

    @Transaction
    open suspend fun incrementObservation(
        stationId: String,
        operatorId: String,
        destinationKey: String,
        bucket: Int,
        platformCode: String,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val existing = getSingleObservation(stationId, operatorId, destinationKey, bucket, platformCode)
        val nextCount = ((existing?.observationCount ?: 0) + 1).coerceAtMost(10_000)
        upsert(
            HistoricalPlatformObservationEntity(
                stationId = stationId,
                operatorId = operatorId,
                destinationKey = destinationKey,
                scheduledMinuteBucket = bucket,
                platformCode = platformCode,
                observationCount = nextCount,
                lastObservedEpochMillis = nowMillis
            )
        )
    }

    @Query("DELETE FROM historical_platform_observations WHERE lastObservedEpochMillis < :thresholdMillis")
    abstract suspend fun pruneStaleObservations(thresholdMillis: Long)
}
