package com.androidfung.departureboard.data.model.jp

import com.androidfung.departureboard.data.resolver.TransitLineNetwork
import java.time.ZoneId

/**
 * Route network definitions for Tokyo JR East & Tokyo Metro using official station numbering codes
 * (e.g. JY01..JY30 for Yamanote Line, G01..G19 for Ginza Line, M01..M25 for Marunouchi Line,
 * JC01..JC24 for Chuo Rapid Line, JB01..JB39 for Chuo-Sobu Line).
 */
object JpTokyoRoutes : TransitLineNetwork {

    override val timeZone: ZoneId = ZoneId.of("Asia/Tokyo")
    override val defaultMode: String = "tube"
    override val averageSecondsBetweenStops: Long = 120L
    override val loopLineCodes: Set<String> = setOf("JY")

    /**
     * Canonical mapping from official Tokyo station numbering codes to English station names.
     */
    val STATION_NAMES_BY_CODE: Map<String, String> = mapOf(
        // JR Yamanote Line (JY01..JY30 circular loop)
        "JY01" to "Tokyo", "JY02" to "Kanda", "JY03" to "Akihabara", "JY04" to "Okachimachi",
        "JY05" to "Ueno", "JY06" to "Uguisudani", "JY07" to "Nippori", "JY08" to "Nishi-Nippori",
        "JY09" to "Tabata", "JY10" to "Komagome", "JY11" to "Sugamo", "JY12" to "Otsuka",
        "JY13" to "Ikebukuro", "JY14" to "Mejiro", "JY15" to "Takadanobaba", "JY16" to "Shin-Okubo",
        "JY17" to "Shinjuku", "JY18" to "Yoyogi", "JY19" to "Harajuku", "JY20" to "Shibuya",
        "JY21" to "Ebisu", "JY22" to "Meguro", "JY23" to "Gotanda", "JY24" to "Osaki",
        "JY25" to "Shinagawa", "JY26" to "Takanawa Gateway", "JY27" to "Tamachi",
        "JY28" to "Hamamatsucho", "JY29" to "Shimbashi", "JY30" to "Yurakucho",

        // Tokyo Metro Ginza Line (G01..G19)
        "G01" to "Shibuya", "G02" to "Omote-sando", "G03" to "Gaiemmae",
        "G04" to "Aoyama-itchome", "G05" to "Akasaka-mitsuke", "G06" to "Tameike-sanno",
        "G07" to "Toranomon", "G08" to "Shimbashi", "G09" to "Ginza",
        "G10" to "Kyobashi", "G11" to "Nihombashi", "G12" to "Mitsukoshimae",
        "G13" to "Kanda", "G14" to "Suehirocho", "G15" to "Ueno-hirokoji",
        "G16" to "Ueno", "G17" to "Inaricho", "G18" to "Tawaramachi", "G19" to "Asakusa",

        // Tokyo Metro Marunouchi Line (M01..M25)
        "M01" to "Ogikubo", "M02" to "Minami-Asagaya", "M03" to "Shin-Koenji",
        "M04" to "Higashi-Koenji", "M05" to "Shin-Nakano", "M06" to "Nakano-sakaue",
        "M07" to "Nishi-Shinjuku", "M08" to "Shinjuku", "M09" to "Shinjuku-sanchome",
        "M10" to "Shinjuku-gyoemmae", "M11" to "Yotsuya-sanchome", "M12" to "Yotsuya",
        "M13" to "Akasaka-mitsuke", "M14" to "Kokkai-gijidomae", "M15" to "Kasumigaseki",
        "M16" to "Ginza", "M17" to "Tokyo", "M18" to "Otemachi", "M19" to "Awajicho",
        "M20" to "Ochanomizu", "M21" to "Hongo-sanchome", "M22" to "Korakuen",
        "M23" to "Myogadani", "M24" to "Shin-Otsuka", "M25" to "Ikebukuro",

        // JR Chuo Rapid Line (JC01..JC24)
        "JC01" to "Tokyo", "JC02" to "Kanda", "JC03" to "Ochanomizu", "JC04" to "Yotsuya",
        "JC05" to "Shinjuku", "JC06" to "Nakano", "JC07" to "Koenji", "JC08" to "Asagaya",
        "JC09" to "Ogikubo", "JC10" to "Nishi-Ogikubo", "JC11" to "Kichijoji",
        "JC12" to "Mitaka", "JC13" to "Musashi-Sakai", "JC14" to "Higashi-Koganei",
        "JC15" to "Musashi-Koganei", "JC16" to "Kokubunji", "JC17" to "Nishi-Kokubunji",
        "JC18" to "Kunitachi", "JC19" to "Tachikawa", "JC22" to "Hachioji", "JC24" to "Takao",

        // JR Chuo-Sobu Line (JB01..JB39)
        "JB01" to "Mitaka", "JB02" to "Kichijoji", "JB03" to "Nishi-Ogikubo", "JB04" to "Ogikubo",
        "JB05" to "Asagaya", "JB06" to "Koenji", "JB07" to "Nakano", "JB08" to "Higashi-Nakano",
        "JB09" to "Okubo", "JB10" to "Shinjuku", "JB11" to "Yoyogi", "JB12" to "Sendagaya",
        "JB13" to "Shinanomachi", "JB14" to "Yotsuya", "JB15" to "Ichigaya", "JB16" to "Iidabashi",
        "JB17" to "Suidobashi", "JB18" to "Ochanomizu", "JB19" to "Akihabara",
        "JB20" to "Asakusabashi", "JB21" to "Ryogoku", "JB22" to "Kinshicho", "JB23" to "Kameido",
        "JB24" to "Hirai", "JB25" to "Shin-Koiwa", "JB26" to "Koiwa", "JB27" to "Ichikawa",
        "JB28" to "Moto-Yawata", "JB29" to "Shimosa-Nakayama", "JB30" to "Nishi-Funabashi",
        "JB31" to "Funabashi", "JB32" to "Higashi-Funabashi", "JB33" to "Tsudanuma",
        "JB34" to "Makuharihongo", "JB35" to "Makuhari", "JB36" to "Shin-Kemigawa",
        "JB37" to "Inage", "JB38" to "Nishi-Chiba", "JB39" to "Chiba"
    )

