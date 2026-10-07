package com.androidfung.departureboard.data.model

/**
 * Standardized cardinal and transit directions.
 */
enum class TransitDirection(
    val displayName: String,
    val shortCode: String,
    val indicatorArrow: String
) {
    NORTHBOUND("Northbound", "NB", "↑ NB"),
    SOUTHBOUND("Southbound", "SB", "↓ SB"),
    EASTBOUND("Eastbound", "EB", "→ EB"),
    WESTBOUND("Westbound", "WB", "← WB"),
    INBOUND("Inbound", "IN", "IN"),
    OUTBOUND("Outbound", "OUT", "OUT");

    companion object {
        fun fromString(value: String?): TransitDirection? {
            if (value.isNullOrBlank()) return null
            val clean = value.trim()
            return entries.firstOrNull {
                it.displayName.equals(clean, ignoreCase = true) ||
                        it.shortCode.equals(clean, ignoreCase = true) ||
                        it.name.equals(clean, ignoreCase = true)
            }
        }
    }
}
