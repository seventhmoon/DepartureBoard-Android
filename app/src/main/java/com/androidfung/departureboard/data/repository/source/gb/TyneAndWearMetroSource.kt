package com.androidfung.departureboard.data.repository.source.gb

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.StationLineInfo
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.network.TyneAndWearMetroApiService
import com.androidfung.departureboard.data.network.TyneAndWearMetroNetworkClient
import com.androidfung.departureboard.data.repository.source.DepartureSource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Departure source for Tyne and Wear Metro stations (Newcastle & Sunderland)
 * querying Nexus RTI API.
 */
class TyneAndWearMetroSource(
    private val apiService: TyneAndWearMetroApiService = TyneAndWearMetroNetworkClient.apiService
) : DepartureSource {

    companion object {
        // Known 3-letter station codes for Tyne and Wear Metro
        val METRO_STATIONS = mapOf(
            "CEN" to "Central Station (Metro)",
            "MTS" to "Monument",
            "MTW" to "Monument",
            "HAY" to "Haymarket",
            "SGF" to "South Gosforth",
            "AIR" to "Newcastle Airport (Metro)",
            "STJ" to "St James",
            "SUN" to "Sunderland (Metro)",
            "NCL" to "Central Station",
            "GAT" to "Gateshead",
            "GHD" to "Gateshead Stadium",
            "JES" to "Jesmond",
            "WJS" to "West Jesmond",
            "ILF" to "Ilford Road",
            "REG" to "Regent Centre",
            "KGP" to "Kingston Park",
            "HEW" to "Heworth",
            "MAN" to "Manors",
            "BYK" to "Byker",
            "WLS" to "Wallsend",
            "NSH" to "North Shields",
            "TYN" to "Tynemouth",
            "CUL" to "Cullercoats",
            "WTL" to "Whitley Bay",
            "SSS" to "South Shields",
            "SOL" to "Stadium of Light",
            "PLI" to "Park Lane",
            "SHL" to "South Hylton"
        )

        private val YellowLineColor = Color(0xFFF7B11E)
        private val GreenLineColor = Color(0xFF009640)

        fun searchMetroStations(query: String): List<Station> {
            val trimmed = query.trim().lowercase()
            if (trimmed.length < 3) return emptyList()
            return METRO_STATIONS.entries
                .filter { (code, _) -> code != "MTW" && code != "NCL" }
                .filter { (code, name) ->
                    code.equals(trimmed, ignoreCase = true) ||
                        name.lowercase().contains(trimmed) ||
                        (trimmed.contains("metro") && name.lowercase().contains(trimmed.replace("metro", "").trim()))
                }
                .map { (code, name) ->
                    Station(
                        id = code,
                        name = if (name.contains("Metro")) name else "$name (Tyne & Wear Metro)",
                        modes = listOf("tube"),
                        zone = "Tyne & Wear",
                        lines = listOf(
                            StationLineInfo("metro-yellow", "Yellow Line", "tube"),
                            StationLineInfo("metro-green", "Green Line", "tube")
                        ),
                        isFavorite = false
                    )
                }
        }
    }

    override fun canHandle(stationId: String, stationName: String, isBusStop: Boolean): Boolean {
        if (isBusStop) return false
        val idUpper = stationId.uppercase()
        val nameLower = stationName.lowercase()
        return METRO_STATIONS.containsKey(idUpper) ||
                nameLower.contains("tyne and wear") ||
                nameLower.contains("tyne & wear") ||
                nameLower.contains("nexus metro") ||
                (nameLower.contains("monument") && !nameLower.contains("bank"))
    }

    override suspend fun fetchDepartures(
        stationId: String,
        stationName: String,
        isBusStop: Boolean
    ): Result<List<Departure>> = coroutineScope {
        try {
            val code = when {
                METRO_STATIONS.containsKey(stationId.uppercase()) -> {
                    val raw = stationId.uppercase()
                    if (raw == "NCL") "CEN" else raw
                }
                stationId.length == 3 -> stationId.uppercase()
                stationName.contains("Monument", ignoreCase = true) -> "MTS"
                stationName.contains("Central", ignoreCase = true) || stationId.equals("910GNWCSTLE", ignoreCase = true) -> "CEN"
                stationName.contains("Haymarket", ignoreCase = true) -> "HAY"
                stationName.contains("Gateshead", ignoreCase = true) -> "GAT"
                stationName.contains("Jesmond", ignoreCase = true) -> "JES"
                stationName.contains("Tynemouth", ignoreCase = true) -> "TYN"
                stationName.contains("Whitley Bay", ignoreCase = true) -> "WTL"
                stationName.contains("South Shields", ignoreCase = true) -> "SSS"
                else -> return@coroutineScope Result.success(emptyList())
            }

            // Query platforms 1 and 2 concurrently
            val p1Deferred = async { try { apiService.getPlatformTimes(code, 1) } catch (_: Exception) { emptyList() } }
            val p2Deferred = async { try { apiService.getPlatformTimes(code, 2) } catch (_: Exception) { emptyList() } }

            val p1 = p1Deferred.await()
            val p2 = p2Deferred.await()

            val departures = mutableListOf<Departure>()

            fun mapItems(items: List<com.androidfung.departureboard.data.network.NexusMetroDepartureItem>, platformNum: Int) {
                items.forEach { item ->
                    val mins = item.dueIn.coerceAtLeast(0)
                    val seconds = mins * 60
                    val dest = item.destination?.ifBlank { null } ?: "Metro Service"
                    val isGreen = dest.contains("Airport", ignoreCase = true) || dest.contains("South Hylton", ignoreCase = true)
                    val badgeColor = if (isGreen) GreenLineColor else YellowLineColor
                    val lineDisplayName = if (isGreen) "Green Line" else "Yellow Line"

                    val badge = LineBadgeInfo(
                        lineId = if (isGreen) "metro-green" else "metro-yellow",
                        displayName = lineDisplayName,
                        backgroundColor = badgeColor,
                        textColor = if (isGreen) Color.White else Color.Black,
                        mode = TransitMode.TUBE,
                        lineCode = "MET"
                    )

                    departures.add(
                        Departure(
                            id = "nexus_${code}_${platformNum}_${item.trn ?: mins}_$seconds",
                            stationId = stationId,
                            stationName = stationName,
                            lineId = badge.lineId,
                            lineName = lineDisplayName,
                            platformName = "Platform $platformNum",
                            destinationName = dest,
                            towards = dest,
                            timeToStationSeconds = seconds,
                            expectedArrivalIso = null,
                            currentLocation = item.lastEventLocation,
                            modeName = "tube",
                            status = if (mins == 0) DepartureStatus.DUE else DepartureStatus.ON_TIME,
                            lineBadge = badge
                        )
                    )
                }
            }

            mapItems(p1, 1)
            mapItems(p2, 2)

            Result.success(departures.sortedBy { it.timeToStationSeconds })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
