package com.androidfung.departureboard.data.model.gb

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import java.io.Reader
import java.io.StringReader
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

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
            id = "bods-${vehicleRef ?: lineRef}_${destinationRef ?: dest}_${destinationAimedArrivalTime ?: originRef ?: stationId}",
            stationId = stationId,
            stationName = stationName,
            lineId = "bods-$lineRef",
            lineName = cleanLine,
            platformName = originRef?.takeIf { it.isNotBlank() && it.length <= 4 } ?: "",
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
 * Shared hardened XML pull parser factory and deterministic hash helper for BODS SIRI feeds.
 */
internal object BodsXmlPullHelper {
    private const val FEATURE_PROCESS_DOCDECL =
        "http://xmlpull.org/v1/doc/features.html#process-docdecl"

    fun newHardenedParser(
        reader: Reader,
        pullParser: XmlPullParser? = null
    ): XmlPullParser {
        val safeReader = rejectDoctypeDeclarations(reader)
        if (pullParser != null) {
            configureSecurityFeatures(pullParser)
            return pullParser
        }
        return try {
            XmlPullParserFactory.newInstance().apply {
                isNamespaceAware = false
            }.newPullParser().apply {
                configureSecurityFeatures(this)
                setInput(safeReader)
            }
        } catch (_: Throwable) {
            val kxml = Class.forName("org.kxml2.io.KXmlParser")
                .getDeclaredConstructor()
                .newInstance() as XmlPullParser
            configureSecurityFeatures(kxml)
            kxml.setInput(safeReader)
            kxml
        }
    }

    private fun configureSecurityFeatures(parser: XmlPullParser) {
        try {
            parser.setFeature(FEATURE_PROCESS_DOCDECL, false)
        } catch (_: Exception) {
        }
        try {
            parser.setFeature(XmlPullParser.FEATURE_VALIDATION, false)
        } catch (_: Exception) {
        }
    }

    private fun rejectDoctypeDeclarations(reader: Reader): Reader {
        val peekSize = 4096
        val buffer = CharArray(peekSize)
        var totalRead = 0
        while (totalRead < peekSize) {
            val n = reader.read(buffer, totalRead, peekSize - totalRead)
            if (n <= 0) break
            totalRead += n
        }
        if (totalRead > 0) {
            val prefix = String(buffer, 0, totalRead)
            if (prefix.contains("<!DOCTYPE", ignoreCase = true) ||
                prefix.contains("<!ENTITY", ignoreCase = true)
            ) {
                throw SecurityException("DOCTYPE and ENTITY declarations are disallowed in BODS XML feeds")
            }
        }
        return java.io.PushbackReader(reader, peekSize).apply {
            if (totalRead > 0) unread(buffer, 0, totalRead)
        }
    }

    fun fnv1a64(input: String): ULong {
        var hash = 0xcbf29ce484222325UL
        for (ch in input) {
            hash = (hash xor ch.code.toULong()) * 0x100000001b3UL
        }
        return hash
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
            StringReader(xmlString).use { reader ->
                val parser = BodsXmlPullHelper.newHardenedParser(reader, pullParser)

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
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
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
        val textBuffer = StringBuilder()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name ?: ""
                    textBuffer.setLength(0)
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                    if (currentTag.isNotEmpty()) {
                        parser.text?.let { textBuffer.append(it) }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val text = textBuffer.toString().trim()
                    if (text.isNotEmpty() && currentTag.isNotEmpty()) {
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
                    textBuffer.setLength(0)
                    currentTag = ""
                    if (parser.name.equals("VehicleActivity", ignoreCase = true)) {
                        break
                    }
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
