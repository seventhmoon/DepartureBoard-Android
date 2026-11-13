package com.androidfung.departureboard.data.gtfs

import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.OccupancyStatus
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Lightweight, zero-dependency GTFS-Realtime (Protocol Buffers v2/v3 wire format) decoder
 * supporting `FeedMessage`, `TripUpdate`, `VehiclePosition`, and `Alert` entities per the
 * MobilityData GTFS-RT specification.
 */
object GtfsRealtimeDecoder {

    enum class GtfsOccupancyStatus(val code: Int) {
        EMPTY(0),
        MANY_SEATS_AVAILABLE(1),
        FEW_SEATS_AVAILABLE(2),
        STANDING_ROOM_ONLY(3),
        CRUSHED_STANDING_ROOM_ONLY(4),
        FULL(5),
        NOT_ACCEPTING_PASSENGERS(6);

        companion object {
            fun fromCode(code: Int): GtfsOccupancyStatus? = entries.firstOrNull { it.code == code }
        }
    }

    data class TripDescriptor(
        val tripId: String = "",
        val routeId: String = "",
        val scheduleRelationship: Int = 0 // 0=SCHEDULED, 1=ADDED, 2=UNSCHEDULED, 3=CANCELED
    )

    data class StopTimeUpdate(
        val stopSequence: Int = 0,
        val stopId: String = "",
        val arrivalDelaySeconds: Int? = null,
        val departureDelaySeconds: Int? = null,
        val scheduleRelationship: Int = 0 // 0=SCHEDULED, 1=SKIPPED, 2=NO_DATA
    )

    data class TripUpdate(
        val trip: TripDescriptor = TripDescriptor(),
        val delaySeconds: Int? = null,
        val stopTimeUpdates: List<StopTimeUpdate> = emptyList()
    )

    data class VehiclePosition(
        val trip: TripDescriptor = TripDescriptor(),
        val currentStopSequence: Int? = null,
        val stopId: String = "",
        val occupancyStatus: GtfsOccupancyStatus? = null
    )

    data class ServiceAlert(
        val headerText: String = "",
        val descriptionText: String = "",
        val routeIds: List<String> = emptyList(),
        val stopIds: List<String> = emptyList()
    )

    data class FeedEntity(
        val id: String,
        val isDeleted: Boolean = false,
        val tripUpdate: TripUpdate? = null,
        val vehiclePosition: VehiclePosition? = null,
        val alert: ServiceAlert? = null
    )

    data class FeedMessage(
        val gtfsRealtimeVersion: String = "",
        val timestampSeconds: Long = 0L,
        val entities: List<FeedEntity> = emptyList()
    )

    fun decode(bytes: ByteArray): FeedMessage {
        if (bytes.isEmpty()) return FeedMessage()
        return ByteArrayInputStream(bytes).use { stream ->
            var version = ""
            var timestamp = 0L
            val entities = mutableListOf<FeedEntity>()
            readMessage(stream, bytes.size) { fieldNumber, wireType, reader ->
                when (fieldNumber) {
                    1 if wireType == WIRE_LEN -> {
                        val sub = reader.readBytes()
                        readMessage(ByteArrayInputStream(sub), sub.size) { hField, hWire, hReader ->
                            when {
                                hField == 1 && hWire == WIRE_LEN -> version = hReader.readString()
                                hField == 3 && hWire == WIRE_VARINT -> timestamp = hReader.readVarint()
                                else -> hReader.skip(hWire)
                            }
                        }
                    }
                    2 if wireType == WIRE_LEN -> {
                        val sub = reader.readBytes()
                        entities.add(parseFeedEntity(sub))
                    }
                    else -> reader.skip(wireType)
                }
            }
            FeedMessage(version, timestamp, entities)
        }
    }

    /**
     * Reconciles GTFS-RT `TripUpdate` delays/cancellations and `VehiclePosition` occupancy levels
     * onto static/live [Departure] items matched by `tripId` (`departure.id`) or `routeId` (`departure.lineBadge.id`).
     */
    fun applyRealtimeUpdates(
        departures: List<Departure>,
        feed: FeedMessage
    ): List<Departure> {
        if (departures.isEmpty() || feed.entities.isEmpty()) return departures
        val tripUpdatesByTripId = mutableMapOf<String, TripUpdate>()
        val occupancyByTripId = mutableMapOf<String, OccupancyStatus>()
        for (entity in feed.entities) {
            if (entity.isDeleted) continue
            entity.tripUpdate?.let { tu ->
                if (tu.trip.tripId.isNotBlank()) tripUpdatesByTripId[tu.trip.tripId] = tu
            }
            entity.vehiclePosition?.let { vp ->
                val status = vp.occupancyStatus?.let { OccupancyStatus.entries.firstOrNull { o -> o.gtfsCode == it.code } }
                if (status != null && vp.trip.tripId.isNotBlank()) {
                    occupancyByTripId[vp.trip.tripId] = status
                }
            }
        }
        return departures.map { dep ->
            val tripUpdate = tripUpdatesByTripId[dep.id]
            val rtOccupancy = occupancyByTripId[dep.id]
            if (tripUpdate == null && rtOccupancy == null) return@map dep

            val isCancelled = tripUpdate?.trip?.scheduleRelationship == 3
            val delaySecs = tripUpdate?.delaySeconds
                ?: tripUpdate?.stopTimeUpdates?.firstOrNull()?.departureDelaySeconds
                ?: tripUpdate?.stopTimeUpdates?.firstOrNull()?.arrivalDelaySeconds
            val delayMinutes = delaySecs?.let { it / 60 } ?: 0
            val updatedStatus = when {
                isCancelled -> DepartureStatus.CANCELLED
                delayMinutes > 1 -> DepartureStatus.DELAYED
                else -> dep.status
            }
            val updatedSecs = if (delaySecs != null && !isCancelled) {
                (dep.timeToStationSeconds + delaySecs).coerceAtLeast(0)
            } else {
                dep.timeToStationSeconds
            }
            dep.copy(
                status = updatedStatus,
                timeToStationSeconds = updatedSecs,
                occupancyStatus = rtOccupancy?.name ?: dep.occupancyStatus
            )
        }
    }

