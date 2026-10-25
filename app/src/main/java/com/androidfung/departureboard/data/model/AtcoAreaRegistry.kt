package com.androidfung.departureboard.data.model

/**
 * Geographic macro-regions for NaPTAN ATCO Local Transport Authorities.
 */
enum class AtcoRegion(val displayName: String) {
    NATIONAL("National Infrastructure"),
    LONDON("London"),
    ENGLAND_SOUTH_EAST("South East England"),
    ENGLAND_SOUTH_WEST("South West England"),
    ENGLAND_EAST_MIDLANDS("East Midlands"),
    ENGLAND_EAST_ANGLIA("East Anglia"),
    ENGLAND_WEST_MIDLANDS("West Midlands"),
    ENGLAND_NORTH_WEST("North West England"),
    ENGLAND_NORTH_EAST("North East England"),
    ENGLAND_YORKSHIRE("Yorkshire"),
    WALES("Wales"),
    SCOTLAND("Scotland"),
    NORTHERN_IRELAND("Northern Ireland"),
}

/**
 * NaPTAN ATCO Area Code definitions published by the Department for Transport (DfT).
 *
 * The first 3 digits of any UK NaPTAN ATCO code identify the Local Transport Authority (LTA)
 * or national modal infrastructure authority.
 * Reference: https://beta-naptan.dft.gov.uk/article/atco-codes-in-use
 */
