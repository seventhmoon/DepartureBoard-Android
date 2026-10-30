package com.androidfung.departureboard.ai

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager

/**
 * Resolved execution strategy determining which AI layer handles the query.
 */
enum class AiExecutionTier(val tag: String) {
    INSTANT_DETERMINISTIC("⚡ [Fast Heuristics]"),
    LITERT_LOCAL_CLASSIFIER("🧠 [LiteRT Intent]"),
    ON_DEVICE_LLM("🤖 [On-Device LLM]"),
    CLOUD_SYNTHESIS("☁️ [Cloud LLM]")
}

/**
 * Intelligent Query Router and Cascade Orchestrator.
 * Dynamically resolves whether a transit query can be resolved in <5ms via deterministic lookups,
 * LiteRT intent classification, or elevated to on-device/cloud LLM synthesis based on battery,
 * memory, network status, and hardware capability.
 */
class AiQueryRouter(
    private val context: Context,
    private val dynamicAiProvider: DynamicAiAssistantProvider? = null
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
        // 1. Explicit user override from Developer / AI settings
        when (preferredModelType) {
            AiModelType.LOGIC_FALLBACK -> return AiExecutionTier.INSTANT_DETERMINISTIC
            AiModelType.CLOUD -> return AiExecutionTier.CLOUD_SYNTHESIS
            AiModelType.LOCAL -> {
                if (dynamicAiProvider?.isFeatureInstalled == true) {
                    return AiExecutionTier.ON_DEVICE_LLM
                }
            }
            AiModelType.DEFAULT -> { /* Proceed to cascading auto-routing */ }
        }

        val clean = query.trim().lowercase()

        // 2. Exact CRS/Bus Line/Station direct queries -> Instant Deterministic (<5ms)
        val isExactBusOrLine = clean.matches(Regex("""^(?:bus\s+)?([0-9]{1,3}|sl[0-9]{1,2}|n[0-9]{1,3})$"""))
        val isSimpleDeparture = clean.startsWith("next ") && !clean.contains("?") && !clean.contains("why") && !clean.contains("how")

        if (isExactBusOrLine || (isSimpleDeparture && clean.split(" ").size <= 5)) {
            return AiExecutionTier.INSTANT_DETERMINISTIC
        }

        // 3. Check device battery & memory constraints
        val isLowBattery = getBatteryPercentage() <= 15
        val isLowMemory = isLowMemoryDevice()
        val isOnline = isNetworkConnected()

        // 4. Offline scenario -> prioritize on-device LLM if available, else fast classifier/rules
        if (!isOnline) {
            return if (dynamicAiProvider?.isFeatureInstalled == true && !isLowMemory) {
                AiExecutionTier.ON_DEVICE_LLM
            } else {
                AiExecutionTier.INSTANT_DETERMINISTIC
            }
        }

        // 5. Complex reasoning/open-ended questions -> LLM synthesis
        val needsReasoning = clean.contains("why") || clean.contains("how") ||
                clean.contains("should i") || clean.contains("better to") ||
                clean.contains("explain") || clean.contains("alternative") ||
                classifiedIntent == TransitQueryIntent.GENERAL_QUERY

        if (needsReasoning) {
            return if (dynamicAiProvider?.isFeatureInstalled == true && !isLowBattery && !isLowMemory) {
                AiExecutionTier.ON_DEVICE_LLM
            } else {
                AiExecutionTier.CLOUD_SYNTHESIS
            }
        }

        // 6. Default for disruption checks & station lookups -> LiteRT Intent + deterministic card
        return AiExecutionTier.LITERT_LOCAL_CLASSIFIER
    }

    private fun isNetworkConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun getBatteryPercentage(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return 100
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun isLowMemoryDevice(): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        return memInfo.lowMemory || (memInfo.totalMem / (1024 * 1024 * 1024) < 4)
    }
}
