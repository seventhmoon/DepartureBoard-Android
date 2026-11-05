package com.androidfung.departureboard.data.model.sg

import com.androidfung.departureboard.data.topology.TransitLineNetwork
import java.time.ZoneId

/**
 * Route network definitions for Singapore MRT using official LTA station codes
 * (e.g. NS1..NS28, EW1..EW33, CG1..CG2, NE1..NE18, CC1..CC29, DT1..DT35, TE1..TE29).
 */
object SgMrtRoutes : TransitLineNetwork {

    override val timeZone: ZoneId = ZoneId.of("Asia/Singapore")
    override val defaultMode: String = "mrt"
    override val averageSecondsBetweenStops: Long = 120L

    /**
     * Canonical mapping from individual LTA station codes to their English display name.
     */
    val STATION_NAMES_BY_CODE: Map<String, String> = mapOf(
        // North-South Line (NSL)
        "NS1" to "Jurong East", "NS2" to "Bukit Batok", "NS3" to "Bukit Gombak",
        "NS4" to "Choa Chu Kang", "NS5" to "Yew Tee", "NS7" to "Kranji",
        "NS8" to "Marsiling", "NS9" to "Woodlands", "NS10" to "Admiralty",
        "NS11" to "Sembawang", "NS12" to "Canberra", "NS13" to "Yishun",
        "NS14" to "Khatib", "NS15" to "Yio Chu Kang", "NS16" to "Ang Mo Kio",
        "NS17" to "Bishan", "NS18" to "Braddell", "NS19" to "Toa Payoh",
        "NS20" to "Novena", "NS21" to "Newton", "NS22" to "Orchard",
        "NS23" to "Somerset", "NS24" to "Dhoby Ghaut", "NS25" to "City Hall",
        "NS26" to "Raffles Place", "NS27" to "Marina Bay", "NS28" to "Marina South Pier",

        // East-West Line (EWL) & Changi Airport Branch (CG)
        "EW1" to "Pasir Ris", "EW2" to "Tampines", "EW3" to "Simei",
        "EW4" to "Tanah Merah", "EW5" to "Bedok", "EW6" to "Kembangan",
        "EW7" to "Eunos", "EW8" to "Paya Lebar", "EW9" to "Aljunied",
        "EW10" to "Kallang", "EW11" to "Lavender", "EW12" to "Bugis",
        "EW13" to "City Hall", "EW14" to "Raffles Place", "EW15" to "Tanjong Pagar",
        "EW16" to "Outram Park", "EW17" to "Tiong Bahru", "EW18" to "Redhill",
        "EW19" to "Queenstown", "EW20" to "Commonwealth", "EW21" to "Buona Vista",
        "EW22" to "Dover", "EW23" to "Clementi", "EW24" to "Jurong East",
        "EW25" to "Chinese Garden", "EW26" to "Lakeside", "EW27" to "Boon Lay",
        "EW28" to "Pioneer", "EW29" to "Joo Koon", "EW30" to "Gul Circle",
        "EW31" to "Tuas Crescent", "EW32" to "Tuas West Road", "EW33" to "Tuas Link",
        "CG1" to "Expo", "CG2" to "Changi Airport",

        // North East Line (NEL)
        "NE1" to "HarbourFront", "NE3" to "Outram Park", "NE4" to "Chinatown",
        "NE5" to "Clarke Quay", "NE6" to "Dhoby Ghaut", "NE7" to "Little India",
        "NE8" to "Farrer Park", "NE9" to "Boon Keng", "NE10" to "Potong Pasir",
        "NE11" to "Woodleigh", "NE12" to "Serangoon", "NE13" to "Kovan",
        "NE14" to "Hougang", "NE15" to "Buangkok", "NE16" to "Sengkang",
        "NE17" to "Punggol", "NE18" to "Punggol Coast",

        // Circle Line (CCL)
        "CC1" to "Dhoby Ghaut", "CC2" to "Bras Basah", "CC3" to "Esplanade",
        "CC4" to "Promenade", "CC5" to "Nicoll Highway", "CC6" to "Stadium",
        "CC7" to "Mountbatten", "CC8" to "Dakota", "CC9" to "Paya Lebar",
        "CC10" to "MacPherson", "CC11" to "Tai Seng", "CC12" to "Bartley",
        "CC13" to "Serangoon", "CC14" to "Lorong Chuan", "CC15" to "Bishan",
        "CC16" to "Marymount", "CC17" to "Caldecott", "CC19" to "Botanic Gardens",
        "CC20" to "Farrer Road", "CC21" to "Holland Village", "CC22" to "Buona Vista",
        "CC23" to "one-north", "CC24" to "Kent Ridge", "CC25" to "Haw Par Villa",
        "CC26" to "Pasir Panjang", "CC27" to "Labrador Park", "CC28" to "Telok Blangah",
        "CC29" to "HarbourFront", "CE1" to "Bayfront", "CE2" to "Marina Bay",

        // Downtown Line (DTL)
        "DT1" to "Bukit Panjang", "DT2" to "Cashew", "DT3" to "Hillview",
        "DT5" to "Beauty World", "DT6" to "King Albert Park", "DT7" to "Sixth Avenue",
        "DT8" to "Tan Kah Kee", "DT9" to "Botanic Gardens", "DT10" to "Stevens",
        "DT11" to "Newton", "DT12" to "Little India", "DT13" to "Rochor",
        "DT14" to "Bugis", "DT15" to "Promenade", "DT16" to "Bayfront",
        "DT17" to "Downtown", "DT18" to "Telok Ayer", "DT19" to "Chinatown",
        "DT20" to "Fort Canning", "DT21" to "Bencoolen", "DT22" to "Jalan Besar",
        "DT23" to "Bendemeer", "DT24" to "Geylang Bahru", "DT25" to "Mattar",
        "DT26" to "MacPherson", "DT27" to "Ubi", "DT28" to "Kaki Bukit",
        "DT29" to "Bedok North", "DT30" to "Bedok Reservoir", "DT31" to "Tampines West",
        "DT32" to "Tampines", "DT33" to "Tampines East", "DT34" to "Upper Changi",
        "DT35" to "Expo",

        // Thomson-East Coast Line (TEL)
        "TE1" to "Woodlands North", "TE2" to "Woodlands", "TE3" to "Woodlands South",
        "TE4" to "Springleaf", "TE5" to "Lentor", "TE6" to "Mayflower",
        "TE7" to "Bright Hill", "TE8" to "Upper Thomson", "TE9" to "Caldecott",
        "TE11" to "Stevens", "TE12" to "Napier", "TE13" to "Orchard Boulevard",
        "TE14" to "Orchard", "TE15" to "Great World", "TE16" to "Havelock",
        "TE17" to "Outram Park", "TE18" to "Maxwell", "TE19" to "Shenton Way",
        "TE20" to "Marina Bay", "TE22" to "Gardens by the Bay", "TE23" to "Tanjong Rhu",
        "TE24" to "Katong Park", "TE25" to "Tanjong Katong", "TE26" to "Marine Parade",
        "TE27" to "Marine Terrace", "TE28" to "Siglap", "TE29" to "Bayshore"
    )

