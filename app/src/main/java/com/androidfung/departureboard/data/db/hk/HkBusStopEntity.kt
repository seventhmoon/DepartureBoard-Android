package com.androidfung.departureboard.data.db.hk

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.StationLineInfo
import com.androidfung.departureboard.data.model.hk.HkBusStopDirectory

/**
 * Pre-populated indexed Hong Kong bus stop entity.
 */
@Entity(
    tableName = "hk_bus_stops",
    indices = [
        Index(value = ["nameEn"]),
        Index(value = ["nameTc"])
    ]
)
data class HkBusStopEntity(
    @PrimaryKey
    val stopId: String,
    val nameEn: String,
    val nameTc: String,
    val lat: Double,
    val lon: Double
) {
    fun toEntry(): HkBusStopDirectory.HkBusStopEntry = HkBusStopDirectory.HkBusStopEntry(
        stopId = stopId,
        nameEn = nameEn,
        nameTc = nameTc,
        lat = lat,
        lon = lon
    )

    fun toStation(): Station = Station(
        id = "HK_BUS_$stopId",
        name = if (nameTc.isNotBlank()) "$nameEn ($nameTc)" else nameEn,
        modes = listOf("bus"),
        lines = listOf(StationLineInfo("BUS", "KMB / LWB"))
    )
}
