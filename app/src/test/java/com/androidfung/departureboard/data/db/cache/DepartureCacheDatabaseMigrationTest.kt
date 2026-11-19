package com.androidfung.departureboard.data.db.cache

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class DepartureCacheDatabaseMigrationTest {

    @Test
    fun migrate5To6_addsLocalizedDestinationNamesJson_and_preservesExistingRows() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "departure_cache_migration_test.db"
        context.deleteDatabase(dbName)

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
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
                                `timeToStationSeconds` INTEGER NOT NULL,
                                `cachedAtMillis` INTEGER NOT NULL,
                                PRIMARY KEY(`stationId`, `id`)
                            )
                            """.trimIndent()
                        )
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
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )

        try {
            val db = helper.writableDatabase
            db.execSQL(
                "INSERT INTO cached_departures (id, stationId, stationName, lineId, lineName, platformName, destinationName, timeToStationSeconds, cachedAtMillis) " +
                    "VALUES ('DEP_1', '940GZZLUKSX', 'Kings Cross', 'victoria', 'Victoria', 'Platform 4', 'Brixton', 120, 1700000000000)"
            )
            db.execSQL(
                "INSERT INTO historical_platform_observations VALUES ('940GZZLUKSX', 'tfl', 'brixton', 480, 'Platform 4', 12, 1700000000000)"
            )

            DepartureCacheDatabase.MIGRATION_5_6.migrate(db)

            val columns = mutableSetOf<String>()
            db.query("PRAGMA table_info(cached_departures)").use { cursor ->
                val nameIdx = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    columns.add(cursor.getString(nameIdx))
                }
            }
            assertTrue(columns.contains("localizedDestinationNamesJson"))

            db.query("SELECT destinationName, localizedDestinationNamesJson FROM cached_departures WHERE id = 'DEP_1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Brixton", cursor.getString(0))
                assertTrue(cursor.isNull(1))
            }

            db.query("SELECT observationCount FROM historical_platform_observations WHERE stationId = '940GZZLUKSX'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(12, cursor.getInt(0))
            }
        } finally {
            helper.close()
            context.deleteDatabase(dbName)
        }
    }
}
