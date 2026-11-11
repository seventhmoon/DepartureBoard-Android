package com.androidfung.departureboard.ai

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

/**
 * Detects zero-download Android 14+ System AICore (`com.google.android.aicore`) availability
 * on supported hardware (e.g. Pixel 8/9+, Galaxy S24/S25+) and formats concise concourse-ready
 * responses without requiring the user to download a 400 MB `.litertlm` dynamic feature module.
 */
object SystemAiCoreHelper {

    const val AICORE_PACKAGE_NAME = "com.google.android.aicore"

    fun isSystemAiCoreAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        return try {
            val pm = context.packageManager
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(AICORE_PACKAGE_NAME, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(AICORE_PACKAGE_NAME, 0)
            }
            val appInfo = pkgInfo.applicationInfo ?: return false
            val isSystemApp = (appInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
            appInfo.enabled && isSystemApp
        } catch (_: Exception) {
            false
        }
    }

    fun synthesizeConciseReply(userQuery: String, groundedTransitContext: String): String {
        val trimmed = groundedTransitContext.trim()
        if (trimmed.isEmpty()) return groundedTransitContext
        val cleanQuery = userQuery.trim().lowercase()
        return when {
            cleanQuery.contains("alternative") || cleanQuery.contains("avoid") ->
                "$trimmed Tip: check the Alternative Route card below to switch to an unaffected line in one tap."
            cleanQuery.contains("refund") || cleanQuery.contains("repay") || cleanQuery.contains("compensation") ->
                "$trimmed Use the Delay Repay card below to save your receipt and open the operator claim portal."
            else -> trimmed
        }
    }
}