    override val lineSequences: Map<String, List<String>> = mapOf(
        "JY" to listOf(
            "JY01", "JY02", "JY03", "JY04", "JY05", "JY06", "JY07", "JY08", "JY09", "JY10",
            "JY11", "JY12", "JY13", "JY14", "JY15", "JY16", "JY17", "JY18", "JY19", "JY20",
            "JY21", "JY22", "JY23", "JY24", "JY25", "JY26", "JY27", "JY28", "JY29", "JY30"
        ),
        "G" to listOf(
            "G19", "G18", "G17", "G16", "G15", "G14", "G13", "G12", "G11", "G10",
            "G09", "G08", "G07", "G06", "G05", "G04", "G03", "G02", "G01"
        ),
        "M" to listOf(
            "M01", "M02", "M03", "M04", "M05", "M06", "M07", "M08", "M09", "M10",
            "M11", "M12", "M13", "M14", "M15", "M16", "M17", "M18", "M19", "M20",
            "M21", "M22", "M23", "M24", "M25"
        ),
        "JC" to listOf(
            "JC01", "JC02", "JC03", "JC04", "JC05", "JC06", "JC07", "JC08", "JC09",
            "JC10", "JC11", "JC12", "JC13", "JC14", "JC15", "JC16", "JC17", "JC18",
            "JC19", "JC22", "JC24"
        ),
        "JB" to listOf(
            "JB39", "JB38", "JB37", "JB36", "JB35", "JB34", "JB33", "JB32", "JB31", "JB30",
            "JB29", "JB28", "JB27", "JB26", "JB25", "JB24", "JB23", "JB22", "JB21", "JB20",
            "JB19", "JB18", "JB17", "JB16", "JB15", "JB14", "JB13", "JB12", "JB11", "JB10",
            "JB09", "JB08", "JB07", "JB06", "JB05", "JB04", "JB03", "JB02", "JB01"
        )
    )

    override fun resolveStationName(stationCodeOrName: String): String {
        val clean = stationCodeOrName.trim().uppercase()
        return STATION_NAMES_BY_CODE[clean]
            ?: JpStationDirectory.getStationById("JP_METRO_$clean")?.name?.substringBefore(" (")?.trim()
            ?: stationCodeOrName
    }

    override fun resolveStationId(lineCode: String, stationCodeOrName: String): String {
        val clean = stationCodeOrName.trim().uppercase()
        return JpStationDirectory.getStationById("JP_METRO_$clean")?.id
            ?: "JP_METRO_$clean"
    }
}
