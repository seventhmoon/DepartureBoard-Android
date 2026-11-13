package com.androidfung.departureboard.data.gbfs

import com.androidfung.departureboard.data.model.TransitRegion
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GbfsV3FeedClientTest {

    @Test
    fun findNearbyHubs_parsesGbfsV3LocalizedNamesEbikesAndFiltersByDistance() = runTest {
        val discoveryJson = """
            {
              "version": "3.0",
              "data": {
                "feeds": [
                  {"name": "station_information", "url": "https://example.test/gbfs/station_information.json"},
                  {"name": "station_status", "url": "https://example.test/gbfs/station_status.json"},
                  {"name": "vehicle_types", "url": "https://example.test/gbfs/vehicle_types.json"}
                ]
              }
            }
        """.trimIndent()

        val vehicleTypesJson = """
            {
              "data": {
                "vehicle_types": [
                  {"vehicle_type_id": "bike_human", "form_factor": "bicycle", "propulsion_type": "human"},
                  {"vehicle_type_id": "ebike_1", "form_factor": "bicycle", "propulsion_type": "electric_assist"}
                ]
              }
            }
        """.trimIndent()

        val stationInfoJson = """
            {
              "data": {
                "stations": [
                  {
                    "station_id": "near_1",
                    "name": [{"text": "渋谷ハチ公前", "language": "ja"}, {"text": "Shibuya Hachiko Exit", "language": "en"}],
                    "lat": 35.6591,
                    "lon": 139.7006
                  },
                  {
                    "station_id": "closed_2",
                    "name": [{"text": "Closed Hub", "language": "en"}],
                    "lat": 35.6592,
                    "lon": 139.7008
                  },
                  {
                    "station_id": "far_3",
                    "name": "Far Station",
                    "lat": 35.6800,
                    "lon": 139.7500
                  }
                ]
              }
            }
        """.trimIndent()

        val stationStatusJson = """
            {
              "data": {
                "stations": [
                  {
                    "station_id": "near_1",
                    "num_vehicles_available": 10,
                    "num_docks_available": 4,
                    "is_renting": true,
                    "vehicle_types_available": [
                      {"vehicle_type_id": "bike_human", "count": 3},
                      {"vehicle_type_id": "ebike_1", "count": 7}
                    ]
                  },
                  {
                    "station_id": "closed_2",
                    "num_vehicles_available": 5,
                    "num_docks_available": 5,
                    "is_renting": false
                  },
                  {
                    "station_id": "far_3",
                    "num_vehicles_available": 8,
                    "num_docks_available": 2,
                    "is_renting": true
                  }
                ]
              }
            }
        """.trimIndent()

        val client = GbfsV3FeedClient { url ->
            when {
                url.contains("docomo-cycle-tokyo/gbfs.json") -> discoveryJson
                url.endsWith("station_information.json") -> stationInfoJson
                url.endsWith("station_status.json") -> stationStatusJson
                url.endsWith("vehicle_types.json") -> vehicleTypesJson
                else -> null
            }
        }

        val hubs = client.findNearbyHubs(
            region = TransitRegion.JP,
            lat = 35.6580,
            lon = 139.7016,
            radiusMeters = 500
        )

        assertEquals(1, hubs.size)
        val hub = hubs.first()
        assertEquals("Shibuya Hachiko Exit", hub.name)
        assertEquals(3, hub.totalBikesAvailable)
        assertEquals(7, hub.totalEbikesAvailable)
        assertEquals(4, hub.totalDocksAvailable)
        assertTrue((hub.distanceMeters ?: 999) < 500)
    }
}
