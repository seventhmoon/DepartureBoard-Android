package com.androidfung.departureboard.ai

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.core.content.getSystemService

/**
 * Resolved execution strategy determining which AI layer handles the query.
 */
enum class AiExecutionTier(val tag: String) {
    INSTANT_DETERMINISTIC("⚡ [Fast Heuristics]"),
    LITERT_LOCAL_CLASSIFIER("🧠 [LiteRT Intent]"),
    SYSTEM_AICORE("✨ [System AICore]"),
    ON_DEVICE_LLM("🤖 [On-Device LLM]"),
    CLOUD_SYNTHESIS("☁️ [Cloud LLM]")
}

/**
 * Intelligent Query Router and Cascade Orchestrator.
 * Dynamically resolves whether a transit query can be resolved in <5ms via deterministic lookups,
 * LiteRT intent classification, zero-download Android System AICore, or elevated to on-device/cloud
 * LLM synthesis based on battery, memory, network status, and hardware capability.
 */
class AiQueryRouter(
    private val context: Context,
    private val dynamicAiProvider: DynamicAiAssistantProvider? = null,
    private val isSystemAiCoreAvailable: () -> Boolean = { SystemAiCoreHelper.isSystemAiCoreAvailable(context) }
) {

    /**
     * Inspects query context, user intent, device memory, battery, and network conditions
     * to choose the best execution tier.
     */
    fun routeQuery(
        query: String,
        classifiedIntent: TransitQueryIntent?,
        preferredModelType: AiModelType
    ): AiExecutionTier {
        val hasSystemAiCore = isSystemAiCoreAvailable()

        // 1. Explicit user override from Developer / AI settings
        when (preferredModelType) {
            AiModelType.LOGIC_FALLBACK -> return AiExecutionTier.INSTANT_DETERMINISTIC
            AiModelType.CLOUD -> return AiExecutionTier.CLOUD_SYNTHESIS
            AiModelType.LOCAL -> {
                return when {
                    hasSystemAiCore -> AiExecutionTier.SYSTEM_AICORE
                    else -> AiExecutionTier.ON_DEVICE_LLM
                }
            }
            AiModelType.DEFAULT -> { /* Proceed to cascading auto-routing */ }
        }

        val clean = query.trim().lowercase()

        // 2. Exact CRS/Bus Line/Station direct queries -> Instant Deterministic (<5ms)
        val isExactBusOrLine = clean.matches(Regex("""^(?:(?:bus|巴士|バス|버스)\s*)?([0-9]{1,3}[a-z]?|sl[0-9]{1,2}|n[0-9]{1,3}|[a-z][0-9]{1,3}[a-z]?)$"""))
        val isSimpleDeparture = (clean.startsWith("next ") || clean.contains("下班") || clean.contains("下一班") || clean.contains("次の") || clean.contains("다음")) &&
            !clean.contains("?") && !clean.contains("？") &&
            !clean.contains("why") && !clean.contains("how") &&
            !clean.contains("點解") && !clean.contains("為什麼") && !clean.contains("なぜ") && !clean.contains("왜")
        val isShortQuery = if (clean.contains(" ")) clean.split(Regex("""\s+""")).size <= 5 else clean.length <= 16

        if (isExactBusOrLine || (isSimpleDeparture && isShortQuery)) {
            return AiExecutionTier.INSTANT_DETERMINISTIC
        }

        // 3. Check device battery & memory constraints
        val isLowBattery = getBatteryPercentage() <= 15
        val isLowMemory = isLowMemoryDevice()
        val isOnline = isNetworkConnected()

        // 4. Offline scenario -> prioritize System AICore or on-device LLM if available, else fast rules
        if (!isOnline) {
            return when {
                hasSystemAiCore && !isLowMemory -> AiExecutionTier.SYSTEM_AICORE
                dynamicAiProvider?.isFeatureInstalled == true && !isLowMemory -> AiExecutionTier.ON_DEVICE_LLM
                else -> AiExecutionTier.INSTANT_DETERMINISTIC
            }
        }

        // 5. Complex reasoning/open-ended questions -> LLM synthesis
        val needsReasoning = clean.contains("why") || clean.contains("how") ||
                clean.contains("should i") || clean.contains("better to") ||
                clean.contains("explain") || clean.contains("alternative") ||
                clean.contains("點解") || clean.contains("為什麼") || clean.contains("怎麼") ||
                clean.contains("應唔應該") || clean.contains("應該") || clean.contains("替代") ||
                clean.contains("なぜ") || clean.contains("どう") || clean.contains("べき") ||
                clean.contains("迂回") || clean.contains("おすすめ") ||
                clean.contains("왜") || clean.contains("어떻게") || clean.contains("대안") ||
                classifiedIntent == TransitQueryIntent.GENERAL_QUERY

        if (needsReasoning) {
            return when {
                hasSystemAiCore && !isLowBattery && !isLowMemory -> AiExecutionTier.SYSTEM_AICORE
                dynamicAiProvider?.isFeatureInstalled == true && !isLowBattery && !isLowMemory -> AiExecutionTier.ON_DEVICE_LLM
                else -> AiExecutionTier.CLOUD_SYNTHESIS
            }
        }

        // 6. Default for disruption checks & station lookups -> LiteRT Intent + deterministic card
        return AiExecutionTier.LITERT_LOCAL_CLASSIFIER
    }

    private fun isNetworkConnected(): Boolean {
        val cm = context.getSystemService<ConnectivityManager>() ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun getBatteryPercentage(): Int {
        val bm = context.getSystemService<BatteryManager>() ?: return 100
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun isLowMemoryDevice(): Boolean {
        val am = context.getSystemService<ActivityManager>() ?: return false
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        return memInfo.lowMemory || (memInfo.totalMem / (1024 * 1024 * 1024) < 4)
    }
}
