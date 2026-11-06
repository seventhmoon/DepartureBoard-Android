package com.androidfung.departureboard.data.gtfs

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for static GTFS schedules, routes, stops, and calling points.
 * Supports offline timetable lookups.
 */
@Database(
    entities = [
        GtfsStopEntity::class,
        GtfsRouteEntity::class,
        GtfsTripEntity::class,
        GtfsStopTimeEntity::class,
        GtfsCalendarEntity::class,
        GtfsCalendarDateEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class GtfsDatabase : RoomDatabase() {

    abstract fun gtfsDao(): GtfsDao

    companion object {
        private const val DB_NAME = "gtfs_schedule.db"

        @Volatile
        private var instance: GtfsDatabase? = null

        fun getInstance(context: Context): GtfsDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    GtfsDatabase::class.java,
                    DB_NAME
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
