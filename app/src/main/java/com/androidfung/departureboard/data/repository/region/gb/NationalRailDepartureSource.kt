package com.androidfung.departureboard.data.repository.region.gb

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.TransitAlert
import com.androidfung.departureboard.data.model.gb.NrBoardResponse
import com.androidfung.departureboard.data.model.gb.NrServiceDetailsResponse
import com.androidfung.departureboard.data.model.gb.NrTrainService
import com.androidfung.departureboard.data.network.gb.NationalRailApiService
import com.androidfung.departureboard.data.repository.DepartureSource
import com.androidfung.departureboard.data.repository.region.gb.mapper.GbTransitAlertMapper
import com.androidfung.departureboard.data.repository.region.gb.mapper.NationalRailDepartureMapper
import com.androidfung.departureboard.util.CoalescingTimedCache
import com.androidfung.departureboard.util.runSuspendCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

/**
 * Departure source for National Rail (Darwin LDBWS) live departures, rail replacement buses,
 * station NRCC alerts, arrivals, and detailed train formations.
 * Handles CRS code resolution via [NationalRailStationCodes] and queries [NationalRailApiService].
 */
class NationalRailDepartureSource(
    private val nrApiService: NationalRailApiService
) : DepartureSource {

    private val nrccAlertsByCrs = ConcurrentHashMap<String, List<TransitAlert>>()
    private val serviceDetailsCache = CoalescingTimedCache<String, NrServiceDetailsResponse?>(ttlMillis = 60_000L)
    private val sourceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun canHandle(stationId: String, stationName: String, isBusStop: Boolean): Boolean {
        if (isBusStop) return false
        return true
    }

    override suspend fun fetchDepartures(
        stationId: String,
        stationName: String,
        isBusStop: Boolean
    ): Result<List<Departure>> = coroutineScope {
        if (isBusStop) return@coroutineScope Result.success(emptyList())

        val crsCodes = NationalRailStationCodes.findCrsCodes(stationId, stationName)
        if (crsCodes.isEmpty()) {
            return@coroutineScope Result.success(emptyList())
        }

        val perCrsResults = crsCodes.map { crs ->
            async {
                runSuspendCatching {
                    val board = runSuspendCatching { nrApiService.getDepBoardWithDetails(crs) }
                        .getOrElse { nrApiService.getDepartureBoard(crs) }
                    cacheNrccMessages(crs, board)
                    extractServices(board, crs)
                }
            }
        }.awaitAll()

        var lastError: Throwable? = null
        val collectedServices = mutableListOf<NrTrainService>()
        for (res in perCrsResults) {
            res.fold(
                onSuccess = { collectedServices.addAll(it) },
                onFailure = { lastError = it }
            )
        }

        if (collectedServices.isEmpty() && lastError != null) {
            return@coroutineScope Result.failure(lastError)
        }

        val departures = collectedServices.map { service ->
            NationalRailDepartureMapper.mapToDeparture(service, stationId, stationName)
        }

        Result.success(departures)
    }

    suspend fun fetchArrivals(
        stationId: String,
        stationName: String
    ): Result<List<Departure>> = coroutineScope {
        val crsCodes = NationalRailStationCodes.findCrsCodes(stationId, stationName)
        if (crsCodes.isEmpty()) return@coroutineScope Result.success(emptyList())

        val perCrsResults = crsCodes.map { crs ->
            async {
                runSuspendCatching {
                    val board = nrApiService.getArrBoardWithDetails(crs)
                    cacheNrccMessages(crs, board)
                    (board.trainServices.orEmpty() + board.busServices.orEmpty()).filter { service ->
                        !service.std.isNullOrBlank() || !service.etd.isNullOrBlank()
                    }
                }
            }
        }.awaitAll()

        var lastError: Throwable? = null
        val collected = mutableListOf<NrTrainService>()
        for (res in perCrsResults) {
            res.fold(
                onSuccess = { collected.addAll(it) },
                onFailure = { lastError = it }
            )
        }

        if (collected.isEmpty() && lastError != null) {
            return@coroutineScope Result.failure(lastError)
        }

        Result.success(
            collected.map { NationalRailDepartureMapper.mapToDeparture(it, stationId, stationName) }
        )
    }

    suspend fun fetchServiceDetails(serviceId: String): NrServiceDetailsResponse? {
        if (serviceId.isBlank()) return null
        return serviceDetailsCache.getOrPutCoalesced(key = serviceId, scope = sourceScope) {
            runSuspendCatching { nrApiService.getServiceDetails(serviceId) }.getOrNull()
        }
    }

    suspend fun getCachedNrccAlerts(stationId: String, stationName: String): List<TransitAlert> {
        val crsCodes = NationalRailStationCodes.findCrsCodes(stationId, stationName)
        return crsCodes.flatMap { crs -> nrccAlertsByCrs[crs.uppercase()].orEmpty() }
    }

    fun getAllCachedNrccAlerts(): List<TransitAlert> =
        nrccAlertsByCrs.values.flatten().distinctBy { it.descriptionText ?: it.headerText }

    private fun extractServices(board: NrBoardResponse, crs: String): List<NrTrainService> {
        val combined = board.trainServices.orEmpty() + board.busServices.orEmpty()
        return combined.filter { service ->
            val destCrs = service.destination.firstOrNull()?.crs
            (!service.std.isNullOrBlank() || !service.etd.isNullOrBlank()) &&
                (destCrs == null || !destCrs.equals(crs, ignoreCase = true))
        }
    }

    private fun cacheNrccMessages(crs: String, board: NrBoardResponse) {
        val mapped = board.nrccMessages.orEmpty().mapIndexedNotNull { idx, msg ->
            GbTransitAlertMapper.fromNrccMessage(msg, stationCrs = crs, index = idx)
        }
        if (mapped.isNotEmpty()) {
            nrccAlertsByCrs[crs.uppercase()] = mapped
        } else {
            nrccAlertsByCrs.remove(crs.uppercase())
        }
    }
}
