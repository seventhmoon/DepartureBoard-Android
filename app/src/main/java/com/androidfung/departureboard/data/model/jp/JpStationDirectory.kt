package com.androidfung.departureboard.data.model.jp

import com.androidfung.departureboard.data.model.Station
import com.androidfung.departureboard.data.model.StationDirectory
import com.androidfung.departureboard.data.model.StationLineInfo

/**
 * Directory of Tokyo stations with Kanji, Hiragana, and English/Romaji search support.
 */
object JpStationDirectory : StationDirectory {

    override val defaultStations: List<Station>
        get() = JpDefaultStations.POPULAR_STATIONS

    override fun searchStations(query: String): List<Station> = searchJpStations(query)

    override fun getStationById(stationId: String): Station? {
        return ALL_STATIONS.find { it.toStation().id == stationId }?.toStation()
    }

    data class JpStationEntry(
        val code: String,
        val englishName: String,
        val japaneseName: String,
        val hiragana: String,
        val lines: List<Pair<String, String>>,
        val modes: List<String> = listOf("tube")
    ) {
        fun toStation(): Station = Station(
            id = "JP_METRO_$code",
            name = "$englishName ($japaneseName)",
            modes = modes,
            lines = lines.map { StationLineInfo(it.first, it.second) }
        )
    }

