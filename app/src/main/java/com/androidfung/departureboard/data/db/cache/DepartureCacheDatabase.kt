package com.androidfung.departureboard.data.db.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Dynamic runtime cache database for departures and learned historical platform assignments across all regions.
 * Fully managed by Room in the app sandbox (no pre-packaged assets).
 */
@Database(
    entities = [
        CachedDepartureEntity::class,
        HistoricalPlatformObservationEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class DepartureCacheDatabase : RoomDatabase() {

    abstract fun departureDao(): DepartureDao

    abstract fun platformPredictionDao(): PlatformPredictionDao

    companion object {
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cached_departures ADD COLUMN isHistoricalExpectedPlatform INTEGER")
                db.execSQL("ALTER TABLE cached_departures ADD COLUMN expectedPlatformConfidence INTEGER")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `historical_platform_observations` (
                        `stationId` TEXT NOT NULL,
                        `operatorId` TEXT NOT NULL,
                        `destinationKey` TEXT NOT NULL,
                        `scheduledMinuteBucket` INTEGER NOT NULL,
                        `platformCode` TEXT NOT NULL,
                        `observationCount` INTEGER NOT NULL,
                        `lastObservedEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`stationId`, `operatorId`, `destinationKey`, `scheduledMinuteBucket`, `platformCode`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_historical_platform_observations_stationId_operatorId_destinationKey` ON `historical_platform_observations` (`stationId`, `operatorId`, `destinationKey`)"
                )
            }
        }

        @Volatile
        private var instance: DepartureCacheDatabase? = null

        fun getInstance(context: Context): DepartureCacheDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DepartureCacheDatabase::class.java,
                    "departure_cache.db"
                )
                    .addMigrations(MIGRATION_4_5)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