    private fun parseFeedEntity(bytes: ByteArray): FeedEntity {
        var id = ""
        var isDeleted = false
        var tripUpdate: TripUpdate? = null
        var vehiclePosition: VehiclePosition? = null
        var alert: ServiceAlert? = null
        readMessage(ByteArrayInputStream(bytes), bytes.size) { field, wire, reader ->
            when {
                field == 1 && wire == WIRE_LEN -> id = reader.readString()
                field == 2 && wire == WIRE_VARINT -> isDeleted = reader.readVarint() != 0L
                field == 3 && wire == WIRE_LEN -> tripUpdate = parseTripUpdate(reader.readBytes())
                field == 4 && wire == WIRE_LEN -> vehiclePosition = parseVehiclePosition(reader.readBytes())
                field == 5 && wire == WIRE_LEN -> alert = parseAlert(reader.readBytes())
                else -> reader.skip(wire)
            }
        }
        return FeedEntity(id, isDeleted, tripUpdate, vehiclePosition, alert)
    }

    private fun parseTripDescriptor(bytes: ByteArray): TripDescriptor {
        var tripId = ""
        var routeId = ""
        var rel = 0
        readMessage(ByteArrayInputStream(bytes), bytes.size) { field, wire, reader ->
            when {
                field == 1 && wire == WIRE_LEN -> tripId = reader.readString()
                field == 5 && wire == WIRE_LEN -> routeId = reader.readString()
                field == 4 && wire == WIRE_VARINT -> rel = reader.readVarint().toInt()
                else -> reader.skip(wire)
            }
        }
        return TripDescriptor(tripId, routeId, rel)
    }

    private fun parseTripUpdate(bytes: ByteArray): TripUpdate {
        var trip = TripDescriptor()
        var delay: Int? = null
        val stopUpdates = mutableListOf<StopTimeUpdate>()
        readMessage(ByteArrayInputStream(bytes), bytes.size) { field, wire, reader ->
            when {
                field == 1 && wire == WIRE_LEN -> trip = parseTripDescriptor(reader.readBytes())
                field == 2 && wire == WIRE_LEN -> stopUpdates.add(parseStopTimeUpdate(reader.readBytes()))
                field == 5 && wire == WIRE_VARINT -> delay = reader.readVarint().toInt()
                else -> reader.skip(wire)
            }
        }
        return TripUpdate(trip, delay, stopUpdates)
    }

    private fun parseStopTimeUpdate(bytes: ByteArray): StopTimeUpdate {
        var seq = 0
        var stopId = ""
        var arrDelay: Int? = null
        var depDelay: Int? = null
        var rel = 0
        readMessage(ByteArrayInputStream(bytes), bytes.size) { field, wire, reader ->
            when {
                field == 1 && wire == WIRE_VARINT -> seq = reader.readVarint().toInt()
                field == 4 && wire == WIRE_LEN -> stopId = reader.readString()
                field == 2 && wire == WIRE_LEN -> arrDelay = parseStopTimeEventDelay(reader.readBytes())
                field == 3 && wire == WIRE_LEN -> depDelay = parseStopTimeEventDelay(reader.readBytes())
                field == 5 && wire == WIRE_VARINT -> rel = reader.readVarint().toInt()
                else -> reader.skip(wire)
            }
        }
        return StopTimeUpdate(seq, stopId, arrDelay, depDelay, rel)
    }

    private fun parseStopTimeEventDelay(bytes: ByteArray): Int? {
        var delay: Int? = null
        readMessage(ByteArrayInputStream(bytes), bytes.size) { field, wire, reader ->
            if (field == 1 && wire == WIRE_VARINT) {
                delay = reader.readVarint().toInt()
            } else {
                reader.skip(wire)
            }
        }
        return delay
    }

