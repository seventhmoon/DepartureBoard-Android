package com.androidfung.departureboard.data.repository

import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks and estimates terminal turnaround dwell times using Working Timetable (WTT) section run times
 * and stepping-back dwell constants, refined by empirical moving averages.
 *
 * Reconciles the difference between inbound train platform arrival and outbound passenger departure.
 */
object TurnaroundTimeTracker {

    /**
     * Calibrated Working Timetable (WTT) reference for specific key terminus stations.
     * Incorporates operational "stepping-back" dwell times and penultimate section run times.
     */
    data class TerminusWttProfile(
        val terminusName: String,
        val lineId: String,
        val penultimateStationName: String,
        val penultimateRunTimeSeconds: Int,
        val steppingBackDwellSeconds: Int,
        val stationIds: Set<String> = emptySet()
    )

    // Specific WTT profiles for major high-frequency London termini
    private val WTT_TERMINUS_PROFILES = listOf(
        // Victoria line (intensive 36 tph stepping-back)
        TerminusWttProfile("Brixton", "victoria", "Stockwell", 115, 110, setOf("940GZZLUBXN")),
        TerminusWttProfile("Walthamstow Central", "victoria", "Blackhorse Road", 110, 120, setOf("940GZZLUWWL", "940GZZLUWAC")),

        // Northern line (stepping-back at Morden, Edgware, High Barnet, Battersea, Mill Hill East)
        TerminusWttProfile("Morden", "northern", "South Wimbledon", 100, 150, setOf("940GZZLUMDN")),
        TerminusWttProfile("Edgware", "northern", "Burnt Oak", 120, 180, setOf("940GZZLUEGW")),
        TerminusWttProfile("High Barnet", "northern", "Totteridge & Whetstone", 135, 180, setOf("940GZZLUHBT")),
        TerminusWttProfile("Battersea Power Station", "northern", "Nine Elms", 110, 150, setOf("940GZZBPSUST")),
        TerminusWttProfile("Mill Hill East", "northern", "Finchley Central", 180, 150, setOf("940GZZLUMHL")),

        // Jubilee line
        TerminusWttProfile("Stratford", "jubilee", "West Ham", 140, 180, setOf("940GZZLUSTD")),
        TerminusWttProfile("Stanmore", "jubilee", "Canons Park", 120, 180, setOf("940GZZLUSTM")),

        // Bakerloo line
        TerminusWttProfile("Elephant & Castle", "bakerloo", "Lambeth North", 110, 180, setOf("940GZZLUEHA")),
        TerminusWttProfile("Harrow & Wealdstone", "bakerloo", "Kenton", 125, 210, setOf("940GZZLUHAW")),

        // Central line
        TerminusWttProfile("West Ruislip", "central", "Ruislip Gardens", 130, 210, setOf("940GZZLUWRP")),
        TerminusWttProfile("Ealing Broadway", "central", "West Acton", 140, 210, setOf("940GZZLUEBY")),
        TerminusWttProfile("Epping", "central", "Theydon Bois", 160, 210, setOf("940GZZLUEPG")),
        TerminusWttProfile("Hainault", "central", "Fairlop", 110, 180, setOf("940GZZLUHHL")),

        // Piccadilly line
        TerminusWttProfile("Cockfosters", "piccadilly", "Oakwood", 130, 210, setOf("940GZZLUCKF")),
        TerminusWttProfile("Uxbridge", "piccadilly", "Hillingdon", 140, 240, setOf("940GZZLUUXB")),
        TerminusWttProfile("Heathrow Terminal 5", "piccadilly", "Heathrow Terminals 2 & 3", 180, 240, setOf("940GZZLUHR5")),

        // District line
        TerminusWttProfile("Upminster", "district", "Upminster Bridge", 110, 240, setOf("940GZZLUUPM")),
        TerminusWttProfile("Wimbledon", "district", "Wimbledon Park", 120, 240, setOf("940GZZLUWBR")),
        TerminusWttProfile("Richmond", "district", "Kew Gardens", 150, 240, setOf("940GZZLURMD")),

        // Elizabeth line
        TerminusWttProfile("Abbey Wood", "elizabeth-line", "Woolwich", 190, 300, setOf("910GABWD", "HUBABW")),
        TerminusWttProfile("Reading", "elizabeth-line", "Twyford", 300, 360, setOf("910GREADING")),
        TerminusWttProfile("Shenfield", "elizabeth-line", "Brentwood", 240, 360, setOf("910GSHENFLD"))
    )

    // Lookup index by clean terminus name + lineId, and by stationId + lineId
    private val WTT_NAME_LOOKUP = WTT_TERMINUS_PROFILES.associateBy {
        "${StationNameFormatter.clean(it.terminusName).lowercase()}_${it.lineId.lowercase()}"
    }
    private val WTT_ID_LOOKUP = buildMap {
        for (profile in WTT_TERMINUS_PROFILES) {
            for (id in profile.stationIds) {
                put("${id.uppercase()}_${profile.lineId.lowercase()}", profile)
            }
        }
    }

