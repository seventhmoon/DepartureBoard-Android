package com.androidfung.departureboard.wear.model

/**
 * Lightweight departure item optimized for Wear OS wrist departure boards,
 * Quick Tiles, and Watch Face Complications.
 */
data class WearDepartureItem(
    val id: String,
    val lineName: String,
    val destination: String,
    val destinationCrs: String,
    val platformCode: String,
    val isPlatformConfirmed: Boolean,
    val isPlatformChanged: Boolean = false,
    val previousPlatformCode: String? = null,
    val countdownMinutes: Int,
    val scheduledTime: String,
    val statusLabel: String = "On time",
    val lineColorArgb: Long = 0xFF0098D4,
    val delayRepayBadge: String? = null,
    val isLastTrain: Boolean = false
) {
    val cleanPlatformCode: String
        get() = WearSnapshotFormatter.compactPlatformCode(platformCode)

    val isPlatformTbd: Boolean
        get() = !isPlatformConfirmed || cleanPlatformCode.isBlank() || cleanPlatformCode.equals("TBD", ignoreCase = true)

    val formattedCountdown: String
        get() = when {
            countdownMinutes <= 0 -> "Due"
            else -> "${countdownMinutes}m"
        }

    val formattedPlatformShort: String
        get() {
            if (isPlatformTbd) return "TBD"
            val code = cleanPlatformCode
            return if (code.firstOrNull()?.isDigit() == true) "Plat $code" else code
        }

    val compactDestination: String
        get() = WearSnapshotFormatter.compactStationName(destination)

    val compactStatusOrLine: String
        get() = WearSnapshotFormatter.compactStatusLabel(statusLabel, lineName)
}

data class WearStationBoard(
    val stationId: String,
    val stationName: String,
    val stationCrs: String,
    val lastUpdatedLabel: String,
    val departures: List<WearDepartureItem>,
    val commuteLabel: String? = null,
    val isAtConcourse: Boolean = false
) {
    val compactStationName: String
        get() = WearSnapshotFormatter.compactStationName(stationName)
}

/**
 * Real-time tracked train state mirrored from the Phone's `TrainTrackingService` to the Watch.
 */
data class WearTrackedJourneyState(
    val departureId: String,
    val stationId: String,
    val stationName: String,
    val destinationName: String,
    val lineName: String,
    val platformCode: String,
    val isPlatformConfirmed: Boolean,
    val remainingSeconds: Int,
    val currentLocation: String?,
    val stopsAway: Int?,
    val targetAlightStopName: String?,
    val isCancelled: Boolean,
    val lineColorArgb: Long = 0xFF1976D2
) {
    val isPlatformTbd: Boolean
        get() = !isPlatformConfirmed || platformCode.isBlank() || platformCode.equals("TBD", ignoreCase = true)

    val formattedCountdown: String
        get() = when {
            isCancelled -> "Cancelled"
            remainingSeconds <= 30 -> "Due"
            else -> "${(remainingSeconds / 60).coerceAtLeast(1)}m"
        }
}

/**
 * Snapshot payload formatted for a Wear OS Watch Face Complication (`SHORT_TEXT` / `LONG_TEXT` / `RANGED_VALUE`)
 * and Quick Tile.
 */
data class WearComplicationSnapshot(
    val shortText: String,
    val longText: String,
    val titleText: String,
    val rangedProgressPercent: Int
)

enum class WearAiPromptType {
    FASTEST_TRAIN,
    DELAYS_AND_BACKLOG,
    LAST_TRAIN,
    VOICE_QUERY
}

data class WearAiConcourseResponse(
    val stationId: String,
    val headline: String,
    val summary: String,
    val rawQuery: String,
    val isFromPhone: Boolean = false
)

object WearSnapshotFormatter {

    private val PLATFORM_NUMBER_REGEX = Regex("""(?:Plat(?:form)?\s*)?(\d+[A-Za-z]?)\b""", RegexOption.IGNORE_CASE)