enum class AtcoArea(
    val code: String,
    val displayName: String,
    val region: AtcoRegion,
    val isModalInfrastructure: Boolean = false,
) {
    // National / Modal Infrastructure
    NATIONAL_RAIL("910", "National Rail", AtcoRegion.NATIONAL, isModalInfrastructure = true),
    AIRPORTS("920", "Airports", AtcoRegion.NATIONAL, isModalInfrastructure = true),
    FERRY("930", "Ferry Services", AtcoRegion.NATIONAL, isModalInfrastructure = true),
    TRAM_METRO("940", "Tram & Metro", AtcoRegion.NATIONAL, isModalInfrastructure = true),
    PEOPLE_MOVER("945", "Automated People Mover", AtcoRegion.NATIONAL, isModalInfrastructure = true),
    NORTHERN_IRELAND_RAIL("970", "Northern Ireland Rail", AtcoRegion.NORTHERN_IRELAND, isModalInfrastructure = true),
    NON_PASSENGER("999", "Non-Passenger Stops", AtcoRegion.NATIONAL, isModalInfrastructure = true),

    // Greater London
    GREATER_LONDON("490", "Greater London", AtcoRegion.LONDON),

    // England – South East
    BEDFORD("020", "Bedford", AtcoRegion.ENGLAND_SOUTH_EAST),
    CENTRAL_BEDFORDSHIRE("021", "Central Bedfordshire", AtcoRegion.ENGLAND_SOUTH_EAST),
    LUTON("029", "Luton", AtcoRegion.ENGLAND_SOUTH_EAST),
    WEST_BERKSHIRE("030", "West Berkshire", AtcoRegion.ENGLAND_SOUTH_EAST),
    WOKINGHAM("035", "Wokingham", AtcoRegion.ENGLAND_SOUTH_EAST),
    WINDSOR_AND_MAIDENHEAD("036", "Windsor & Maidenhead", AtcoRegion.ENGLAND_SOUTH_EAST),
    SLOUGH("037", "Slough", AtcoRegion.ENGLAND_SOUTH_EAST),
    BRACKNELL_FOREST("038", "Bracknell Forest", AtcoRegion.ENGLAND_SOUTH_EAST),
    READING("039", "Reading", AtcoRegion.ENGLAND_SOUTH_EAST),
    BUCKINGHAMSHIRE("040", "Buckinghamshire", AtcoRegion.ENGLAND_SOUTH_EAST),
    MILTON_KEYNES("049", "Milton Keynes", AtcoRegion.ENGLAND_SOUTH_EAST),
    EAST_SUSSEX("140", "East Sussex", AtcoRegion.ENGLAND_SOUTH_EAST),
    BRIGHTON_AND_HOVE("149", "Brighton and Hove", AtcoRegion.ENGLAND_SOUTH_EAST),
    ESSEX("150", "Essex", AtcoRegion.ENGLAND_SOUTH_EAST),
    SOUTHEND_ON_SEA("158", "Southend-on-Sea", AtcoRegion.ENGLAND_SOUTH_EAST),
    THURROCK("159", "Thurrock", AtcoRegion.ENGLAND_SOUTH_EAST),
    HERTFORDSHIRE("210", "Hertfordshire", AtcoRegion.ENGLAND_SOUTH_EAST),
    ISLE_OF_WIGHT("230", "Isle of Wight", AtcoRegion.ENGLAND_SOUTH_EAST),
    KENT("240", "Kent", AtcoRegion.ENGLAND_SOUTH_EAST),
    MEDWAY("249", "Medway", AtcoRegion.ENGLAND_SOUTH_EAST),
    OXFORDSHIRE("340", "Oxfordshire", AtcoRegion.ENGLAND_SOUTH_EAST),
    SURREY("400", "Surrey", AtcoRegion.ENGLAND_SOUTH_EAST),
    WEST_SUSSEX("440", "West Sussex", AtcoRegion.ENGLAND_SOUTH_EAST),

    // England – South West
    BRISTOL("010", "Bristol", AtcoRegion.ENGLAND_SOUTH_WEST),
    SOUTH_GLOUCESTERSHIRE("017", "South Gloucestershire", AtcoRegion.ENGLAND_SOUTH_WEST),
    BATH_AND_NORTH_EAST_SOMERSET("018", "Bath & North East Somerset", AtcoRegion.ENGLAND_SOUTH_WEST),
    NORTH_SOMERSET("019", "North Somerset", AtcoRegion.ENGLAND_SOUTH_WEST),
    CORNWALL("080", "Cornwall", AtcoRegion.ENGLAND_SOUTH_WEST),
    DEVON("110", "Devon", AtcoRegion.ENGLAND_SOUTH_WEST),
    PLYMOUTH("118", "Plymouth", AtcoRegion.ENGLAND_SOUTH_WEST),
    TORBAY("119", "Torbay", AtcoRegion.ENGLAND_SOUTH_WEST),
    DORSET("120", "Dorset", AtcoRegion.ENGLAND_SOUTH_WEST),
    POOLE("128", "Poole", AtcoRegion.ENGLAND_SOUTH_WEST),
    BOURNEMOUTH("129", "Bournemouth", AtcoRegion.ENGLAND_SOUTH_WEST),
    GLOUCESTERSHIRE("160", "Gloucestershire", AtcoRegion.ENGLAND_SOUTH_WEST),
    HAMPSHIRE("190", "Hampshire", AtcoRegion.ENGLAND_SOUTH_WEST),
    SOUTHAMPTON("198", "Southampton", AtcoRegion.ENGLAND_SOUTH_WEST),
    PORTSMOUTH("199", "Portsmouth", AtcoRegion.ENGLAND_SOUTH_WEST),
    SOMERSET("360", "Somerset", AtcoRegion.ENGLAND_SOUTH_WEST),
    WILTSHIRE("460", "Wiltshire", AtcoRegion.ENGLAND_SOUTH_WEST),
    SWINDON("468", "Swindon", AtcoRegion.ENGLAND_SOUTH_WEST),

    // England – East Midlands
    PETERBOROUGH("059", "Peterborough", AtcoRegion.ENGLAND_EAST_MIDLANDS),
    DERBYSHIRE("100", "Derbyshire", AtcoRegion.ENGLAND_EAST_MIDLANDS),
    DERBY("109", "Derby", AtcoRegion.ENGLAND_EAST_MIDLANDS),
    NORTH_LINCOLNSHIRE("227", "North Lincolnshire", AtcoRegion.ENGLAND_EAST_MIDLANDS),
    NORTH_EAST_LINCOLNSHIRE("228", "North East Lincolnshire", AtcoRegion.ENGLAND_EAST_MIDLANDS),
    LEICESTERSHIRE("260", "Leicestershire", AtcoRegion.ENGLAND_EAST_MIDLANDS),
    RUTLAND("268", "Rutland", AtcoRegion.ENGLAND_EAST_MIDLANDS),
    LEICESTER("269", "Leicester", AtcoRegion.ENGLAND_EAST_MIDLANDS),
    LINCOLNSHIRE("270", "Lincolnshire", AtcoRegion.ENGLAND_EAST_MIDLANDS),
    WEST_NORTHAMPTONSHIRE("300", "West Northamptonshire", AtcoRegion.ENGLAND_EAST_MIDLANDS),
    NORTH_NORTHAMPTONSHIRE("305", "North Northamptonshire", AtcoRegion.ENGLAND_EAST_MIDLANDS),
    NOTTINGHAMSHIRE("330", "Nottinghamshire", AtcoRegion.ENGLAND_EAST_MIDLANDS),
    NOTTINGHAM("339", "Nottingham", AtcoRegion.ENGLAND_EAST_MIDLANDS),

    // England – East Anglia
    CAMBRIDGESHIRE("050", "Cambridgeshire", AtcoRegion.ENGLAND_EAST_ANGLIA),
    NORFOLK("290", "Norfolk", AtcoRegion.ENGLAND_EAST_ANGLIA),
    SUFFOLK("390", "Suffolk", AtcoRegion.ENGLAND_EAST_ANGLIA),

    // England – West Midlands
    WORCESTERSHIRE("200", "Worcestershire", AtcoRegion.ENGLAND_WEST_MIDLANDS),
    HEREFORDSHIRE("209", "Herefordshire", AtcoRegion.ENGLAND_WEST_MIDLANDS),
    SHROPSHIRE("350", "Shropshire", AtcoRegion.ENGLAND_WEST_MIDLANDS),
    TELFORD_AND_WREKIN("359", "Telford & Wrekin", AtcoRegion.ENGLAND_WEST_MIDLANDS),
    STAFFORDSHIRE("380", "Staffordshire", AtcoRegion.ENGLAND_WEST_MIDLANDS),
    STOKE_ON_TRENT("389", "Stoke-on-Trent", AtcoRegion.ENGLAND_WEST_MIDLANDS),
    WARWICKSHIRE("420", "Warwickshire", AtcoRegion.ENGLAND_WEST_MIDLANDS),
    WEST_MIDLANDS("430", "West Midlands", AtcoRegion.ENGLAND_WEST_MIDLANDS),

    // England – North West
    CHESHIRE_EAST("060", "Cheshire East", AtcoRegion.ENGLAND_NORTH_WEST),
    CHESHIRE_WEST_AND_CHESTER("061", "Cheshire West & Chester", AtcoRegion.ENGLAND_NORTH_WEST),
    HALTON("068", "Halton", AtcoRegion.ENGLAND_NORTH_WEST),
    WARRINGTON("069", "Warrington", AtcoRegion.ENGLAND_NORTH_WEST),
    CUMBRIA("090", "Cumbria", AtcoRegion.ENGLAND_NORTH_WEST),
    GREATER_MANCHESTER("180", "Greater Manchester", AtcoRegion.ENGLAND_NORTH_WEST),
    LANCASHIRE("250", "Lancashire", AtcoRegion.ENGLAND_NORTH_WEST),
    BLACKBURN_WITH_DARWEN("258", "Blackburn with Darwen", AtcoRegion.ENGLAND_NORTH_WEST),
    BLACKPOOL("259", "Blackpool", AtcoRegion.ENGLAND_NORTH_WEST),
    MERSEYSIDE("280", "Merseyside", AtcoRegion.ENGLAND_NORTH_WEST),

    // England – North East
    HARTLEPOOL("075", "Hartlepool", AtcoRegion.ENGLAND_NORTH_EAST),
    DARLINGTON("076", "Darlington", AtcoRegion.ENGLAND_NORTH_EAST),
    STOCKTON_ON_TEES("077", "Stockton-on-Tees", AtcoRegion.ENGLAND_NORTH_EAST),
    REDCAR_AND_CLEVELAND("078", "Redcar & Cleveland", AtcoRegion.ENGLAND_NORTH_EAST),
    MIDDLESBROUGH("079", "Middlesbrough", AtcoRegion.ENGLAND_NORTH_EAST),
    DURHAM("130", "Durham", AtcoRegion.ENGLAND_NORTH_EAST),
    NORTHUMBERLAND("310", "Northumberland", AtcoRegion.ENGLAND_NORTH_EAST),
    TYNE_AND_WEAR("410", "Tyne & Wear", AtcoRegion.ENGLAND_NORTH_EAST),

    // England – Yorkshire
    EAST_RIDING_OF_YORKSHIRE("220", "East Riding of Yorkshire", AtcoRegion.ENGLAND_YORKSHIRE),
    KINGSTON_UPON_HULL("229", "Kingston upon Hull", AtcoRegion.ENGLAND_YORKSHIRE),
    NORTH_YORKSHIRE("320", "North Yorkshire", AtcoRegion.ENGLAND_YORKSHIRE),
    YORK("329", "York", AtcoRegion.ENGLAND_YORKSHIRE),
    SOUTH_YORKSHIRE("370", "South Yorkshire", AtcoRegion.ENGLAND_YORKSHIRE),
    WEST_YORKSHIRE("450", "West Yorkshire", AtcoRegion.ENGLAND_YORKSHIRE),

    // Wales
    DENBIGHSHIRE("511", "Denbighshire", AtcoRegion.WALES),
    FLINTSHIRE("512", "Flintshire", AtcoRegion.WALES),
    CONWY("513", "Conwy", AtcoRegion.WALES),
    WREXHAM("514", "Wrexham", AtcoRegion.WALES),
    PEMBROKESHIRE("521", "Pembrokeshire", AtcoRegion.WALES),
    CARMARTHENSHIRE("522", "Carmarthenshire", AtcoRegion.WALES),
    CEREDIGION("523", "Ceredigion", AtcoRegion.WALES),
    NEWPORT("531", "Newport", AtcoRegion.WALES),
    BLAENAU_GWENT("532", "Blaenau Gwent", AtcoRegion.WALES),
    MONMOUTHSHIRE("533", "Monmouthshire", AtcoRegion.WALES),
    TORFAEN("534", "Torfaen", AtcoRegion.WALES),
    GWYNEDD("540", "Gwynedd", AtcoRegion.WALES),
    ISLE_OF_ANGLESEY("541", "Isle of Anglesey", AtcoRegion.WALES),
    BRIDGEND("551", "Bridgend", AtcoRegion.WALES),
    RHONDDA_CYNON_TAF("552", "Rhondda Cynon Taf", AtcoRegion.WALES),
    MERTHYR_TYDFIL("553", "Merthyr Tydfil", AtcoRegion.WALES),
    CAERPHILLY("554", "Caerphilly", AtcoRegion.WALES),
    POWYS("561", "Powys", AtcoRegion.WALES),
    CARDIFF("571", "Cardiff", AtcoRegion.WALES),
    VALE_OF_GLAMORGAN("572", "Vale of Glamorgan", AtcoRegion.WALES),
    SWANSEA("581", "Swansea", AtcoRegion.WALES),
    NEATH_PORT_TALBOT("582", "Neath Port Talbot", AtcoRegion.WALES),

    // Scotland
    WESTERN_ISLES("601", "Western Isles", AtcoRegion.SCOTLAND),
    ORKNEY_ISLANDS("602", "Orkney Islands", AtcoRegion.SCOTLAND),
    SHETLAND_ISLANDS("603", "Shetland Islands", AtcoRegion.SCOTLAND),
    ARGYLL_AND_BUTE("607", "Argyll & Bute", AtcoRegion.SCOTLAND),
    WEST_DUNBARTONSHIRE("608", "West Dunbartonshire", AtcoRegion.SCOTLAND),
    GLASGOW("609", "Glasgow", AtcoRegion.SCOTLAND),
    EAST_DUNBARTONSHIRE("611", "East Dunbartonshire", AtcoRegion.SCOTLAND),
    EAST_RENFREWSHIRE("612", "East Renfrewshire", AtcoRegion.SCOTLAND),
    INVERCLYDE("613", "Inverclyde", AtcoRegion.SCOTLAND),
    RENFREWSHIRE("614", "Renfrewshire", AtcoRegion.SCOTLAND),
    SOUTH_LANARKSHIRE("615", "South Lanarkshire", AtcoRegion.SCOTLAND),
    NORTH_LANARKSHIRE("616", "North Lanarkshire", AtcoRegion.SCOTLAND),
    NORTH_AYRSHIRE("617", "North Ayrshire", AtcoRegion.SCOTLAND),
    EAST_AYRSHIRE("618", "East Ayrshire", AtcoRegion.SCOTLAND),
    SOUTH_AYRSHIRE("619", "South Ayrshire", AtcoRegion.SCOTLAND),
    EDINBURGH("620", "Edinburgh", AtcoRegion.SCOTLAND),
    EAST_LOTHIAN("627", "East Lothian", AtcoRegion.SCOTLAND),
    MIDLOTHIAN("628", "Midlothian", AtcoRegion.SCOTLAND),
    WEST_LOTHIAN("629", "West Lothian", AtcoRegion.SCOTLAND),
    ABERDEENSHIRE("630", "Aberdeenshire", AtcoRegion.SCOTLAND),
    MORAY("638", "Moray", AtcoRegion.SCOTLAND),
    ABERDEEN("639", "Aberdeen", AtcoRegion.SCOTLAND),
    DUNDEE("640", "Dundee", AtcoRegion.SCOTLAND),
    PERTH_AND_KINROSS("648", "Perth & Kinross", AtcoRegion.SCOTLAND),
    ANGUS("649", "Angus", AtcoRegion.SCOTLAND),
    FIFE("650", "Fife", AtcoRegion.SCOTLAND),
    STIRLING("660", "Stirling", AtcoRegion.SCOTLAND),
    CLACKMANNANSHIRE("668", "Clackmannanshire", AtcoRegion.SCOTLAND),
    FALKIRK("669", "Falkirk", AtcoRegion.SCOTLAND),
    HIGHLAND("670", "Highland", AtcoRegion.SCOTLAND),
    DUMFRIES_AND_GALLOWAY("680", "Dumfries & Galloway", AtcoRegion.SCOTLAND),
    SCOTTISH_BORDERS("690", "Scottish Borders", AtcoRegion.SCOTLAND),

    // Northern Ireland
    NORTHERN_IRELAND("700", "Northern Ireland", AtcoRegion.NORTHERN_IRELAND);

    /**
     * Indicates whether stops belonging to this authority represent regional UK buses
     * handled by the Department for Transport (DfT) Bus Open Data Service (BODS).
     */
    val isRegionalBusAuthority: Boolean
        get() = !isModalInfrastructure &&
                this != GREATER_LONDON &&
                this != NORTHERN_IRELAND

    companion object {
        private val BY_CODE: Map<String, AtcoArea> = entries.associateBy { it.code }

        /**
         * Resolves the [AtcoArea] for a 3-digit area prefix.
         */
        fun fromPrefix(prefix: String?): AtcoArea? = if (prefix != null) BY_CODE[prefix] else null

        /**
         * Resolves the [AtcoArea] from a full NaPTAN ATCO stop ID (e.g. "1800SB04611" -> GREATER_MANCHESTER).
         */
        fun fromAtcoCode(atcoCode: String?): AtcoArea? {
            if (atcoCode == null) return null
            val prefix = AtcoAreaRegistry.extractPrefix(atcoCode) ?: return null
            return BY_CODE[prefix]
        }
    }
}

