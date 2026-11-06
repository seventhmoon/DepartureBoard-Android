package com.androidfung.departureboard.data.db

import com.androidfung.departureboard.data.db.gb.GbStationCrsDao
import com.androidfung.departureboard.data.db.gb.GbStationCrsEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GbStationCrsDaoTest {

    @Test
    fun testGbStationCrsDaoOperations() = runBlocking {
        val fakeDao = object : GbStationCrsDao {
            private val entities = mutableListOf(
                GbStationCrsEntity(1, "910GKNGX", "KGX", "Kings Cross", "kings cross", "national-rail"),
                GbStationCrsEntity(2, "910GSTPX", "STP", "St Pancras International", "st pancras international", "national-rail"),
                GbStationCrsEntity(3, "910GEUSTON", "EUS", "London Euston", "london euston", "national-rail")
            )

            override suspend fun getCrsCodesForStationId(stationId: String): List<String> =
                entities.filter { it.stationId.equals(stationId, ignoreCase = true) }.map { it.crsCode }

            override suspend fun getCrsCodesForExactName(normalizedName: String): List<String> =
                entities.filter { it.normalizedName.equals(normalizedName, ignoreCase = true) }.map { it.crsCode }

            override suspend fun getCrsCodesForStationName(name: String): List<String> =
                entities.filter { it.stationName.equals(name, ignoreCase = true) }.map { it.crsCode }

            override suspend fun getCrsCodesForNameKeyword(keyword: String): List<String> =
                entities.filter { it.normalizedName.contains(keyword, ignoreCase = true) }.map { it.crsCode }

            override suspend fun getStationsByNameKeyword(keyword: String): List<GbStationCrsEntity> =
                entities.filter { it.normalizedName.contains(keyword, ignoreCase = true) }.take(10)

            override suspend fun getStationByCrsCode(crsCode: String): GbStationCrsEntity? =
                entities.firstOrNull { it.crsCode.equals(crsCode, ignoreCase = true) }

            override suspend fun getStationByStationId(stationId: String): GbStationCrsEntity? =
                entities.firstOrNull {
                    it.stationId.equals(stationId, ignoreCase = true) || it.crsCode.equals(stationId, ignoreCase = true)
                }

            override suspend fun insertAll(mappings: List<GbStationCrsEntity>) {
                entities.addAll(mappings)
            }

            override suspend fun getCount(): Int = entities.size
        }

        assertEquals(3, fakeDao.getCount())

        val crsCodes = fakeDao.getCrsCodesForStationId("910GKNGX")
        assertEquals(listOf("KGX"), crsCodes)

        val byCrs = fakeDao.getStationByCrsCode("STP")
        assertNotNull(byCrs)
        assertEquals("St Pancras International", byCrs?.stationName)

        val keywordMatches = fakeDao.getStationsByNameKeyword("cross")
        assertEquals(1, keywordMatches.size)
        assertEquals("KGX", keywordMatches.first().crsCode)
    }
}