    /**
     * Compacts verbose TfL/Darwin platform strings (e.g. `"6 • Eastbound"`, `"Eastbound - Platform 6"`)
     * into a short single-token badge (`"6"`, `"12A"`, `"EB"`, `"NB"`, or `"TBD"`) that never wraps on a watch face.
     */
    fun compactPlatformCode(raw: String): String {
        val trimmed = raw
            .replace("Platform", "", ignoreCase = true)
            .replace("Plat", "", ignoreCase = true)
            .trim()
        if (trimmed.isBlank() || trimmed.equals("TBD", ignoreCase = true)) return "TBD"

        // 1. If there is a platform number (e.g. "6 • Eastbound" or "Eastbound - 6"), extract just the number
        PLATFORM_NUMBER_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.let { num ->
            if (num.isNotBlank()) return num.uppercase()
        }

        // 2. Otherwise shorten compass / rail directions
        val lower = trimmed.lowercase()
        return when {
            lower.contains("eastbound") -> "EB"
            lower.contains("westbound") -> "WB"
            lower.contains("northbound") -> "NB"
            lower.contains("southbound") -> "SB"
            lower.contains("inner") -> "IR"
            lower.contains("outer") -> "OR"
            else -> trimmed.split("•", "-").first().trim().take(5).ifBlank { "TBD" }
        }
    }

    /**
     * Shortens verbose station and destination names for round Wear OS screens.
     */
    fun compactStationName(raw: String): String {
        var name = raw
            .replace("Underground Station", "", ignoreCase = true)
            .replace("Rail Station", "", ignoreCase = true)
            .replace("DLR Station", "", ignoreCase = true)
            .replace("Overground Station", "", ignoreCase = true)
            .replace("Station", "", ignoreCase = true)
            .replace("King's Cross St. Pancras", "King's Cross")
            .replace("Kings Cross St Pancras", "King's Cross")
            .replace("London ", "")
            .replace(Regex("""\s*\([^)]*\)"""), "")
            .trim()
        if (name.isBlank()) name = raw.trim()
        return name
    }

    /**
     * Converts verbose vehicle location strings (e.g. `"Between Finsbury Park and Manor House"`)
     * into a concise 1-2 word status or short line name for the watch row.
     */
    fun compactStatusLabel(statusLabel: String, lineName: String): String {
        val s = statusLabel.trim()
        val lower = s.lowercase()
        return when {
            lower.startsWith("between ") || lower.startsWith("approaching ") || lower.startsWith("at ") || lower.startsWith("left ") || lower.startsWith("departed ") -> {
                lineName
                    .replace(" line", "", ignoreCase = true)
                    .replace(" Railway", "", ignoreCase = true)
                    .trim()
                    .take(12)
            }
            s.length > 14 -> s.take(12).trimEnd() + "…"
            else -> s
        }
    }

