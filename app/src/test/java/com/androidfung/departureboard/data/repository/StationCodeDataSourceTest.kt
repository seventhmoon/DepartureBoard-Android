package com.androidfung.departureboard.data.repository

import com.androidfung.departureboard.data.db.StationCrsDao
import com.androidfung.departureboard.data.db.StationCrsEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StationCodeDataSourceTest {

    private class FakeStationCrsDao : StationCrsDao {
        override suspend fun getCrsCodesForStationId(stationId: String): List<String> {
            return when (stationId.uppercase()) {
                "HUBPAD" -> listOf("PAD")
                "HUBBFR" -> listOf("BFR")
                "910GKNGX" -> listOf("KGX")
                "910GSUTTON" -> listOf("SUO")
                "KGX" -> listOf("KGX")
                "SUO" -> listOf("SUO")
                "SUT" -> listOf("SUT")
                else -> emptyList()
            }
        }

        override suspend fun getCrsCodesForExactName(normalizedName: String): List<String> {
            return when (normalizedName.lowercase()) {
                "canary wharf" -> listOf("CWX")
                "sutton (london)" -> listOf("SUO")
                "sutton" -> listOf("SUO")
                else -> emptyList()
            }
        }

        override suspend fun getCrsCodesForStationName(name: String): List<String> = emptyList()

        override suspend fun getCrsCodesForNameKeyword(keyword: String): List<String> {
            // Emulate fuzzy search containing "bank"
            return if (keyword.lowercase().contains("bank")) {
                listOf("WBQ", "CYK")
            } else emptyList()
        }

        override suspend fun getStationsByNameKeyword(keyword: String): List<StationCrsEntity> = emptyList()
        override suspend fun getStationByCrsCode(crsCode: String): StationCrsEntity? = null
        override suspend fun getStationByStationId(stationId: String): StationCrsEntity? = null
        override suspend fun insertAll(mappings: List<StationCrsEntity>) {}
        override suspend fun getCount(): Int = 0
    }

    private val fakeDao = FakeStationCrsDao()
    private val dataSource: StationCodeDataSource = StationCodeDataSourceImpl(fakeDao)

    @Test
    fun testBankStationDoesNotMatchNationalRail() = runTest {
        // Bank station as HUBBAN or 940GZZLUBNK must NOT resolve to National Rail stations like WBQ
        val crsListHub = dataSource.findCrsCodes("HUBBAN", "Bank")
        assertTrue("Bank HUB ID must not resolve to National Rail", crsListHub.isEmpty())

        val crsList940 = dataSource.findCrsCodes("940GZZLUBNK", "Bank Underground Station")
        assertTrue("Bank 940 ID must not resolve to National Rail", crsList940.isEmpty())
    }

    @Test
    fun testValidHubInterchangeResolvesById() = runTest {
        val crsList = dataSource.findCrsCodes("HUBPAD", "Paddington")
        assertEquals(listOf("PAD"), crsList)
    }

    @Test
    fun testDirectCrsCodeResolves() = runTest {
        val crsList = dataSource.findCrsCodes("KGX", "King's Cross")
        assertEquals(listOf("KGX"), crsList)
    }

    @Test
    fun testEmbedded910GResolves() = runTest {
        val crsList = dataSource.findCrsCodes("910GKGX", "King's Cross")
        assertEquals(listOf("KGX"), crsList)
    }

    @Test
    fun test910GStationNameDoesNotSliceIntoUnrelatedCrs() = runTest {
        // "910GSUTTON" has length 9. Slicing index 4..7 yielded "SUT" (Sutton Coldfield in Birmingham).
        // It must NOT resolve to SUT. Instead, it must resolve via exact mapping or name to SUO (Sutton London).
        val crsList = dataSource.findCrsCodes("910GSUTTON", "Sutton (London)")
        assertEquals(listOf("SUO"), crsList)
    }
}
