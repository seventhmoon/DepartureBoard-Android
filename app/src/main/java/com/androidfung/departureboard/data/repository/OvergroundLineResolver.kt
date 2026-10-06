package com.androidfung.departureboard.data.repository

/**
 * Deterministic line resolver for London Overground services.
 *
 * London Overground has 6 officially branded lines (Liberty, Lioness, Mildmay, Suffragette,
 * Weaver, Windrush). This resolver maps train services to their specific line using:
 * 1. Station CRS codes (O(1) set lookup)
 * 2. Origin/Destination station CRS codes from Darwin NRE data
 * 3. Station/Destination name normalization fallback
 */
internal object OvergroundLineResolver {

    data class ResolvedLine(
        val id: String,
        val name: String
    )

    val DEFAULT_OVERGROUND = ResolvedLine("overground", "London Overground")

    // --- Line Definitions ---

    /**
     * Liberty line: Romford <-> Upminster
     */
    private val LIBERTY_CRS = setOf("ROM", "UPM", "EMP")
    private val LIBERTY_TERMINI = setOf("romford", "upminster")
    private val LIBERTY_STATIONS = setOf("romford", "upminster", "emerson park")

    /**
     * Lioness line: Euston <-> Watford Junction
     */
    private val LIONESS_CRS = setOf(
        "EUS", "WFJ", "WMB", "HRW", "KNR", "BCY", "CPK", "HKC", "HAC", "HXX",
        "NWB", "SKN", "KNT", "SBP", "HDN", "WIJ", "KGL", "QPK", "KRH", "SUH"
    )
    private val LIONESS_TERMINI = setOf("euston", "watford junction", "watford")
    private val LIONESS_STATIONS = setOf(
        "euston", "watford junction", "watford", "bushey", "carpenders park",
        "hatch end", "headstone lane", "harrow & wealdstone", "harrow and wealdstone",
        "kenton", "south kenton", "north wembley", "wembley central", "stonebridge park",
        "harlesden", "willesden junction", "kensal green", "queen's park", "queens park",
        "kilburn high road", "south hampstead"
    )

    /**
     * Weaver line: Liverpool Street <-> Cheshunt / Enfield Town / Chingford
     */
    private val WEAVER_CRS = setOf(
        "LST", "CHH", "ENF", "CHG", "BET", "CSB", "HOK", "STN", "SMR", "WST",
        "WRO", "HGY", "BLX", "SSO", "BKH", "EDM", "SCY", "TTH", "WHC"
    )
    private val WEAVER_TERMINI = setOf(
        "liverpool street", "cheshunt", "enfield town", "enfield", "chingford"
    )
    private val WEAVER_STATIONS = setOf(
        "liverpool street", "bethnal green", "cambridge heath", "london fields",
        "hackney downs", "rectory road", "stoke newington", "stamford hill",
        "seven sisters", "bruce grove", "white hart lane", "silver street",
        "edmonton green", "southbury", "turkey street", "theobalds grove",
        "cheshunt", "bush hill park", "enfield town", "clapton", "st james street",
        "walthamstow central", "wood street", "highams park", "chingford"
    )

    /**
     * Suffragette line: Gospel Oak <-> Barking Riverside
     */
    private val SUFFRAGETTE_CRS = setOf(
        "GPO", "BGK", "UHL", "CRH", "HRY", "SOH", "BHR", "WTM", "LEY", "LER",
        "WNP", "WGR", "BKG"
    )
    private val SUFFRAGETTE_TERMINI = setOf("gospel oak", "barking riverside", "barking")
    private val SUFFRAGETTE_STATIONS = setOf(
        "gospel oak", "upper holloway", "crouch hill", "harringay green lanes",
        "south tottenham", "blackhorse road", "walthamstow queen's road",
        "walthamstow queens road", "leyton midland road", "leytonstone high road",
        "wanstead park", "woodgrange park", "barking", "barking riverside"
    )

    /**
     * Windrush line: Highbury & Islington <-> New Cross / Crystal Palace / West Croydon / Clapham Junction
     */
    private val WINDRUSH_CRS = setOf(
        "HHY", "NXG", "CYP", "WCR", "CLJ", "CAN", "HPA", "SDC", "HOX", "HAG",
        "DLT", "SDE", "WHC", "SDA", "WAP", "ROT", "CWA", "SQE", "NWX", "QRP",
        "PMR", "BFR", "HON", "FOH", "SYD", "CPY", "ANZ", "NWJ"
    )
    private val WINDRUSH_TERMINI = setOf(
        "highbury & islington", "highbury and islington", "crystal palace",
        "west croydon", "new cross", "clapham junction"
    )
    private val WINDRUSH_STATIONS = setOf(
        "highbury & islington", "canonbury", "dalston junction", "haggerston",
        "hoxton", "shoreditch high street", "whitechapel", "shadwell", "wapping",
        "rotherhithe", "canada water", "surrey quays", "new cross", "new cross gate",
        "brockley", "honor oak park", "forest hill", "sydenham", "crystal palace",
        "penge west", "anerley", "norwood junction", "west croydon", "queens road peckham",
        "peckham rye", "denmark hill", "clapham high street", "wandsworth road"
    )

