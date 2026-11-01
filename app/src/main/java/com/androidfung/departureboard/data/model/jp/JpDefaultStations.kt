package com.androidfung.departureboard.data.model.jp

import com.androidfung.departureboard.data.model.CallingPoint
import com.androidfung.departureboard.data.model.Departure
import com.androidfung.departureboard.data.model.DepartureStatus
import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.StationLineInfo
import com.androidfung.departureboard.data.model.TransitMode
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Default popular stations and offline departure generation for Tokyo (Tokyo Metro & JR East).
 */
object JpDefaultStations {

    val POPULAR_STATIONS: List<Station> = listOf(
        Station(
            id = "JP_METRO_M17_JY01",
            name = "Tokyo (東京)",
            modes = listOf("national-rail", "tube"),
            lines = listOf(
                StationLineInfo("M", "Marunouchi Line"),
                StationLineInfo("JY", "Yamanote Line"),
                StationLineInfo("JC", "Chuo Line")
            )
        ),
        Station(
            id = "JP_METRO_M08_JY17",
            name = "Shinjuku (新宿)",
            modes = listOf("national-rail", "tube"),
            lines = listOf(
                StationLineInfo("M", "Marunouchi Line"),
                StationLineInfo("S", "Shinjuku Line"),
                StationLineInfo("E", "Oedo Line"),
                StationLineInfo("JY", "Yamanote Line"),
                StationLineInfo("JC", "Chuo Line"),
                StationLineInfo("JB", "Chuo-Sobu Line"),
                StationLineInfo("JA", "Saikyo Line"),
                StationLineInfo("JS", "Shonan-Shinjuku Line"),
                StationLineInfo("OH", "Odakyu Line"),
                StationLineInfo("KO", "Keio Line")
            )
        ),
        Station(
            id = "JP_METRO_G01_Z01_F16_JY20",
            name = "Shibuya (渋谷)",
            modes = listOf("national-rail", "tube"),
            lines = listOf(
                StationLineInfo("G", "Ginza Line"),
                StationLineInfo("Z", "Hanzomon Line"),
                StationLineInfo("F", "Fukutoshin Line"),
                StationLineInfo("JY", "Yamanote Line"),
                StationLineInfo("JA", "Saikyo Line"),
                StationLineInfo("JS", "Shonan-Shinjuku Line"),
                StationLineInfo("TY", "Tokyu Toyoko Line"),
                StationLineInfo("DT", "Tokyu Den-en-toshi Line"),
                StationLineInfo("IN", "Keio Inokashira Line")
            )
        ),
        Station(
            id = "JP_METRO_G09_M16_H08",
            name = "Ginza (銀座)",
            modes = listOf("tube"),
            lines = listOf(
                StationLineInfo("G", "Ginza Line"),
                StationLineInfo("M", "Marunouchi Line"),
                StationLineInfo("H", "Hibiya Line")
            )
        ),
        Station(
            id = "JP_METRO_H15_JY03",
            name = "Akihabara (秋葉原)",
            modes = listOf("national-rail", "tube"),
            lines = listOf(
                StationLineInfo("H", "Hibiya Line"),
                StationLineInfo("JY", "Yamanote Line")
            )
        ),
        Station(
            id = "JP_METRO_G16_H17_JY05",
            name = "Ueno (上野)",
            modes = listOf("national-rail", "tube"),
            lines = listOf(
                StationLineInfo("G", "Ginza Line"),
                StationLineInfo("H", "Hibiya Line"),
                StationLineInfo("JY", "Yamanote Line")
            )
        ),
        Station(
            id = "JP_METRO_M25_Y09_F09_JY13",
            name = "Ikebukuro (池袋)",
            modes = listOf("national-rail", "tube"),
            lines = listOf(
                StationLineInfo("M", "Marunouchi Line"),
                StationLineInfo("Y", "Yurakucho Line"),
                StationLineInfo("F", "Fukutoshin Line"),
                StationLineInfo("JY", "Yamanote Line")
            )
        ),
        Station(
            id = "JP_METRO_H04_JY21",
            name = "Ebisu (恵比寿)",
            modes = listOf("national-rail", "tube"),
            lines = listOf(
                StationLineInfo("H", "Hibiya Line"),
                StationLineInfo("JY", "Yamanote Line")
            )
        )
    )

