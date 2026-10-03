package com.androidfung.departureboard.data.repository

import android.content.Context
import com.androidfung.departureboard.data.datastore.StationPreferencesDataSource
import com.androidfung.departureboard.data.datastore.StationPreferencesDataStore
import com.androidfung.departureboard.data.model.DefaultStations
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TflLineColors
import com.androidfung.departureboard.data.network.TflApiService
import com.androidfung.departureboard.data.network.TflNetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Repository interface for TfL transit data operations.
 */
interface TransitRepository {
    val savedStationsFlow: Flow<List<Station>>
    val recentStationIdFlow: Flow<String?>

    suspend fun searchStations(query: String): Result<List<Station>>
    suspend fun getDepartures(stationId: String, stationName: String): Result<List<Departure>>
    fun getDeparturesFlow(stationId: String, stationName: String): Flow<Result<List<Departure>>>
    suspend fun getLineStatuses(): Map<String, com.androidfung.departureboard.data.model.TflLineStatusItem>
    suspend fun saveStation(station: Station)
    suspend fun reorderStations(stations: List<Station>)
    suspend fun removeStation(stationId: String)
    suspend fun setRecentStationId(stationId: String)
}

/**
 * Implementation of TransitRepository using TfL API, DataStore, and offline fallbacks.
 */
