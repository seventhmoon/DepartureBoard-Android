package com.androidfung.departureboard.data.gtfs

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.model.CallingPoint
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.JourneyDestination
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.PlatformInfo
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.StopActivityType
import com.androidfung.departureboard.data.model.TrainInfo
import com.androidfung.departureboard.data.model.TransitDirection
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.model.TransitOperator
import com.androidfung.departureboard.data.model.WheelchairBoardingStatus
import com.androidfung.departureboard.data.topology.CallingPointsResolver
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Resolves static GTFS schedules, active service calendars, over-midnight trips, and calling points
 * from [GtfsDao] into domain [Station], [Departure], and [CallingPoint] models.
 */
class GtfsScheduleResolver(
    private val dao: GtfsDao
) : CallingPointsResolver {

    constructor(database: GtfsDatabase) : this(database.gtfsDao())

    /**
     * Resolves the set of active `service_id`s for a given [date], combining weekly `calendar.txt`
     * rules with `calendar_dates.txt` additions (`exceptionType == 1`) and removals (`exceptionType == 2`).
     */
    suspend fun getActiveServiceIds(date: LocalDate): Set<String> {
        val calendars = dao.getAllCalendars()
        val dateStr = GtfsTimeUtils.formatDateToGtfsDate(date)
        val exceptions = dao.getCalendarDatesForDate(dateStr)

        val activeIds = calendars
            .filter { GtfsTimeUtils.isCalendarActiveOnDate(it, date) }
            .map { it.serviceId }
            .toMutableSet()

        for (exception in exceptions) {
            when (exception.exceptionType) {
                1 -> activeIds.add(exception.serviceId)
                2 -> activeIds.remove(exception.serviceId)
            }
        }
        return activeIds
    }

    /**
     * Maps a [GtfsStopEntity] into a domain [Station].
     */
    fun mapStopToStation(stop: GtfsStopEntity, modes: List<String> = listOf(TransitMode.RAIL.id)): Station {
        return Station(
            id = stop.stopId,
            name = stop.stopName,
            modes = modes,
            zone = stop.zoneId,
            lat = stop.stopLat,
            lon = stop.stopLon,
            wheelchairStatus = WheelchairBoardingStatus.fromGtfsCode(stop.wheelchairBoarding)
        )
    }

    /**
     * Queries upcoming scheduled departures for [stopId] (including child platform stops) at [now],
     * accounting for both today's active services and yesterday's over-midnight services (`>= 24:00:00`).
     */
    suspend fun getScheduledDepartures(
        stopId: String,
        now: ZonedDateTime = ZonedDateTime.now(
            com.androidfung.departureboard.data.model.TransitRegion.fromStationId(stopId).timeZone
        ),
        limit: Int = 10
    ): List<Departure> {
        val parentStop = dao.getStopById(stopId)
        val childStops = dao.getChildStops(stopId)
        val allStopsById = buildMap {
            if (parentStop != null) put(parentStop.stopId, parentStop)
            childStops.forEach { put(it.stopId, it) }
        }
        val targetStopIds = allStopsById.keys.ifEmpty { setOf(stopId) }.toList()
        val stationName = parentStop?.stopName ?: childStops.firstOrNull()?.stopName ?: stopId

        val currentSecondsOfDay = now.toLocalTime().toSecondOfDay()
        val today = now.toLocalDate()
        val yesterday = today.minusDays(1)

        val allCalendars = dao.getAllCalendars()
        val todayExceptions = dao.getCalendarDatesForDate(GtfsTimeUtils.formatDateToGtfsDate(today))
        val yesterdayExceptions = dao.getCalendarDatesForDate(GtfsTimeUtils.formatDateToGtfsDate(yesterday))
        val hasCalendarData = allCalendars.isNotEmpty() || todayExceptions.isNotEmpty() || yesterdayExceptions.isNotEmpty()

        data class StopTimeWithOffset(val stopTime: GtfsStopTimeEntity, val effectiveNowSeconds: Int)

        val candidates = mutableListOf<StopTimeWithOffset>()
        if (hasCalendarData) {
            val todayServices = getActiveServiceIds(today)
            if (todayServices.isNotEmpty()) {
                val todayStopTimes = dao.getUpcomingStopTimesForServices(
                    stopIds = targetStopIds,
                    serviceIds = todayServices.toList(),
                    afterTimeSeconds = currentSecondsOfDay,
                    limit = limit
                )
                todayStopTimes.forEach { candidates.add(StopTimeWithOffset(it, currentSecondsOfDay)) }
            }

            // Over-midnight trips from yesterday's service day (e.g. 25:15:00 when it is 01:00:00 today)
            val yesterdayOverMidnightSeconds = currentSecondsOfDay + GtfsTimeUtils.SECONDS_PER_DAY
            val yesterdayServices = getActiveServiceIds(yesterday)
            if (yesterdayServices.isNotEmpty()) {
                val overMidnightStopTimes = dao.getUpcomingStopTimesForServices(
                    stopIds = targetStopIds,
                    serviceIds = yesterdayServices.toList(),
                    afterTimeSeconds = yesterdayOverMidnightSeconds,
                    limit = limit
                )
                overMidnightStopTimes.forEach { candidates.add(StopTimeWithOffset(it, yesterdayOverMidnightSeconds)) }
            }
        } else {
            for (targetId in targetStopIds) {
                dao.getUpcomingStopTimes(targetId, currentSecondsOfDay, limit).forEach {
                    candidates.add(StopTimeWithOffset(it, currentSecondsOfDay))
                }
            }
        }

        val sortedCandidates = candidates
            .sortedBy { it.stopTime.departureTimeSeconds - it.effectiveNowSeconds }
            .take(limit)

        val departures = mutableListOf<Departure>()
        for (candidate in sortedCandidates) {
            val st = candidate.stopTime
            val trip = dao.getTripById(st.tripId) ?: continue
            val route = dao.getRouteById(trip.routeId) ?: continue
            val stopEntity = allStopsById[st.stopId] ?: dao.getStopById(st.stopId)

            val tripStopTimes = dao.getCallingPointsForTrip(trip.tripId)
            // Skip if this is the final terminus and no boarding is possible
            if (tripStopTimes.isNotEmpty() && tripStopTimes.last().stopSequence == st.stopSequence && st.pickupType == 1) {
                continue
            }

            val callingPoints = mapTripStopTimesToCallingPoints(
                tripStopTimes = tripStopTimes,
                currentStopSequence = st.stopSequence,
                modeId = TransitMode.fromGtfsRouteType(route.routeType).id
            )

            val terminusName = callingPoints.lastOrNull()?.stationName
                ?: trip.tripHeadsign
                ?: route.routeLongName.ifBlank { route.routeShortName }
            val headsign = st.stopHeadsign?.ifBlank { null } ?: trip.tripHeadsign?.ifBlank { null } ?: terminusName

            val platformCode = stopEntity?.platformCode
            val platformInfo = platformCode?.takeIf { it.isNotBlank() }?.let {
                PlatformInfo(code = it, isConfirmed = true)
            }
            val platformDisplay = platformInfo?.formatDisplay() ?: ""

            val mode = TransitMode.fromGtfsRouteType(route.routeType)
            val bgColor = parseHexColor(route.routeColor, defaultColor = Color(0xFF1E3561))
            val txtColor = parseHexColor(route.routeTextColor, defaultColor = Color.White)
            val displayLineName = route.routeShortName.ifBlank { route.routeLongName }

            val badge = LineBadgeInfo(
                lineId = route.routeId,
                displayName = displayLineName,
                backgroundColor = bgColor,
                textColor = txtColor,
                mode = mode,
                lineCode = route.routeShortName
            )

            val secondsUntilDeparture = (st.departureTimeSeconds - candidate.effectiveNowSeconds).coerceAtLeast(0)
            val scheduledInstant = now.plusSeconds(secondsUntilDeparture.toLong()).toInstant()
            val stopActivity = resolveStopActivity(
                pickupType = st.pickupType,
                dropOffType = st.dropOffType
            )
            val wheelchairAccessible = when (trip.wheelchairAccessible) {
                1 -> true
                2 -> false
                else -> null
            }
            val directionLabel = when (trip.directionId) {
                0 -> TransitDirection.OUTBOUND.displayName
                1 -> TransitDirection.INBOUND.displayName
                else -> null
            }

            departures.add(
                Departure(
                    id = "${trip.tripId}_${st.stopSequence}",
                    stationId = stopId,
                    stationName = stationName,
                    lineId = route.routeId,
                    lineName = route.routeLongName.ifBlank { route.routeShortName },
                    platformName = platformDisplay,
                    destinationName = headsign,
                    towards = null,
                    direction = directionLabel,
                    timeToStationSeconds = secondsUntilDeparture,
                    expectedArrivalIso = scheduledInstant.toString(),
                    currentLocation = GtfsTimeUtils.formatSecondsToClockTime(st.departureTimeSeconds),
                    modeName = mode.id,
                    lineBadge = badge,
                    callingPoints = callingPoints,
                    status = DepartureStatus.fromString(null, secondsUntilDeparture),
                    destinationStationId = tripStopTimes.lastOrNull()?.stopId,
                    isWheelchairAccessible = wheelchairAccessible,
                    platform = platformInfo,
                    train = TrainInfo(
                        serviceId = trip.tripId,
                        operator = route.agencyId?.let { TransitOperator(id = it, name = it) },
                        isWheelchairAccessible = wheelchairAccessible
                    ),
                    scheduledDepartureInstant = scheduledInstant,
                    expectedDepartureInstant = scheduledInstant,
                    stopActivity = stopActivity,
                    wheelchairStatus = WheelchairBoardingStatus.fromGtfsCode(trip.wheelchairAccessible),
                    journeyDestination = JourneyDestination(
                        terminusName = terminusName,
                        tripHeadsign = headsign,
                        terminusStationId = tripStopTimes.lastOrNull()?.stopId
                    ),
                    dataFreshness = com.androidfung.departureboard.data.model.DataFreshness.OFFLINE_SCHEDULED
                )
            )
        }

        return departures
    }

    override fun canHandle(departure: Departure): Boolean {
        return departure.id.isNotBlank() && departure.train?.serviceId != null
    }

    override suspend fun resolveCallingPoints(departure: Departure): List<CallingPoint> {
        val rawTripId = departure.train?.serviceId ?: departure.id.substringBeforeLast("_")
        val tripStopTimes = dao.getCallingPointsForTrip(rawTripId)
        if (tripStopTimes.isEmpty()) return emptyList()

        val currentSeq = tripStopTimes.firstOrNull {
            it.stopId.equals(departure.stationId, ignoreCase = true)
        }?.stopSequence ?: tripStopTimes.first().stopSequence

        return mapTripStopTimesToCallingPoints(
            tripStopTimes = tripStopTimes,
            currentStopSequence = currentSeq,
            modeId = departure.modeName
        )
    }

    private suspend fun mapTripStopTimesToCallingPoints(
        tripStopTimes: List<GtfsStopTimeEntity>,
        currentStopSequence: Int,
        modeId: String
    ): List<CallingPoint> {
        val subsequent = tripStopTimes.filter { it.stopSequence >= currentStopSequence }
        if (subsequent.isEmpty()) return emptyList()
        val lastSeq = subsequent.last().stopSequence
        val distinctStopIds = subsequent.map { it.stopId }.distinct()
        val stopsById = dao.getStopsByIds(distinctStopIds).associateBy { it.stopId }

        return subsequent.map { st ->
            val stop = stopsById[st.stopId]
            val timeFormatted = GtfsTimeUtils.formatSecondsToClockTime(st.departureTimeSeconds)
            CallingPoint(
                stationName = stop?.stopName ?: st.stopId,
                scheduledTime = timeFormatted,
                estimatedTime = timeFormatted,
                isCurrentStation = st.stopSequence == currentStopSequence,
                isDestination = st.stopSequence == lastSeq,
                stationId = st.stopId,
                modes = listOf(modeId),
                stopActivity = resolveStopActivity(
                    pickupType = st.pickupType,
                    dropOffType = st.dropOffType
                )
            )
        }
    }

    companion object {
        fun resolveStopActivity(
            pickupType: Int,
            dropOffType: Int
        ): StopActivityType = when {
            pickupType == 1 && dropOffType == 1 -> StopActivityType.NOT_ALLOWED
            pickupType == 1 -> StopActivityType.ALIGHTING_ONLY
            dropOffType == 1 -> StopActivityType.BOARDING_ONLY
            pickupType == 2 || dropOffType == 2 -> StopActivityType.CALL_AGENCY
            pickupType == 3 || dropOffType == 3 -> StopActivityType.COORDINATE_DRIVER
            else -> StopActivityType.NORMAL
        }

        fun parseHexColor(hex: String?, defaultColor: Color): Color {
            if (hex.isNullOrBlank()) return defaultColor
            val cleaned = hex.trim().removePrefix("#")
            if (cleaned.length != 6 && cleaned.length != 8) return defaultColor
            return try {
                val argb = if (cleaned.length == 6) {
                    0xFF000000L or cleaned.toLong(16)
                } else {
                    cleaned.toLong(16)
                }
                Color(argb)
            } catch (_: NumberFormatException) {
                defaultColor
            }
        }
    }
}
