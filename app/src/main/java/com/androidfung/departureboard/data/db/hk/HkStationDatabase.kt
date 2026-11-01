package com.androidfung.departureboard.data.db.hk

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Dedicated Room database for Hong Kong transit stations and bus stops.
 * Pre-populated from assets/station_hk.db, loaded only when Hong Kong region is active.
 */
@Database(
    entities = [
        HkBusStopEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class HkStationDatabase : RoomDatabase() {

    abstract fun hkBusStopDao(): HkBusStopDao

    companion object {
        @Volatile
        private var instance: HkStationDatabase? = null

        fun getInstance(context: Context): HkStationDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    HkStationDatabase::class.java,
                    "station_hk.db"
                )
                    .createFromAsset("station_hk.db")
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
