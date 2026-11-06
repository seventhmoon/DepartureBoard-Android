package com.androidfung.departureboard.di

import android.content.Context
import com.androidfung.departureboard.billing.BillingDataSource
import com.androidfung.departureboard.billing.BillingRepository
import com.androidfung.departureboard.data.datastore.StationPreferencesDataSource
import com.androidfung.departureboard.data.datastore.StationPreferencesDataStore
import com.androidfung.departureboard.data.db.cache.DepartureCacheDatabase
import com.androidfung.departureboard.data.db.cache.DepartureDao
import com.androidfung.departureboard.data.db.gb.GbStationDatabase
import com.androidfung.departureboard.data.db.gb.GbStationCrsDao
import com.androidfung.departureboard.data.db.gb.StationCrsDao
import com.androidfung.departureboard.data.db.hk.HkBusStopDao
import com.androidfung.departureboard.data.db.hk.HkStationDatabase
import com.androidfung.departureboard.data.network.gb.BodsApiService
import com.androidfung.departureboard.data.network.gb.BodsNetworkClient
import com.androidfung.departureboard.data.network.gb.NationalRailApiService
import com.androidfung.departureboard.data.network.gb.NationalRailNetworkClient
import com.androidfung.departureboard.data.network.gb.TflApiService
import com.androidfung.departureboard.data.network.gb.TflNetworkClient
import com.androidfung.departureboard.data.network.hk.HkBusApiService
import com.androidfung.departureboard.data.network.hk.HkBusNetworkClient
import com.androidfung.departureboard.data.network.hk.HkMtrApiService
import com.androidfung.departureboard.data.network.hk.HkMtrNetworkClient
import com.androidfung.departureboard.data.repository.TransitRepository
import com.androidfung.departureboard.data.repository.TransitRepositoryImpl
import com.androidfung.departureboard.data.repository.region.gb.NationalRailStationCodes

/**
 * Lightweight, centralized dependency container for the application.
 *
 * Encapsulates instantiation and lifecycle of core singletons (API services, DAOs,
 * DataStore, Repositories) avoiding ad-hoc service-locator calls and redundant constructor logic.
 */
interface AppContainer {
    val applicationContext: Context
    val tflApiService: TflApiService
    val nationalRailApiService: NationalRailApiService
    val bodsApiService: BodsApiService
    val hkMtrApiService: HkMtrApiService
    val kmbBusApiService: HkBusApiService
    val citybusApiService: HkBusApiService
    val stationPreferencesDataSource: StationPreferencesDataSource
    val departureDao: DepartureDao
    val stationCrsDao: GbStationCrsDao
    val gbStationCrsDao: GbStationCrsDao get() = stationCrsDao
    val hkBusStopDao: HkBusStopDao
    val transitRepository: TransitRepository
    val billingRepository: BillingDataSource
}

class DefaultAppContainer(
    override val applicationContext: Context
) : AppContainer {

    init {
        // Ensure shared network and station code singletons receive application context
        TflNetworkClient.initialize(applicationContext)
        NationalRailStationCodes.initialize(applicationContext)
    }

    override val tflApiService: TflApiService by lazy {
        TflNetworkClient.apiService
    }

    override val nationalRailApiService: NationalRailApiService by lazy {
        NationalRailNetworkClient.apiService
    }

    override val bodsApiService: BodsApiService by lazy {
        BodsNetworkClient.apiService
    }

    override val hkMtrApiService: HkMtrApiService by lazy {
        HkMtrNetworkClient.apiService
    }

    override val kmbBusApiService: HkBusApiService by lazy {
        HkBusNetworkClient.kmbApiService
    }

    override val citybusApiService: HkBusApiService by lazy {
        HkBusNetworkClient.citybusApiService
    }

    override val stationPreferencesDataSource: StationPreferencesDataSource by lazy {
        StationPreferencesDataStore(applicationContext)
    }

    override val departureDao: DepartureDao by lazy {
        DepartureCacheDatabase.getInstance(applicationContext).departureDao()
    }

    override val stationCrsDao: StationCrsDao by lazy {
        GbStationDatabase.getInstance(applicationContext).stationCrsDao()
    }

    override val hkBusStopDao: HkBusStopDao by lazy {
        HkStationDatabase.getInstance(applicationContext).hkBusStopDao()
    }

    override val transitRepository: TransitRepository by lazy {
        TransitRepositoryImpl(
            apiService = tflApiService,
            nrApiService = nationalRailApiService,
            bodsApiService = bodsApiService,
            dataStore = stationPreferencesDataSource,
            departureDao = departureDao,
            context = applicationContext
        )
    }

    override val billingRepository: BillingDataSource by lazy {
        BillingRepository.getInstance(applicationContext)
    }
}
