package com.androidfung.departureboard.data.repository

import com.androidfung.departureboard.data.db.AppDatabase
import com.androidfung.departureboard.data.db.StationCrsDao

/**
 * Facade for National Rail 3-letter CRS code resolution, delegating to the Room-backed
 * [StationCrsDao] and [StationCodeDataSource].
 */
object NationalRailStationCodes {

    @Volatile
    private var dataSource: StationCodeDataSource? = null

    fun initialize(context: android.content.Context) {
        if (dataSource == null) {
            synchronized(this) {
                if (dataSource == null) {
                    val db = AppDatabase.getInstance(context.applicationContext)
                    dataSource = StationCodeDataSource(db.stationCrsDao())
                }
            }
        }
    }

    fun setDao(dao: StationCrsDao) {
        dataSource = StationCodeDataSource(dao)
    }

    /**
     * Resolves the list of 3-letter CRS codes for a station given its ID and name.
     */
    suspend fun findCrsCodes(stationId: String, stationName: String): List<String> {
        // Direct 3-letter uppercase CRS check
        if (stationId.length == 3 && stationId.all { it.isLetter() }) {
            return listOf(stationId.uppercase())
        }

        val ds = dataSource ?: return emptyList()
        return ds.findCrsCodes(stationId, stationName)
    }

    suspend fun findCrs(stationId: String, stationName: String): String? {
        return findCrsCodes(stationId, stationName).firstOrNull()
    }
}
