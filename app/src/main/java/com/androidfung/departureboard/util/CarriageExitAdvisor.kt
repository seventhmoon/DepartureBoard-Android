package com.androidfung.departureboard.util

import com.androidfung.departureboard.data.model.CoachInfo
import com.androidfung.departureboard.data.model.OccupancyStatus
import com.androidfung.departureboard.data.model.TrainFormation

/**
 * Recommends the optimal carriage(s) to board ("Best Carriage to Board") based on
 * exit barriers, step-free lift cores, and cross-platform interchanges at the user's
 * target alighting stop or final destination across GB, HK, JP, SG, and AU hubs,
 * falling back to the quietest / least-crowded coaches when live loading telemetry is available.
 */
object CarriageExitAdvisor {

    enum class ExitPosition {
        FRONT,
        FRONT_MIDDLE,
        MIDDLE,
        REAR_MIDDLE,
        REAR
    }

    data class CarriageRecommendation(
        val recommendedCoachLabels: Set<String>,
        val position: ExitPosition,
        val targetStationName: String,
        val reason: String
    )

    private data class HubAlignmentRule(
        val keywords: List<String>,
        val position: ExitPosition,
        val reason: String
    )

    private val HUB_ALIGNMENT_RULES: List<HubAlignmentRule> = listOf(
        // London / UK buffer-stop termini -> Front carriages arrive right at the main concourse ticket gates
        HubAlignmentRule(
            keywords = listOf(
                "paddington", "euston", "king's cross", "kings cross", "st pancras",
                "waterloo", "liverpool street", "marylebone", "cannon street",
                "charing cross", "moorgate", "fenchurch street"
            ),
            position = ExitPosition.FRONT,
            reason = "Front coaches stop closest to main concourse ticket gates & Underground transfer"
        ),
        HubAlignmentRule(
            keywords = listOf("victoria"),
            position = ExitPosition.FRONT,
            reason = "Front coaches align with main concourse barriers & Victoria/District subway"
        ),
        // Major London cross-platform / central escalator & Elizabeth line lift cores
        HubAlignmentRule(
            keywords = listOf(
                "farringdon", "tottenham court road", "bond street", "whitechapel",
                "canary wharf", "custom house", "woolwich"
            ),
            position = ExitPosition.MIDDLE,
            reason = "Middle coaches align with central Elizabeth line escalators & step-free lifts"
        ),
        HubAlignmentRule(
            keywords = listOf(
                "oxford circus", "green park", "holborn", "piccadilly circus",
                "leicester square", "westminster", "london bridge", "stratford", "clapham junction"
            ),
            position = ExitPosition.MIDDLE,
            reason = "Middle coaches align with primary interchange escalators & transfer bridge"
        ),
        HubAlignmentRule(
            keywords = listOf("bank", "monument"),
            position = ExitPosition.FRONT_MIDDLE,
            reason = "Front-middle coaches align with DLR / Central / Northern transfer passages"
        ),
        // Hong Kong MTR cross-platform & atrium hubs
        HubAlignmentRule(
            keywords = listOf(
                "admiralty", "金鐘", "mong kok", "旺角", "prince edward", "太子",
                "lai king", "荔景", "north point", "北角", "quarry bay", "鰂魚涌",
                "yau tong", "油塘", "tiu keng leng", "調景嶺", "kowloon tong", "九龍塘",
                "tai wai", "大圍", "nam cheong", "南昌", "sunny bay", "欣澳"
            ),
            position = ExitPosition.MIDDLE,
            reason = "Middle coaches align with cross-platform interchange & central escalators"
        ),
        HubAlignmentRule(
            keywords = listOf("central", "中環", "hong kong", "香港"),
            position = ExitPosition.FRONT_MIDDLE,
            reason = "Front-middle coaches align with Pedder St / Airport Express concourse walk"
        ),
        // Tokyo major rail hubs
        HubAlignmentRule(
            keywords = listOf("shibuya", "渋谷", "shinagawa", "品川", "ueno", "上野"),
            position = ExitPosition.FRONT_MIDDLE,
            reason = "Front-middle coaches align with primary transfer concourse & Hachikō/Central gates"
        ),
        HubAlignmentRule(
            keywords = listOf(
                "shinjuku", "新宿", "tokyo", "東京", "ikebukuro", "池袋",
                "akihabara", "秋葉原", "otemachi", "大手町", "shimbashi", "新橋"
            ),
            position = ExitPosition.MIDDLE,
            reason = "Middle coaches align with central transfer passage & main ticket gates"
        ),
        // Singapore MRT interchanges
        HubAlignmentRule(
            keywords = listOf(
                "jurong east", "raffles place", "city hall", "dhoby ghaut",
                "bishan", "paya lebar", "serangoon", "buona vista", "marina bay"
            ),
            position = ExitPosition.MIDDLE,
            reason = "Middle coaches align with cross-platform MRT interchange & lift core"
        ),
        HubAlignmentRule(
            keywords = listOf("outram park", "harbourfront", "changi airport"),
            position = ExitPosition.REAR_MIDDLE,
            reason = "Rear-middle coaches align with TEL/NEL transfer linkway & escalators"
        ),
        // Sydney Trains & Metro hubs
        HubAlignmentRule(
            keywords = listOf("town hall", "martin place", "chatswood", "parramatta", "strathfield", "redfern"),
            position = ExitPosition.MIDDLE,
            reason = "Middle coaches align with central concourse & Sydney Metro transfer"
        ),
        HubAlignmentRule(
            keywords = listOf("wynyard", "central station", "sydney central"),
            position = ExitPosition.FRONT_MIDDLE,
            reason = "Front-middle coaches align with Grand Concourse / Barangaroo Walk exits"
        )
    )

