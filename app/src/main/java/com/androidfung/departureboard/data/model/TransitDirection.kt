package com.androidfung.departureboard.data.model

import androidx.annotation.StringRes
import com.androidfung.departureboard.R

/**
 * Standardized cardinal and transit directions.
 */
enum class TransitDirection(
    val displayName: String,
    val shortCode: String,
    val indicatorArrow: String,
    @StringRes val labelResId: Int
) {
    NORTHBOUND("Northbound", "NB", "↑ NB", R.string.dir_northbound),
    SOUTHBOUND("Southbound", "SB", "↓ SB", R.string.dir_southbound),
    EASTBOUND("Eastbound", "EB", "→ EB", R.string.dir_eastbound),
    WESTBOUND("Westbound", "WB", "← WB", R.string.dir_westbound),
    INBOUND("Inbound", "IN", "IN", R.string.dir_inbound),
    OUTBOUND("Outbound", "OUT", "OUT", R.string.dir_outbound),
    CLOCKWISE("Clockwise", "CW", "↻ CW", R.string.dir_clockwise),
    ANTI_CLOCKWISE("Anti-Clockwise", "CCW", "↺ CCW", R.string.dir_anti_clockwise);

    companion object {
        fun fromString(value: String?): TransitDirection? {
            if (value.isNullOrBlank()) return null
            val clean = value.trim()
            return when {
                clean.equals("Counter-Clockwise", ignoreCase = true) ||
                    clean.equals("CounterClockwise", ignoreCase = true) ||
                    clean.equals("Outer", ignoreCase = true) -> ANTI_CLOCKWISE
                clean.equals("Inner", ignoreCase = true) -> CLOCKWISE
                else -> entries.firstOrNull {
                    it.displayName.equals(clean, ignoreCase = true) ||
                        it.shortCode.equals(clean, ignoreCase = true) ||
                        it.name.equals(clean, ignoreCase = true)
                }
            }
        }
    }
}
