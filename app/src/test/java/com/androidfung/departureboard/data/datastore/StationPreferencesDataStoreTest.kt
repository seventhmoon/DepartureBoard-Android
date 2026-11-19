package com.androidfung.departureboard.data.datastore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.androidfung.departureboard.data.model.Station
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class StationPreferencesDataStoreTest {

    @Test
    fun parseRecentStations_skipsNullArrayElementsWithoutCrashing() {
        val method = StationPreferencesDataStore::class.java.getDeclaredMethod(
            "parseRecentStations",
            String::class.java
        ).apply { isAccessible = true }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val ds = StationPreferencesDataStore(context)

        val json = """
            [
              null,
              {"id":"940GZZLUOXC","name":"Oxford Circus","modes":["tube"],"isFavorite":false},
              null
            ]
        """.trimIndent()

        @Suppress("UNCHECKED_CAST")
        val parsed = method.invoke(ds, json) as List<StationDto>
        assertEquals(1, parsed.size)
        assertEquals("940GZZLUOXC", parsed.first().id)
    }

    @Test
    fun corruptDataStoreFile_recoversCleanlyToDefaults() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val datastoreFile = File(context.filesDir, "datastore/station_preferences.preferences_pb")
        datastoreFile.parentFile?.mkdirs()
        datastoreFile.writeBytes(byteArrayOf(0x01, 0x02, 0x03, 0x7F, -1, -2))

        val ds = StationPreferencesDataStore(context)
        val recents = ds.recentSearchedStationsFlow.first()
        assertTrue(recents.isEmpty())
    }
}