    /**
     * Evaluates a concourse AI prompt locally against the current watch station board for instant
     * sub-50ms wrist feedback while the phone's deeper timetable/backlog response is in flight.
     */
    fun evaluateAiPromptLocally(
        station: WearStationBoard,
        promptType: WearAiPromptType,
        queryText: String = ""
    ): WearAiConcourseResponse {
        val deps = station.departures
        if (deps.isEmpty()) {
            return WearAiConcourseResponse(
                stationId = station.stationId,
                headline = "No Active Trains",
                summary = "No live departures listed at ${station.stationName}.",
                rawQuery = queryText.ifBlank { "Departures at ${station.stationName}" }
            )
        }

        val lowerQuery = queryText.lowercase()
        return when {
            promptType == WearAiPromptType.DELAYS_AND_BACKLOG || lowerQuery.contains("delay") || lowerQuery.contains("repay") -> {
                val delayRepayDep = deps.firstOrNull { !it.delayRepayBadge.isNullOrBlank() }
                val platChangedDep = deps.firstOrNull { it.isPlatformChanged }
                when {
                    delayRepayDep != null -> WearAiConcourseResponse(
                        stationId = station.stationId,
                        headline = "💸 ${delayRepayDep.delayRepayBadge}",
                        summary = "${delayRepayDep.destination} (${delayRepayDep.scheduledTime}) is eligible for Delay Repay compensation.",
                        rawQuery = "Are there delays or Delay Repay at ${station.stationName}?"
                    )
                    platChangedDep != null -> WearAiConcourseResponse(
                        stationId = station.stationId,
                        headline = "⚠️ Platform Altered",
                        summary = "${platChangedDep.destination} moved to Plat ${platChangedDep.platformCode}. All other services on time.",
                        rawQuery = "Any platform changes or delays at ${station.stationName}?"
                    )
                    else -> WearAiConcourseResponse(
                        stationId = station.stationId,
                        headline = "✅ Good Concourse Flow",
                        summary = "All ${deps.size} upcoming departures at ${station.stationName} are running normally.",
                        rawQuery = "Are there delays or platform backlogs at ${station.stationName}?"
                    )
                }
            }

            promptType == WearAiPromptType.LAST_TRAIN || lowerQuery.contains("last") || lowerQuery.contains("night") -> {
                val lastTrain = deps.firstOrNull { it.isLastTrain }
                if (lastTrain != null) {
                    WearAiConcourseResponse(
                        stationId = station.stationId,
                        headline = "🌙 Last Train Alert",
                        summary = "Last train to ${lastTrain.destination} departs in ${lastTrain.formattedCountdown} (${lastTrain.formattedPlatformShort}).",
                        rawQuery = "When is the last train from ${station.stationName}?"
                    )
                } else {
                    val latest = deps.maxByOrNull { it.countdownMinutes } ?: deps.first()
                    WearAiConcourseResponse(
                        stationId = station.stationId,
                        headline = "🕒 Service Active",
                        summary = "Regular service running at ${station.stationName}. Next: ${deps.first().destination} (${deps.first().formattedCountdown}), latest shown: ${latest.destination} (${latest.formattedCountdown}).",
                        rawQuery = "When is the last train from ${station.stationName}?"
                    )
                }
            }

            else -> {
                val matchedVoice = if (lowerQuery.isNotBlank()) {
                    deps.firstOrNull { dep ->
                        lowerQuery.contains(dep.destination.lowercase()) ||
                            lowerQuery.contains(dep.destinationCrs.lowercase()) ||
                            dep.destination.lowercase().split(" ").any { w -> w.length >= 4 && lowerQuery.contains(w) }
                    }
                } else null

                val best = matchedVoice
                    ?: deps.firstOrNull { !it.isPlatformTbd }
                    ?: deps.first()

                val platAction = if (best.isPlatformTbd) {
                    "Wait in concourse (Plat TBD)"
                } else {
                    "Go to Plat ${best.platformCode}"
                }
                WearAiConcourseResponse(
                    stationId = station.stationId,
                    headline = "⚡ $platAction • ${best.formattedCountdown}",
                    summary = "${best.destination} (${best.lineName}) is your best boarding option at ${station.stationName}.",
                    rawQuery = queryText.ifBlank { "Which train should I take from ${station.stationName}?" }
                )
            }
        }
    }

    /**
     * Formats the top upcoming departure into a glanceable Watch Face Complication & Quick Tile summary.
     * Example `shortText`: `"VIC • 3m • P4"` or `"WAT • Due • P11"`
     * Example `longText`: `"Victoria in 3m • Plat 4 (On time)"`
     */
    fun buildComplicationSnapshot(
        station: WearStationBoard,
        departure: WearDepartureItem? = station.departures.firstOrNull()
    ): WearComplicationSnapshot {
        if (departure == null) {
            return WearComplicationSnapshot(
                shortText = "${station.stationCrs} • --",
                longText = "${station.stationName}: No upcoming trains",
                titleText = station.stationName,
                rangedProgressPercent = 0
            )
        }

        val cleanPlat = departure.cleanPlatformCode
        val platShort = when {
            departure.isPlatformTbd -> "TBD"
            cleanPlat.firstOrNull()?.isDigit() == true -> "P$cleanPlat"
            else -> cleanPlat
        }
        val shortText = "${departure.destinationCrs} • ${departure.formattedCountdown} • $platShort"
        val longText = "${departure.compactDestination} in ${departure.formattedCountdown} • ${departure.formattedPlatformShort}"

        // Ranged value (0..100) for circular watch face progress ring as train approaches (15m -> 0m)
        val clampedMins = departure.countdownMinutes.coerceIn(0, 15)
        val progress = ((15 - clampedMins) * 100) / 15

        return WearComplicationSnapshot(
            shortText = shortText,
            longText = longText,
            titleText = "${station.stationCrs} → ${departure.destinationCrs}",
            rangedProgressPercent = progress.coerceIn(5, 100)
        )
    }

