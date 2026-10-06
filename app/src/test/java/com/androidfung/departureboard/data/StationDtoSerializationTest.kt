package com.androidfung.departureboard.data

import com.androidfung.departureboard.data.datastore.StationDto
import com.androidfung.departureboard.data.datastore.StationLineDto
import com.androidfung.departureboard.data.datastore.toDto
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.StationLineInfo
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the DataStore station JSON round-trip using the same Moshi configuration as
 * [com.androidfung.departureboard.data.datastore.StationPreferencesDataStore] (codegen
 * adapters, no reflective KotlinJsonAdapterFactory). Guards against R8/serialization
 * regressions in release builds.
 */
class StationDtoSerializationTest {

    private val moshi = Moshi.Builder().build()
    private val adapter = moshi.adapter<List<StationDto>>(
        Types.newParameterizedType(List::class.java, StationDto::class.java)
    )

    private val sampleStations = listOf(
        Station(
            id = "940GZZLUOXC",
            name = "Oxford Circus",
            modes = listOf("tube"),
            zone = "1",
            lat = 51.5152,
            lon = -0.1419,
            isFavorite = true,
            lines = listOf(
                StationLineInfo("bakerloo", "Bakerloo", "tube"),
                StationLineInfo("central", "Central", "tube")
            )
        ),
        Station(
            id = "910GKNGX",
            name = "King's Cross",
            modes = listOf("national-rail"),
            lat = 51.5316,
            lon = -0.1235,
            lines = emptyList()
        )
    )

    @Test
    fun `station list round-trips through JSON with full fidelity`() {
        val json = adapter.toJson(sampleStations.map { it.toDto() })

        val restored = adapter.fromJson(json)
        assertNotNull(restored)
        assertEquals(2, restored!!.size)

        val first = restored[0].toStation()
        assertEquals("940GZZLUOXC", first.id)
        assertEquals("Oxford Circus", first.name)
        assertEquals(listOf("tube"), first.modes)
        assertEquals("1", first.zone)
        assertEquals(51.5152, first.lat!!, 1e-9)
        assertEquals(-0.1419, first.lon!!, 1e-9)
        assertTrue(first.isFavorite)
        assertEquals(2, first.lines.size)
        assertEquals("bakerloo", first.lines[0].id)
        assertEquals("Bakerloo", first.lines[0].name)
        assertEquals("tube", first.lines[0].mode)

        val second = restored[1].toStation()
        assertEquals("King's Cross", second.name)
        assertEquals(listOf("national-rail"), second.modes)
        assertNull(second.zone)
        assertTrue(second.lines.isEmpty())
    }

    @Test
    fun `empty list round-trips as empty list not null`() {
        val json = adapter.toJson(emptyList<StationDto>())
        val restored = adapter.fromJson(json)
        assertNotNull(restored)
        assertTrue(restored!!.isEmpty())
    }

    @Test
    fun `literal null json deserializes to null so callers can apply defaults`() {
        assertNull(adapter.fromJson("null"))
    }

    @Test
    fun `dto fields survive independently`() {
        val dto = StationDto(
            id = "ID1",
            name = "Name",
            modes = listOf("bus"),
            zone = "2",
            lat = 1.0,
            lon = 2.0,
            isFavorite = false,
            lines = listOf(StationLineDto("l1", "Line One", "dlr"))
        )
        val singleAdapter = moshi.adapter(StationDto::class.java)
        val restored = singleAdapter.fromJson(singleAdapter.toJson(dto))!!
        assertEquals(dto, restored)
    }
}
