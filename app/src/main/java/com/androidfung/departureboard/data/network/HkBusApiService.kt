package com.androidfung.departureboard.data.network

import com.androidfung.departureboard.data.model.HkCitybusEtaResponse
import com.androidfung.departureboard.data.model.HkKmbStopEtaResponse
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Retrofit interface for Hong Kong Bus open data APIs (KMB / LWB and Citybus via data.gov.hk).
 */
interface HkBusApiService {

    /**
     * Retrieves all live ETA predictions for all routes calling at a given KMB/LWB stop.
     * Base URL: https://data.etabus.gov.hk/
     * Example: https://data.etabus.gov.hk/v1/transport/kmb/stop-eta/001554
     */
    @GET("v1/transport/kmb/stop-eta/{stop_id}")
    suspend fun getKmbStopEta(
        @Path("stop_id") stopId: String
    ): HkKmbStopEtaResponse

    /**
     * Retrieves ordered stop sequence for a specific KMB route and direction.
     * Example: https://data.etabus.gov.hk/v1/transport/kmb/route-stop/1A/outbound/1
     */
    @GET("v1/transport/kmb/route-stop/{route}/{direction}/{service_type}")
    suspend fun getKmbRouteStops(
        @Path("route") route: String,
        @Path("direction") direction: String,
        @Path("service_type") serviceType: String = "1"
    ): com.androidfung.departureboard.data.model.HkKmbRouteStopResponse

    /**
     * Retrieves stop name and coordinate details for a specific KMB stop ID.
     * Example: https://data.etabus.gov.hk/v1/transport/kmb/stop/BDE7696BD66CE10F
     */
    @GET("v1/transport/kmb/stop/{stop_id}")
    suspend fun getKmbStopDetail(
        @Path("stop_id") stopId: String
    ): com.androidfung.departureboard.data.model.HkKmbStopDetailResponse

    /**
     * Retrieves live ETA predictions for a specific route and stop on Citybus.
     * Base URL: https://rt.data.gov.hk/
     * Example: https://rt.data.gov.hk/v2/transport/citybus/eta/CTB/001554/102
     */
    @GET("v2/transport/citybus/eta/{company_id}/{stop_id}/{route}")
    suspend fun getCitybusEta(
        @Path("company_id") companyId: String = "CTB",
        @Path("stop_id") stopId: String,
        @Path("route") route: String
    ): HkCitybusEtaResponse
}