    private val STATION_DEPARTURE_TEMPLATES = mapOf(
        "Tokyo" to listOf(
            DepartureTemplate("M", "Ogikubo (荻窪)", "1", listOf(120, 300, 480)),
            DepartureTemplate("M", "Ikebukuro (池袋)", "2", listOf(180, 360, 540)),
            DepartureTemplate("JY", "Shinagawa / Shibuya (Inner Loop)", "4", listOf(90, 270, 450)),
            DepartureTemplate("JY", "Ueno / Ikebukuro (Outer Loop)", "5", listOf(150, 330, 510))
        ),
        "Shinjuku" to listOf(
            DepartureTemplate("M", "Tokyo / Ikebukuro (池袋)", "1", listOf(120, 300, 480)),
            DepartureTemplate("S", "Motoyawata (本八幡)", "2", listOf(180, 420)),
            DepartureTemplate("E", "Roppongi / Daimon (六本木・大門)", "7", listOf(150, 360)),
            DepartureTemplate("JY", "Shibuya / Shinagawa", "14", listOf(90, 270, 450)),
            DepartureTemplate("JY", "Ikebukuro / Ueno", "15", listOf(150, 330, 510)),
            DepartureTemplate("JC", "Takao (Rapid)", "11", listOf(180, 420)),
            DepartureTemplate("JB", "Mitaka / Omiya", "13", listOf(210, 480)),
            DepartureTemplate("JA", "Omiya / Kawagoe", "1", listOf(240, 540)),
            DepartureTemplate("JS", "Ofuna / Odawara", "2", listOf(270, 600)),
            DepartureTemplate("OH", "Hakone-Yumoto / Odawara", "4", listOf(300, 660)),
            DepartureTemplate("KO", "Hachioji / Takaosanguchi", "3", listOf(330, 720))
        ),
        "Shibuya" to listOf(
            DepartureTemplate("G", "Asakusa (浅草)", "1", listOf(120, 300, 480)),
            DepartureTemplate("Z", "Oshiage (押上)", "2", listOf(150, 360)),
            DepartureTemplate("F", "Wakoshi (和光市)", "3", listOf(180, 420)),
            DepartureTemplate("JY", "Shinjuku / Ikebukuro", "1", listOf(90, 270, 450)),
            DepartureTemplate("JY", "Shinagawa / Tokyo", "2", listOf(150, 330, 510)),
            DepartureTemplate("JA", "Omiya (大宮)", "3", listOf(210, 480)),
            DepartureTemplate("JS", "Zushi / Ofuna", "4", listOf(240, 540)),
            DepartureTemplate("TY", "Yokohama / Motomachi-Chukagai", "5", listOf(180, 420)),
            DepartureTemplate("DT", "Chuo-Rinkan (中央林間)", "6", listOf(210, 450)),
            DepartureTemplate("IN", "Kichijoji (吉祥寺)", "1", listOf(270, 570))
        ),
        "Ginza" to listOf(
            DepartureTemplate("G", "Asakusa (浅草)", "1", listOf(120, 300, 480)),
            DepartureTemplate("G", "Shibuya (渋谷)", "2", listOf(150, 330, 510)),
            DepartureTemplate("M", "Ikebukuro (池袋)", "3", listOf(180, 360)),
            DepartureTemplate("H", "Kita-Senju (北千住)", "5", listOf(150, 390))
        ),
        "Akihabara" to listOf(
            DepartureTemplate("H", "Naka-Meguro (中目黒)", "1", listOf(120, 360)),
            DepartureTemplate("H", "Kita-Senju (北千住)", "2", listOf(180, 420)),
            DepartureTemplate("JY", "Tokyo / Shinagawa", "1", listOf(90, 270)),
            DepartureTemplate("JY", "Ueno / Ikebukuro", "2", listOf(150, 330))
        ),
        "Ueno" to listOf(
            DepartureTemplate("G", "Shibuya (渋谷)", "1", listOf(120, 300, 480)),
            DepartureTemplate("G", "Asakusa (浅草)", "2", listOf(180, 360)),
            DepartureTemplate("JY", "Tokyo / Shinagawa", "2", listOf(90, 270)),
            DepartureTemplate("JY", "Ikebukuro / Shinjuku", "3", listOf(150, 330))
        ),
        "Ikebukuro" to listOf(
            DepartureTemplate("M", "Tokyo / Ogikubo", "1", listOf(120, 300, 480)),
            DepartureTemplate("Y", "Shin-Kiba (新木場)", "3", listOf(180, 420)),
            DepartureTemplate("F", "Shibuya / Motomachi-Chukagai", "5", listOf(150, 390)),
            DepartureTemplate("JY", "Shinjuku / Shibuya", "1", listOf(90, 270))
        ),
        "Ebisu" to listOf(
            DepartureTemplate("H", "Kita-Senju (北千住)", "1", listOf(120, 360)),
            DepartureTemplate("H", "Naka-Meguro (中目黒)", "2", listOf(180, 420)),
            DepartureTemplate("JY", "Shinjuku / Ikebukuro", "1", listOf(90, 270)),
            DepartureTemplate("JY", "Shinagawa / Tokyo", "2", listOf(150, 330))
        ),
        "Shinagawa" to listOf(
            DepartureTemplate("JY", "Shibuya / Shinjuku (Outer Loop)", "1", listOf(90, 270, 450)),
            DepartureTemplate("JY", "Tokyo / Ueno (Inner Loop)", "2", listOf(150, 330, 510)),
            DepartureTemplate("JK", "Ofuna (大船)", "3", listOf(120, 360)),
            DepartureTemplate("JK", "Omiya (大宮)", "4", listOf(180, 420)),
            DepartureTemplate("JT", "Odawara / Atami (熱海)", "5", listOf(240, 540)),
            DepartureTemplate("KK", "Haneda Airport (羽田空港)", "1", listOf(120, 300, 480))
        ),
        "Asakusa" to listOf(
            DepartureTemplate("G", "Shibuya (渋谷)", "1", listOf(120, 300, 480)),
            DepartureTemplate("G", "Shibuya (渋谷)", "2", listOf(240, 420)),
            DepartureTemplate("A", "Nishi-Magome / Haneda Airport", "1", listOf(150, 390)),
            DepartureTemplate("A", "Oshiage / Narita Airport", "2", listOf(180, 450))
        ),
        "Roppongi" to listOf(
            DepartureTemplate("H", "Kita-Senju (北千住)", "1", listOf(120, 360)),
            DepartureTemplate("H", "Naka-Meguro (中目黒)", "2", listOf(180, 420)),
            DepartureTemplate("E", "Daimon / Ryogoku", "1", listOf(150, 390)),
            DepartureTemplate("E", "Tochomae / Hikarigaoka", "2", listOf(210, 450))
        ),
        "Omote-sando" to listOf(
            DepartureTemplate("G", "Asakusa (浅草)", "1", listOf(120, 300, 480)),
            DepartureTemplate("G", "Shibuya (渋谷)", "2", listOf(180, 360)),
            DepartureTemplate("C", "Kita-Senju / Toride", "1", listOf(150, 390)),
            DepartureTemplate("C", "Yoyogi-Uehara (代々木上原)", "2", listOf(210, 450)),
            DepartureTemplate("Z", "Oshiage (押上)", "3", listOf(150, 360)),
            DepartureTemplate("Z", "Shibuya / Chuo-Rinkan", "4", listOf(240, 480))
        ),
        "Aoyama-itchome" to listOf(
            DepartureTemplate("G", "Asakusa (浅草)", "1", listOf(120, 300, 480)),
            DepartureTemplate("G", "Shibuya (渋谷)", "2", listOf(180, 360)),
            DepartureTemplate("Z", "Oshiage (押上)", "1", listOf(150, 390)),
            DepartureTemplate("Z", "Shibuya / Chuo-Rinkan", "2", listOf(210, 450)),
            DepartureTemplate("E", "Daimon / Ryogoku", "1", listOf(180, 420)),
            DepartureTemplate("E", "Roppongi / Tochomae", "2", listOf(240, 480))
        ),
        "Nihombashi" to listOf(
            DepartureTemplate("G", "Asakusa (浅草)", "1", listOf(120, 300, 480)),
            DepartureTemplate("G", "Shibuya (渋谷)", "2", listOf(180, 360)),
            DepartureTemplate("T", "Nishi-Funabashi (西船橋)", "1", listOf(150, 390)),
            DepartureTemplate("T", "Nakano / Mitaka", "2", listOf(210, 450)),
            DepartureTemplate("A", "Nishi-Magome / Haneda Airport", "1", listOf(180, 420)),
            DepartureTemplate("A", "Oshiage / Narita Airport", "2", listOf(240, 480))
        ),
        "Kayabacho" to listOf(
            DepartureTemplate("H", "Kita-Senju (北千住)", "1", listOf(120, 360)),
            DepartureTemplate("H", "Naka-Meguro (中目黒)", "2", listOf(180, 420)),
            DepartureTemplate("T", "Nishi-Funabashi (西船橋)", "1", listOf(150, 390)),
            DepartureTemplate("T", "Nakano / Mitaka", "2", listOf(210, 450))
        ),
        "Kasumigaseki" to listOf(
            DepartureTemplate("M", "Ogikubo (荻窪)", "1", listOf(120, 300, 480)),
            DepartureTemplate("M", "Ikebukuro (池袋)", "2", listOf(180, 360)),
            DepartureTemplate("H", "Kita-Senju (北千住)", "1", listOf(150, 390)),
            DepartureTemplate("H", "Naka-Meguro (中目黒)", "2", listOf(210, 450)),
            DepartureTemplate("C", "Kita-Senju / Toride", "1", listOf(150, 390)),
            DepartureTemplate("C", "Yoyogi-Uehara (代々木上原)", "2", listOf(240, 480))
        ),
        "Kudanshita" to listOf(
            DepartureTemplate("T", "Nishi-Funabashi (西船橋)", "1", listOf(150, 390)),
            DepartureTemplate("T", "Nakano / Mitaka", "2", listOf(210, 450)),
            DepartureTemplate("S", "Motoyawata (本八幡)", "1", listOf(180, 420)),
            DepartureTemplate("S", "Shinjuku / Hashimoto", "2", listOf(240, 480)),
            DepartureTemplate("Z", "Oshiage (押上)", "3", listOf(150, 390)),
            DepartureTemplate("Z", "Shibuya / Chuo-Rinkan", "4", listOf(210, 450))
        ),
        "Nakano" to listOf(
            DepartureTemplate("T", "Nishi-Funabashi / Tsudanuma", "1", listOf(120, 300, 480)),
            DepartureTemplate("JC", "Takao / Ome", "7", listOf(150, 390)),
            DepartureTemplate("JC", "Shinjuku / Tokyo", "8", listOf(180, 420)),
            DepartureTemplate("JB", "Chiba (千葉)", "5", listOf(210, 450))
        ),
        "Kawasaki" to listOf(
            DepartureTemplate("JT", "Yokohama / Odawara / Atami", "1", listOf(120, 300, 480)),
            DepartureTemplate("JT", "Shinagawa / Tokyo / Ueno", "2", listOf(150, 360, 540)),
            DepartureTemplate("JK", "Yokohama / Ofuna", "3", listOf(90, 270, 450)),
            DepartureTemplate("JK", "Shinagawa / Tokyo / Omiya", "4", listOf(120, 330, 510)),
            DepartureTemplate("JN", "Musashi-Kosugi / Tachikawa", "5", listOf(180, 420))
        ),
        "Yokohama" to listOf(
            DepartureTemplate("JT", "Odawara / Atami", "5", listOf(120, 360)),
            DepartureTemplate("JT", "Kawasaki / Tokyo / Ueno", "7", listOf(150, 390)),
            DepartureTemplate("JK", "Ofuna (大船)", "3", listOf(90, 270)),
            DepartureTemplate("JK", "Kawasaki / Tokyo / Omiya", "4", listOf(120, 300)),
            DepartureTemplate("JO", "Kamakura / Kurihama", "9", listOf(180, 420)),
            DepartureTemplate("JO", "Shinagawa / Tokyo / Chiba", "10", listOf(210, 480)),
            DepartureTemplate("TY", "Shibuya / Ikebukuro", "1", listOf(120, 300, 480))
        ),
        "Musashi-Kosugi" to listOf(
            DepartureTemplate("JN", "Kawasaki (川崎)", "1", listOf(120, 360)),
            DepartureTemplate("JN", "Musashi-Mizonokuchi / Tachikawa", "2", listOf(150, 390)),
            DepartureTemplate("JS", "Yokohama / Zushi", "3", listOf(180, 420)),
            DepartureTemplate("JS", "Shinjuku / Omiya", "4", listOf(210, 450))
        ),
        "Kamata" to listOf(
            DepartureTemplate("JK", "Kawasaki / Yokohama / Ofuna", "1", listOf(120, 300, 480)),
            DepartureTemplate("JK", "Shinagawa / Tokyo / Omiya", "2", listOf(150, 360, 540))
        ),
        "Shin-Yokohama" to listOf(
            DepartureTemplate("JH", "Machida / Hachioji", "1", listOf(150, 390)),
            DepartureTemplate("JH", "Kikuna / Higashi-Kanagawa / Yokohama", "2", listOf(180, 420))
        ),
        "Omiya" to listOf(
            DepartureTemplate("JK", "Ueno / Tokyo / Shinagawa", "1", listOf(120, 300, 480)),
            DepartureTemplate("JA", "Shinjuku / Shibuya", "19", listOf(150, 360)),
            DepartureTemplate("JS", "Shinjuku / Yokohama", "7", listOf(180, 420))
        ),
        "Chiba" to listOf(
            DepartureTemplate("JB", "Akihabara / Shinjuku / Mitaka", "1", listOf(120, 300, 480)),
            DepartureTemplate("JO", "Tokyo / Shinagawa / Yokohama", "3", listOf(180, 420))
        ),
        "Haneda Airport" to listOf(
            DepartureTemplate("KK", "Shinagawa / Sengakuji / Asakusa", "1", listOf(120, 300, 480)),
            DepartureTemplate("KK", "Yokohama / Kamata", "2", listOf(180, 420))
        )
    )

