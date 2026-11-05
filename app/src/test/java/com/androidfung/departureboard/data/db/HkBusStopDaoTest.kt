package com.androidfung.departureboard.data.db

import com.androidfung.departureboard.data.db.hk.HkBusStopDao
import com.androidfung.departureboard.data.db.hk.HkBusStopEntity
import com.androidfung.departureboard.data.model.hk.HkBusStopDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HkBusStopDaoTest {

    @Test
    fun testFakeHkBusStopDao() {
        val fakeDao = object : HkBusStopDao {
            private val list = listOf(
                HkBusStopEntity("7613538D58A9C452", "HAU TAK ESTATE (TK215)", "厚德邨 (TK215)", 22.317, 114.262),
                HkBusStopEntity("1B1A7253CC1D3148", "HAU TAK LANE", "厚德里", 22.318, 114.263),
                HkBusStopEntity("OTHER001", "SHA TIN CENTRAL", "沙田市中心", 22.381, 114.188)
            )

            override suspend fun searchStops(query: String, limit: Int): List<HkBusStopEntity> {
                val q = query.lowercase()
                return list.filter { it.nameEn.lowercase().contains(q) || it.nameTc.lowercase().contains(q) }.take(limit)
            }

            override suspend fun getStopById(stopId: String): HkBusStopEntity? {
                return list.firstOrNull { it.stopId == stopId }
            }

            override suspend fun getStopCount(): Int {
                return list.size
            }
        }

        HkBusStopDirectory.setDao(fakeDao)

        kotlinx.coroutines.runBlocking {
            assertEquals(3, fakeDao.getStopCount())

            val enResults = fakeDao.searchStops("hau tak")
            assertEquals(2, enResults.size)
            assertTrue(enResults.any { it.stopId == "7613538D58A9C452" })
            assertTrue(enResults.any { it.stopId == "1B1A7253CC1D3148" })

            val tcResults = fakeDao.searchStops("厚德")
            assertEquals(2, tcResults.size)

            val stop = fakeDao.getStopById("7613538D58A9C452")
            assertNotNull(stop)
            assertEquals("HAU TAK ESTATE (TK215)", stop?.nameEn)
        }
    }
}
