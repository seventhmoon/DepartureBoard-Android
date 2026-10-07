package com.androidfung.departureboard.data.repository

import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TflStopPointChild
import com.androidfung.departureboard.data.model.TflStopPointMatch
import com.androidfung.departureboard.data.model.TransitMode

/**
 * Transforms raw TfL API stop and station models into clean domain [Station] models.
 */
internal object TflStationMapper {

    fun fromStopPointMatch(match: TflStopPointMatch): List<Station> {
        // Disambiguate King's Cross / St Pancras megahub into its distinct stations for user clarity
        if (match.id == "HUBKGX" || match.name.contains("King's Cross & St Pancras", ignoreCase = true)) {
            return listOf(
                Station(
                    id = "940GZZLUKSX",
                    name = "King's Cross St. Pancras",
                    modes = listOf("tube"),
                    zone = "1",
                    lat = 51.5308,
                    lon = -0.1238,
                    isFavorite = false,
                    lines = listOf(
                        com.androidfung.departureboard.data.model.StationLineInfo("circle", "Circle", "tube"),
                        com.androidfung.departureboard.data.model.StationLineInfo("hammersmith-city", "Hammersmith & City", "tube"),
                        com.androidfung.departureboard.data.model.StationLineInfo("metropolitan", "Metropolitan", "tube"),
                        com.androidfung.departureboard.data.model.StationLineInfo("northern", "Northern", "tube"),
                        com.androidfung.departureboard.data.model.StationLineInfo("piccadilly", "Piccadilly", "tube"),
                        com.androidfung.departureboard.data.model.StationLineInfo("victoria", "Victoria", "tube")
                    )
                ),
                Station(
                    id = "910GKNGX",
                    name = "King's Cross",
                    modes = listOf("national-rail"),
                    zone = "1",
                    lat = 51.5316,
                    lon = -0.1235,
                    isFavorite = false,
                    lines = listOf(
                        com.androidfung.departureboard.data.model.StationLineInfo("london-north-eastern-railway", "LNER", "national-rail"),
                        com.androidfung.departureboard.data.model.StationLineInfo("great-northern", "Great Northern", "national-rail"),
                        com.androidfung.departureboard.data.model.StationLineInfo("grand-central", "Grand Central", "national-rail"),
                        com.androidfung.departureboard.data.model.StationLineInfo("hull-trains", "Hull Trains", "national-rail"),
                        com.androidfung.departureboard.data.model.StationLineInfo("lumo", "Lumo", "national-rail"),
                        com.androidfung.departureboard.data.model.StationLineInfo("thameslink", "Thameslink", "national-rail")
                    )
                ),
                Station(
                    id = "910GSTPX",
                    name = "St Pancras International",
                    modes = listOf("national-rail", "international-rail"),
                    zone = "1",
                    lat = 51.5316,
                    lon = -0.1261,
                    isFavorite = false,
                    lines = listOf(
                        com.androidfung.departureboard.data.model.StationLineInfo("thameslink", "Thameslink", "national-rail"),
                        com.androidfung.departureboard.data.model.StationLineInfo("southeastern", "Southeastern", "national-rail"),
                        com.androidfung.departureboard.data.model.StationLineInfo("east-midlands-railway", "East Midlands Railway", "national-rail")
                    )
                )
            )
        }

        val cleaned = StationNameFormatter.clean(match.name)
        val isBusOnly = TransitMode.isBusOnly(match.modes)

        val stopLetter = if (isBusOnly && !cleaned.contains("Stop ", ignoreCase = true)) {
            TflStopPointUtils.extractBusStopLetter(match.id)
        } else null

        val towards = match.towards?.takeIf { it.isNotBlank() && !it.trim().equals("null", ignoreCase = true) }
        val displayName = buildDisambiguatedStopName(cleaned, stopLetter, towards)

        return listOf(
            Station(
                id = match.id,
                name = displayName,
                modes = match.modes,
                zone = match.zone,
                lat = match.lat,
                lon = match.lon,
                isFavorite = false
            )
        )
    }

    fun fromStopPointChild(sp: TflStopPointChild): Station {
        val common = sp.commonName ?: "Bus Stop"
        val cleaned = StationNameFormatter.clean(common)
        val letter = sp.stopLetter?.takeIf { it.isNotBlank() }
            ?: sp.indicator?.takeIf { it.startsWith("Stop ", ignoreCase = true) }?.removePrefix("Stop ")?.trim()
            ?: TflStopPointUtils.extractBusStopLetter(sp.id)

        val towards = sp.towards?.takeIf { it.isNotBlank() && !it.trim().equals("null", ignoreCase = true) }
            ?: sp.additionalProperties.firstOrNull { it.key.equals("Towards", ignoreCase = true) }?.value?.takeIf { it.isNotBlank() }

        val displayName = buildDisambiguatedStopName(cleaned, letter, towards)

        return Station(
            id = sp.id,
            name = displayName,
            modes = listOf(TransitMode.BUS.id),
            zone = sp.additionalProperties.firstOrNull { it.key.equals("Zone", ignoreCase = true) }?.value,
            lat = sp.lat,
            lon = sp.lon,
            isFavorite = false
        )
    }

    /**
     * Extracts served line metadata from [TflStopPointDetail] (including lineModeGroups).
     */
    fun extractLines(detail: com.androidfung.departureboard.data.model.TflStopPointDetail): List<com.androidfung.departureboard.data.model.StationLineInfo> {
        val modeMap = mutableMapOf<String, String>()
        for ((modeName1, lineIdentifier) in detail.lineModeGroups) {
            val modeName = modeName1 ?: continue
            for (lineId in lineIdentifier) {
                modeMap[lineId.lowercase()] = modeName
            }
        }

        val isStPancrasInternational = detail.id == "910GSTPX" || detail.commonName?.equals("St Pancras International", ignoreCase = true) == true
        val isKingsCrossNR = detail.id == "910GKNGX" || detail.commonName?.equals("King's Cross", ignoreCase = true) == true

        return detail.lines
            .filter { line ->
                // Filter out non-rail bus line numbers for train/tube stations unless bus is the primary station type
                val id = line.id.lowercase()
                if (id.all { it.isDigit() } || id.startsWith("n")) return@filter false

                val mode = line.modeName?.lowercase() ?: modeMap[id]?.lowercase()

                // If this is St Pancras International or King's Cross (NR), explicitly filter out Tube lines and modes
                if (isStPancrasInternational || isKingsCrossNR) {
                    if (mode == "tube" || id in listOf("circle", "hammersmith-city", "metropolitan", "northern", "piccadilly", "victoria", "tube")) {
                        return@filter false
                    }
                }

                true
            }
            .map { line ->
                val lineId = line.id.lowercase()
                val lineName = line.name?.takeIf { it.isNotBlank() } ?: line.id
                val mode = line.modeName ?: modeMap[lineId]
                com.androidfung.departureboard.data.model.StationLineInfo(
                    id = lineId,
                    name = lineName,
                    mode = mode
                )
            }
            .distinctBy { it.id }
            .sortedBy { it.name }
    }

    private fun buildDisambiguatedStopName(cleaned: String, stopLetter: String?, towards: String?): String {
        val details = mutableListOf<String>()
        if (!stopLetter.isNullOrBlank()) {
            details.add("Stop $stopLetter")
        }
        if (towards != null && !cleaned.contains("towards", ignoreCase = true)) {
            details.add("towards $towards")
        }
        return if (details.isNotEmpty()) "$cleaned (${details.joinToString(", ")})" else cleaned
    }
}
