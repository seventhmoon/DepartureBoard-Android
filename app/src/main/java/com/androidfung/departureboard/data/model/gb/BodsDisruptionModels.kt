package com.androidfung.departureboard.data.model.gb

import java.io.InputStream
import java.io.Reader
import java.io.StringReader
import kotlin.coroutines.cancellation.CancellationException
import org.xmlpull.v1.XmlPullParser

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
        return parseReader(StringReader(xmlString), pullParser)
    }

    fun parseStream(
        inputStream: InputStream,
        pullParser: XmlPullParser? = null
    ): List<BodsDisruption> {
        return parseReader(inputStream.bufferedReader(Charsets.UTF_8), pullParser)
    }

    private fun parseReader(
        reader: Reader,
        pullParser: XmlPullParser? = null
    ): List<BodsDisruption> {
        val disruptions = mutableListOf<BodsDisruption>()
        try {
            reader.use { safeReader ->
                val parser = BodsXmlPullHelper.newHardenedParser(safeReader, pullParser)

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
            }
        } catch (e: CancellationException) {
            throw e
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
                    textBuffer.setLength(0)
                    currentTag = ""
                    if (parser.name?.equals("PtSituationElement", ignoreCase = true) == true) {
                        break
                    }
                }
            }
        }

        if (situationNumber.isNotBlank() || summary.isNotBlank() || !description.isNullOrBlank()) {
            return BodsDisruption(
                situationNumber = situationNumber.ifBlank {
                    "bods-disruption-${BodsXmlPullHelper.fnv1a64("$lineRef|$summary|$description|$participantRef").toString(16)}"
                },
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
