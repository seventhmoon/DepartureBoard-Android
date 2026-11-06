package com.androidfung.departureboard.data.gtfs.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        GtfsStopEntity::class,
        GtfsRouteEntity::class,
        GtfsTripEntity::class,
        GtfsStopTimeEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class GtfsDatabase : RoomDatabase() {

    abstract fun gtfsDao(): GtfsDao

    companion object {
        @Volatile
        private var INSTANCE: GtfsDatabase? = null

        fun getInstance(context: Context): GtfsDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    GtfsDatabase::class.java,
                    "gtfs_schedule.db"
                ).fallbackToDestructiveMigration(dropAllTables = true).build().also { INSTANCE = it }
            }
        }

        fun createInMemory(context: Context): GtfsDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                GtfsDatabase::class.java
            ).allowMainThreadQueries().build()
        }
    }
}
