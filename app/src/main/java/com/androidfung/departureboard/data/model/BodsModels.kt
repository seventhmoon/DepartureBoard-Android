package com.androidfung.departureboard.data.model

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Parsed vehicle activity from BODS SIRI-VM live vehicle monitoring datafeed.
 */
data class BodsVehicleActivity(
    val lineRef: String,
    val publishedLineName: String,
    val operatorRef: String,
    val originRef: String? = null,
    val originName: String? = null,
    val destinationRef: String? = null,
    val destinationName: String? = null,
    val destinationAimedArrivalTime: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val vehicleRef: String? = null,
    val bearing: Double? = null,
    val wheelchairAccessible: Boolean? = null,
    val occupancy: String? = null,
    val vehicleFeature: String? = null
) {
    /**
     * Maps BODS monitored journey to domain Departure model.
     */
    fun toDeparture(
        stationId: String,
        stationName: String,
        stationLat: Double?,
        stationLon: Double?
    ): Departure {
        val dest = destinationName
            ?.replace("_", " ")
            ?.replace("__", " ")
            ?.trim()
            ?.ifBlank { null }
            ?: "Scheduled Service"

        // Calculate estimated seconds to arrival based on distance or aimed arrival time
        val seconds = calculateTimeToArrival(stationLat, stationLon)

        val cleanLine = publishedLineName.ifBlank { lineRef }

        val badge = TflLineColors.getLineBadge(
            lineId = "bods-$lineRef",
            lineName = cleanLine,
            modeName = "bus"
        )

        return Departure(
            id = "bods-${vehicleRef ?: lineRef}_${destinationRef ?: dest}_$seconds",
            stationId = stationId,
            stationName = stationName,
            lineId = "bods-$lineRef",
            lineName = cleanLine,
            platformName = destinationRef?.takeIf { it.startsWith("210") || it.length <= 4 } ?: "Bus Stand",
            destinationName = dest,
            towards = dest,
            timeToStationSeconds = seconds,
            expectedArrivalIso = destinationAimedArrivalTime,
            currentLocation = if (latitude != null && longitude != null) "In service" else null,
            modeName = "bus",
            status = DepartureStatus.ON_TIME,
            lineBadge = badge,
            destinationStationId = destinationRef,
            isWheelchairAccessible = wheelchairAccessible,
            occupancyStatus = occupancy
        )
    }

    private fun calculateTimeToArrival(stationLat: Double?, stationLon: Double?): Int {
        if (!destinationAimedArrivalTime.isNullOrBlank()) {
            try {
                val arrivalInstant = Instant.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(destinationAimedArrivalTime))
                val diff = arrivalInstant.epochSecond - Instant.now().epochSecond
                if (diff in 0..7200) return diff.toInt()
            } catch (_: Exception) {}
        }

        // Fallback: estimate time based on distance from station (assuming average bus speed ~20 km/h = 5.5 m/s)
        if (latitude != null && longitude != null && stationLat != null && stationLon != null) {
            val distMeters = haversineMeters(latitude, longitude, stationLat, stationLon)
            val estSeconds = (distMeters / 5.5).toInt().coerceIn(60, 3600)
            return estSeconds
        }

        return 300 // default 5 min
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}

/**
 * Fast streaming XmlPullParser for BODS SIRI-VM responses.
 */
object BodsXmlParser {

    fun parse(
        xmlString: String,
        pullParser: XmlPullParser? = null
    ): List<BodsVehicleActivity> {
        val activities = mutableListOf<BodsVehicleActivity>()
        try {
            val parser = pullParser ?: try {
                XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }.newPullParser().apply {
                    setInput(StringReader(xmlString))
                }
            } catch (_: Throwable) {
                // In standard JVM unit tests without android.jar XML implementation
                val kxml = Class.forName("org.kxml2.io.KXmlParser").getDeclaredConstructor().newInstance() as XmlPullParser
                kxml.setInput(StringReader(xmlString))
                kxml
            }

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && (parser.name?.equals("VehicleActivity", ignoreCase = true) == true)) {
                    val activity = parseVehicleActivity(parser)
                    if (activity != null) {
                        activities.add(activity)
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return activities
    }

    private fun parseVehicleActivity(parser: XmlPullParser): BodsVehicleActivity? {
        var lineRef = ""
        var publishedLineName = ""
        var operatorRef = ""
        var originRef: String? = null
        var originName: String? = null
        var destinationRef: String? = null
        var destinationName: String? = null
        var destinationAimedArrivalTime: String? = null
        var latitude: Double? = null
        var longitude: Double? = null
        var vehicleRef: String? = null
        var bearing: Double? = null
        var wheelchairAccessible: Boolean? = null
        var occupancy: String? = null
        var vehicleFeature: String? = null

        var currentTag = ""
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name ?: ""
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim() ?: ""
                    if (text.isNotEmpty()) {
                        when (currentTag) {
                            "LineRef" -> lineRef = text
                            "PublishedLineName" -> publishedLineName = text
                            "OperatorRef" -> operatorRef = text
                            "OriginRef" -> originRef = text
                            "OriginName" -> originName = text
                            "DestinationRef" -> destinationRef = text
                            "DestinationName" -> destinationName = text
                            "DestinationAimedArrivalTime" -> destinationAimedArrivalTime = text
                            "Latitude" -> latitude = text.toDoubleOrNull()
                            "Longitude" -> longitude = text.toDoubleOrNull()
                            "VehicleRef" -> vehicleRef = text
                            "Bearing" -> bearing = text.toDoubleOrNull()
                            "WheelchairAccessible" -> {
                                val lower = text.lowercase()
                                wheelchairAccessible = lower == "true" || lower == "1" || lower == "accessible"
                            }
                            "Occupancy" -> occupancy = text
                            "VehicleFeatureRef" -> vehicleFeature = text
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("VehicleActivity", ignoreCase = true)) {
                        break
                    }
                    currentTag = ""
                }
            }
        }

        if (lineRef.isNotBlank() || publishedLineName.isNotBlank()) {
            return BodsVehicleActivity(
                lineRef = lineRef,
                publishedLineName = publishedLineName,
                operatorRef = operatorRef,
                originRef = originRef,
                originName = originName,
                destinationRef = destinationRef,
                destinationName = destinationName,
                destinationAimedArrivalTime = destinationAimedArrivalTime,
                latitude = latitude,
                longitude = longitude,
                vehicleRef = vehicleRef,
                bearing = bearing,
                wheelchairAccessible = wheelchairAccessible,
                occupancy = occupancy,
                vehicleFeature = vehicleFeature
            )
        }
        return null
    }
}
