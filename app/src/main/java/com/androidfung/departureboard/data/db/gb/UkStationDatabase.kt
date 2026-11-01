package com.androidfung.departureboard.data.db.gb

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Dedicated Room database for United Kingdom / National Rail station CRS mappings.
 * Pre-packaged from assets/station_gb.db.
 */
@Database(
    entities = [
        StationCrsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class UkStationDatabase : RoomDatabase() {

    abstract fun stationCrsDao(): StationCrsDao

    companion object {
        @Volatile
        private var instance: UkStationDatabase? = null

        fun getInstance(context: Context): UkStationDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    UkStationDatabase::class.java,
                    "station_gb.db"
                )
                    .createFromAsset("station_gb.db")
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