    fun recommend(
        formation: TrainFormation?,
        targetStopOrDestination: String?
    ): CarriageRecommendation? {
        if (formation == null) return null
        val coaches: List<CoachInfo> = if (formation.coaches.isNotEmpty()) {
            formation.coaches
        } else if (formation.coachCount >= 2) {
            (1..formation.coachCount).map { CoachInfo(coachNumber = "$it") }
        } else {
            return null
        }

        if (coaches.size < 2) return null

        val cleanTarget = targetStopOrDestination?.trim().orEmpty()
        val lowerTarget = cleanTarget.lowercase()

        val matchedRule = if (lowerTarget.isNotEmpty()) {
            HUB_ALIGNMENT_RULES.firstOrNull { rule ->
                rule.keywords.any { kw -> lowerTarget.contains(kw) }
            }
        } else null

        if (matchedRule != null) {
            val selectedIndices = selectIndicesForPosition(coaches.size, matchedRule.position)
            val labels = selectedIndices.map { idx -> coaches[idx].coachNumber ?: "${idx + 1}" }.toSet()
            return CarriageRecommendation(
                recommendedCoachLabels = labels,
                position = matchedRule.position,
                targetStationName = cleanTarget,
                reason = matchedRule.reason
            )
        }

        // Fallback: recommend least-crowded coaches when per-coach loading telemetry exists
        val lowCrowdingIndices = coaches.mapIndexedNotNull { idx, c ->
            when (c.parsedOccupancyStatus) {
                OccupancyStatus.EMPTY, OccupancyStatus.MANY_SEATS_AVAILABLE -> idx
                else -> null
            }
        }
        val hasMixedOccupancy = coaches.mapNotNull { it.parsedOccupancyStatus }.distinct().size >= 2
        if (lowCrowdingIndices.isNotEmpty() && hasMixedOccupancy) {
            val picked = lowCrowdingIndices.take(2)
            val labels = picked.map { idx -> coaches[idx].coachNumber ?: "${idx + 1}" }.toSet()
            val avgIdx = picked.average()
            val pos = when {
                avgIdx <= coaches.lastIndex * 0.25 -> ExitPosition.FRONT
                avgIdx >= coaches.lastIndex * 0.75 -> ExitPosition.REAR
                else -> ExitPosition.MIDDLE
            }
            return CarriageRecommendation(
                recommendedCoachLabels = labels,
                position = pos,
                targetStationName = cleanTarget.ifEmpty { "Destination" },
                reason = "Most available seats based on live carriage weight sensors"
            )
        }

        return null
    }

    internal fun selectIndicesForPosition(totalCoaches: Int, position: ExitPosition): List<Int> {
        if (totalCoaches <= 1) return listOf(0)
        if (totalCoaches <= 3) {
            return when (position) {
                ExitPosition.FRONT, ExitPosition.FRONT_MIDDLE -> listOf(0)
                ExitPosition.MIDDLE -> listOf(totalCoaches / 2)
                ExitPosition.REAR_MIDDLE, ExitPosition.REAR -> listOf(totalCoaches - 1)
            }
        }
        return when (position) {
            ExitPosition.FRONT -> listOf(0, 1)
            ExitPosition.FRONT_MIDDLE -> {
                val idx = ((totalCoaches - 1) * 0.30).toInt().coerceIn(0, totalCoaches - 2)
                listOf(idx, idx + 1)
            }
            ExitPosition.MIDDLE -> {
                val mid = (totalCoaches - 1) / 2
                if (totalCoaches % 2 == 0) listOf(mid, (mid + 1).coerceAtMost(totalCoaches - 1)) else listOf(mid)
            }
            ExitPosition.REAR_MIDDLE -> {
                val idx = ((totalCoaches - 1) * 0.65).toInt().coerceIn(0, totalCoaches - 2)
                listOf(idx, idx + 1)
            }
            ExitPosition.REAR -> listOf(totalCoaches - 2, totalCoaches - 1)
        }
    }
}
