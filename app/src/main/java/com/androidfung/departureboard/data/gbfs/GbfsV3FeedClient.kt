package com.androidfung.departureboard.data.gbfs

import com.androidfung.departureboard.data.model.MicromobilityHub
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.TransitRegion
import com.androidfung.departureboard.data.network.SharedNetworkConfig
import com.androidfung.departureboard.util.CoalescingTimedCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Cross-region General Bikeshare Feed Specification (GBFS v3.0 & v2.x compatible) adapter.
 * Resolves `gbfs.json` auto-discovery, `station_information`, `station_status`, and `vehicle_types`
 * to surface nearby bike & e-scooter hubs around stations across supported transit regions.
 */
class GbfsV3FeedClient(
    private val fetchJson: suspend (String) -> String? = ::defaultFetchJson
) {

    data class GbfsFeedEndpoint(
        val operatorName: String,
        val discoveryUrl: String
    )

    data class GbfsStationInfo(
        val stationId: String,
        val name: String,
        val lat: Double,
        val lon: Double
    )

    data class GbfsStationStatus(
        val stationId: String,
        val bikesAvailable: Int,
        val ebikesAvailable: Int,
        val docksAvailable: Int,
        val isRenting: Boolean
    )

    private data class CachedOperatorSnapshot(
        val stations: List<GbfsStationInfo>,
        val statusesById: Map<String, GbfsStationStatus>
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val snapshotCache = CoalescingTimedCache<String, CachedOperatorSnapshot?>(
        ttlMillis = 120_000L
    )

    suspend fun enrichStationIfNeeded(station: Station, radiusMeters: Int = 500): Station {
        if (station.micromobilityHubs.isNotEmpty()) return station
        val lat = station.lat ?: return station
        val lon = station.lon ?: return station
        val hubs = findNearbyHubs(
            region = inferRegion(station.id),
            lat = lat,
            lon = lon,
            radiusMeters = radiusMeters
        )
        return if (hubs.isNotEmpty()) station.copy(micromobilityHubs = hubs) else station
    }

    suspend fun findNearbyHubs(
        region: TransitRegion,
        lat: Double,
        lon: Double,
        radiusMeters: Int = 500,
        limit: Int = 4
    ): List<MicromobilityHub> = withContext(Dispatchers.IO) {
        val feeds = REGION_FEEDS[region].orEmpty()
        if (feeds.isEmpty()) return@withContext emptyList()

        val allHubs = mutableListOf<MicromobilityHub>()
        for (feed in feeds) {
            val snapshot = snapshotCache.getOrPutCoalesced(
                key = feed.discoveryUrl,
                scope = scope,
                shouldCache = { it != null }
            ) {
                loadOperatorSnapshot(feed)
            } ?: continue

            for (st in snapshot.stations) {
                val distance = haversineMeters(lat, lon, st.lat, st.lon).toInt()
                if (distance > radiusMeters) continue
                val status = snapshot.statusesById[st.stationId] ?: continue
                if (!status.isRenting) continue
                allHubs += MicromobilityHub(
                    id = "gbfs_${feed.operatorName}_${st.stationId}",
                    name = st.name,
                    totalBikesAvailable = status.bikesAvailable,
                    totalEbikesAvailable = status.ebikesAvailable,
                    totalDocksAvailable = status.docksAvailable,
                    operatorName = "${feed.operatorName} • ${st.name}",
                    distanceMeters = distance
                )
            }
        }
        allHubs.sortedBy { it.distanceMeters ?: Int.MAX_VALUE }.take(limit)
    }

    private suspend fun loadOperatorSnapshot(feed: GbfsFeedEndpoint): CachedOperatorSnapshot? {
        return try {
            val discoveryRaw = fetchJson(feed.discoveryUrl) ?: return null
            val discoveryUrls = parseDiscoveryUrls(discoveryRaw)
            val infoUrl = discoveryUrls["station_information"] ?: return null
            val statusUrl = discoveryUrls["station_status"] ?: return null

            val infoRaw = fetchJson(infoUrl) ?: return null
            val statusRaw = fetchJson(statusUrl) ?: return null
            val vehicleTypesRaw = discoveryUrls["vehicle_types"]?.let { fetchJson(it) }

            val electricTypeIds = vehicleTypesRaw?.let(::parseElectricVehicleTypeIds).orEmpty()
            val stations = parseStationInformation(infoRaw)
            val statuses = parseStationStatus(statusRaw, electricTypeIds).associateBy { it.stationId }
            CachedOperatorSnapshot(stations, statuses)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private fun inferRegion(stationId: String): TransitRegion = when {
            stationId.startsWith("HK_") -> TransitRegion.HK
            stationId.startsWith("SG_") -> TransitRegion.SG
            stationId.startsWith("AU_") -> TransitRegion.AU
            stationId.startsWith("JP_") -> TransitRegion.JP
            else -> TransitRegion.GB
        }

        private val REGION_FEEDS: Map<TransitRegion, List<GbfsFeedEndpoint>> = mapOf(
            TransitRegion.GB to listOf(
                GbfsFeedEndpoint(
                    operatorName = "Santander Cycles",
                    discoveryUrl = "https://tfl.gov.uk/tfl/syndication/feeds/cycle-hire/gbfs.json"
                )
            ),
            TransitRegion.JP to listOf(
                GbfsFeedEndpoint(
                    operatorName = "Docomo Bike Share",
                    discoveryUrl = "https://api-public.odpt.org/api/v4/gbfs/docomo-cycle-tokyo/gbfs.json"
                ),
                GbfsFeedEndpoint(
                    operatorName = "HELLO CYCLING",
                    discoveryUrl = "https://api-public.odpt.org/api/v4/gbfs/hellocycling/gbfs.json"
                )
            ),
            TransitRegion.AU to listOf(
                GbfsFeedEndpoint(
                    operatorName = "Lime Sydney",
                    discoveryUrl = "https://data.lime.bike/api/partners/v2/gbfs/sydney/gbfs.json"
                )
            ),
            TransitRegion.SG to listOf(
                GbfsFeedEndpoint(
                    operatorName = "Anywheel SG",
                    discoveryUrl = "https://gbfs.anywheel.sg/v3/gbfs.json"
                )
            ),
            TransitRegion.HK to listOf(
                GbfsFeedEndpoint(
                    operatorName = "LocoBike HK",
                    discoveryUrl = "https://gbfs.locobike.hk/v3/gbfs.json"
                )
            )
        )

        private val httpClient: OkHttpClient by lazy {
            SharedNetworkConfig.baseClient
        }

        private suspend fun defaultFetchJson(url: String): String? = withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                resp.body.string()
            }
        }

        private val moshi: Moshi by lazy { Moshi.Builder().build() }
        private val mapAdapter by lazy { moshi.adapter(Map::class.java) }

        @Suppress("UNCHECKED_CAST")
        private fun parseJsonMap(rawJson: String): Map<String, Any?>? =
            runCatching { mapAdapter.fromJson(rawJson) as? Map<String, Any?> }.getOrNull()

        @Suppress("UNCHECKED_CAST")
        private fun Map<String, Any?>.obj(key: String): Map<String, Any?>? =
            this[key] as? Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        private fun Map<String, Any?>.arr(key: String): List<Map<String, Any?>> =
            (this[key] as? List<*>)?.mapNotNull { it as? Map<String, Any?> }.orEmpty()

        /**
         * Parses `gbfs.json` discovery feeds supporting both GBFS v3.0 (`data.feeds`) and
         * GBFS v2.x (`data.<lang>.feeds`).
         */
        fun parseDiscoveryUrls(rawJson: String): Map<String, String> {
            val data = parseJsonMap(rawJson)?.obj("data") ?: return emptyMap()
            val feedsList = data.arr("feeds").ifEmpty {
                data.values.firstNotNullOfOrNull { value ->
                    (value as? Map<*, *>)?.let { sub ->
                        @Suppress("UNCHECKED_CAST")
                        (sub as Map<String, Any?>).arr("feeds").takeIf { it.isNotEmpty() }
                    }
                }.orEmpty()
            }
            if (feedsList.isEmpty()) return emptyMap()

            val result = mutableMapOf<String, String>()
            for (item in feedsList) {
                val name = (item["name"] as? String)?.trim().orEmpty()
                val url = (item["url"] as? String)?.trim().orEmpty()
                if (name.isNotEmpty() && url.startsWith("https://")) {
                    result[name] = url
                }
            }
            return result
        }

        fun parseElectricVehicleTypeIds(rawJson: String): Set<String> {
            val data = parseJsonMap(rawJson)?.obj("data") ?: return emptySet()
            val types = data.arr("vehicle_types")
            val electricIds = mutableSetOf<String>()
            for (obj in types) {
                val id = (obj["vehicle_type_id"] as? String)?.trim().orEmpty()
                val propulsion = (obj["propulsion_type"] as? String)?.lowercase().orEmpty()
                if (id.isNotEmpty() && "electric" in propulsion) {
                    electricIds += id
                }
            }
            return electricIds
        }

        fun parseStationInformation(rawJson: String): List<GbfsStationInfo> {
            val data = parseJsonMap(rawJson)?.obj("data") ?: return emptyList()
            val stations = data.arr("stations")
            val list = ArrayList<GbfsStationInfo>(stations.size)
            for (obj in stations) {
                val id = (obj["station_id"] as? String)?.trim().orEmpty()
                val name = extractLocalizedString(obj["name"])
                val lat = (obj["lat"] as? Number)?.toDouble() ?: Double.NaN
                val lon = (obj["lon"] as? Number)?.toDouble() ?: Double.NaN
                if (id.isNotEmpty() && name.isNotEmpty() && !lat.isNaN() && !lon.isNaN()) {
                    list += GbfsStationInfo(id, name, lat, lon)
                }
            }
            return list
        }

        fun parseStationStatus(
            rawJson: String,
            electricVehicleTypeIds: Set<String> = emptySet()
        ): List<GbfsStationStatus> {
            val data = parseJsonMap(rawJson)?.obj("data") ?: return emptyList()
            val stations = data.arr("stations")
            val list = ArrayList<GbfsStationStatus>(stations.size)
            for (obj in stations) {
                val id = (obj["station_id"] as? String)?.trim().orEmpty()
                if (id.isEmpty()) continue
                val totalVehicles = ((obj["num_vehicles_available"] as? Number)
                    ?: (obj["num_bikes_available"] as? Number))?.toInt() ?: 0
                val docks = (obj["num_docks_available"] as? Number)?.toInt() ?: 0
                val isRenting = (obj["is_renting"] as? Boolean) ?: true

                var ebikes = 0
                val typesArr = obj.arr("vehicle_types_available")
                if (typesArr.isNotEmpty() && electricVehicleTypeIds.isNotEmpty()) {
                    for (tObj in typesArr) {
                        val typeId = tObj["vehicle_type_id"] as? String
                        if (typeId in electricVehicleTypeIds) {
                            ebikes += (tObj["count"] as? Number)?.toInt() ?: 0
                        }
                    }
                }
                val mechanicalBikes = (totalVehicles - ebikes).coerceAtLeast(0)
                list += GbfsStationStatus(
                    stationId = id,
                    bikesAvailable = mechanicalBikes,
                    ebikesAvailable = ebikes,
                    docksAvailable = docks,
                    isRenting = isRenting
                )
            }
            return list
        }

        private fun extractLocalizedString(raw: Any?): String {
            return when (raw) {
                is String -> raw.trim()
                is List<*> -> {
                    var fallback = ""
                    for (item in raw) {
                        val entry = item as? Map<*, *> ?: continue
                        val text = (entry["text"] as? String)?.trim().orEmpty()
                        val lang = (entry["language"] as? String)?.lowercase().orEmpty()
                        if (lang.startsWith("en") && text.isNotEmpty()) return text
                        if (fallback.isEmpty() && text.isNotEmpty()) fallback = text
                    }
                    fallback
                }
                else -> ""
            }
        }

        fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6_371_000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
            return 2 * r * asin(sqrt(a))
        }
    }
}