class TransitRepositoryImpl(
    private val apiService: TflApiService = TflNetworkClient.apiService,
    private val dataStore: StationPreferencesDataSource
) : TransitRepository {

    constructor(context: Context) : this(
        apiService = TflNetworkClient.apiService,
        dataStore = StationPreferencesDataStore(context.applicationContext)
    )

    override val savedStationsFlow: Flow<List<Station>> = dataStore.savedStationsFlow
    override val recentStationIdFlow: Flow<String?> = dataStore.recentStationIdFlow

    override suspend fun searchStations(query: String): Result<List<Station>> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.success(DefaultStations.POPULAR_STATIONS)
        }

        try {
            val response = apiService.searchStations(query = trimmed)
            val matchedStations = response.matches.map { match ->
                Station(
                    id = match.id,
                    name = cleanStationName(match.name),
                    modes = match.modes,
                    zone = match.zone,
                    lat = match.lat,
                    lon = match.lon,
                    isFavorite = false
                )
            }

            if (matchedStations.isNotEmpty()) {
                Result.success(matchedStations)
            } else {
                // Check local default stations for a match
                val localMatches = DefaultStations.POPULAR_STATIONS.filter {
                    it.name.contains(trimmed, ignoreCase = true)
                }
                Result.success(localMatches)
            }
        } catch (e: Exception) {
            // Offline fallback: filter local popular stations
            val localMatches = DefaultStations.POPULAR_STATIONS.filter {
                it.name.contains(trimmed, ignoreCase = true)
            }
            if (localMatches.isNotEmpty()) {
                Result.success(localMatches)
            } else {
                Result.failure(e)
            }
        }
    }

    override suspend fun getDepartures(stationId: String, stationName: String): Result<List<Departure>> =
        withContext(Dispatchers.IO) {
            try {
                // Fetch arrivals directly, or expand child stop points if this is a bus station / transit hub
                var rawArrivals = apiService.getArrivals(stationId)

                try {
                    val detail = apiService.getStopPointDetail(stationId)

                    if (rawArrivals.isEmpty()) {
                        // Prioritize tube, rail, and tram child stops before bus stops when expanding hubs
                        val sortedChildren = detail.children.sortedByDescending { child ->
                            when {
                                child.id.startsWith("940G") -> 3 // Tube / Tram / Rail
                                child.id.startsWith("910G") -> 2 // National Rail
                                else -> 1 // Bus stops
                            }
                        }

                        val childArrivals = mutableListOf<com.androidfung.departureboard.data.model.TflArrivalPrediction>()
                        for (child in sortedChildren) {
                            if (child.id != stationId) {
                                try {
                                    val arrivals = apiService.getArrivals(child.id)
                                    if (arrivals.isNotEmpty()) {
                                        childArrivals.addAll(arrivals)
                                    }
                                } catch (_: Exception) {
                                    // continue to next child
                                }
                            }
                        }

                        if (childArrivals.isNotEmpty()) {
                            rawArrivals = childArrivals
                        }
                    }
                } catch (_: Exception) {
                    // ignore detail fetch failures
                }

                if (rawArrivals.isEmpty()) {
                    // Provide fallback if empty list returned
                    val fallbacks = DefaultStations.getFallbackDepartures(stationId, stationName)
                    Result.success(fallbacks)
                } else {
                    // Filter duplicates: at terminus stations (like Edgware), TfL publishes the exact same incoming train
                    // (same vehicleId or same timeToStation & destination) onto multiple platforms before platform assignment is finalized.
                    val distinctArrivals = rawArrivals
                        .distinctBy { item ->
                            if (!item.vehicleId.isNullOrBlank()) {
                                item.vehicleId
                            } else {
                                "${item.lineId}_${item.destinationName}_${item.timeToStation / 30}"
                            }
                        }

                    val departures = distinctArrivals
                        .sortedBy { it.timeToStation }
                        .map { item ->
                            val lineName = item.lineName ?: item.lineId ?: "Transit"
                            val badge = TflLineColors.getLineBadge(
                                lineId = item.lineId,
                                lineName = item.lineName,
                                modeName = item.modeName
                            )
                            val resolvedDest = resolveOutboundDestination(
                                stationName = stationName,
                                itemDestination = item.destinationName,
                                itemTowards = item.towards,
                                lineId = item.lineId,
                                platformName = item.platformName
                            )
                            val isBus = item.modeName.equals("bus", ignoreCase = true) || item.lineId?.toIntOrNull() != null
                            val rawPlatform = item.platformName?.trim() ?: ""
                            val platformDisplay = when {
                                rawPlatform.isNotBlank() && rawPlatform.lowercase() != "null" -> {
                                    when {
                                        isBus && !rawPlatform.startsWith("Stop ", ignoreCase = true) -> "Stop $rawPlatform"
                                        // Elizabeth line core stations often return "A" or "B"
                                        rawPlatform.matches(Regex("^[A-Z0-9]$")) -> "Platform $rawPlatform"
                                        !rawPlatform.startsWith("Platform", ignoreCase = true) &&
                                        !rawPlatform.contains("bound", ignoreCase = true) &&
                                        !rawPlatform.startsWith("Stop", ignoreCase = true) -> "Platform $rawPlatform"
                                        else -> rawPlatform
                                    }
                                }
                                isBus && !item.towards.isNullOrBlank() && item.towards.trim().lowercase() != "null" ->
                                    "towards ${cleanStationName(item.towards)}"
                                else -> if (isBus) "Bus Stand" else "Platform"
                            }

                            val cleanTowards = item.towards?.takeIf { it.trim().lowercase() != "null" }?.let { cleanStationName(it) }

                            Departure(
                                id = item.id,
                                stationId = item.naptanId ?: stationId,
                                stationName = cleanStationName(item.stationName ?: stationName),
                                lineId = item.lineId ?: "transit",
                                lineName = lineName,
                                platformName = platformDisplay,
                                destinationName = resolvedDest.ifBlank { "Destination" },
                                towards = cleanTowards,
                                timeToStationSeconds = item.timeToStation,
                                expectedArrivalIso = item.expectedArrival,
                                currentLocation = item.currentLocation?.takeIf { it.trim().lowercase() != "null" },
                                modeName = item.modeName ?: if (isBus) "bus" else "tube",
                                lineBadge = badge
                            )
                        }

                    Result.success(departures)
                }
            } catch (e: Exception) {
                // If network fails, serve fallback departures for popular stations
                val fallbacks = DefaultStations.getFallbackDepartures(stationId, stationName)
                if (fallbacks.isNotEmpty()) {
                    Result.success(fallbacks)
                } else {
                    Result.failure(e)
                }
            }
        }

    override fun getDeparturesFlow(stationId: String, stationName: String): Flow<Result<List<Departure>>> = flow {
        emit(getDepartures(stationId, stationName))
    }.flowOn(Dispatchers.IO)

    override suspend fun getLineStatuses(): Map<String, com.androidfung.departureboard.data.model.TflLineStatusItem> =
        withContext(Dispatchers.IO) {
            try {
                val statuses = apiService.getLineStatuses()
                statuses.associateBy { it.id.lowercase() }
            } catch (_: Exception) {
                emptyMap()
            }
        }

    override suspend fun saveStation(station: Station) {
        dataStore.saveStation(station)
    }

    override suspend fun reorderStations(stations: List<Station>) {
        dataStore.saveStations(stations)
    }

    override suspend fun removeStation(stationId: String) {
        dataStore.removeStation(stationId)
    }

    override suspend fun setRecentStationId(stationId: String) {
        dataStore.setRecentStationId(stationId)
    }

    private fun cleanStationName(name: String): String {
        return name
            .replace(" Underground Station", "")
            .replace(" Underground", "")
            .replace(" Rail Station", "")
            .replace(" DLR Station", "")
            .replace(" Tram Stop", "")
            .replace(" Bus Station", "")
            .replace(" Station", "")
            .trim()
    }

    /**
     * Resolves the outbound destination for terminus stations when TfL's prediction API reports
     * inbound terminating trains arriving at the station itself (e.g. Edgware, Mill Hill East, Brixton).
     */
    private fun resolveOutboundDestination(
        stationName: String,
        itemDestination: String?,
        itemTowards: String?,
        lineId: String?,
        platformName: String?
    ): String {
        val currentStation = cleanStationName(stationName).lowercase()
        val rawDest = cleanStationName(itemDestination ?: itemTowards ?: "Destination")
        val towardsLower = (itemTowards ?: "").lowercase()
        val platLower = (platformName ?: "").lowercase()
        val lineLower = (lineId ?: "").lowercase()

        val isTerminatingAtThisStation = rawDest.isNotBlank() && rawDest.equals(currentStation, ignoreCase = true)
        if (!isTerminatingAtThisStation) {
            return rawDest
        }

        // The train terminates at this station; infer the departing destination for passengers on the platform
        return when {
            // Northern Line
            "edgware" in currentStation -> when {
                "via cx" in towardsLower || "charing cross" in towardsLower -> "Morden via Charing Cross"
                "via bank" in towardsLower || "bank" in towardsLower -> "Morden via Bank"
                "battersea" in towardsLower -> "Battersea Power Station"
                else -> "Morden / Battersea"
            }
            "mill hill east" in currentStation -> when {
                "battersea" in towardsLower -> "Battersea Power Station"
                "via cx" in towardsLower || "charing cross" in towardsLower -> "Battersea via Charing Cross"
                "via bank" in towardsLower || "bank" in towardsLower -> "Morden via Bank"
                else -> "Finchley Central"
            }
            "high barnet" in currentStation -> when {
                "via cx" in towardsLower || "charing cross" in towardsLower -> "Battersea Power Station"
                "via bank" in towardsLower || "bank" in towardsLower -> "Morden via Bank"
                else -> "Morden / Battersea"
            }
            "morden" in currentStation -> when {
                "via cx" in towardsLower || "charing cross" in towardsLower -> "Edgware via Charing Cross"
                "via bank" in towardsLower || "bank" in towardsLower -> "High Barnet via Bank"
                else -> "Edgware / High Barnet"
            }
            "battersea" in currentStation -> "High Barnet / Edgware via Charing Cross"

            // Victoria Line
            "brixton" in currentStation -> "Walthamstow Central"
            "walthamstow" in currentStation -> "Brixton"

            // Bakerloo Line
            "elephant & castle" in currentStation -> "Harrow & Wealdstone / Queen's Park"
            "harrow & wealdstone" in currentStation -> "Elephant & Castle"

            // Central Line
            "west ruislip" in currentStation -> "Epping via Bank"
            "ealing broadway" in currentStation -> when {
                "district" in lineLower -> "Upminster via Tower Hill"
                "elizabeth" in lineLower -> "Abbey Wood / Shenfield"
                else -> "Epping / Hainault"
            }
            "epping" in currentStation -> "West Ruislip / Ealing Broadway"
            "hainault" in currentStation -> "Central London via Newbury Park"
            "woodford" in currentStation -> "Central London via Hainault"

            // District & Circle Lines
            "wimbledon" in currentStation -> when {
                "edgware road" in towardsLower -> "Edgware Road via High Street Kensington"
                "tower hill" in towardsLower || "upminster" in towardsLower || "barking" in towardsLower -> "Upminster via Tower Hill"
                else -> "Upminster / Edgware Road"
            }
            "richmond" in currentStation -> when {
                "overground" in lineLower || "mildmay" in lineLower -> "Stratford via Highbury & Islington"
                else -> "Upminster via Tower Hill"
            }
            "upminster" in currentStation -> "Richmond / Ealing Broadway / Wimbledon"
            "edgware road" in currentStation -> when {
                "circle" in lineLower -> "Hammersmith via Tower Hill"
                else -> "Wimbledon via High Street Kensington"
            }
            "hammersmith" in currentStation -> when {
                "hammersmith" in lineLower -> "Barking via King's Cross"
                else -> "Edgware Road via Aldgate"
            }
            "barking" in currentStation -> when {
                "hammersmith" in lineLower -> "Hammersmith via King's Cross"
                else -> "Wimbledon / Richmond / Ealing Broadway"
            }

            // Jubilee Line
            "stanmore" in currentStation -> "Stratford via Central London"
            "stratford" in currentStation -> when {
                "jubilee" in lineLower -> "Stanmore via Central London"
                "central" in lineLower -> "West Ruislip / Ealing Broadway"
                "dlr" in lineLower -> "Lewisham / Woolwich Arsenal"
                else -> "Westbound Services"
            }
            "canary wharf" in currentStation && "jubilee" in lineLower -> when {
                "eastbound" in platLower || "platform 2" in platLower -> "Stratford via North Greenwich"
                else -> "Stanmore / Wembley Park"
            }

            // Metropolitan Line
            "aldgate" in currentStation -> "Uxbridge / Watford / Amersham"
            "amersham" in currentStation -> "Aldgate / Baker Street"
            "chesham" in currentStation -> "Aldgate / Baker Street"
            "watford" in currentStation -> "Aldgate / Baker Street"
            "uxbridge" in currentStation -> if ("piccadilly" in lineLower) "Cockfosters" else "Aldgate / Baker Street"

            // Piccadilly Line
            "cockfosters" in currentStation -> "Heathrow / Uxbridge"
            "heathrow terminal 4" in currentStation -> "Cockfosters via Central London"
            "heathrow terminal 5" in currentStation -> when {
                "elizabeth" in lineLower -> "Abbey Wood / Shenfield"
                else -> "Cockfosters via Central London"
            }

            // Waterloo & City Line
            "waterloo" in currentStation && ("waterloo" in lineLower || "city" in lineLower) -> "Bank"
            "bank" in currentStation && ("waterloo" in lineLower || "city" in lineLower) -> "Waterloo"

            // Elizabeth Line
            "reading" in currentStation -> "Abbey Wood / Shenfield"
            "shenfield" in currentStation -> "Reading / Heathrow Terminal 5"
            "abbey wood" in currentStation -> "Reading / Heathrow Terminal 5"

            // DLR
            "tower gateway" in currentStation -> "Beckton via Canary Wharf"
            "beckton" in currentStation -> "Tower Gateway / Bank"
            "lewisham" in currentStation -> "Bank / Stratford"
            "woolwich arsenal" in currentStation -> "Bank / Stratford International"

            // Fallback by platform direction if available
            "southbound" in platLower -> "Southbound Services"
            "northbound" in platLower -> "Northbound Services"
            "eastbound" in platLower -> "Eastbound Services"
            "westbound" in platLower -> "Westbound Services"
            else -> "Outbound Services"
        }
    }
}