    /**
     * Mildmay line: Richmond / Clapham Junction <-> Stratford
     */
    private val MILDMAY_CRS = setOf(
        "RMD", "CLJ", "SRA", "WHD", "KGW", "GUN", "STA", "ACC", "SAE", "ACT",
        "WLA", "SCT", "KNR", "BRR", "FNR", "HWH", "GPO", "KTW", "CMD", "CRD",
        "DKP", "HAC", "HMT", "HKW"
    )
    private val MILDMAY_TERMINI = setOf("richmond", "stratford", "clapham junction")
    private val MILDMAY_STATIONS = setOf(
        "richmond", "kew gardens", "gunnersbury", "south acton", "acton central",
        "willesden junction", "kensal rise", "brondesbury park", "brondesbury",
        "west hampstead", "finchley road & frognal", "finchley road and frognal",
        "hampstead heath", "gospel oak", "kentish town west", "camden road",
        "caledonian road & barnsbury", "caledonian road and barnsbury", "highbury & islington",
        "canonbury", "dalston kingsland", "hackney central", "homerton", "hackney wick",
        "stratford", "shepherd's bush", "kensington olympia", "west brompton", "imperial wharf"
    )

    /**
     * Resolves the specific London Overground line.
     *
     * @param stationCrs Station 3-letter CRS code (e.g. "ROM", "WFJ", "EUS")
     * @param stationName Display name of the current station
     * @param destCrs Destination station CRS code if available
     * @param destName Display name of the destination
     */
    fun resolve(
        stationCrs: String? = null,
        stationName: String? = null,
        destCrs: String? = null,
        destName: String? = null
    ): ResolvedLine {
        val stCrsUpper = stationCrs?.trim()?.uppercase()
        val dstCrsUpper = destCrs?.trim()?.uppercase()
        val stNorm = stationName?.lowercase()?.trim() ?: ""
        val dstNorm = destName?.lowercase()?.trim() ?: ""

        // 1. Direct CRS check on exclusive station codes
        when {
            stCrsUpper in LIBERTY_CRS || dstCrsUpper in LIBERTY_CRS -> return ResolvedLine("liberty", "Liberty")
            stCrsUpper in SUFFRAGETTE_CRS || dstCrsUpper in SUFFRAGETTE_CRS -> return ResolvedLine("suffragette", "Suffragette")
        }

        // 2. Terminus destination matching (high confidence disambiguation)
        when {
            LIBERTY_TERMINI.any { dstNorm.contains(it) } -> return ResolvedLine("liberty", "Liberty")
            SUFFRAGETTE_TERMINI.any { dstNorm.contains(it) } -> return ResolvedLine("suffragette", "Suffragette")
            LIONESS_TERMINI.any { dstNorm.contains(it) } && !isMildmayOrWindrush(stCrsUpper, stNorm) ->
                return ResolvedLine("lioness", "Lioness")
            WEAVER_TERMINI.any { dstNorm.contains(it) } && !dstNorm.contains("stratford") ->
                return ResolvedLine("weaver", "Weaver")
        }

        // 3. Current station CRS membership
        when {
            stCrsUpper in LIONESS_CRS -> return ResolvedLine("lioness", "Lioness")
            stCrsUpper in WEAVER_CRS -> return ResolvedLine("weaver", "Weaver")
            stCrsUpper in WINDRUSH_CRS && !stCrsUpper.equals("CLJ") -> return ResolvedLine("windrush", "Windrush")
            stCrsUpper in MILDMAY_CRS && !stCrsUpper.equals("CLJ") -> return ResolvedLine("mildmay", "Mildmay")
        }

        // 4. Station name keywords matching
        when {
            LIBERTY_STATIONS.any { stNorm.contains(it) } -> return ResolvedLine("liberty", "Liberty")
            SUFFRAGETTE_STATIONS.any { stNorm.contains(it) } -> return ResolvedLine("suffragette", "Suffragette")
            LIONESS_STATIONS.any { stNorm.contains(it) } -> return ResolvedLine("lioness", "Lioness")
            WEAVER_STATIONS.any { stNorm.contains(it) } -> return ResolvedLine("weaver", "Weaver")
            WINDRUSH_STATIONS.any { stNorm.contains(it) } && WINDRUSH_TERMINI.any { dstNorm.contains(it) } ->
                return ResolvedLine("windrush", "Windrush")
            MILDMAY_STATIONS.any { stNorm.contains(it) } && MILDMAY_TERMINI.any { dstNorm.contains(it) } ->
                return ResolvedLine("mildmay", "Mildmay")
            WINDRUSH_STATIONS.any { stNorm.contains(it) } -> return ResolvedLine("windrush", "Windrush")
            MILDMAY_STATIONS.any { stNorm.contains(it) } -> return ResolvedLine("mildmay", "Mildmay")
        }

        return DEFAULT_OVERGROUND
    }

    private fun isMildmayOrWindrush(crs: String?, name: String): Boolean {
        return (crs in MILDMAY_CRS || crs in WINDRUSH_CRS) ||
                name.contains("richmond") || name.contains("stratford") || name.contains("clapham junction")
    }
}
