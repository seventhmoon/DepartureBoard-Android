package com.androidfung.departureboard.data.model

import com.androidfung.departureboard.data.i18n.LocalizedNameProvider

/**
 * Definition of an individual MTR station with code, bilingual names, and coordinates.
 */
data class HkMtrStation(
    val code: String,
    override val nameEn: String,
    override val nameTc: String,
    val lat: Double = 0.0,
    val lon: Double = 0.0
) : LocalizedNameProvider {
    override val nameSc: String? get() = null
    val bilingualName: String get() = getBilingualName(parentheses = false)
    val displayName: String get() = getBilingualName(parentheses = true)
}

/**
 * Central single-source-of-truth registry mapping MTR 3-letter station codes
 * to bilingual station details across all lines.
 */
object HkMtrStationRegistry {

    val STATIONS: Map<String, HkMtrStation> = listOf(
        // Island Line (ISL)
        HkMtrStation("KET", "Kennedy Town", "堅尼地城", 22.2812, 114.1289),
        HkMtrStation("HKU", "HKU", "香港大學", 22.2840, 114.1352),
        HkMtrStation("SYP", "Sai Ying Pun", "西營盤", 22.2861, 114.1432),
        HkMtrStation("SHW", "Sheung Wan", "上環", 22.2869, 114.1523),
        HkMtrStation("CEN", "Central", "中環", 22.2819, 114.1582),
        HkMtrStation("ADM", "Admiralty", "金鐘", 22.2789, 114.1652),
        HkMtrStation("WAC", "Wan Chai", "灣仔", 22.2778, 114.1730),
        HkMtrStation("CAB", "Causeway Bay", "銅鑼灣", 22.2804, 114.1838),
        HkMtrStation("TIH", "Tin Hau", "天后", 22.2824, 114.1923),
        HkMtrStation("FOH", "Fortress Hill", "炮台山", 22.2882, 114.1942),
        HkMtrStation("NOP", "North Point", "北角", 22.2913, 114.2005),
        HkMtrStation("QUB", "Quarry Bay", "鰂魚涌", 22.2878, 114.2096),
        HkMtrStation("TAK", "Tai Koo", "太古", 22.2847, 114.2166),
        HkMtrStation("SWH", "Sai Wan Ho", "西灣河", 22.2822, 114.2219),
        HkMtrStation("SKW", "Shau Kei Wan", "筲箕灣", 22.2794, 114.2285),
        HkMtrStation("HFC", "Heng Fa Chuen", "杏花邨", 22.2769, 114.2401),
        HkMtrStation("CHW", "Chai Wan", "柴灣", 22.2642, 114.2366),

        // Tsuen Wan Line (TWL)
        HkMtrStation("TST", "Tsim Sha Tsui", "尖沙咀", 22.2976, 114.1722),
        HkMtrStation("JOR", "Jordan", "佐敦", 22.3049, 114.1717),
        HkMtrStation("YMT", "Yau Ma Tei", "油麻地", 22.3131, 114.1706),
        HkMtrStation("MOK", "Mong Kok", "旺角", 22.3193, 114.1694),
        HkMtrStation("PRE", "Prince Edward", "太子", 22.3246, 114.1685),
        HkMtrStation("SSP", "Sham Shui Po", "深水埗", 22.3308, 114.1622),
        HkMtrStation("CSW", "Cheung Sha Wan", "長沙灣", 22.3353, 114.1566),
        HkMtrStation("LCK", "Lai Chi Kok", "荔枝角", 22.3375, 114.1489),
        HkMtrStation("MEF", "Mei Foo", "美孚", 22.3372, 114.1408),
        HkMtrStation("LAK", "Lai King", "荔景", 22.3486, 114.1264),
        HkMtrStation("KWF", "Kwai Fong", "葵芳", 22.3571, 114.1283),
        HkMtrStation("KWH", "Kwai Hing", "葵興", 22.3633, 114.1311),
        HkMtrStation("TWH", "Tai Wo Hau", "大窩口", 22.3708, 114.1247),
        HkMtrStation("TSW", "Tsuen Wan", "荃灣", 22.3736, 114.1178),

        // Kwun Tong Line (KTL)
        HkMtrStation("WHA", "Whampoa", "黃埔", 22.3050, 114.1883),
        HkMtrStation("HOM", "Ho Man Tin", "何文田", 22.3094, 114.1825),
        HkMtrStation("SKM", "Shek Kip Mei", "石硤尾", 22.3325, 114.1686),
        HkMtrStation("KOT", "Kowloon Tong", "九龍塘", 22.3370, 114.1764),
        HkMtrStation("LOF", "Lok Fu", "樂富", 22.3389, 114.1872),
        HkMtrStation("WTS", "Wong Tai Sin", "黃大仙", 22.3422, 114.1936),
        HkMtrStation("DIH", "Diamond Hill", "鑽石山", 22.3400, 114.2017),
        HkMtrStation("CHH", "Choi Hung", "彩虹", 22.3347, 114.2089),
        HkMtrStation("KOB", "Kowloon Bay", "九龍灣", 22.3236, 114.2142),
        HkMtrStation("NTK", "Ngau Tau Kok", "牛頭角", 22.3156, 114.2189),
        HkMtrStation("KWT", "Kwun Tong", "觀塘", 22.3122, 114.2264),
        HkMtrStation("LAT", "Lam Tin", "藍田", 22.3069, 114.2336),
        HkMtrStation("YAT", "Yau Tong", "油塘", 22.2978, 114.2372),
        HkMtrStation("TIK", "Tiu Keng Leng", "調景嶺", 22.3047, 114.2525),

        // East Rail Line (EAL)
        HkMtrStation("EXC", "Exhibition Centre", "會展", 22.2828, 114.1756),
        HkMtrStation("HUH", "Hung Hom", "紅磡", 22.3031, 114.1819),
        HkMtrStation("MKK", "Mong Kok East", "旺角東", 22.3225, 114.1725),
        HkMtrStation("TAW", "Tai Wai", "大圍", 22.3728, 114.1786),
        HkMtrStation("SHT", "Sha Tin", "沙田", 22.3831, 114.1878),
        HkMtrStation("FOT", "Fo Tan", "火炭", 22.3958, 114.1969),
        HkMtrStation("RAC", "Racecourse", "馬場", 22.4000, 114.2000),
        HkMtrStation("UNI", "University", "大學", 22.4131, 114.2103),
        HkMtrStation("TAP", "Tai Po Market", "大埔墟", 22.4447, 114.1706),
        HkMtrStation("TWO", "Tai Wo", "太和", 22.4508, 114.1611),
        HkMtrStation("FAN", "Fanling", "粉嶺", 22.4925, 114.1397),
        HkMtrStation("SHS", "Sheung Shui", "上水", 22.5011, 114.1278),
        HkMtrStation("LOW", "Lo Wu", "羅湖", 22.5283, 114.1131),
        HkMtrStation("LMC", "Lok Ma Chau", "落馬洲", 22.5156, 114.0661),

        // Tuen Ma Line (TML)
        HkMtrStation("WKS", "Wu Kai Sha", "烏溪沙", 22.4286, 114.2444),
        HkMtrStation("MOS", "Ma On Shan", "馬鞍山", 22.4244, 114.2319),
        HkMtrStation("HEO", "Heng On", "恆安", 22.4172, 114.2289),
        HkMtrStation("TSH", "Tai Shui Hang", "大水坑", 22.4089, 114.2217),
        HkMtrStation("SHM", "Shek Mun", "石門", 22.3878, 114.2081),
        HkMtrStation("CIO", "City One", "第一城", 22.3833, 114.2036),
        HkMtrStation("STW", "Sha Tin Wai", "沙田圍", 22.3769, 114.1953),
        HkMtrStation("CKT", "Che Kung Temple", "車公廟", 22.3747, 114.1861),
        HkMtrStation("HIK", "Hin Keng", "顯徑", 22.3625, 114.1706),
        HkMtrStation("KAT", "Kai Tak", "啟德", 22.3314, 114.1997),
        HkMtrStation("SUW", "Sung Wong Toi", "宋皇臺", 22.3267, 114.1903),
        HkMtrStation("TKW", "To Kwa Wan", "土瓜灣", 22.3169, 114.1883),
        HkMtrStation("ETS", "East Tsim Sha Tsui", "尖東", 22.2961, 114.1747),
        HkMtrStation("AUS", "Austin", "柯士甸", 22.3042, 114.1664),
        HkMtrStation("NAC", "Nam Cheong", "南昌", 22.3258, 114.1539),
        HkMtrStation("TWW", "Tsuen Wan West", "荃灣西", 22.3686, 114.1108),
        HkMtrStation("KSR", "Kam Sheung Road", "錦上路", 22.4344, 114.0633),
        HkMtrStation("YUL", "Yuen Long", "元朗", 22.4450, 114.0353),
        HkMtrStation("LOP", "Long Ping", "朗屏", 22.4475, 114.0256),
        HkMtrStation("TIS", "Tin Shui Wai", "天水圍", 22.4464, 114.0028),
        HkMtrStation("SIH", "Siu Hong", "兆康", 22.4117, 113.9781),
        HkMtrStation("TUM", "Tuen Mun", "屯門", 22.3953, 113.9733),

        // Tung Chung Line (TCL) & Airport Express (AEL)
        HkMtrStation("HOK", "Hong Kong", "香港", 22.2842, 114.1581),
        HkMtrStation("KOW", "Kowloon", "九龍", 22.3047, 114.1614),
        HkMtrStation("OLY", "Olympic", "奧運", 22.3175, 114.1603),
        HkMtrStation("TSY", "Tsing Yi", "青衣", 22.3583, 114.1067),
        HkMtrStation("SUN", "Sunny Bay", "欣澳", 22.3311, 114.0289),
        HkMtrStation("TUC", "Tung Chung", "東涌", 22.2892, 113.9414),
        HkMtrStation("AIR", "Airport", "機場", 22.3156, 113.9358),
        HkMtrStation("AWE", "AsiaWorld-Expo", "博覽館", 22.3211, 113.9422),

        // South Island Line (SIL)
        HkMtrStation("OCP", "Ocean Park", "海洋公園", 22.2478, 114.1706),
        HkMtrStation("WCH", "Wong Chuk Hang", "黃竹坑", 22.2483, 114.1678),
        HkMtrStation("LET", "Lei Tung", "利東", 22.2428, 114.1561),
        HkMtrStation("SOH", "South Horizons", "海怡半島", 22.2433, 114.1489),

        // Tseung Kwan O Line (TKL)
        HkMtrStation("TKO", "Tseung Kwan O", "將軍澳", 22.3075, 114.2600),
        HkMtrStation("HAH", "Hang Hau", "坑口", 22.3158, 114.2644),
        HkMtrStation("POA", "Po Lam", "寶琳", 22.3225, 114.2575),
        HkMtrStation("LHP", "LOHAS Park", "康城", 22.2961, 114.2700)
    ).associateBy { it.code }

    /**
     * Resolves the bilingual name (e.g. "Central 中環") for a 3-letter station code.
     */
    fun getName(code: String): String {
        return STATIONS[code.uppercase()]?.bilingualName ?: code
    }

    /**
     * Resolves the station info for a given code.
     */
    fun getStation(code: String): HkMtrStation? {
        return STATIONS[code.uppercase()]
    }
}