    private data class DepartureTemplate(
        val lineCode: String,
        val destination: String,
        val platform: String,
        val headwaysSeconds: List<Int>
    )

    fun getFallbackDepartures(stationId: String, stationName: String): List<Departure> {
        val cleanName = stationName.replace(Regex("\\s*\\(.*\\)"), "").trim()
        val stationEntry = JpStationDirectory.ALL_STATIONS.firstOrNull { 
            it.englishName.equals(cleanName, ignoreCase = true) || it.japaneseName == cleanName 
        }
        val defaultLineCode = stationEntry?.lines?.firstOrNull()?.first ?: "JY"

        val templates = STATION_DEPARTURE_TEMPLATES[cleanName]
            ?: listOf(
                DepartureTemplate(defaultLineCode, "Inbound", "1", listOf(180, 480)),
                DepartureTemplate(defaultLineCode, "Outbound", "2", listOf(240, 540))
            )

        val now = Instant.now()
        val departures = mutableListOf<Departure>()

        templates.forEach { template ->
            val tokyoLine = JpTokyoLine.fromCode(template.lineCode) ?: JpTokyoLine.GINZA
            template.headwaysSeconds.forEachIndexed { idx, waitSec ->
                val departureInstant = now.plusSeconds(waitSec.toLong())
                val formattedArrival = DateTimeFormatter.ISO_INSTANT.format(departureInstant)

                departures.add(
                    Departure(
                        id = "JP_METRO_${template.lineCode}_${cleanName}_${template.platform}_${departureInstant.epochSecond}_$idx",
                        stationId = stationId,
                        stationName = stationName,
                        lineId = template.lineCode,
                        lineName = tokyoLine.lineName,
                        platformName = "Platform ${template.platform}",
                        destinationName = template.destination,
                        towards = template.destination,
                        timeToStationSeconds = waitSec,
                        expectedArrivalIso = formattedArrival,
                        currentLocation = if (waitSec <= 60) "Approaching" else "In Transit",
                        modeName = if (tokyoLine.mode == TransitMode.NATIONAL_RAIL) "national-rail" else "tube",
                        lineBadge = tokyoLine.toLineBadge(),
                        status = if (waitSec <= 60) DepartureStatus.DUE else DepartureStatus.ON_TIME,
                        callingPoints = listOf(
                            CallingPoint(cleanName, "Dep"),
                            CallingPoint(template.destination, "${waitSec / 60 + 6} min")
                        )
                    )
                )
            }
        }

        return departures.sortedBy { it.timeToStationSeconds }
    }
}
