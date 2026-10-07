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
        val steppingBackDwellSeconds: Int
    )

    // Specific WTT profiles for major high-frequency London termini
    private val WTT_TERMINUS_PROFILES = listOf(
        // Victoria line (intensive 36 tph stepping-back)
        TerminusWttProfile("Brixton", "victoria", "Stockwell", 115, 110),
        TerminusWttProfile("Walthamstow Central", "victoria", "Blackhorse Road", 110, 120),

        // Northern line (stepping-back at Morden, Edgware, High Barnet, Battersea)
        TerminusWttProfile("Morden", "northern", "South Wimbledon", 100, 150),
        TerminusWttProfile("Edgware", "northern", "Burnt Oak", 120, 180),
        TerminusWttProfile("High Barnet", "northern", "Totteridge & Whetstone", 135, 180),
        TerminusWttProfile("Battersea Power Station", "northern", "Nine Elms", 110, 150),
        TerminusWttProfile("Mill Hill East", "northern", "Finchley Central", 180, 150),

        // Jubilee line
        TerminusWttProfile("Stratford", "jubilee", "West Ham", 140, 180),
        TerminusWttProfile("Stanmore", "jubilee", "Canons Park", 120, 180),

        // Bakerloo line
        TerminusWttProfile("Elephant & Castle", "bakerloo", "Lambeth North", 110, 180),
        TerminusWttProfile("Harrow & Wealdstone", "bakerloo", "Kenton", 125, 210),

        // Central line
        TerminusWttProfile("West Ruislip", "central", "Ruislip Gardens", 130, 210),
        TerminusWttProfile("Ealing Broadway", "central", "West Acton", 140, 210),
        TerminusWttProfile("Epping", "central", "Theydon Bois", 160, 210),
        TerminusWttProfile("Hainault", "central", "Fairlop", 110, 180),

        // Piccadilly line
        TerminusWttProfile("Cockfosters", "piccadilly", "Oakwood", 130, 210),
        TerminusWttProfile("Uxbridge", "piccadilly", "Hillingdon", 140, 240),
        TerminusWttProfile("Heathrow Terminal 5", "piccadilly", "Heathrow Terminals 2 & 3", 180, 240),

        // District line
        TerminusWttProfile("Upminster", "district", "Upminster Bridge", 110, 240),
        TerminusWttProfile("Wimbledon", "district", "Wimbledon Park", 120, 240),
        TerminusWttProfile("Richmond", "district", "Kew Gardens", 150, 240),

        // Elizabeth line
        TerminusWttProfile("Abbey Wood", "elizabeth-line", "Woolwich", 190, 300),
        TerminusWttProfile("Reading", "elizabeth-line", "Twyford", 300, 360),
        TerminusWttProfile("Shenfield", "elizabeth-line", "Brentwood", 240, 360)
    )

    // Lookup index by clean terminus name + lineId
    private val WTT_LOOKUP = WTT_TERMINUS_PROFILES.associateBy {
        "${StationNameFormatter.clean(it.terminusName).lowercase()}_${it.lineId.lowercase()}"
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
     * Returns the WTT profile if available for a given station name and line.
     */
    fun getWttProfile(stationName: String, lineId: String): TerminusWttProfile? {
        val key = "${StationNameFormatter.clean(stationName).lowercase()}_${lineId.lowercase()}"
        return WTT_LOOKUP[key]
            ?: WTT_TERMINUS_PROFILES.firstOrNull {
                it.lineId.equals(lineId, ignoreCase = true) &&
                stationName.contains(it.terminusName, ignoreCase = true)
            }
    }

    /**
     * Returns the estimated turnaround dwell buffer (in seconds) for a terminus station and line.
     * Uses WTT stepping-back standards as baseline, refined by learned moving averages.
     */
    fun getEstimatedTurnaroundSeconds(stationNameOrId: String, lineId: String): Int {
        val cleanStation = StationNameFormatter.clean(stationNameOrId).lowercase()
        val cleanLine = lineId.lowercase()
        val key = "${cleanStation}_${cleanLine}"

        // Check if there is an empirically learned moving average
        learnedTurnarounds[key]?.let { return it }

        // Otherwise check the Working Timetable profile for this specific station
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