    // Default conservative fallback priors (in seconds) based on physical line operating standards
    private val DEFAULT_PRIORS_SECONDS = mapOf(
        "victoria" to 120,     // 2.0 mins (Automatic Train Operation fast turnaround)
        "northern" to 165,     // 2.75 mins
        "jubilee" to 180,      // 3.0 mins
        "central" to 195,      // 3.25 mins
        "piccadilly" to 210,   // 3.5 mins
        "bakerloo" to 195,     // 3.25 mins
        "district" to 240,     // 4.0 mins
        "circle" to 240,       // 4.0 mins
        "hammersmith-city" to 240,
        "metropolitan" to 270, // 4.5 mins
        "elizabeth-line" to 300, // 5.0 mins
        "overground" to 360,   // 6.0 mins
        "national-rail" to 420 // 7.0 mins
    )

    // In-memory cache of learned turnaround times: key = "station_clean_lineId"
    private val learnedTurnarounds = ConcurrentHashMap<String, Int>()

    // Track vehicle arrival timestamps at terminus to observe outbound departures: key = "stationId_vehicleId"
    private val vehicleArrivalTimestamps = ConcurrentHashMap<String, Long>()

    /**
     * Returns the WTT profile if available for a given station name or station ID and line.
     */
    fun getWttProfile(stationNameOrId: String, lineId: String): TerminusWttProfile? {
        val cleanLine = lineId.lowercase()
        // 1. Try exact Station ID lookup first (O(1))
        val idKey = "${stationNameOrId.uppercase()}_$cleanLine"
        WTT_ID_LOOKUP[idKey]?.let { return it }

        // 2. Try normalized name lookup (O(1))
        val nameKey = "${StationNameFormatter.clean(stationNameOrId).lowercase()}_$cleanLine"
        WTT_NAME_LOOKUP[nameKey]?.let { return it }

        // 3. Fallback to substring matching
        return WTT_TERMINUS_PROFILES.firstOrNull {
            it.lineId.equals(cleanLine, ignoreCase = true) && (
                stationNameOrId.contains(it.terminusName, ignoreCase = true) ||
                it.terminusName.contains(stationNameOrId, ignoreCase = true) ||
                it.stationIds.any { id -> id.equals(stationNameOrId, ignoreCase = true) }
            )
        }
    }

    /**
     * Returns the estimated turnaround dwell buffer (in seconds) for a terminus station and line.
     * Uses WTT stepping-back standards as baseline, refined by learned moving averages.
     */
    fun getEstimatedTurnaroundSeconds(stationNameOrId: String, lineId: String): Int {
        val cleanLine = lineId.lowercase()

        // 1. Check if there is an empirically learned moving average by ID or Name
        val idKey = "${stationNameOrId.uppercase()}_$cleanLine"
        learnedTurnarounds[idKey]?.let { return it }

        val nameKey = "${StationNameFormatter.clean(stationNameOrId).lowercase()}_$cleanLine"
        learnedTurnarounds[nameKey]?.let { return it }

        // 2. Check the Working Timetable profile for this specific station
        val wtt = getWttProfile(stationNameOrId, lineId)
        if (wtt != null) {
            return wtt.steppingBackDwellSeconds
        }

        // Generic line prior fallback
        return DEFAULT_PRIORS_SECONDS[cleanLine] ?: 180
    }

    /**
     * Records an observed vehicle arriving at a terminus platform and updates empirical dwell times
     * if the vehicle later transitions into an outbound departure.
     */
    fun recordVehicleArrival(stationId: String, vehicleId: String) {
        if (vehicleId.isBlank()) return
        val vehicleKey = "${stationId.lowercase()}_$vehicleId"
        vehicleArrivalTimestamps[vehicleKey] = System.currentTimeMillis()
    }

    /**
     * Call when a vehicle departs outbound from the terminus platform to calculate actual observed dwell.
     */
    fun recordVehicleDeparture(stationId: String, lineId: String, vehicleId: String) {
        if (vehicleId.isBlank()) return
        val vehicleKey = "${stationId.lowercase()}_$vehicleId"
        val arrivalTime = vehicleArrivalTimestamps.remove(vehicleKey) ?: return
        val observedDwellSeconds = ((System.currentTimeMillis() - arrivalTime) / 1000).toInt()

        // Filter out extreme anomalies (e.g. overnight depot stabling > 30m or glitch < 30s)
        if (observedDwellSeconds in 45..1200) {
            val key = "${stationId.lowercase()}_${lineId.lowercase()}"
            val prior = getEstimatedTurnaroundSeconds(stationId, lineId)
            // Exponential moving average: alpha = 0.35 (gives 35% weight to fresh observation)
            val updated = ((0.35 * observedDwellSeconds) + (0.65 * prior)).toInt()
            learnedTurnarounds[key] = updated
        }
    }
}
