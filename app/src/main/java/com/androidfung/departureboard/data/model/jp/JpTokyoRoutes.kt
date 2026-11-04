package com.androidfung.departureboard.data.model.jp

import com.androidfung.departureboard.data.resolver.TransitLineNetwork
import java.time.ZoneId

/**
 * Route network definitions for Tokyo JR & Metro.
 */
object JpTokyoRoutes : TransitLineNetwork {

    override val timeZone: ZoneId = ZoneId.of("Asia/Tokyo")
    override val defaultMode: String = "tube"
    override val averageSecondsBetweenStops: Long = 120L

    override val lineSequences: Map<String, List<String>> = mapOf(
        "JY" to listOf(
            "Tokyo", "Kanda", "Akihabara", "Okachimachi", "Ueno", "Uguisudani", "Nippori", "Nishi-Nippori",
            "Tabata", "Komagome", "Sugamo", "Otsuka", "Ikebukuro", "Mejiro", "Takadanobaba", "Shin-Okubo",
            "Shinjuku", "Yoyogi", "Harajuku", "Shibuya", "Ebisu", "Meguro", "Gotanda", "Osaki",
            "Shinagawa", "Takanawa Gateway", "Tamachi", "Hamamatsucho", "Shimbashi", "Yurakucho", "Tokyo"
        ),
        "G" to listOf(
            "Asakusa", "Tawaramachi", "Inaricho", "Ueno", "Ueno-hirokoji", "Suehirocho", "Kanda",
            "Mitsukoshimae", "Nihombashi", "Kyobashi", "Ginza", "Shimbashi", "Toranomon",
            "Tameike-sanno", "Akasaka-mitsuke", "Aoyama-itchome", "Gaiemmae", "Omote-sando", "Shibuya"
        ),
        "M" to listOf(
            "Ogikubo", "Minami-Asagaya", "Shin-Koenji", "Higashi-Koenji", "Shin-Nakano", "Nakano-sakaue",
            "Nishi-Shinjuku", "Shinjuku", "Shinjuku-sanchome", "Shinjuku-gyoemmae", "Yotsuya-sanchome",
            "Yotsuya", "Akasaka-mitsuke", "Kokkai-gijidomae", "Kasumigaseki", "Ginza", "Tokyo",
            "Otemachi", "Awajicho", "Ochanomizu", "Hongo-sanchome", "Korakuen", "Myogadani",
            "Shin-Otsuka", "Ikebukuro"
        ),
        "JC" to listOf(
            "Tokyo", "Kanda", "Ochanomizu", "Yotsuya", "Shinjuku", "Nakano", "Koenji", "Asagaya",
            "Ogikubo", "Nishi-Ogikubo", "Kichijoji", "Mitaka", "Musashi-Sakai", "Higashi-Koganei",
            "Musashi-Koganei", "Kokubunji", "Nishi-Kokubunji", "Kunitachi", "Tachikawa", "Hachioji", "Takao"
        ),
        "JB" to listOf(
            "Chiba", "Nishi-Chiba", "Inage", "Shin-Kemigawa", "Makuhari", "Makuharihongo", "Tsudanuma",
            "Higashi-Funabashi", "Funabashi", "Nishi-Funabashi", "Shimosa-Nakayama", "Moto-Yawata",
            "Ichikawa", "Koiwa", "Shin-Koiwa", "Hirai", "Kameido", "Kinshicho", "Ryogoku", "Asakusabashi",
            "Akihabara", "Ochanomizu", "Suidobashi", "Iidabashi", "Ichigaya", "Yotsuya", "Shinanomachi",
            "Sendagaya", "Yoyogi", "Shinjuku", "Okubo", "Higashi-Nakano", "Nakano", "Koenji", "Asagaya",
            "Ogikubo", "Nishi-Ogikubo", "Kichijoji", "Mitaka"
        )
    )
}
