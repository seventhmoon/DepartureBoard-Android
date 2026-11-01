package com.androidfung.departureboard.data.db.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Dynamic runtime cache database for departures across all regions.
 * Fully managed by Room in the app sandbox (no pre-packaged assets).
 */
@Database(
    entities = [
        CachedDepartureEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class DepartureCacheDatabase : RoomDatabase() {

    abstract fun departureDao(): DepartureDao

    companion object {
        @Volatile
        private var instance: DepartureCacheDatabase? = null

        fun getInstance(context: Context): DepartureCacheDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DepartureCacheDatabase::class.java,
                    "departure_cache.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
