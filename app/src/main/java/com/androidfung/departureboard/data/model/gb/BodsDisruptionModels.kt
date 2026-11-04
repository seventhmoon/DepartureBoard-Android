package com.androidfung.departureboard.data.model

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * Domain representation of a DfT BODS SIRI-SX Disruption or situation alert.
 */
data class BodsDisruption(
    val situationNumber: String,
    val lineRef: String? = null,
    val summary: String,
    val description: String? = null,
    val reason: String? = null,
    val severity: String? = null,
    val participantRef: String? = null
)

/**
 * Streaming parser for BODS SIRI-SX XML situation exchange feeds.
 */
object BodsDisruptionXmlParser {

    fun parse(
        xmlString: String,
        pullParser: XmlPullParser? = null
    ): List<BodsDisruption> {
        val disruptions = mutableListOf<BodsDisruption>()
        try {
            val parser = pullParser ?: try {
                XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }.newPullParser().apply {
                    setInput(StringReader(xmlString))
                }
            } catch (_: Throwable) {
                val kxml = Class.forName("org.kxml2.io.KXmlParser").getDeclaredConstructor().newInstance() as XmlPullParser
                kxml.setInput(StringReader(xmlString))
                kxml
            }

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && (parser.name?.equals("PtSituationElement", ignoreCase = true) == true)) {
                    val disruption = parseSituationElement(parser)
                    if (disruption != null) {
                        disruptions.add(disruption)
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {}
        return disruptions
    }

    private fun parseSituationElement(parser: XmlPullParser): BodsDisruption? {
        var situationNumber = ""
        var lineRef: String? = null
        var summary = ""
        var description: String? = null
        var reason: String? = null
        var severity: String? = null
        var participantRef: String? = null

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
                            "SituationNumber" -> situationNumber = text
                            "LineRef" -> lineRef = text
                            "PublishedLineName" -> if (lineRef == null) lineRef = text
                            "Summary" -> summary = text
                            "Description" -> description = text
                            "ReasonName", "Reason" -> reason = text
                            "Severity" -> severity = text
                            "ParticipantRef" -> participantRef = text
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name?.equals("PtSituationElement", ignoreCase = true) == true) {
                        break
                    }
                    currentTag = ""
                }
            }
        }

        if (situationNumber.isNotBlank() || summary.isNotBlank() || !description.isNullOrBlank()) {
            return BodsDisruption(
                situationNumber = situationNumber.ifBlank { "bods-disruption-${System.currentTimeMillis()}" },
                lineRef = lineRef,
                summary = summary.ifBlank { description ?: "Service Disruption" },
                description = description,
                reason = reason,
                severity = severity,
                participantRef = participantRef
            )
        }
        return null
    }
}
