package com.androidfung.departureboard.data.model

import com.androidfung.departureboard.data.model.gb.GbTransitLine

/**
 * Domain-level line equivalence matcher.
 * Resolves transit line identities and aliases across transit networks (TfL, BODS bus, etc.).
 */
object TransitLineMatcher {

    /**
     * Matches line IDs permissively across transit naming conventions.
     * e.g., "elizabeth" vs "elizabeth-line", "hammersmith-city" vs "hammersmith" or "h&c".
     */
    fun isSameLine(lineId1: String?, lineId2: String?): Boolean {
        if (lineId1.isNullOrBlank() || lineId2.isNullOrBlank()) return false
        val id1 = lineId1.lowercase().trim()
        val id2 = lineId2.lowercase().trim()
        if (id1 == id2) return true

        // Match BODS regional bus line IDs (e.g. "bods-310" vs "310")
        val stripped1 = id1.removePrefix("bods-")
        val stripped2 = id2.removePrefix("bods-")
        if (stripped1 == stripped2) return true

        // Match domain-level GB transit lines via unified GbTransitLine registry
        val gbLine1 = GbTransitLine.fromCode(id1)
        val gbLine2 = GbTransitLine.fromCode(id2)
        if (gbLine1 != null && gbLine2 != null) {
            if (gbLine1 == gbLine2) return true
            // London Overground generic vs specific named lines (e.g. Lioness line at Euston)
            if ((gbLine1 == GbTransitLine.OVERGROUND || gbLine2 == GbTransitLine.OVERGROUND) &&
                (gbLine1.isOverground && gbLine2.isOverground)) {
                return true
            }
        }

        return false
    }

    /**
     * Determines whether a departure matches a target line identifier.
     */
    fun matchesLine(departureLineId: String?, departureLineName: String?, departureBadgeLineId: String?, targetLineId: String?): Boolean {
        if (targetLineId.isNullOrBlank()) return true
        return isSameLine(departureLineId, targetLineId) ||
                isSameLine(departureBadgeLineId, targetLineId) ||
                departureLineName?.equals(targetLineId.removePrefix("bods-"), ignoreCase = true) == true
    }
}
