package com.androidfung.departureboard.data.model

import com.androidfung.departureboard.data.model.gb.GbTransitLine
import com.androidfung.departureboard.data.model.gb.TflLineColors
import com.androidfung.departureboard.data.model.hk.HkDefaultStations

/**
 * Data-driven inference of default line badges for London transit stations
 * prior to arrival prediction network responses.
 */
object StationLineInferrer {

    private val OVERGROUND_LINE_IDS = GbTransitLine.OVERGROUND_LINES.map { it.code }.toSet()

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
        com.androidfung.departureboard.data.model.gb.TflStationIds.KINGS_CROSS_NR to KINGS_CROSS_NR_LINES,
        "st pancras international" to ST_PANCRAS_INTERNATIONAL_LINES,
        com.androidfung.departureboard.data.model.gb.TflStationIds.ST_PANCRAS_INTERNATIONAL to ST_PANCRAS_INTERNATIONAL_LINES,
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
        "finchley central" to listOf(
            "northern" to "Northern"
        ),
        com.androidfung.departureboard.data.model.gb.TflStationIds.FINCHLEY_CENTRAL to listOf(
            "northern" to "Northern"
        ),
        "mill hill east" to listOf(
            "northern" to "Northern"
        ),
        com.androidfung.departureboard.data.model.gb.TflStationIds.MILL_HILL_EAST to listOf(
            "northern" to "Northern"
        ),
        "east finchley" to listOf(
            "northern" to "Northern"
        ),
        "high barnet" to listOf(
            "northern" to "Northern"
        ),
        "edgware" to listOf(
            "northern" to "Northern"
        ),
        "morden" to listOf(
            "northern" to "Northern"
        ),
        "mornington crescent" to listOf(
            "northern" to "Northern"
        ),
        "camden town" to listOf(
            "northern" to "Northern"
        ),
        "kennington" to listOf(
            "northern" to "Northern"
        ),
        "battersea power station" to listOf(
            "northern" to "Northern"
        ),
        "highgate" to listOf(
            "northern" to "Northern"
        ),
        "archway" to listOf(
            "northern" to "Northern"
        ),
        "tufnell park" to listOf(
            "northern" to "Northern"
        ),
        "euston" to listOf(
            "northern" to "Northern",
            "victoria" to "Victoria",
            "lioness" to "Lioness"
        ),
        "bank" to listOf(
            "central" to "Central",
            "northern" to "Northern",
            "waterloo-city" to "Waterloo & City",
            "dlr" to "DLR"
        ),
        "charing cross" to listOf(
            "bakerloo" to "Bakerloo",
            "northern" to "Northern"
        ),
        "brixton" to listOf(
            "victoria" to "Victoria"
        ),
        "walthamstow central" to listOf(
            "victoria" to "Victoria",
            "weaver" to "Weaver"
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
                if (isKingsCrossOrStPancrasNR && TflLineColors.isTube(line.id, line.mode)) {
                    return@filter false
                }
                true
            }
            .map { line ->
                val mode = line.mode ?: resolveDefaultMode(line.id)
                val region = TransitRegion.fromStationId(station.id)
                if (station.isHkBus || line.id.isHkBusRouteId) {
                    val cleanRoute = line.name.ifBlank { line.id.removePrefix("KMB_").removePrefix("CTB_") }
                    LineBadgeInfo(
                        lineId = cleanRoute,
                        displayName = cleanRoute,
                        backgroundColor = HkDefaultStations.ColorKmbRed,
                        textColor = androidx.compose.ui.graphics.Color.White,
                        mode = TransitMode.BUS
                    )
                } else {
                    region.lineRegistry.fromCode(line.id)?.toLineBadge()
                        ?: TransitRegion.entries.asSequence()
                            .mapNotNull { it.lineRegistry.fromCode(line.id) }
                            .firstOrNull()?.toLineBadge()
                        ?: TflLineColors.getLineBadge(line.id, line.name, mode)
                }
            }
            .distinctBy { it.displayName }
            .sortedBy { it.displayName }
    }

    /**
     * 2. Fallback inference matching against known multi-line transit hubs.
     * Bus stops/stands must never inherit rail/tube/metro interchange lines.
     * Furthermore, station names are matched strictly (by exact ID or exact normalized name)
     * rather than loose substring containment to prevent bus stops like "Stratford Park"
     * or "Stratford Road" from falsely matching the "Stratford" interchange hub.
     */
    private fun inferFromKnownHubs(station: Station): List<LineBadgeInfo>? {
        if (station.isBusOnly || com.androidfung.departureboard.data.repository.region.gb.TflStopPointUtils.isBusStop(station.id, station.name, station.modes)) {
            return null
        }

        val cleanName = com.androidfung.departureboard.data.repository.region.gb.StationNameFormatter.clean(station.name).lowercase().trim()
        val stationIdUpper = station.id.uppercase().trim()

        // 1. Direct match by station ID in STATION_SPECIFIC_LINES
        val linesById = STATION_SPECIFIC_LINES[station.id] ?: STATION_SPECIFIC_LINES[stationIdUpper]
        if (linesById != null) {
            return linesById.map { (id, name) ->
                val mode = resolveDefaultMode(id)
                TflLineColors.getLineBadge(id, name, mode)
            }.sortedBy { it.displayName }
        }

        // 2. Hub ID mapping lookup (e.g. HUBSRA or constituent platform IDs)
        for ((hubId, platformIds) in com.androidfung.departureboard.data.model.gb.TflStationIds.HUB_TO_PLATFORM_NAPTAN) {
            if (stationIdUpper == hubId || platformIds.any { it.equals(stationIdUpper, ignoreCase = true) }) {
                // Check if the hub ID or its clean name exists in STATION_SPECIFIC_LINES
                val mappedLines = STATION_SPECIFIC_LINES[hubId]
                    ?: STATION_SPECIFIC_LINES[cleanName]
                if (mappedLines != null) {
                    return mappedLines.map { (id, name) ->
                        val mode = resolveDefaultMode(id)
                        TflLineColors.getLineBadge(id, name, mode)
                    }.sortedBy { it.displayName }
                }
            }
        }

        // 3. Exact normalized station name match (strictly equal, never loose substring)
        val linesByName = STATION_SPECIFIC_LINES[cleanName]
        if (linesByName != null) {
            return linesByName.map { (id, name) ->
                val mode = resolveDefaultMode(id)
                TflLineColors.getLineBadge(id, name, mode)
            }.sortedBy { it.displayName }
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
                !(isKingsCrossOrStPancrasNR && mode == TransitMode.METRO) && mode != TransitMode.OTHER
            }
            .map { mode ->
                if (station.isHkMtr && mode == TransitMode.METRO) {
                    LineBadgeInfo(
                        lineId = "MTR",
                        displayName = "MTR",
                        backgroundColor = androidx.compose.ui.graphics.Color(0xFF007DC5),
                        textColor = androidx.compose.ui.graphics.Color.White,
                        mode = TransitMode.METRO
                    )
                } else {
                    when (mode) {
                        TransitMode.METRO -> TflLineColors.getLineBadge("tube", "Underground", mode.id)
                        TransitMode.RAIL -> TflLineColors.getLineBadge("national-rail", "National Rail", mode.id)
                        TransitMode.TRAM -> TflLineColors.getLineBadge("tram", "Tram", mode.id)
                        TransitMode.BUS -> TflLineColors.getLineBadge("bus", "Bus", mode.id)
                        TransitMode.CABLE_CAR -> TflLineColors.getLineBadge("cable-car", "IFS Cloud Cable Car", mode.id)
                        TransitMode.FERRY -> TflLineColors.getLineBadge("ferry", "Ferry", mode.id)
                        TransitMode.FUNICULAR -> TflLineColors.getLineBadge("funicular", "Funicular", mode.id)
                        TransitMode.MONORAIL -> TflLineColors.getLineBadge("monorail", "Monorail", mode.id)
                        TransitMode.OTHER -> TflLineColors.getLineBadge("transit", "Transit", mode.id)
                    }
                }
            }
            .distinctBy { it.displayName }
            .sortedBy { it.displayName }
    }

    private fun resolveDefaultMode(lineId: String): String {
        val gbLine = GbTransitLine.fromCode(lineId)
        if (gbLine != null) return gbLine.mode.id
        return when {
            lineId.startsWith("nr-") -> TransitMode.RAIL.id
            else -> TransitMode.METRO.id
        }
    }

    private fun Station.isKingsCrossOrStPancrasNationalRail(): Boolean =
        com.androidfung.departureboard.data.model.gb.TflStationIds.isStPancrasOrKingsCrossNR(id, name)
}
