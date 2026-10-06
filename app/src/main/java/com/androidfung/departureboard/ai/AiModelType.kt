package com.androidfung.departureboard.ai

/**
 * AI engine backend modes for natural language transit query parsing.
 */
enum class AiModelType(
    val id: String,
    val title: String,
    val description: String
) {
    LOCAL(
        id = "local",
        title = "On-Device Local AI",
        description = "Runs on-device (Gemini Nano) for privacy & offline capability"
    ),
    CLOUD(
        id = "cloud",
        title = "Cloud LLM",
        description = "Gemini Cloud API with comprehensive route synthesis"
    ),
    LOGIC_FALLBACK(
        id = "logic_fallback",
        title = "Rule-Based Logic",
        description = "Fast deterministic heuristic parsing (zero latency fallback)"
    );

    companion object {
        fun fromId(id: String?): AiModelType = entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: LOGIC_FALLBACK
    }
}
