package com.androidfung.departureboard.data.repository.region.gb

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.gb.NrTrainService
import com.androidfung.departureboard.data.network.gb.NationalRailApiService
import com.androidfung.departureboard.data.repository.DepartureSource
import com.androidfung.departureboard.data.repository.region.gb.mapper.NationalRailDepartureMapper

/**
 * Departure source for National Rail (Darwin LDBWS) live departures.
 * Handles CRS code resolution via [NationalRailStationCodes] and queries [NationalRailApiService].
 */
class NationalRailDepartureSource(
    private val nrApiService: NationalRailApiService
) : DepartureSource {

    override fun canHandle(stationId: String, stationName: String, isBusStop: Boolean): Boolean {
        if (isBusStop) return false
        return true
    }

    override suspend fun fetchDepartures(
        stationId: String,
        stationName: String,
        isBusStop: Boolean
    ): Result<List<Departure>> {
        if (isBusStop) return Result.success(emptyList())

        val crsCodes = NationalRailStationCodes.findCrsCodes(stationId, stationName)
        if (crsCodes.isEmpty()) {
            return Result.success(emptyList())
        }

        var lastError: Exception? = null
        val collectedServices = mutableListOf<NrTrainService>()

        for (crs in crsCodes) {
            try {
                val board = try {
                    nrApiService.getDepBoardWithDetails(crs)
                } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    nrApiService.getDepartureBoard(crs)
                }
                board.trainServices?.filter { service ->
                    val destCrs = service.destination.firstOrNull()?.crs
                    (!service.std.isNullOrBlank() || !service.etd.isNullOrBlank()) &&
                        (destCrs == null || !destCrs.equals(crs, ignoreCase = true))
                }?.let { collectedServices.addAll(it) }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }

        if (collectedServices.isEmpty() && lastError != null) {
            return Result.failure(lastError)
        }

        val departures = collectedServices.map { service ->
            NationalRailDepartureMapper.mapToDeparture(service, stationId, stationName)
        }

        return Result.success(departures)
    }
}
