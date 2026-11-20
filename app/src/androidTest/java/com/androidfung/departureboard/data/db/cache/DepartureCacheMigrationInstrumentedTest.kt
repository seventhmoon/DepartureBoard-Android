package com.androidfung.departureboard.data.db.cache

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DepartureCacheMigrationInstrumentedTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        DepartureCacheDatabase::class.java
    )

    @Test
    fun migrate5To6_validatedByMigrationTestHelper() {
        helper.createDatabase(TEST_DB, 5).apply {
            execSQL(
                "INSERT INTO cached_departures (id, stationId, stationName, lineId, lineName, platformName, destinationName, timeToStationSeconds, modeName, cachedAtMillis) " +
                    "VALUES ('DEP_5', '940GZZLUKSX', 'Kings Cross', 'victoria', 'Victoria', 'Platform 4', 'Brixton', 120, 'tube', 1700000000000)"
            )
            execSQL(
                "INSERT INTO historical_platform_observations VALUES ('940GZZLUKSX', 'tfl', 'brixton', 480, 'Platform 4', 9, 1700000000000)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            6,
            true,
            DepartureCacheDatabase.MIGRATION_5_6
        )

        db.query("SELECT destinationName, localizedDestinationNamesJson FROM cached_departures WHERE id = 'DEP_5'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Brixton", cursor.getString(0))
            assertTrue(cursor.isNull(1))
        }
        db.query("SELECT observationCount FROM historical_platform_observations WHERE stationId = '940GZZLUKSX'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(9, cursor.getInt(0))
        }
    }

    @Test
    fun migrate4To6_validatedByMigrationTestHelper() {
        helper.createDatabase(TEST_DB, 4).apply {
            execSQL(
                "INSERT INTO cached_departures (id, stationId, stationName, lineId, lineName, platformName, destinationName, timeToStationSeconds, modeName, cachedAtMillis) " +
                    "VALUES ('DEP_4', 'HK_MTR_CEN', 'Central', 'TWL', 'Tsuen Wan Line', 'Platform 1', 'Tsuen Wan', 180, 'tube', 1700000000000)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            6,
            true,
            DepartureCacheDatabase.MIGRATION_4_5,
            DepartureCacheDatabase.MIGRATION_5_6
        )

        db.query("SELECT destinationName FROM cached_departures WHERE id = 'DEP_4'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Tsuen Wan", cursor.getString(0))
        }
        db.execSQL(
            "INSERT INTO historical_platform_observations VALUES ('HK_MTR_CEN', 'mtr', 'tsuen wan', 510, '1', 3, 1700000000000)"
        )
        db.query("SELECT observationCount FROM historical_platform_observations WHERE stationId = 'HK_MTR_CEN'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(3, cursor.getInt(0))
        }
    }

    private companion object {
        const val TEST_DB = "departure_cache_migration_instrumented_test.db"
    }
}