    override val lineSequences: Map<String, List<String>> = mapOf(
        "NSL" to listOf(
            "NS1", "NS2", "NS3", "NS4", "NS5", "NS7", "NS8", "NS9", "NS10", "NS11",
            "NS12", "NS13", "NS14", "NS15", "NS16", "NS17", "NS18", "NS19", "NS20",
            "NS21", "NS22", "NS23", "NS24", "NS25", "NS26", "NS27", "NS28"
        ),
        "EWL" to listOf(
            "EW1", "EW2", "EW3", "EW4", "EW5", "EW6", "EW7", "EW8", "EW9", "EW10",
            "EW11", "EW12", "EW13", "EW14", "EW15", "EW16", "EW17", "EW18", "EW19",
            "EW20", "EW21", "EW22", "EW23", "EW24", "EW25", "EW26", "EW27", "EW28",
            "EW29", "EW30", "EW31", "EW32", "EW33"
        ),
        "EWL_CG" to listOf("EW4", "CG1", "CG2"),
        "NEL" to listOf(
            "NE1", "NE3", "NE4", "NE5", "NE6", "NE7", "NE8", "NE9", "NE10",
            "NE11", "NE12", "NE13", "NE14", "NE15", "NE16", "NE17", "NE18"
        ),
        "CCL" to listOf(
            "CC1", "CC2", "CC3", "CC4", "CC5", "CC6", "CC7", "CC8", "CC9", "CC10",
            "CC11", "CC12", "CC13", "CC14", "CC15", "CC16", "CC17", "CC19", "CC20",
            "CC21", "CC22", "CC23", "CC24", "CC25", "CC26", "CC27", "CC28", "CC29"
        ),
        "DTL" to listOf(
            "DT1", "DT2", "DT3", "DT5", "DT6", "DT7", "DT8", "DT9", "DT10", "DT11",
            "DT12", "DT13", "DT14", "DT15", "DT16", "DT17", "DT18", "DT19", "DT20",
            "DT21", "DT22", "DT23", "DT24", "DT25", "DT26", "DT27", "DT28", "DT29",
            "DT30", "DT31", "DT32", "DT33", "DT34", "DT35"
        ),
        "TEL" to listOf(
            "TE1", "TE2", "TE3", "TE4", "TE5", "TE6", "TE7", "TE8", "TE9", "TE11",
            "TE12", "TE13", "TE14", "TE15", "TE16", "TE17", "TE18", "TE19", "TE20",
            "TE22", "TE23", "TE24", "TE25", "TE26", "TE27", "TE28", "TE29"
        )
    )

    override fun resolveSequenceKey(
        lineCode: String,
        originIdentifier: String,
        destIdentifier: String
    ): String {
        val normOrigin = originIdentifier.uppercase()
        val normDest = destIdentifier.uppercase()
        val isChangiBranch = normOrigin in setOf("CG1", "CG2") ||
            normDest in setOf("CG1", "CG2") ||
            normDest.contains("CHANGI AIRPORT")
        return if (lineCode.equals("EWL", ignoreCase = true) && isChangiBranch) {
            "EWL_CG"
        } else {
            lineCode.uppercase()
        }
    }

    override fun resolveStationName(stationCodeOrName: String): String {
        val clean = stationCodeOrName.trim().uppercase()
        return STATION_NAMES_BY_CODE[clean]
            ?: SgStationDirectory.getStationById("SG_MRT_$clean")?.name
            ?: stationCodeOrName
    }

    override fun resolveStationId(lineCode: String, stationCodeOrName: String): String {
        val clean = stationCodeOrName.trim().uppercase()
        return SgStationDirectory.getStationById("SG_MRT_$clean")?.id
            ?: "SG_MRT_$clean"
    }
}
