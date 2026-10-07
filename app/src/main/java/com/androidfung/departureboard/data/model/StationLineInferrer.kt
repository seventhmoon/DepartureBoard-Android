package com.androidfung.departureboard.data.model

/**
 * Data-driven inference of default line badges for London transit stations
 * prior to arrival prediction network responses.
 */
object StationLineInferrer {

    private val OVERGROUND_LINE_IDS = setOf(
        "liberty", "lioness", "mildmay", "suffragette", "weaver", "windrush", "overground"
    )

    private val KINGS_CROSS_NR_LINES = listOf(
        "nr-gr" to "LNER",
        "nr-gn" to "Great Northern",
        "nr-gc" to "Grand Central",
        "nr-ht" to "Hull Trains",
        "nr-ld" to "Lumo"
    )

    private val ST_PANCRAS_INTERNATIONAL_LINES = listOf(
        "thameslink" to "Thameslink",
        "nr-se" to "Southeastern",
        "nr-em" to "East Midlands Railway",
        "nr-es" to "Eurostar"
    )

    private val ST_PANCRAS_DOMESTIC_LINES = listOf(
        "thameslink" to "Thameslink",
        "nr-se" to "Southeastern",
        "nr-em" to "East Midlands Railway"
    )

    /**
     * Map of known multi-line transit hubs to their constituent lines.
     * Stored as clean line identifiers which are mapped to LineBadgeInfo.
     */
    private val STATION_SPECIFIC_LINES: Map<String, List<Pair<String, String>>> = mapOf(
        "oxford circus" to listOf(
            "bakerloo" to "Bakerloo",
            "central" to "Central",
            "victoria" to "Victoria"
        ),
        "king's cross st. pancras" to listOf(
            "circle" to "Circle",
            "hammersmith-city" to "Hammersmith & City",
            "metropolitan" to "Metropolitan",
            "northern" to "Northern",
            "piccadilly" to "Piccadilly",
            "victoria" to "Victoria"
        ),
        "king's cross" to KINGS_CROSS_NR_LINES,
        "910GKNGX" to KINGS_CROSS_NR_LINES,
        "st pancras international" to ST_PANCRAS_INTERNATIONAL_LINES,
        "910GSTPX" to ST_PANCRAS_INTERNATIONAL_LINES,
        "st pancras" to ST_PANCRAS_DOMESTIC_LINES,
        "waterloo" to listOf(
            "bakerloo" to "Bakerloo",
            "jubilee" to "Jubilee",
            "northern" to "Northern",
            "waterloo-city" to "Waterloo & City"
        ),
        "victoria" to listOf(
            "circle" to "Circle",
            "district" to "District",
            "victoria" to "Victoria"
        ),
        "london bridge" to listOf(
            "jubilee" to "Jubilee",
            "northern" to "Northern"
        ),
        "kentish town" to listOf(
            "northern" to "Northern"
        ),
        "old street" to listOf(
            "northern" to "Northern"
        ),
        "ealing broadway" to listOf(
            "central" to "Central",
            "district" to "District",
            "elizabeth-line" to "Elizabeth line"
        ),
        "wembley park" to listOf(
            "jubilee" to "Jubilee",
            "metropolitan" to "Metropolitan"
        ),
        "paddington" to listOf(
            "bakerloo" to "Bakerloo",
            "circle" to "Circle",
            "district" to "District",
            "elizabeth-line" to "Elizabeth line",
            "hammersmith-city" to "Hammersmith & City"
        ),
        "farringdon" to listOf(
            "circle" to "Circle",
            "elizabeth-line" to "Elizabeth line",
            "hammersmith-city" to "Hammersmith & City",
            "metropolitan" to "Metropolitan"
        ),
        "liverpool street" to listOf(
            "central" to "Central",
            "circle" to "Circle",
            "elizabeth-line" to "Elizabeth line",
            "hammersmith-city" to "Hammersmith & City",
            "metropolitan" to "Metropolitan",
            "overground" to "London Overground"
        ),
        "wimbledon" to listOf(
            "district" to "District",
            "tram" to "Tram",
            "nr-sw" to "South Western Railway",
            "thameslink" to "Thameslink"
        ),
        "stratford" to listOf(
            "central" to "Central",
            "jubilee" to "Jubilee",
            "elizabeth-line" to "Elizabeth line",
            "mildmay" to "Mildmay",
            "dlr" to "DLR",
            "nr-le" to "Greater Anglia",
            "nr-cc" to "c2c"
        ),
        "west ham" to listOf(
            "district" to "District",
            "hammersmith-city" to "Hammersmith & City",
            "jubilee" to "Jubilee",
            "dlr" to "DLR",
            "nr-cc" to "c2c"
        ),
        "canary wharf" to listOf(
            "jubilee" to "Jubilee",
            "elizabeth-line" to "Elizabeth line",
            "dlr" to "DLR"
        ),
        "west hampstead" to listOf(
            "jubilee" to "Jubilee",
            "mildmay" to "Mildmay",
            "thameslink" to "Thameslink"
        )
    )

