package com.androidfung.departureboard.data.model

import android.content.Context
import com.androidfung.departureboard.data.db.hk.HkBusStopDao
import com.androidfung.departureboard.data.db.hk.HkStationDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SQLite / Room backed directory of Hong Kong franchised bus stops with fast indexed bilingual search.
 */
object HkBusStopDirectory {

    data class HkBusStopEntry(
        val stopId: String,
        val nameEn: String,
        val nameTc: String,
        val lat: Double,
        val lon: Double
    ) {
        val displayName: String
            get() = when {
                nameEn.isNotBlank() && nameTc.isNotBlank() -> "$nameEn ($nameTc)"
                nameTc.isNotBlank() -> nameTc
                nameEn.isNotBlank() -> nameEn
                else -> "Bus Stop"
            }

        fun toStation(): Station = Station(
            id = "HK_BUS_KMB_$stopId",
            name = displayName,
            modes = listOf(TransitMode.BUS.id),
            zone = null,
            lat = lat.takeIf { it != 0.0 },
            lon = lon.takeIf { it != 0.0 },
            isFavorite = false,
            lines = listOf(StationLineInfo("KMB", "KMB / LWB", "bus"))
        )
    }

    @Volatile
    private var cachedStops: List<HkBusStopEntry>? = null

    @Volatile
    private var hkBusStopDao: HkBusStopDao? = null

    fun setDao(dao: HkBusStopDao) {
        hkBusStopDao = dao
    }

    private fun getDao(context: Context): HkBusStopDao {
        return hkBusStopDao ?: synchronized(this) {
            hkBusStopDao ?: HkStationDatabase.getInstance(context.applicationContext)
                .hkBusStopDao()
                .also { hkBusStopDao = it }
        }
    }

    /**
     * Searches bus stops matching the query by English name, Traditional Chinese name, or stop ID.
     * Prioritizes fast indexed SQLite query from Room, falling back to cached stops if provided (e.g. in unit tests).
     */
    suspend fun searchStops(context: Context, query: String, limit: Int = 30): List<Station> = withContext(Dispatchers.IO) {
        val trimmed = query.trim().lowercase()
        if (trimmed.isBlank()) return@withContext emptyList()

        val inMemory = cachedStops
        if (inMemory != null) {
            val matches = inMemory.filter { stop ->
                stop.nameEn.lowercase().contains(trimmed) ||
                        stop.nameTc.lowercase().contains(trimmed) ||
                        stop.stopId.equals(trimmed, ignoreCase = true)
            }
            return@withContext matches.take(limit).map { it.toStation() }
        }

        try {
            val dbResults = getDao(context).searchStops(query.trim(), limit)
            if (dbResults.isNotEmpty()) {
                return@withContext dbResults.map { it.toStation() }
            }
        } catch (_: Exception) {
            // DB fallback
        }

        emptyList()
    }

    /**
     * Looks up an individual bus stop entry by stop ID.
     */
    suspend fun findStopById(context: Context, stopId: String): HkBusStopEntry? = withContext(Dispatchers.IO) {
        val cleanStopId = extractRawHkBusStopId(stopId)

        val inMemory = cachedStops
        if (inMemory != null) {
            return@withContext inMemory.firstOrNull { it.stopId.equals(cleanStopId, ignoreCase = true) }
        }

        try {
            val dbResult = getDao(context).getStopById(cleanStopId)
            if (dbResult != null) {
                return@withContext dbResult.toEntry()
            }
        } catch (_: Exception) {
            // DB fallback
        }

        null
    }

    fun setStopsForTesting(stops: List<HkBusStopEntry>) {
        cachedStops = stops
    }
}
