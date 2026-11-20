package com.androidfung.departureboard.data.db.cache

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class DepartureCacheDatabaseMigrationTest {

    @Test
    fun migrateLegacyV5SinglePrimaryKeyToV6_passesRoomSchemaValidation_andPreservesRows() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "departure_cache_v5_pk_migration_test.db"
        context.deleteDatabase(dbName)

        createDatabaseAtVersion(context, dbName, version = 5) { db ->
            createLegacyV5CachedDeparturesTable(db, compositePrimaryKey = false)
            createHistoricalPlatformObservationsTable(db)
            db.execSQL(
                "INSERT INTO cached_departures (id, stationId, stationName, lineId, lineName, platformName, destinationName, timeToStationSeconds, modeName, cachedAtMillis) " +
                    "VALUES ('DEP_1', '940GZZLUKSX', 'Kings Cross', 'victoria', 'Victoria', 'Platform 4', 'Brixton', 120, 'tube', 1700000000000)"
            )
            db.execSQL(
                "INSERT INTO historical_platform_observations VALUES ('940GZZLUKSX', 'tfl', 'brixton', 480, 'Platform 4', 12, 1700000000000)"
            )
        }

        val migratedDb = Room.databaseBuilder(
            context,
            DepartureCacheDatabase::class.java,
            dbName
        )
            .addMigrations(DepartureCacheDatabase.MIGRATION_4_5, DepartureCacheDatabase.MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()

        try {
            val rows = migratedDb.departureDao().getDeparturesForStation("940GZZLUKSX")
            assertEquals(1, rows.size)
            assertEquals("DEP_1", rows.first().id)
            assertEquals("Brixton", rows.first().destinationName)
            assertNull(rows.first().localizedDestinationNamesJson)

            // Verify composite primary key (stationId, id) allows same departure id at another station
            migratedDb.departureDao().insertAll(
                listOf(
                    rows.first().copy(
                        stationId = "940GZZLUVIC",
                        stationName = "Victoria",
                        localizedDestinationNamesJson = """{"ja":"ブリクストン"}"""
                    )
                )
            )
            val victoriaRows = migratedDb.departureDao().getDeparturesForStation("940GZZLUVIC")
            assertEquals(1, victoriaRows.size)
            assertEquals("""{"ja":"ブリクストン"}""", victoriaRows.first().localizedDestinationNamesJson)

            val observations = migratedDb.platformPredictionDao().getObservationsForRoute(
                stationId = "940GZZLUKSX",
                operatorId = "tfl",
                destinationKey = "brixton"
            )
            assertEquals(1, observations.size)
            assertEquals(12, observations.first().observationCount)
        } finally {
            migratedDb.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun migrateV4ToV5ToV6_passesRoomSchemaValidation_andPreservesRows() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "departure_cache_v4_to_v6_migration_test.db"
        context.deleteDatabase(dbName)

        createDatabaseAtVersion(context, dbName, version = 4) { db ->
            createLegacyV4CachedDeparturesTable(db)
            db.execSQL(
                "INSERT INTO cached_departures (id, stationId, stationName, lineId, lineName, platformName, destinationName, timeToStationSeconds, modeName, cachedAtMillis) " +
                    "VALUES ('DEP_V4', 'HK_MTR_CEN', 'Central', 'TWL', 'Tsuen Wan Line', 'Platform 1', 'Tsuen Wan', 180, 'tube', 1700000000000)"
            )
        }

        val migratedDb = Room.databaseBuilder(
            context,
            DepartureCacheDatabase::class.java,
            dbName
        )
            .addMigrations(DepartureCacheDatabase.MIGRATION_4_5, DepartureCacheDatabase.MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()

        try {
            val rows = migratedDb.departureDao().getDeparturesForStation("HK_MTR_CEN")
            assertEquals(1, rows.size)
            assertEquals("DEP_V4", rows.first().id)
            assertEquals("Tsuen Wan", rows.first().destinationName)

            migratedDb.platformPredictionDao().incrementObservation(
                stationId = "HK_MTR_CEN",
                operatorId = "mtr",
                destinationKey = "tsuen wan",
                bucket = 510,
                platformCode = "1",
                nowMillis = 1700000000500
            )
            val obs = migratedDb.platformPredictionDao().getObservationsForRoute("HK_MTR_CEN", "mtr", "tsuen wan")
            assertEquals(1, obs.size)
            assertEquals("1", obs.first().platformCode)
        } finally {
            migratedDb.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun migrate5To6_alsoHandlesPreCompositeV5TableSafely() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "departure_cache_v5_composite_migration_test.db"
        context.deleteDatabase(dbName)

        createDatabaseAtVersion(context, dbName, version = 5) { db ->
            createLegacyV5CachedDeparturesTable(db, compositePrimaryKey = true)
            createHistoricalPlatformObservationsTable(db)
            db.execSQL(
                "INSERT INTO cached_departures (id, stationId, stationName, lineId, lineName, platformName, destinationName, timeToStationSeconds, modeName, cachedAtMillis) " +
                    "VALUES ('DEP_SHARED', '940GZZLUKSX', 'Kings Cross', 'victoria', 'Victoria', 'Platform 4', 'Brixton', 120, 'tube', 1700000000000)"
            )
            db.execSQL(
                "INSERT INTO cached_departures (id, stationId, stationName, lineId, lineName, platformName, destinationName, timeToStationSeconds, modeName, cachedAtMillis) " +
                    "VALUES ('DEP_SHARED', '940GZZLUVIC', 'Victoria', 'victoria', 'Victoria', 'Platform 3', 'Walthamstow', 240, 'tube', 1700000000000)"
            )
        }

        val migratedDb = Room.databaseBuilder(
            context,
            DepartureCacheDatabase::class.java,
            dbName
        )
            .addMigrations(DepartureCacheDatabase.MIGRATION_4_5, DepartureCacheDatabase.MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()

        try {
            assertEquals(1, migratedDb.departureDao().getDeparturesForStation("940GZZLUKSX").size)
            assertEquals(1, migratedDb.departureDao().getDeparturesForStation("940GZZLUVIC").size)
            val cols = mutableSetOf<String>()
            migratedDb.openHelper.writableDatabase.query("PRAGMA table_info(`cached_departures`)").use { cursor ->
                val idx = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) cols.add(cursor.getString(idx))
            }
            assertTrue(cols.contains("localizedDestinationNamesJson"))
        } finally {
            migratedDb.close()
            context.deleteDatabase(dbName)
        }
    }

    private fun createDatabaseAtVersion(
        context: Context,
        dbName: String,
        version: Int,
        seedBlock: (SupportSQLiteDatabase) -> Unit
    ) {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        seedBlock(db)
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        helper.writableDatabase.close()
        helper.close()
    }

    private fun createLegacyV4CachedDeparturesTable(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cached_departures` (
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
                `cachedAtMillis` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_cached_departures_stationId_timeToStationSeconds` ON `cached_departures` (`stationId`, `timeToStationSeconds`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_cached_departures_cachedAtMillis` ON `cached_departures` (`cachedAtMillis`)"
        )
    }

    private fun createLegacyV5CachedDeparturesTable(
        db: SupportSQLiteDatabase,
        compositePrimaryKey: Boolean
    ) {
        val pkClause = if (compositePrimaryKey) {
            "PRIMARY KEY(`stationId`, `id`)"
        } else {
            "PRIMARY KEY(`id`)"
        }
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cached_departures` (
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
                `cachedAtMillis` INTEGER NOT NULL,
                $pkClause
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_cached_departures_stationId_timeToStationSeconds` ON `cached_departures` (`stationId`, `timeToStationSeconds`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_cached_departures_cachedAtMillis` ON `cached_departures` (`cachedAtMillis`)"
        )
    }

    private fun createHistoricalPlatformObservationsTable(db: SupportSQLiteDatabase) {
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