    fun infer(station: Station): List<LineBadgeInfo> {
        return inferFromDynamicLines(station)
            ?: inferFromKnownHubs(station)
            ?: inferFromStationModes(station)
    }

    /**
     * 1. Dynamic inference when station lines are already provided by TfL StopPoint API.
     */
    private fun inferFromDynamicLines(station: Station): List<LineBadgeInfo>? {
        if (station.lines.isEmpty()) return null

        val isKingsCrossOrStPancrasNR = station.isKingsCrossOrStPancrasNationalRail()

        return station.lines
            .filter { line ->
                if (isKingsCrossOrStPancrasNR) {
                    val mode = line.mode?.lowercase().orEmpty()
                    val id = line.id.lowercase()
                    if (mode == "tube" || id in TflLineColors.TUBE_LINE_IDS) {
                        return@filter false
                    }
                }
                true
            }
            .map { line ->
                val mode = line.mode ?: resolveDefaultMode(line.id)
                TflLineColors.getLineBadge(line.id, line.name, mode)
            }
            .distinctBy { it.displayName }
            .sortedBy { it.displayName }
    }

    /**
     * 2. Fallback inference matching against known multi-line transit hubs.
     */
    private fun inferFromKnownHubs(station: Station): List<LineBadgeInfo>? {
        val stName = station.name.lowercase()

        for ((stationKey, lines) in STATION_SPECIFIC_LINES) {
            if (station.id.equals(stationKey, ignoreCase = true) || stName.contains(stationKey)) {
                return lines.map { (id, name) ->
                    val mode = resolveDefaultMode(id)
                    TflLineColors.getLineBadge(id, name, mode)
                }.sortedBy { it.displayName }
            }
        }
        return null
    }

    /**
     * 3. Generic fallback derived from declared station modes.
     */
    private fun inferFromStationModes(station: Station): List<LineBadgeInfo> {
        val isKingsCrossOrStPancrasNR = station.isKingsCrossOrStPancrasNationalRail()

        return TransitMode.fromModes(station.modes)
            .filter { mode ->
                !(isKingsCrossOrStPancrasNR && mode == TransitMode.TUBE) && mode != TransitMode.OTHER
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

    private fun resolveDefaultMode(lineId: String): String = when {
        lineId == "elizabeth" || lineId == "elizabeth-line" -> TransitMode.ELIZABETH_LINE.id
        lineId in OVERGROUND_LINE_IDS -> TransitMode.OVERGROUND.id
        lineId.startsWith("nr-") || lineId == "thameslink" -> TransitMode.NATIONAL_RAIL.id
        else -> TransitMode.TUBE.id
    }

    private fun Station.isKingsCrossOrStPancrasNationalRail(): Boolean {
        val normName = name.lowercase()
        return id == "910GSTPX" || id == "910GKNGX" ||
                normName == "st pancras international" || normName == "st pancras" || normName == "king's cross"
    }
}
