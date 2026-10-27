package com.androidfung.departureboard.data.routes

/**
 * Standard stations on the Tyne and Wear Metro network.
 *
 * @param stationName Canonical station display name.
 * @param aliases Normalized aliases or substrings for fuzzy matching against Nexus API strings.
 */
enum class TyneAndWearMetroStation(
    val stationName: String,
    val aliases: List<String> = listOf(stationName.lowercase())
) {
    // Green Line branch (South of Pelaw)
    SOUTH_HYLTON("South Hylton"),
    PALLION("Pallion"),
    MILLFIELD("Millfield"),
    UNIVERSITY("University"),
    PARK_LANE("Park Lane"),
    SUNDERLAND("Sunderland"),
    ST_PETERS("St Peter's", listOf("st peter's", "st peters")),
    STADIUM_OF_LIGHT("Stadium of Light"),
    SEABURN("Seaburn"),
    EAST_BOLDON("East Boldon"),
    BROCKLEY_WHINS("Brockley Whins"),
    FELLGATE("Fellgate"),

    // Shared Central Core & South Section
    PELAW("Pelaw"),
    HEWORTH("Heworth"),
    FELLING("Felling"),
    GATESHEAD_STADIUM("Gateshead Stadium"),
    GATESHEAD("Gateshead"),
    CENTRAL_STATION("Central Station"),
    MONUMENT("Monument"),
    HAYMARKET("Haymarket"),
    JESMOND("Jesmond"),
    WEST_JESMOND("West Jesmond"),
    ILFORD_ROAD("Ilford Road"),
    SOUTH_GOSFORTH("South Gosforth"),

    // Green Line branch (North of South Gosforth to Airport)
    REGENT_CENTRE("Regent Centre"),
    WANSBECK_ROAD("Wansbeck Road"),
    FAWDON("Fawdon"),
    KINGSTON_PARK("Kingston Park"),
    BANK_FOOT("Bank Foot"),
    CALLERTON_PARKWAY("Callerton Parkway"),
    AIRPORT("Airport", listOf("airport", "newcastle airport")),

    // Yellow Line branch (St James to Monument)
    ST_JAMES("St James", listOf("st james", "st. james")),
    MANORS("Manors"),
    BYKER("Byker"),
    CHILLINGHAM_ROAD("Chillingham Road"),
    WALKERGATE("Walkergate"),
    WALLSEND("Wallsend"),
    HADRIAN_ROAD("Hadrian Road"),
    HOWDON("Howdon"),
    PERCY_MAIN("Percy Main"),
    MEADOW_WELL("Meadow Well"),
    NORTH_SHIELDS("North Shields"),
    TYNEMOUTH("Tynemouth"),
    CULLERCOATS("Cullercoats"),
    WHITLEY_BAY("Whitley Bay"),
    MONKSEATON("Monkseaton"),
    WEST_MONKSEATON("West Monkseaton"),
    SHIREMOOR("Shiremoor"),
    PALMERSVILLE("Palmersville"),
    NORTHUMBERLAND_PARK("Northumberland Park"),
    BENTON("Benton"),
    FOUR_LANE_ENDS("Four Lane Ends"),
    LONGBENTON("Longbenton"),

    // Yellow Line branch (South of Pelaw to South Shields)
    HEBBURN("Hebburn"),
    JARROW("Jarrow"),
    BEDE("Bede"),
    SIMONSIDE("Simonside"),
    TYNE_DOCK("Tyne Dock"),
    CHICHESTER("Chichester"),
    SOUTH_SHIELDS("South Shields");

    companion object {
        fun fromString(query: String?): TyneAndWearMetroStation? {
            if (query.isNullOrBlank()) return null
            val clean = query.replace("Metro", "")
                .replace("Station", "")
                .trim()
                .lowercase()
            return entries.firstOrNull { station ->
                station.stationName.equals(clean, ignoreCase = true) ||
                    station.aliases.any { alias -> clean.contains(alias) || alias.contains(clean) }
            }
        }
    }
}
