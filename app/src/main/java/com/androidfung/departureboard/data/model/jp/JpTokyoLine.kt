package com.androidfung.departureboard.data.model.jp

import androidx.compose.ui.graphics.Color
import com.androidfung.departureboard.data.model.TransitLineDefinition
import com.androidfung.departureboard.data.model.TransitLineRegistry
import com.androidfung.departureboard.data.model.TransitMode

/**
 * Line definitions and badges for Tokyo Metro and JR East.
 */
enum class JpTokyoLine(
    override val code: String,
    val lineName: String,
    val japaneseName: String,
    override val badgeText: String,
    override val color: Color,
    override val mode: TransitMode = TransitMode.METRO
) : TransitLineDefinition {
    GINZA("G", "Ginza Line", "銀座線", "G", Color(0xFFFF9500)),
    MARUNOUCHI("M", "Marunouchi Line", "丸ノ内線", "M", Color(0xFFF62E36)),
    HIBIYA("H", "Hibiya Line", "日比谷線", "H", Color(0xFFB5B5AC)),
    TOZAI("T", "Tozai Line", "東西線", "T", Color(0xFF009BBF)),
    CHIYODA("C", "Chiyoda Line", "千代田線", "C", Color(0xFF00BB85)),
    YURAKUCHO("Y", "Yurakucho Line", "有楽町線", "Y", Color(0xFFC1A470)),
    HANZOMON("Z", "Hanzomon Line", "半蔵門線", "Z", Color(0xFF8F76D6)),
    NAMBOKU("N", "Namboku Line", "南北線", "N", Color(0xFF00AC9B)),
    FUKUTOSHIN("F", "Fukutoshin Line", "副都心線", "F", Color(0xFF9C5E31)),
    // Toei Subway Lines
    ASAKUSA("A", "Asakusa Line", "浅草線", "A", Color(0xFFE85298)),
    MITA("I", "Mita Line", "三田線", "I", Color(0xFF0079C2)),
    SHINJUKU("S", "Shinjuku Line", "新宿線", "S", Color(0xFF6CBB5A)),
    OEDO("E", "Oedo Line", "大江戸線", "E", Color(0xFFB6007A)),
    // JR East Lines
    YAMANOTE("JY", "Yamanote Line", "山手線", "JY", Color(0xFF9ACD32), TransitMode.RAIL),
    CHUO("JC", "Chuo Line (Rapid)", "中央線快速", "JC", Color(0xFFFF4500), TransitMode.RAIL),
    CHUO_SOBU("JB", "Chuo-Sobu Line", "中央・総武線各駅停車", "JB", Color(0xFFFFD700), TransitMode.RAIL),
    SAIKYO("JA", "Saikyo Line", "埼京線", "JA", Color(0xFF00AC9A), TransitMode.RAIL),
    KEIHIN_TOHOKU("JK", "Keihin-Tohoku Line", "京浜東北線", "JK", Color(0xFF00BFFF), TransitMode.RAIL),
    TOKAIDO("JT", "Tokaido Line", "東海道線", "JT", Color(0xFFF68B1E), TransitMode.RAIL),
    YOKOSUKA("JO", "Yokosuka Line", "横須賀線", "JO", Color(0xFF007AC0), TransitMode.RAIL),
    SHONAN_SHINJUKU("JS", "Shonan-Shinjuku Line", "湘南新宿ライン", "JS", Color(0xFFED1C24), TransitMode.RAIL),
    NAMBU("JN", "Nambu Line", "南武線", "JN", Color(0xFFFFD400), TransitMode.RAIL),
    YOKOHAMA("JH", "Yokohama Line", "横浜線", "JH", Color(0xFF80C241), TransitMode.RAIL),
    // Major Private Railways
    ODAKYU("OH", "Odakyu Line", "小田急線", "OH", Color(0xFF0075C2), TransitMode.RAIL),
    KEIO("KO", "Keio Line", "京王線", "KO", Color(0xFFDD0077), TransitMode.RAIL),
    KEIO_INOKASHIRA("IN", "Keio Inokashira Line", "京王井の頭線", "IN", Color(0xFF1D2088), TransitMode.RAIL),
    TOKYU_TOYOKO("TY", "Tokyu Toyoko Line", "東急東横線", "TY", Color(0xFFDA0442), TransitMode.RAIL),
    TOKYU_DENENTOSHI("DT", "Tokyu Den-en-toshi Line", "東急田園都市線", "DT", Color(0xFF00A040), TransitMode.RAIL),
    KEIKYU("KK", "Keikyu Main Line", "京急本線", "KK", Color(0xFF0080DE), TransitMode.RAIL);

    override val localizedNames: Map<String, String> get() = mapOf(
        "en" to lineName,
        "ja" to japaneseName
    )

    companion object : TransitLineRegistry {
        override val allLines: Collection<JpTokyoLine> get() = entries
        override fun fromCode(code: String?): JpTokyoLine? {
            if (code == null) return null
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) || it.badgeText.equals(code, ignoreCase = true) }
        }
    }
}