    /**
     * Built-in sample London concourse boards for immediate standalone Wear OS preview & offline resilience.
     */
    fun defaultConcourseBoards(): List<WearStationBoard> = listOf(
        WearStationBoard(
            stationId = "910GWATRLMN",
            stationName = "London Waterloo",
            stationCrs = "WAT",
            lastUpdatedLabel = "Live • Darwin",
            commuteLabel = "☀️ Morning Commute",
            departures = listOf(
                WearDepartureItem(
                    id = "wat_1",
                    lineName = "South Western Railway",
                    destination = "Guildford",
                    destinationCrs = "GLD",
                    platformCode = "TBD",
                    isPlatformConfirmed = false,
                    countdownMinutes = 4,
                    scheduledTime = "18:15",
                    statusLabel = "Wait in concourse",
                    lineColorArgb = 0xFF0098D4
                ),
                WearDepartureItem(
                    id = "wat_2",
                    lineName = "South Western Railway",
                    destination = "Portsmouth Harbour",
                    destinationCrs = "PMH",
                    platformCode = "12",
                    isPlatformConfirmed = true,
                    countdownMinutes = 7,
                    scheduledTime = "18:18",
                    statusLabel = "Boarding",
                    lineColorArgb = 0xFF0098D4
                ),
                WearDepartureItem(
                    id = "wat_3",
                    lineName = "South Western Railway",
                    destination = "Basingstoke",
                    destinationCrs = "BSK",
                    platformCode = "9",
                    isPlatformConfirmed = true,
                    isPlatformChanged = true,
                    previousPlatformCode = "7",
                    countdownMinutes = 11,
                    scheduledTime = "18:05",
                    statusLabel = "Delayed +17m",
                    lineColorArgb = 0xFFF57C00,
                    delayRepayBadge = "Delay Repay (+17m)"
                )
            )
        ),
        WearStationBoard(
            stationId = "910GEUSTON",
            stationName = "London Euston",
            stationCrs = "EUS",
            lastUpdatedLabel = "Live • Darwin",
            commuteLabel = "🌙 Evening Commute",
            departures = listOf(
                WearDepartureItem(
                    id = "eus_1",
                    lineName = "Avanti West Coast",
                    destination = "Manchester Piccadilly",
                    destinationCrs = "MAN",
                    platformCode = "TBD",
                    isPlatformConfirmed = false,
                    countdownMinutes = 6,
                    scheduledTime = "18:20",
                    statusLabel = "Preparing train",
                    lineColorArgb = 0xFF004F59
                ),
                WearDepartureItem(
                    id = "eus_2",
                    lineName = "London Northwestern",
                    destination = "Milton Keynes Central",
                    destinationCrs = "MKC",
                    platformCode = "8",
                    isPlatformConfirmed = true,
                    countdownMinutes = 9,
                    scheduledTime = "18:23",
                    statusLabel = "On time",
                    lineColorArgb = 0xFF00A650
                )
            )
        ),
        WearStationBoard(
            stationId = "940GZZLUVIC",
            stationName = "Victoria",
            stationCrs = "VIC",
            lastUpdatedLabel = "Live • TfL",
            commuteLabel = "★ Pinned on Phone",
            departures = listOf(
                WearDepartureItem(
                    id = "vic_1",
                    lineName = "Victoria line",
                    destination = "Brixton",
                    destinationCrs = "BRX",
                    platformCode = "4",
                    isPlatformConfirmed = true,
                    countdownMinutes = 1,
                    scheduledTime = "18:12",
                    statusLabel = "Approaching",
                    lineColorArgb = 0xFF0098D4
                ),
                WearDepartureItem(
                    id = "vic_2",
                    lineName = "Southeastern",
                    destination = "Orpington",
                    destinationCrs = "ORP",
                    platformCode = "2",
                    isPlatformConfirmed = true,
                    countdownMinutes = 5,
                    scheduledTime = "18:16",
                    statusLabel = "On time",
                    lineColorArgb = 0xFF002D62,
                    isLastTrain = false
                )
            )
        )
    )

