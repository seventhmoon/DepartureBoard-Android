package com.androidfung.departureboard.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.annotation.ColorInt
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

/**
 * Launches external transit agency / claim / documentation web links using AndroidX Browser
 * Partial Custom Tabs (with automatic fallback to a standard browser [Intent.ACTION_VIEW]).
 */
object CustomTabsLauncher {

    enum class TabPresentation {
        PARTIAL_SHEET,
        FULL_HEIGHT
    }

    private const val DEFAULT_PARTIAL_HEIGHT_FRACTION = 0.75f
    private const val PARTIAL_CORNER_RADIUS_DP = 16

    fun openWebUrl(
        context: Context,
        url: String,
        @ColorInt toolbarColor: Int? = null,
        isDarkTheme: Boolean = false,
        presentation: TabPresentation = TabPresentation.PARTIAL_SHEET
    ): Boolean {
        val uri = parseHttpUri(url) ?: return false
        val activity = context.findActivity()
        val launchContext = activity ?: context

        val customTabsPackage = runCatching {
            CustomTabsClient.getPackageName(context, null)
        }.getOrNull()

        if (!customTabsPackage.isNullOrBlank()) {
            val customTabsIntent = buildCustomTabsIntent(
                context = launchContext,
                toolbarColor = toolbarColor,
                isDarkTheme = isDarkTheme,
                presentation = presentation
            )
            customTabsIntent.intent.setPackage(customTabsPackage)
            val launchedCustomTab = runCatching {
                customTabsIntent.launchUrl(launchContext, uri)
                true
            }.getOrDefault(false)
            if (launchedCustomTab) {
                return true
            }
        }

        val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            if (activity == null) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        return runCatching {
            launchContext.startActivity(fallbackIntent)
            true
        }.getOrDefault(false)
    }

    internal fun buildCustomTabsIntent(
        context: Context,
        @ColorInt toolbarColor: Int? = null,
        isDarkTheme: Boolean = false,
        presentation: TabPresentation = TabPresentation.PARTIAL_SHEET,
        sheetHeightFraction: Float = DEFAULT_PARTIAL_HEIGHT_FRACTION
    ): CustomTabsIntent {
        val activity = context.findActivity()
        val colorScheme = if (isDarkTheme) {
            CustomTabsIntent.COLOR_SCHEME_DARK
        } else {
            CustomTabsIntent.COLOR_SCHEME_LIGHT
        }

        val builder = CustomTabsIntent.Builder()
            .setColorScheme(colorScheme)
            .setShowTitle(true)
            .setUrlBarHidingEnabled(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_ON)

        if (toolbarColor != null) {
            val colorParams = CustomTabColorSchemeParams.Builder()
                .setToolbarColor(toolbarColor)
                .setNavigationBarColor(toolbarColor)
                .build()
            builder.setDefaultColorSchemeParams(colorParams)
        }

        if (presentation == TabPresentation.PARTIAL_SHEET && activity != null) {
            val displayHeightPx = context.resources.displayMetrics.heightPixels
            val initialHeightPx = (displayHeightPx * sheetHeightFraction.coerceIn(0.35f, 0.95f))
                .toInt()
                .coerceAtLeast(1)
            builder.setInitialActivityHeightPx(
                initialHeightPx,
                CustomTabsIntent.ACTIVITY_HEIGHT_ADJUSTABLE
            )
            builder.setToolbarCornerRadiusDp(PARTIAL_CORNER_RADIUS_DP)
        }

        val customTabsIntent = builder.build()
        if (activity == null) {
            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return customTabsIntent
    }

    internal fun parseHttpUri(rawUrl: String): Uri? {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return null
        val uri = trimmed.toUri()
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrBlank()) return null
        return uri
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