/**
 * Helper registry and query utilities for NaPTAN ATCO stop codes.
 */
object AtcoAreaRegistry {

    const val PREFIX_NATIONAL_RAIL = "910"
    const val PREFIX_AIRPORTS = "920"
    const val PREFIX_FERRY = "930"
    const val PREFIX_TRAM_METRO = "940"
    const val PREFIX_PEOPLE_MOVER = "945"
    const val PREFIX_TFL_LONDON = "490"
    const val PREFIX_NORTHERN_IRELAND_RAIL = "970"
    const val PREFIX_NON_PASSENGER = "999"

    /**
     * Extracts the 3-digit area code from a NaPTAN ATCO code, or null if the ID is too short or non-numeric.
     */
    fun extractPrefix(atcoCode: String): String? {
        val trimmed = atcoCode.trim()
        return if (trimmed.length >= 3 && trimmed.take(3).all { it.isDigit() }) {
            trimmed.take(3)
        } else {
            null
        }
    }

    /**
     * Resolves the user-facing area or authority name from an ATCO stop code.
     */
    fun getAreaName(atcoCode: String): String? = AtcoArea.fromAtcoCode(atcoCode)?.displayName

    /**
     * Returns true if the code belongs to a regional UK bus authority (handled by BODS).
     */
    fun isRegionalBusStop(atcoCode: String): Boolean {
        val upper = atcoCode.uppercase().trim()
        if (upper.startsWith("BODS")) return true
        val area = AtcoArea.fromAtcoCode(upper) ?: return false
        return area.isRegionalBusAuthority
    }

    /**
     * Returns true if the code belongs to a Greater London TfL bus stop (starts with 490).
     */
    fun isLondonBusStop(atcoCode: String): Boolean {
        val upper = atcoCode.uppercase().trim()
        return upper.startsWith(PREFIX_TFL_LONDON)
    }

    /**
     * Returns true if the code represents an automated people mover (e.g. Luton DART, Gatwick shuttle).
     */
    fun isPeopleMover(atcoCode: String): Boolean {
        val upper = atcoCode.uppercase().trim()
        return upper.startsWith(PREFIX_PEOPLE_MOVER)
    }
}