    /**
     * Curated catalog of major UK / London rail & Tube concourse hubs available for 1-tap
     * addition directly from the Wear OS wrist station manager.
     */
    fun stationCatalog(): List<WearStationBoard> = defaultConcourseBoards() + listOf(
        WearStationBoard(
            stationId = "910GPADTON",
            stationName = "London Paddington",
            stationCrs = "PAD",
            lastUpdatedLabel = "Live • Darwin",
            commuteLabel = "Concourse Hub",
            departures = listOf(
                WearDepartureItem(
                    id = "pad_1",
                    lineName = "Great Western Railway",
                    destination = "Bristol Temple Meads",
                    destinationCrs = "BRI",
                    platformCode = "TBD",
                    isPlatformConfirmed = false,
                    countdownMinutes = 5,
                    scheduledTime = "18:30",
                    statusLabel = "Wait in concourse",
                    lineColorArgb = 0xFF0A493E
                ),
                WearDepartureItem(
                    id = "pad_2",
                    lineName = "Heathrow Express",
                    destination = "Heathrow Terminal 5",
                    destinationCrs = "HWV",
                    platformCode = "6",
                    isPlatformConfirmed = true,
                    countdownMinutes = 8,
                    scheduledTime = "18:25",
                    statusLabel = "On time",
                    lineColorArgb = 0xFF532E63
                )
            )
        ),
        WearStationBoard(
            stationId = "910GKNGX",
            stationName = "London King's Cross",
            stationCrs = "KGX",
            lastUpdatedLabel = "Live • Darwin",
            commuteLabel = "Concourse Hub",
            departures = listOf(
                WearDepartureItem(
                    id = "kgx_1",
                    lineName = "LNER",
                    destination = "Edinburgh Waverley",
                    destinationCrs = "EDB",
                    platformCode = "TBD",
                    isPlatformConfirmed = false,
                    countdownMinutes = 6,
                    scheduledTime = "18:30",
                    statusLabel = "Preparing train",
                    lineColorArgb = 0xFFCE0E2D
                )
            )
        ),
        WearStationBoard(
            stationId = "910GLNDNBDC",
            stationName = "London Bridge",
            stationCrs = "LBG",
            lastUpdatedLabel = "Live • Darwin",
            commuteLabel = "Concourse Hub",
            departures = listOf(
                WearDepartureItem(
                    id = "lbg_1",
                    lineName = "Thameslink",
                    destination = "Brighton",
                    destinationCrs = "BTN",
                    platformCode = "4",
                    isPlatformConfirmed = true,
                    countdownMinutes = 3,
                    scheduledTime = "18:19",
                    statusLabel = "On time",
                    lineColorArgb = 0xFFE94B8B
                )
            )
        ),
        WearStationBoard(
            stationId = "910GLIVST",
            stationName = "London Liverpool Street",
            stationCrs = "LST",
            lastUpdatedLabel = "Live • Darwin",
            commuteLabel = "Concourse Hub",
            departures = listOf(
                WearDepartureItem(
                    id = "lst_1",
                    lineName = "Greater Anglia",
                    destination = "Norwich",
                    destinationCrs = "NRW",
                    platformCode = "TBD",
                    isPlatformConfirmed = false,
                    countdownMinutes = 5,
                    scheduledTime = "18:30",
                    statusLabel = "Wait in concourse",
                    lineColorArgb = 0xFFD70428
                )
            )
        ),
        WearStationBoard(
            stationId = "940GZZLUOXC",
            stationName = "Oxford Circus",
            stationCrs = "OXC",
            lastUpdatedLabel = "Live • TfL",
            commuteLabel = "Tube Interchange",
            departures = listOf(
                WearDepartureItem(
                    id = "oxc_1",
                    lineName = "Victoria line",
                    destination = "Walthamstow Central",
                    destinationCrs = "WAL",
                    platformCode = "5",
                    isPlatformConfirmed = true,
                    countdownMinutes = 2,
                    scheduledTime = "18:17",
                    statusLabel = "On time",
                    lineColorArgb = 0xFF0098D4
                )
            )
        )
    )
}
