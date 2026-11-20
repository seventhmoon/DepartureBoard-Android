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
    version = 6,
    exportSchema = true
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

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val existingColumns = mutableSetOf<String>()
                db.query("PRAGMA table_info(`cached_departures`)").use { cursor ->
                    val nameIndex = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        if (nameIndex >= 0) {
                            existingColumns.add(cursor.getString(nameIndex))
                        }
                    }
                }

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cached_departures_new` (
                        `id` TEXT NOT NULL,
                        `stationId` TEXT NOT NULL,
                        `stationName` TEXT NOT NULL,
                        `lineId` TEXT NOT NULL,
                        `lineName` TEXT NOT NULL,
                        `platformName` TEXT NOT NULL,
                        `destinationName` TEXT NOT NULL,
                        `towards` TEXT,
                        `direction` TEXT,
                        `timeToStationSeconds` INTEGER NOT NULL,
                        `expectedArrivalIso` TEXT,
                        `currentLocation` TEXT,
                        `modeName` TEXT NOT NULL,
                        `destinationStationId` TEXT,
                        `badgeBgColorArgb` INTEGER,
                        `badgeTextColorArgb` INTEGER,
                        `badgeLineCode` TEXT,
                        `isWheelchairAccessible` INTEGER,
                        `occupancyStatus` TEXT,
                        `platformCode` TEXT,
                        `isPlatformConfirmed` INTEGER,
                        `isHistoricalExpectedPlatform` INTEGER,
                        `expectedPlatformConfidence` INTEGER,
                        `operatorCode` TEXT,
                        `operatorName` TEXT,
                        `coachCount` INTEGER,
                        `cancelReason` TEXT,
                        `delayReason` TEXT,
                        `headwaySeconds` INTEGER,
                        `stopActivityName` TEXT,
                        `statusName` TEXT,
                        `scheduledDepartureEpochMillis` INTEGER,
                        `expectedDepartureEpochMillis` INTEGER,
                        `localizedDestinationNamesJson` TEXT,
                        `cachedAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`stationId`, `id`)
                    )
                    """.trimIndent()
                )

                val targetColumns = listOf(
                    "id" to "''",
                    "stationId" to "''",
                    "stationName" to "''",
                    "lineId" to "''",
                    "lineName" to "''",
                    "platformName" to "''",
                    "destinationName" to "''",
                    "towards" to "NULL",
                    "direction" to "NULL",
                    "timeToStationSeconds" to "0",
                    "expectedArrivalIso" to "NULL",
                    "currentLocation" to "NULL",
                    "modeName" to "''",
                    "destinationStationId" to "NULL",
                    "badgeBgColorArgb" to "NULL",
                    "badgeTextColorArgb" to "NULL",
                    "badgeLineCode" to "NULL",
                    "isWheelchairAccessible" to "NULL",
                    "occupancyStatus" to "NULL",
                    "platformCode" to "NULL",
                    "isPlatformConfirmed" to "NULL",
                    "isHistoricalExpectedPlatform" to "NULL",
                    "expectedPlatformConfidence" to "NULL",
                    "operatorCode" to "NULL",
                    "operatorName" to "NULL",
                    "coachCount" to "NULL",
                    "cancelReason" to "NULL",
                    "delayReason" to "NULL",
                    "headwaySeconds" to "NULL",
                    "stopActivityName" to "NULL",
                    "statusName" to "NULL",
                    "scheduledDepartureEpochMillis" to "NULL",
                    "expectedDepartureEpochMillis" to "NULL",
                    "localizedDestinationNamesJson" to "NULL",
                    "cachedAtMillis" to "0"
                )

                val insertCols = targetColumns.joinToString(", ") { "`${it.first}`" }
                val selectExprs = targetColumns.joinToString(", ") { (col, fallback) ->
                    if (col in existingColumns) "`$col`" else "$fallback AS `$col`"
                }

                db.execSQL(
                    "INSERT OR REPLACE INTO `cached_departures_new` ($insertCols) SELECT $selectExprs FROM `cached_departures`"
                )
                db.execSQL("DROP TABLE `cached_departures`")
                db.execSQL("ALTER TABLE `cached_departures_new` RENAME TO `cached_departures`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_cached_departures_stationId_timeToStationSeconds` ON `cached_departures` (`stationId`, `timeToStationSeconds`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_cached_departures_cachedAtMillis` ON `cached_departures` (`cachedAtMillis`)"
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
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
