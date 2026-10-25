package com.androidfung.departureboard.data.repository.search

import com.androidfung.departureboard.data.model.DefaultStations
import com.androidfung.departureboard.data.model.TflSearchResponse
import com.androidfung.departureboard.data.model.TflStopPointMatch
import com.androidfung.departureboard.test.FakeBodsApiService
import com.androidfung.departureboard.test.FakeTflApiService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StationSearchEngineTest {

    @Test
    fun testSearchStations_blankReturnsPopularStations() = runTest {
        val engine = StationSearchEngine(
            apiService = FakeTflApiService(),
            bodsApiService = FakeBodsApiService()
        )

        val result = engine.searchStations("")
        assertTrue(result.isSuccess)
        assertEquals(DefaultStations.POPULAR_STATIONS, result.getOrNull())
    }

    @Test
    fun testSearchStations_tflApiMatch() = runTest {
        val fakeApi = FakeTflApiService(
            searchResult = TflSearchResponse(
                total = 1,
                matches = listOf(
                    TflStopPointMatch(
                        id = "940GZZLUOXC",
                        name = "Oxford Circus Underground Station",
                        modes = listOf("tube"),
                        zone = "1",
                        lat = 51.5152,
                        lon = -0.1419
                    )
                )
            )
        )

        val engine = StationSearchEngine(
            apiService = fakeApi,
            bodsApiService = FakeBodsApiService()
        )

        val result = engine.searchStations("Oxford")
        assertTrue(result.isSuccess)
        val stations = result.getOrNull()!!
        assertEquals(1, stations.size)
        assertEquals("Oxford Circus", stations[0].name)
    }

    @Test
    fun testSearchStations_rankingPutsExactMatchFirst() = runTest {
        val fakeApi = FakeTflApiService(
            searchResult = TflSearchResponse(
                total = 4,
                matches = listOf(
                    TflStopPointMatch(
                        id = "490003260A",
                        name = "Bank Station (Stop A)",
                        modes = listOf("bus"),
                        zone = "1",
                        lat = 51.5134,
                        lon = -0.0888
                    ),
                    TflStopPointMatch(
                        id = "490001234B",
                        name = "Redbank Road",
                        modes = listOf("bus"),
                        zone = "3",
                        lat = 51.5200,
                        lon = -0.0900
                    ),
                    TflStopPointMatch(
                        id = "940GZZLUBNK",
                        name = "Bank Underground Station",
                        modes = listOf("tube", "dlr"),
                        zone = "1",
                        lat = 51.5133,
                        lon = -0.0890
                    ),
                    TflStopPointMatch(
                        id = "490005678C",
                        name = "South Bank Business Centre",
                        modes = listOf("bus"),
                        zone = "1",
                        lat = 51.5050,
                        lon = -0.1100
                    )
                )
            )
        )

        val engine = StationSearchEngine(
            apiService = fakeApi,
            bodsApiService = FakeBodsApiService()
        )

        val result = engine.searchStations("Bank")
        assertTrue(result.isSuccess)
        val stations = result.getOrNull()!!
        assertTrue(stations.isNotEmpty())
        // "Bank" exact match should be ranked #1
        assertEquals("Bank", stations[0].name)
        assertEquals("940GZZLUBNK", stations[0].id)
    }

    @Test
    fun testSearchStations_errorFallsBackToPopularMatches() = runTest {
        val fakeApi = FakeTflApiService(shouldThrow = true)
        val engine = StationSearchEngine(
            apiService = fakeApi,
            bodsApiService = FakeBodsApiService()
        )

        val result = engine.searchStations("Oxford")
        assertTrue(result.isSuccess)
        val stations = result.getOrNull()!!
        assertTrue(stations.any { it.name == "Oxford Circus" })
    }

    @Test
    fun testSearchStations_deduplicatesTflNationalRailAgainstLocalRail() = runTest {
        // When TfL returns a National Rail stop (e.g. Sutton / City Thameslink with id "910G..."),
        // StationSearchEngine should prioritize the TfL station and not produce a duplicate entry.
        val fakeApi = FakeTflApiService(
            searchResult = TflSearchResponse(
                total = 1,
                matches = listOf(
                    TflStopPointMatch(
                        id = "910GSUTTON",
                        name = "Sutton (London) Rail Station",
                        modes = listOf("national-rail"),
                        zone = "5",
                        lat = 51.3595,
                        lon = -0.1912
                    )
                )
            )
        )

        val fakeDao = object : com.androidfung.departureboard.data.db.StationCrsDao {
            override suspend fun getCrsCodesForStationId(stationId: String): List<String> {
                return if (stationId == "910GSUTTON") listOf("SUO") else emptyList()
            }
            override suspend fun getCrsCodesForExactName(normalizedName: String): List<String> = emptyList()
            override suspend fun getCrsCodesForStationName(name: String): List<String> = emptyList()
            override suspend fun getCrsCodesForNameKeyword(keyword: String): List<String> = emptyList()
            override suspend fun getStationsByNameKeyword(keyword: String): List<com.androidfung.departureboard.data.db.StationCrsEntity> {
                return if ("sutton" in keyword) {
                    listOf(
                        com.androidfung.departureboard.data.db.StationCrsEntity(
                            stationId = "SUO",
                            crsCode = "SUO",
                            stationName = "Sutton (London)",
                            normalizedName = "sutton (london)",
                            modes = "national-rail"
                        )
                    )
                } else emptyList()
            }
            override suspend fun getStationByCrsCode(crsCode: String): com.androidfung.departureboard.data.db.StationCrsEntity? = null
            override suspend fun getStationByStationId(stationId: String): com.androidfung.departureboard.data.db.StationCrsEntity? = null
            override suspend fun insertAll(mappings: List<com.androidfung.departureboard.data.db.StationCrsEntity>) {}
            override suspend fun getCount(): Int = 1
        }

        val engine = StationSearchEngine(
            apiService = fakeApi,
            bodsApiService = FakeBodsApiService(),
            stationCrsDao = fakeDao
        )

        val result = engine.searchStations("Sutton")
        assertTrue(result.isSuccess)
        val stations = result.getOrNull()!!
        val suttonStations = stations.filter { it.name == "Sutton (London)" }
        assertEquals(1, suttonStations.size)
        assertEquals("910GSUTTON", suttonStations.first().id)
        assertEquals("5", suttonStations.first().zone)
    }

    @Test
    fun testSearchStations_deduplicatesViaNormalizedNameFallbackWhenIdNotMapped() = runTest {
        // If TfL returns a National Rail station whose specific ID is not yet in the DB mappings,
        // it should successfully fall back to matching by exact normalized name.
        val fakeApi = FakeTflApiService(
            searchResult = TflSearchResponse(
                total = 1,
                matches = listOf(
                    TflStopPointMatch(
                        id = "910GMLHB",
                        name = "Mill Hill Broadway Rail Station",
                        modes = listOf("national-rail"),
                        zone = "4",
                        lat = 51.6130,
                        lon = -0.2492
                    )
                )
            )
        )

        val fakeDao = object : com.androidfung.departureboard.data.db.StationCrsDao {
            override suspend fun getCrsCodesForStationId(stationId: String): List<String> = emptyList() // ID lookup fails
            override suspend fun getCrsCodesForExactName(normalizedName: String): List<String> {
                return if (normalizedName == "mill hill broadway") listOf("MIL") else emptyList()
            }
            override suspend fun getCrsCodesForStationName(name: String): List<String> = emptyList()
            override suspend fun getCrsCodesForNameKeyword(keyword: String): List<String> = emptyList()
            override suspend fun getStationsByNameKeyword(keyword: String): List<com.androidfung.departureboard.data.db.StationCrsEntity> {
                return if ("mill hill" in keyword) {
                    listOf(
                        com.androidfung.departureboard.data.db.StationCrsEntity(
                            stationId = "MIL",
                            crsCode = "MIL",
                            stationName = "Mill Hill Broadway",
                            normalizedName = "mill hill broadway",
                            modes = "national-rail"
                        )
                    )
                } else emptyList()
            }
            override suspend fun getStationByCrsCode(crsCode: String): com.androidfung.departureboard.data.db.StationCrsEntity? = null
            override suspend fun getStationByStationId(stationId: String): com.androidfung.departureboard.data.db.StationCrsEntity? = null
            override suspend fun insertAll(mappings: List<com.androidfung.departureboard.data.db.StationCrsEntity>) {}
            override suspend fun getCount(): Int = 1
        }

        val engine = StationSearchEngine(
            apiService = fakeApi,
            bodsApiService = FakeBodsApiService(),
            stationCrsDao = fakeDao
        )

        val result = engine.searchStations("Mill Hill Broadway")
        assertTrue(result.isSuccess)
        val stations = result.getOrNull()!!
        val millHillStations = stations.filter { it.name == "Mill Hill Broadway" }
        assertEquals(1, millHillStations.size)
        assertEquals("910GMLHB", millHillStations.first().id)
        assertEquals("4", millHillStations.first().zone)
    }
}