    private fun parseVehiclePosition(bytes: ByteArray): VehiclePosition {
        var trip = TripDescriptor()
        var stopSeq: Int? = null
        var stopId = ""
        var occupancy: GtfsOccupancyStatus? = null
        readMessage(ByteArrayInputStream(bytes), bytes.size) { field, wire, reader ->
            when {
                field == 1 && wire == WIRE_LEN -> trip = parseTripDescriptor(reader.readBytes())
                field == 3 && wire == WIRE_VARINT -> stopSeq = reader.readVarint().toInt()
                field == 7 && wire == WIRE_LEN -> stopId = reader.readString()
                field == 9 && wire == WIRE_VARINT -> occupancy = GtfsOccupancyStatus.fromCode(reader.readVarint().toInt())
                else -> reader.skip(wire)
            }
        }
        return VehiclePosition(trip, stopSeq, stopId, occupancy)
    }

    private fun parseAlert(bytes: ByteArray): ServiceAlert {
        var header = ""
        var desc = ""
        val routeIds = mutableListOf<String>()
        val stopIds = mutableListOf<String>()
        readMessage(ByteArrayInputStream(bytes), bytes.size) { field, wire, reader ->
            when {
                field == 5 && wire == WIRE_LEN -> {
                    readMessage(ByteArrayInputStream(reader.readBytes()), Int.MAX_VALUE) { ef, ew, er ->
                        when {
                            ef == 2 && ew == WIRE_LEN -> routeIds.add(er.readString())
                            ef == 5 && ew == WIRE_LEN -> stopIds.add(er.readString())
                            else -> er.skip(ew)
                        }
                    }
                }
                field == 10 && wire == WIRE_LEN -> header = parseTranslatedString(reader.readBytes())
                field == 11 && wire == WIRE_LEN -> desc = parseTranslatedString(reader.readBytes())
                else -> reader.skip(wire)
            }
        }
        return ServiceAlert(header, desc, routeIds, stopIds)
    }

    private fun parseTranslatedString(bytes: ByteArray): String {
        var firstText = ""
        readMessage(ByteArrayInputStream(bytes), bytes.size) { field, wire, reader ->
            if (field == 1 && wire == WIRE_LEN && firstText.isEmpty()) {
                val transBytes = reader.readBytes()
                readMessage(ByteArrayInputStream(transBytes), transBytes.size) { tf, tw, tr ->
                    if (tf == 1 && tw == WIRE_LEN && firstText.isEmpty()) {
                        firstText = tr.readString()
                    } else {
                        tr.skip(tw)
                    }
                }
            } else {
                reader.skip(wire)
            }
        }
        return firstText
    }

    private const val WIRE_VARINT = 0
    private const val WIRE_FIXED64 = 1
    private const val WIRE_LEN = 2
    private const val WIRE_FIXED32 = 5

    private class WireReader(private val input: InputStream) {
        fun readTag(): Int {
            val v = readRawVarint32()
            return v
        }

        private fun readRawVarint32(): Int {
            val first = input.read()
            if (first < 0) return 0
            return readVarintTail(first).toInt()
        }

        fun readVarint(): Long {
            val first = input.read()
            if (first < 0) return 0L
            return readVarintTail(first)
        }

        private fun readVarintTail(firstByte: Int): Long {
            var result = (firstByte and 0x7F).toLong()
            if ((firstByte and 0x80) == 0) return result
            var shift = 7
            while (shift < 64) {
                val b = input.read()
                if (b < 0) break
                result = result or ((b and 0x7F).toLong() shl shift)
                if ((b and 0x80) == 0) return result
                shift += 7
            }
            return result
        }

        fun readBytes(): ByteArray {
            val len = readVarint().toInt().coerceAtLeast(0).coerceAtMost(8 * 1024 * 1024)
            val buf = ByteArray(len)
            var offset = 0
            while (offset < len) {
                val read = input.read(buf, offset, len - offset)
                if (read <= 0) break
                offset += read
            }
            return if (offset == len) buf else buf.copyOf(offset)
        }

        fun readString(): String = String(readBytes(), StandardCharsets.UTF_8)

        fun skip(wireType: Int) {
            when (wireType) {
                WIRE_VARINT -> readVarint()
                WIRE_FIXED64 -> skipExact(8)
                WIRE_LEN -> skipExact(readVarint().toInt().coerceAtLeast(0))
                WIRE_FIXED32 -> skipExact(4)
                else -> Unit
            }
        }

        private fun skipExact(count: Int) {
            var remaining = count.toLong()
            while (remaining > 0) {
                val skipped = input.skip(remaining)
                if (skipped > 0) {
                    remaining -= skipped
                } else {
                    if (input.read() < 0) break
                    remaining--
                }
            }
        }
    }

    private inline fun readMessage(
        input: InputStream,
        @Suppress("UNUSED_PARAMETER") maxBytes: Int,
        onField: (fieldNumber: Int, wireType: Int, reader: WireReader) -> Unit
    ) {
        val reader = WireReader(input)
        while (true) {
            val tag = reader.readTag()
            if (tag == 0) break
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x07
            onField(fieldNumber, wireType, reader)
        }
    }
}
