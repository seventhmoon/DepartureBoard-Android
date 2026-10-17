package com.androidfung.departureboard.data.repository.source

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.TransitMode
import com.androidfung.departureboard.data.network.TyneAndWearMetroApiService
import com.androidfung.departureboard.data.network.TyneAndWearMetroNetworkClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
        private val METRO_STATIONS = mapOf(
            "CEN" to "Central Station",
            "MTS" to "Monument",
            "MTW" to "Monument",
            "HAY" to "Haymarket",
            "SGF" to "South Gosforth",
            "AIR" to "Airport",
            "STJ" to "St James",
            "SUN" to "Sunderland",
            "NCL" to "Central Station"
        )

        private val YellowLineColor = Color(0xFFF7B11E)
        private val GreenLineColor = Color(0xFF009640)
    }

    override fun canHandle(stationId: String, stationName: String, isBusStop: Boolean): Boolean {
        if (isBusStop) return false
        val idUpper = stationId.uppercase()
        val nameLower = stationName.lowercase()
        return METRO_STATIONS.containsKey(idUpper) ||
                nameLower.contains("tyne and wear") ||
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
                stationId.length == 3 -> stationId.uppercase()
                stationName.contains("Monument", ignoreCase = true) -> "MTS"
                stationName.contains("Central", ignoreCase = true) || stationId.equals("910GNWCSTLE", ignoreCase = true) -> "CEN"
                stationName.contains("Haymarket", ignoreCase = true) -> "HAY"
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