    val ALL_STATIONS: List<JpStationEntry> = listOf(
        JpStationEntry("M17_JY01", "Tokyo", "東京", "とうきょう", listOf("M" to "Marunouchi Line", "JY" to "Yamanote Line", "JC" to "Chuo Line", "JK" to "Keihin-Tohoku Line", "JT" to "Tokaido Line", "JO" to "Yokosuka Line"), listOf("national-rail", "tube")),
        JpStationEntry("M08_JY17", "Shinjuku", "新宿", "しんじゅく", listOf("M" to "Marunouchi Line", "S" to "Shinjuku Line", "E" to "Oedo Line", "JY" to "Yamanote Line", "JC" to "Chuo Line", "JB" to "Chuo-Sobu Line", "JA" to "Saikyo Line", "JS" to "Shonan-Shinjuku Line", "OH" to "Odakyu Line", "KO" to "Keio Line"), listOf("national-rail", "tube")),
        JpStationEntry("G01_Z01_F16_JY20", "Shibuya", "渋谷", "しぶや", listOf("G" to "Ginza Line", "Z" to "Hanzomon Line", "F" to "Fukutoshin Line", "JY" to "Yamanote Line", "JA" to "Saikyo Line", "JS" to "Shonan-Shinjuku Line", "TY" to "Tokyu Toyoko Line", "DT" to "Tokyu Den-en-toshi Line", "IN" to "Keio Inokashira Line"), listOf("national-rail", "tube")),
        JpStationEntry("G09_M16_H08", "Ginza", "銀座", "ぎんざ", listOf("G" to "Ginza Line", "M" to "Marunouchi Line", "H" to "Hibiya Line")),
        JpStationEntry("H15_JY03", "Akihabara", "秋葉原", "あきはばら", listOf("H" to "Hibiya Line", "JY" to "Yamanote Line", "JB" to "Chuo-Sobu Line", "JK" to "Keihin-Tohoku Line"), listOf("national-rail", "tube")),
        JpStationEntry("G16_H17_JY05", "Ueno", "上野", "うえの", listOf("G" to "Ginza Line", "H" to "Hibiya Line", "JY" to "Yamanote Line", "JK" to "Keihin-Tohoku Line"), listOf("national-rail", "tube")),
        JpStationEntry("M25_Y09_F09_JY13", "Ikebukuro", "池袋", "いけぶくろ", listOf("M" to "Marunouchi Line", "Y" to "Yurakucho Line", "F" to "Fukutoshin Line", "JY" to "Yamanote Line", "JS" to "Shonan-Shinjuku Line"), listOf("national-rail", "tube")),
        JpStationEntry("H04_JY21", "Ebisu", "恵比寿", "えびす", listOf("H" to "Hibiya Line", "JY" to "Yamanote Line", "JS" to "Shonan-Shinjuku Line"), listOf("national-rail", "tube")),
        JpStationEntry("G19_A18", "Asakusa", "浅草", "あさくさ", listOf("G" to "Ginza Line", "A" to "Asakusa Line"), listOf("tube")),
        JpStationEntry("H07_E23", "Roppongi", "六本木", "ろっぽんぎ", listOf("H" to "Hibiya Line", "E" to "Oedo Line"), listOf("tube")),
        JpStationEntry("C09", "Omote-sando", "表参道", "おもてさんどう", listOf("G" to "Ginza Line", "C" to "Chiyoda Line", "Z" to "Hanzomon Line")),
        JpStationEntry("Z03", "Aoyama-itchome", "青山一丁目", "あおやまいっちょうめ", listOf("G" to "Ginza Line", "Z" to "Hanzomon Line", "E" to "Oedo Line")),
        JpStationEntry("T09_A13", "Nihombashi", "日本橋", "にほんばし", listOf("G" to "Ginza Line", "T" to "Tozai Line", "A" to "Asakusa Line"), listOf("tube")),
        JpStationEntry("T10", "Kayabacho", "茅場町", "かやばちょう", listOf("H" to "Hibiya Line", "T" to "Tozai Line")),
        JpStationEntry("M14_C08", "Kasumigaseki", "霞ケ関", "かすみがせき", listOf("M" to "Marunouchi Line", "H" to "Hibiya Line", "C" to "Chiyoda Line")),
        JpStationEntry("T06_S05_Z06", "Kudanshita", "九段下", "くだんした", listOf("T" to "Tozai Line", "S" to "Shinjuku Line", "Z" to "Hanzomon Line")),
        JpStationEntry("T01", "Nakano", "中野", "なかの", listOf("T" to "Tozai Line", "JC" to "Chuo Line", "JB" to "Chuo-Sobu Line"), listOf("national-rail", "tube")),
        JpStationEntry("JY25_JK20_JT03_KK01", "Shinagawa", "品川", "しながわ", listOf("JY" to "Yamanote Line", "JK" to "Keihin-Tohoku Line", "JT" to "Tokaido Line", "JO" to "Yokosuka Line", "KK" to "Keikyu Main Line"), listOf("national-rail")),
        JpStationEntry("JT04_JK16_JN01", "Kawasaki", "川崎", "かわさき", listOf("JT" to "Tokaido Line", "JK" to "Keihin-Tohoku Line", "JN" to "Nambu Line"), listOf("national-rail")),
        JpStationEntry("JT05_JK12_JS13_TY21", "Yokohama", "横浜", "よこはま", listOf("JT" to "Tokaido Line", "JK" to "Keihin-Tohoku Line", "JO" to "Yokosuka Line", "JS" to "Shonan-Shinjuku Line", "TY" to "Tokyu Toyoko Line", "KK" to "Keikyu Main Line"), listOf("national-rail")),
        JpStationEntry("JH16", "Shin-Yokohama", "新横浜", "しんよこはま", listOf("JH" to "Yokohama Line"), listOf("national-rail")),
        JpStationEntry("JN07_JS15_TY11", "Musashi-Kosugi", "武蔵小杉", "むさしこすぎ", listOf("JN" to "Nambu Line", "JS" to "Shonan-Shinjuku Line", "JO" to "Yokosuka Line", "TY" to "Tokyu Toyoko Line"), listOf("national-rail")),
        JpStationEntry("JK17", "Kamata", "蒲田", "かまた", listOf("JK" to "Keihin-Tohoku Line"), listOf("national-rail")),
        JpStationEntry("JK47_JS24_JA26", "Omiya", "大宮", "おおみや", listOf("JK" to "Keihin-Tohoku Line", "JS" to "Shonan-Shinjuku Line", "JA" to "Saikyo Line", "JT" to "Tokaido Line"), listOf("national-rail")),
        JpStationEntry("JB39_JO28", "Chiba", "千葉", "ちば", listOf("JB" to "Chuo-Sobu Line", "JO" to "Yokosuka Line"), listOf("national-rail")),
        JpStationEntry("KK16_KK17", "Haneda Airport", "羽田空港", "はねだくうこう", listOf("KK" to "Keikyu Main Line"), listOf("national-rail"))
    )

    fun searchJpStations(query: String): List<Station> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return JpDefaultStations.POPULAR_STATIONS

        return ALL_STATIONS
            .filter { entry ->
                entry.englishName.lowercase().contains(q) ||
                        entry.japaneseName.contains(q) ||
                        entry.hiragana.contains(q) ||
                        entry.code.lowercase().contains(q) ||
                        entry.lines.any { it.first.lowercase().contains(q) || it.second.lowercase().contains(q) }
            }
            .map { it.toStation() }
    }
}
