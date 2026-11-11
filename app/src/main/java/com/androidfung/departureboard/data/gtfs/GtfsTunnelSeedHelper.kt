package com.androidfung.departureboard.data.gtfs

/**
 * Seeds essential underground metro / rapid-transit static GTFS entries into [GtfsDao] when
 * the local GTFS database is empty so "Underground Tunnel Mode" works out of the box when
 * cellular connectivity drops on deep-level platforms.
 */
object GtfsTunnelSeedHelper {

    @Volatile
    private var seededInSession = false

    suspend fun ensureCoreMetroSeeded(dao: GtfsDao) {
        if (seededInSession) return
        seededInSession = true

        // Skip seeding if GTFS feeds have already been imported or seeded
        if (dao.getAllCalendars().isNotEmpty()) return

        val calendars = listOf(
            GtfsCalendarEntity(
                serviceId = "TUNNEL_DAILY",
                monday = 1,
                tuesday = 1,
                wednesday = 1,
                thursday = 1,
                friday = 1,
                saturday = 1,
                sunday = 1,
                startDate = "20240101",
                endDate = "20301231"
            )
        )

        val stops = listOf(
            GtfsStopEntity(stopId = "940GZZLUOXC", stopName = "Oxford Circus", stopLat = 51.5152, stopLon = -0.1419, locationType = 0, platformCode = "3"),
            GtfsStopEntity(stopId = "940GZZLUKSX", stopName = "King's Cross St. Pancras", stopLat = 51.5308, stopLon = -0.1238, locationType = 0, platformCode = "1"),
            GtfsStopEntity(stopId = "940GZZLUVIC", stopName = "Victoria", stopLat = 51.4965, stopLon = -0.1447, locationType = 0, platformCode = "4"),
            GtfsStopEntity(stopId = "940GZZLUWLO", stopName = "Waterloo", stopLat = 51.5036, stopLon = -0.1143, locationType = 0, platformCode = "2"),
            GtfsStopEntity(stopId = "940GZZLUPAC", stopName = "Paddington", stopLat = 51.5154, stopLon = -0.1755, locationType = 0, platformCode = "A"),
            GtfsStopEntity(stopId = "940GZZLULVT", stopName = "Liverpool Street", stopLat = 51.5178, stopLon = -0.0823, locationType = 0, platformCode = "B"),
            GtfsStopEntity(stopId = "HK_MTR_CEN", stopName = "Central", stopLat = 22.2819, stopLon = 114.1577, locationType = 0, platformCode = "1"),
            GtfsStopEntity(stopId = "HK_MTR_ADM", stopName = "Admiralty", stopLat = 22.2790, stopLon = 114.1655, locationType = 0, platformCode = "2"),
            GtfsStopEntity(stopId = "HK_MTR_TST", stopName = "Tsim Sha Tsui", stopLat = 22.2976, stopLon = 114.1722, locationType = 0, platformCode = "1"),
            GtfsStopEntity(stopId = "HK_MTR_MOK", stopName = "Mong Kok", stopLat = 22.3193, stopLon = 114.1694, locationType = 0, platformCode = "1")
        )

        val routes = listOf(
            GtfsRouteEntity(routeId = "victoria", agencyId = "TfL", routeShortName = "VIC", routeLongName = "Victoria", routeType = 1, routeColor = "0098D4", routeTextColor = "FFFFFF"),
            GtfsRouteEntity(routeId = "northern", agencyId = "TfL", routeShortName = "NOR", routeLongName = "Northern", routeType = 1, routeColor = "000000", routeTextColor = "FFFFFF"),
            GtfsRouteEntity(routeId = "elizabeth", agencyId = "TfL", routeShortName = "ELZ", routeLongName = "Elizabeth line", routeType = 2, routeColor = "6950A1", routeTextColor = "FFFFFF"),
            GtfsRouteEntity(routeId = "TWL", agencyId = "MTR", routeShortName = "TWL", routeLongName = "Tsuen Wan Line", routeType = 1, routeColor = "E2231A", routeTextColor = "FFFFFF")
        )

        val trips = mutableListOf<GtfsTripEntity>()
        val stopTimes = mutableListOf<GtfsStopTimeEntity>()

        // Seed 6-minute rapid-transit cadence from 05:30 (19800s) to 00:30 (88200s) for core underground corridors
        var departureSec = 19_800
        var idx = 0
        while (departureSec <= 88_200) {
            val vicTripId = "TUNNEL_VIC_$idx"
            trips.add(
                GtfsTripEntity(
                    tripId = vicTripId,
                    routeId = "victoria",
                    serviceId = "TUNNEL_DAILY",
                    tripHeadsign = "Walthamstow Central",
                    directionId = 0,
                    wheelchairAccessible = 1
                )
            )
            stopTimes.add(GtfsStopTimeEntity(tripId = vicTripId, stopId = "940GZZLUVIC", stopSequence = 1, arrivalTimeSeconds = departureSec, departureTimeSeconds = departureSec))
            stopTimes.add(GtfsStopTimeEntity(tripId = vicTripId, stopId = "940GZZLUOXC", stopSequence = 2, arrivalTimeSeconds = departureSec + 240, departureTimeSeconds = departureSec + 240))
            stopTimes.add(GtfsStopTimeEntity(tripId = vicTripId, stopId = "940GZZLUKSX", stopSequence = 3, arrivalTimeSeconds = departureSec + 540, departureTimeSeconds = departureSec + 540))

            val elzTripId = "TUNNEL_ELZ_$idx"
            trips.add(
                GtfsTripEntity(
                    tripId = elzTripId,
                    routeId = "elizabeth",
                    serviceId = "TUNNEL_DAILY",
                    tripHeadsign = "Shenfield",
                    directionId = 0,
                    wheelchairAccessible = 1
                )
            )
            stopTimes.add(GtfsStopTimeEntity(tripId = elzTripId, stopId = "940GZZLUPAC", stopSequence = 1, arrivalTimeSeconds = departureSec + 60, departureTimeSeconds = departureSec + 60))
            stopTimes.add(GtfsStopTimeEntity(tripId = elzTripId, stopId = "940GZZLULVT", stopSequence = 2, arrivalTimeSeconds = departureSec + 660, departureTimeSeconds = departureSec + 660))

            val norTripId = "TUNNEL_NOR_$idx"
            trips.add(
                GtfsTripEntity(
                    tripId = norTripId,
                    routeId = "northern",
                    serviceId = "TUNNEL_DAILY",
                    tripHeadsign = "High Barnet",
                    directionId = 0,
                    wheelchairAccessible = 1
                )
            )
            stopTimes.add(GtfsStopTimeEntity(tripId = norTripId, stopId = "940GZZLUWLO", stopSequence = 1, arrivalTimeSeconds = departureSec + 120, departureTimeSeconds = departureSec + 120))
            stopTimes.add(GtfsStopTimeEntity(tripId = norTripId, stopId = "940GZZLUKSX", stopSequence = 2, arrivalTimeSeconds = departureSec + 780, departureTimeSeconds = departureSec + 780))

            val mtrTripId = "TUNNEL_TWL_$idx"
            trips.add(
                GtfsTripEntity(
                    tripId = mtrTripId,
                    routeId = "TWL",
                    serviceId = "TUNNEL_DAILY",
                    tripHeadsign = "Tsuen Wan",
                    directionId = 0,
                    wheelchairAccessible = 1
                )
            )
            stopTimes.add(GtfsStopTimeEntity(tripId = mtrTripId, stopId = "HK_MTR_CEN", stopSequence = 1, arrivalTimeSeconds = departureSec, departureTimeSeconds = departureSec))
            stopTimes.add(GtfsStopTimeEntity(tripId = mtrTripId, stopId = "HK_MTR_ADM", stopSequence = 2, arrivalTimeSeconds = departureSec + 120, departureTimeSeconds = departureSec + 120))
            stopTimes.add(GtfsStopTimeEntity(tripId = mtrTripId, stopId = "HK_MTR_TST", stopSequence = 3, arrivalTimeSeconds = departureSec + 300, departureTimeSeconds = departureSec + 300))
            stopTimes.add(GtfsStopTimeEntity(tripId = mtrTripId, stopId = "HK_MTR_MOK", stopSequence = 4, arrivalTimeSeconds = departureSec + 600, departureTimeSeconds = departureSec + 600))

            departureSec += 360
            idx++
        }

        dao.insertCalendars(calendars)
        dao.insertStops(stops)
        dao.insertRoutes(routes)
        dao.insertTrips(trips)
        dao.insertStopTimes(stopTimes)
    }
}
