package com.androidfung.departureboard.data.model.sg

import com.androidfung.departureboard.data.resolver.TransitLineNetwork
import java.time.ZoneId

/**
 * Route network definitions for Singapore MRT.
 */
object SgMrtRoutes : TransitLineNetwork {

    override val timeZone: ZoneId = ZoneId.of("Asia/Singapore")
    override val defaultMode: String = "mrt"
    override val averageSecondsBetweenStops: Long = 120L

    override val lineSequences: Map<String, List<String>> = mapOf(
        "NSL" to listOf(
            "Jurong East", "Bukit Batok", "Bukit Gombak", "Choa Chu Kang", "Yew Tee", "Kranji",
            "Marsiling", "Woodlands", "Admiralty", "Sembawang", "Canberra", "Yishun", "Khatib",
            "Yio Chu Kang", "Ang Mo Kio", "Bishan", "Braddell", "Toa Payoh", "Novena", "Newton",
            "Orchard", "Somerset", "Dhoby Ghaut", "City Hall", "Raffles Place", "Marina Bay", "Marina South Pier"
        ),
        "EWL" to listOf(
            "Pasir Ris", "Tampines", "Simei", "Tanah Merah", "Bedok", "Kembangan", "Eunos",
            "Paya Lebar", "Aljunied", "Kallang", "Lavender", "Bugis", "City Hall", "Raffles Place",
            "Tanjong Pagar", "Outram Park", "Tiong Bahru", "Redhill", "Queenstown", "Commonwealth",
            "Buona Vista", "Dover", "Clementi", "Jurong East", "Chinese Garden", "Lakeside",
            "Boon Lay", "Pioneer", "Joo Koon", "Gul Circle", "Tuas Crescent", "Tuas West Road", "Tuas Link"
        ),
        "NEL" to listOf(
            "HarbourFront", "Outram Park", "Chinatown", "Clarke Quay", "Dhoby Ghaut", "Little India",
            "Farrer Park", "Boon Keng", "Potong Pasir", "Woodleigh", "Serangoon", "Kovan",
            "Hougang", "Buangkok", "Sengkang", "Punggol", "Punggol Coast"
        ),
        "CCL" to listOf(
            "Dhoby Ghaut", "Bras Basah", "Esplanade", "Promenade", "Nicoll Highway", "Stadium",
            "Mountbatten", "Dakota", "Paya Lebar", "MacPherson", "Tai Seng", "Bartley",
            "Serangoon", "Lorong Chuan", "Bishan", "Marymount", "Caldecott", "Botanic Gardens",
            "Farrer Road", "Holland Village", "Buona Vista", "one-north", "Kent Ridge", "Haw Par Villa",
            "Pasir Panjang", "Labrador Park", "Telok Blangah", "HarbourFront"
        ),
        "DTL" to listOf(
            "Bukit Panjang", "Cashew", "Hillview", "Beauty World", "King Albert Park", "Sixth Avenue",
            "Tan Kah Kee", "Botanic Gardens", "Stevens", "Newton", "Little India", "Rochor",
            "Bugis", "Promenade", "Bayfront", "Downtown", "Telok Ayer", "Chinatown",
            "Fort Canning", "Bencoolen", "Jalan Besar", "Bendemeer", "Geylang Bahru", "Mattar",
            "MacPherson", "Ubi", "Kaki Bukit", "Bedok North", "Bedok Reservoir", "Tampines West",
            "Tampines", "Tampines East", "Upper Changi", "Expo"
        ),
        "TEL" to listOf(
            "Woodlands North", "Woodlands", "Woodlands South", "Springleaf", "Lentor", "Mayflower",
            "Bright Hill", "Upper Thomson", "Caldecott", "Stevens", "Napier", "Orchard Boulevard",
            "Orchard", "Great World", "Havelock", "Outram Park", "Maxwell", "Shenton Way",
            "Marina Bay", "Gardens by the Bay", "Tanjong Rhu", "Katong Park", "Tanjong Katong",
            "Marine Parade", "Marine Terrace", "Siglap", "Bayshore"
        )
    )
}
