package com.androidfung.departureboard.data.model

/**
 * Data-driven inference of default line badges for London transit stations
 * prior to arrival prediction network responses.
 */
object StationLineInferrer {

    /**
     * Map of known multi-line transit hubs to their constituent lines.
     * Stored as clean line identifiers which are mapped to LineBadgeInfo.
     */
    private val STATION_SPECIFIC_LINES: Map<String, List<Pair<String, String>>> = mapOf(
        "oxford circus" to listOf("bakerloo" to "Bakerloo", "central" to "Central", "victoria" to "Victoria"),
        "king's cross st. pancras" to listOf(
            "circle" to "Circle", "hammersmith-city" to "Hammersmith & City",
            "metropolitan" to "Metropolitan", "northern" to "Northern",
            "piccadilly" to "Piccadilly", "victoria" to "Victoria"
        ),
        "king's cross" to listOf(
            "nr-gr" to "LNER", "nr-gn" to "Great Northern", "nr-gc" to "Grand Central",
            "nr-ht" to "Hull Trains", "nr-ld" to "Lumo"
        ),
        "910GKNGX" to listOf(
            "nr-gr" to "LNER", "nr-gn" to "Great Northern", "nr-gc" to "Grand Central",
            "nr-ht" to "Hull Trains", "nr-ld" to "Lumo"
        ),
        "st pancras international" to listOf(
            "thameslink" to "Thameslink", "nr-se" to "Southeastern", "nr-em" to "East Midlands Railway",
            "nr-es" to "Eurostar"
        ),
        "910GSTPX" to listOf(
            "thameslink" to "Thameslink", "nr-se" to "Southeastern", "nr-em" to "East Midlands Railway",
            "nr-es" to "Eurostar"
        ),
        "st pancras" to listOf(
            "thameslink" to "Thameslink", "nr-se" to "Southeastern", "nr-em" to "East Midlands Railway"
        ),
        "waterloo" to listOf("bakerloo" to "Bakerloo", "jubilee" to "Jubilee", "northern" to "Northern", "waterloo-city" to "Waterloo & City"),
        "victoria" to listOf("circle" to "Circle", "district" to "District", "victoria" to "Victoria"),
        "london bridge" to listOf("jubilee" to "Jubilee", "northern" to "Northern"),
        "kentish town" to listOf("northern" to "Northern"),
        "old street" to listOf("northern" to "Northern"),
        "ealing broadway" to listOf("central" to "Central", "district" to "District", "elizabeth-line" to "Elizabeth line"),
        "wembley park" to listOf("jubilee" to "Jubilee", "metropolitan" to "Metropolitan"),
        "paddington" to listOf("bakerloo" to "Bakerloo", "circle" to "Circle", "district" to "District", "elizabeth-line" to "Elizabeth line", "hammersmith-city" to "Hammersmith & City"),
        "farringdon" to listOf("circle" to "Circle", "elizabeth-line" to "Elizabeth line", "hammersmith-city" to "Hammersmith & City", "metropolitan" to "Metropolitan"),
        "liverpool street" to listOf("central" to "Central", "circle" to "Circle", "elizabeth-line" to "Elizabeth line", "hammersmith-city" to "Hammersmith & City", "metropolitan" to "Metropolitan", "overground" to "London Overground")
    )

    fun infer(station: Station): List<LineBadgeInfo> {
        // 1. Dynamic: If station already has line info populated from TfL StopPoint API
        if (station.lines.isNotEmpty()) {
            val isStPancrasOrKingsCrossNR = station.id == "910GSTPX" || station.id == "910GKNGX" ||
                    station.name.lowercase().let { it == "st pancras international" || it == "st pancras" || it == "king's cross" }

            return station.lines
                .filter { line ->
                    if (isStPancrasOrKingsCrossNR) {
                        val mode = line.mode?.lowercase() ?: ""
                        val id = line.id.lowercase()
                        if (mode == "tube" || id in TflLineColors.TUBE_LINE_IDS) {
                            return@filter false
                        }
                    }
                    true
                }
                .map { line ->
                    val mode = line.mode ?: when {
                        line.id == "elizabeth" || line.id == "elizabeth-line" -> TransitMode.ELIZABETH_LINE.id
                        line.id in listOf("liberty", "lioness", "mildmay", "suffragette", "weaver", "windrush", "overground") -> TransitMode.OVERGROUND.id
                        line.id.startsWith("nr-") || line.id == "thameslink" -> TransitMode.NATIONAL_RAIL.id
                        else -> TransitMode.TUBE.id
                    }
                    TflLineColors.getLineBadge(line.id, line.name, mode)
                }.distinctBy { it.displayName }.sortedBy { it.displayName }
        }

        val stName = station.name.lowercase()

        // Check station ID or name for St Pancras or King's Cross National Rail
        val isStPancrasOrKingsCrossNR = station.id == "910GSTPX" || station.id == "910GKNGX" ||
                stName == "st pancras international" || stName == "st pancras" || stName == "king's cross"

        // 2. Fallback to known multi-line transit hubs
        for ((stationKey, lines) in STATION_SPECIFIC_LINES) {
            if (station.id.equals(stationKey, ignoreCase = true) || stName.contains(stationKey)) {
                return lines.map { (id, name) ->
                    val mode = when {
                        id == "elizabeth-line" -> TransitMode.ELIZABETH_LINE.id
                        id == "overground" -> TransitMode.OVERGROUND.id
                        id.startsWith("nr-") || id == "thameslink" -> TransitMode.NATIONAL_RAIL.id
                        else -> TransitMode.TUBE.id
                    }
                    TflLineColors.getLineBadge(id, name, mode)
                }.sortedBy { it.displayName }
            }
        }

        // 3. Generic fallback from station mode declarations using TransitMode
        return TransitMode.fromModes(station.modes)
            .filter { mode ->
                if (isStPancrasOrKingsCrossNR && mode == TransitMode.TUBE) {
                    false
                } else {
                    mode != TransitMode.OTHER
                }
            }
            .map { mode ->
                when (mode) {
                    TransitMode.TUBE -> TflLineColors.getLineBadge("tube", "Underground", mode.id)
                    TransitMode.ELIZABETH_LINE -> TflLineColors.getLineBadge("elizabeth-line", "Elizabeth line", mode.id)
                    TransitMode.OVERGROUND -> TflLineColors.getLineBadge("overground", "Overground", mode.id)
                    TransitMode.DLR -> TflLineColors.getLineBadge("dlr", "DLR", mode.id)
                    TransitMode.TRAM -> TflLineColors.getLineBadge("tram", "Tram", mode.id)
                    TransitMode.BUS -> TflLineColors.getLineBadge("bus", "Bus", mode.id)
                    TransitMode.NATIONAL_RAIL -> TflLineColors.getLineBadge("national-rail", "National Rail", mode.id)
                    TransitMode.CABLE_CAR -> TflLineColors.getLineBadge("cable-car", "IFS Cloud Cable Car", mode.id)
                    TransitMode.OTHER -> TflLineColors.getLineBadge("transit", "Transit", mode.id)
                }
            }
            .distinctBy { it.displayName }
            .sortedBy { it.displayName }
    }
}

