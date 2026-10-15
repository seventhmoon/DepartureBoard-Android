package com.androidfung.departureboard.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Models for TfL Line Timetable endpoint:
 * https://api.tfl.gov.uk/Line/{id}/Timetable/{fromStopPointId}
 */
@JsonClass(generateAdapter = true)
data class TflTimetableResponse(
    @Json(name = "lineId") val lineId: String? = null,
    @Json(name = "lineName") val lineName: String? = null,
    @Json(name = "direction") val direction: String? = null,
    @Json(name = "timetable") val timetable: TflTimetable? = null,
    @Json(name = "disambiguation") val disambiguation: TflDisambiguation? = null
)

@JsonClass(generateAdapter = true)
data class TflTimetable(
    @Json(name = "name") val name: String? = null,
    @Json(name = "routes") val routes: List<TflTimetableRoute> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TflTimetableRoute(
    @Json(name = "schedules") val schedules: List<TflSchedule> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TflSchedule(
    @Json(name = "name") val name: String = "",
    @Json(name = "knownJourneys") val knownJourneys: List<TflKnownJourney> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TflKnownJourney(
    @Json(name = "hour") val hour: String = "",
    @Json(name = "minute") val minute: String = "",
    @Json(name = "intervalId") val intervalId: Int = 0
) {
    val timeInt: Int
        get() = (hour.toIntOrNull() ?: 0) * 60 + (minute.toIntOrNull() ?: 0)
    
    val formattedTime: String
        get() = "%02d:%02d".format(hour.toIntOrNull() ?: 0, minute.toIntOrNull() ?: 0)
}

@JsonClass(generateAdapter = true)
data class TflDisambiguation(
    @Json(name = "disambiguationOptions") val disambiguationOptions: List<TflDisambiguationOption> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TflDisambiguationOption(
    @Json(name = "description") val description: String? = null,
    @Json(name = "uri") val uri: String? = null
)

/**
 * Result representing scheduled timetable info when station is closed/overnight.
 */
data class StationServiceHoursInfo(
    val isClosedForNight: Boolean,
    val lastTrainTime: String? = null,
    val firstTrainTime: String? = null,
    val lineName: String? = null
)
