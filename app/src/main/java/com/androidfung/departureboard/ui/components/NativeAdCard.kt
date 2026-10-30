package com.androidfung.departureboard.ui.components

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.androidfung.departureboard.BuildConfig
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView

// Official Google AdMob Test Ad Unit ID for Native Advanced Ads
private const val TEST_NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"

// Production Native Ad Unit IDs per placement for distinct analytics and optimization
object NativeAdUnits {
    const val DASHBOARD = "ca-app-pub-9890667024752973/5640941464"
    const val STATION_DETAIL = "ca-app-pub-9890667024752973/5640941464" // Update when new ID is provisioned
    const val TRAIN_JOURNEY = "ca-app-pub-9890667024752973/5640941464"  // Update when new ID is provisioned
}

enum class NativeAdStyle {
    CARD, // Styled to look like a StationDepartureCard (for Dashboard feed/grid)
    ROW,  // Styled to look like a DepartureDetailItemRow (for Station Detail list)
    TIMELINE // Styled to integrate seamlessly with the Train Journey timeline
}

/**
 * Material 3 Native Ad Composable that dynamically matches the Departure Board design system.
 * Supports both [NativeAdStyle.CARD] and [NativeAdStyle.ROW] layouts.
 */
@Composable
fun NativeAdViewContainer(
    modifier: Modifier = Modifier,
    style: NativeAdStyle = NativeAdStyle.CARD,
    accentColor: Color? = null,
    adUnitId: String = if (BuildConfig.DEBUG) {
        TEST_NATIVE_AD_UNIT_ID
    } else {
        when (style) {
            NativeAdStyle.CARD -> NativeAdUnits.DASHBOARD
            NativeAdStyle.ROW -> NativeAdUnits.STATION_DETAIL
            NativeAdStyle.TIMELINE -> NativeAdUnits.TRAIN_JOURNEY
        }
    },
) {
    val context = LocalContext.current
    var loadedNativeAd by remember { mutableStateOf<NativeAd?>(null) }

    LaunchedEffect(adUnitId) {
        val adLoader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { nativeAd ->
                loadedNativeAd?.destroy()
                loadedNativeAd = nativeAd
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    android.util.Log.w(
                        "NativeAdCard",
                        "Failed to load native ad (unit: $adUnitId): [code=${error.code}] ${error.message} (domain: ${error.domain})"
                    )
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .build()
            )
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }

    DisposableEffect(Unit) {
        onDispose {
            loadedNativeAd?.destroy()
            loadedNativeAd = null
        }
    }

    val nativeAd = loadedNativeAd ?: return

    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val primaryColor = (accentColor ?: MaterialTheme.colorScheme.primary).toArgb()

    // Determine high-contrast text color on top of primaryColor (e.g. Northern Line black vs white text)
    val onPrimaryColor = if (accentColor != null) {
        val luminance = androidx.core.graphics.ColorUtils.calculateLuminance(primaryColor)
        if (luminance > 0.5) Color.Black.toArgb() else Color.White.toArgb()
    } else {
        MaterialTheme.colorScheme.onPrimary.toArgb()
    }

    when (style) {
        NativeAdStyle.CARD -> {
            Card(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp)),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                val topGradientColor = MaterialTheme.colorScheme.surfaceContainerLow
                val bottomGradientColor = MaterialTheme.colorScheme.surfaceContainer

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(topGradientColor, bottomGradientColor)
                            )
                        )
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            createCardNativeAdView(
                                context = ctx,
                                nativeAd = nativeAd,
                                onSurfaceColor = onSurfaceColor,
                                onSurfaceVariantColor = onSurfaceVariantColor,
                                primaryColor = primaryColor,
                                onPrimaryColor = onPrimaryColor
                            )
                        },
                        update = { view ->
                            populateCardNativeAdView(
                                nativeAdView = view,
                                nativeAd = nativeAd,
                                onSurfaceColor = onSurfaceColor,
                                onSurfaceVariantColor = onSurfaceVariantColor,
                                primaryColor = primaryColor,
                                onPrimaryColor = onPrimaryColor
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    )
                }
            }
        }
        NativeAdStyle.ROW -> {
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                AndroidView(
                    factory = { ctx ->
                        createRowNativeAdView(
                            context = ctx,
                            nativeAd = nativeAd,
                            onSurfaceColor = onSurfaceColor,
                            onSurfaceVariantColor = onSurfaceVariantColor,
                            primaryColor = primaryColor,
                            onPrimaryColor = onPrimaryColor
                        )
                    },
                    update = { view ->
                        populateRowNativeAdView(
                            nativeAdView = view,
                            nativeAd = nativeAd,
                            onSurfaceColor = onSurfaceColor,
                            onSurfaceVariantColor = onSurfaceVariantColor,
                            primaryColor = primaryColor,
                            onPrimaryColor = onPrimaryColor
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
        }
        NativeAdStyle.TIMELINE -> {
            // Styled as a seamless CallingPointTimeline node item
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Timeline track line and ad node dot matching CallingPointTimelineItem
                val nodeColor = accentColor ?: MaterialTheme.colorScheme.primary
                Column(
                    modifier = Modifier
                        .width(28.dp)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Upper connecting track
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .weight(1f)
                            .background(nodeColor.copy(alpha = 0.5f))
                    )
                    // Ad node icon/indicator
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(nodeColor)
                    )
                    // Lower connecting track
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .weight(1f)
                            .background(nodeColor.copy(alpha = 0.5f))
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .wrapContentHeight()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 1.dp
                ) {
                    AndroidView(
                        factory = { ctx ->
                            createTimelineNativeAdView(
                                context = ctx,
                                nativeAd = nativeAd,
                                onSurfaceColor = onSurfaceColor,
                                onSurfaceVariantColor = onSurfaceVariantColor,
                                primaryColor = primaryColor,
                                onPrimaryColor = onPrimaryColor
                            )
                        },
                        update = { view ->
                            populateTimelineNativeAdView(
                                nativeAdView = view,
                                nativeAd = nativeAd,
                                onSurfaceColor = onSurfaceColor,
                                onSurfaceVariantColor = onSurfaceVariantColor,
                                primaryColor = primaryColor,
                                onPrimaryColor = onPrimaryColor
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// CARD STYLE BUILDER
// -----------------------------------------------------------------------------------------
private fun createCardNativeAdView(
    context: Context,
    nativeAd: NativeAd,
    onSurfaceColor: Int,
    onSurfaceVariantColor: Int,
    primaryColor: Int,
    onPrimaryColor: Int
): NativeAdView {
    val nativeAdView = NativeAdView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }
    val rootLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    // Top Header: "Ad" Pill + Headline + Optional Icon.
    // Right margin leaves clear clearance for AdChoices icon at ADCHOICES_TOP_RIGHT.
    val topHeaderLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dpToPx(context, 8)
            marginEnd = dpToPx(context, 24) // Clearance for AdChoices icon
        }
    }

    val adBadge = TextView(context).apply {
        setText(com.androidfung.departureboard.R.string.ad_badge_label)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(onSurfaceVariantColor)
        val pillBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(context, 4).toFloat()
            setColor(AndroidColor.argb(30, AndroidColor.red(onSurfaceVariantColor), AndroidColor.green(onSurfaceVariantColor), AndroidColor.blue(onSurfaceVariantColor)))
        }
        background = pillBg
        val padH = dpToPx(context, 6)
        val padV = dpToPx(context, 2)
        setPadding(padH, padV, padH, padV)
    }

    val appIcon = ImageView(context).apply {
        id = View.generateViewId()
        val size = dpToPx(context, 28)
        layoutParams = LinearLayout.LayoutParams(size, size).apply {
            marginStart = dpToPx(context, 8)
            marginEnd = dpToPx(context, 8)
        }
    }

    val headlineView = TextView(context).apply {
        id = View.generateViewId()
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(onSurfaceColor)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }

    topHeaderLayout.addView(adBadge)
    topHeaderLayout.addView(appIcon)
    topHeaderLayout.addView(headlineView)

    // MediaView for displaying image or video content
    val mediaView = MediaView(context).apply {
        id = View.generateViewId()
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(context, 160)
        ).apply {
            bottomMargin = dpToPx(context, 8)
        }
    }

    // Body text
    val bodyView = TextView(context).apply {
        id = View.generateViewId()
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTextColor(onSurfaceVariantColor)
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dpToPx(context, 4)
        }
    }

    // Separate Advertiser text view (distinct from bodyView to satisfy AdMob Native Validator)
    val advertiserView = TextView(context).apply {
        id = View.generateViewId()
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        setTextColor(onSurfaceVariantColor)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dpToPx(context, 10)
        }
    }

    // Call to Action button
    val ctaButton = Button(context).apply {
        id = View.generateViewId()
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(onPrimaryColor)
        val btnBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(context, 19).toFloat()
            setColor(primaryColor)
        }
        background = btnBg
        stateListAnimator = null
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(context, 40)
        )
    }

    // MediaView clipping with rounded corners to match M3 card aesthetic
    mediaView.clipToOutline = true
    mediaView.outlineProvider = object : android.view.ViewOutlineProvider() {
        override fun getOutline(view: View, outline: android.graphics.Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, dpToPx(context, 16).toFloat())
        }
    }

    rootLayout.addView(topHeaderLayout)
    rootLayout.addView(mediaView)
    rootLayout.addView(bodyView)
    rootLayout.addView(advertiserView)
    rootLayout.addView(ctaButton)

    nativeAdView.addView(rootLayout)

    nativeAdView.headlineView = headlineView
    nativeAdView.bodyView = bodyView
    nativeAdView.advertiserView = advertiserView
    nativeAdView.iconView = appIcon
    nativeAdView.mediaView = mediaView
    nativeAdView.callToActionView = ctaButton

    populateCardNativeAdView(nativeAdView, nativeAd, onSurfaceColor, onSurfaceVariantColor, primaryColor, onPrimaryColor)
    return nativeAdView
}

private fun populateCardNativeAdView(
    nativeAdView: NativeAdView,
    nativeAd: NativeAd,
    onSurfaceColor: Int,
    onSurfaceVariantColor: Int,
    primaryColor: Int,
    onPrimaryColor: Int
) {
    val headline = nativeAdView.headlineView as? TextView
    val body = nativeAdView.bodyView as? TextView
    val advertiser = nativeAdView.advertiserView as? TextView
    val icon = nativeAdView.iconView as? ImageView
    val media = nativeAdView.mediaView
    val cta = nativeAdView.callToActionView as? Button

    headline?.text = nativeAd.headline
    headline?.setTextColor(onSurfaceColor)

    if (!nativeAd.body.isNullOrBlank()) {
        body?.visibility = View.VISIBLE
        body?.text = nativeAd.body
        body?.setTextColor(onSurfaceVariantColor)
    } else {
        body?.visibility = View.GONE
    }

    if (!nativeAd.advertiser.isNullOrBlank()) {
        advertiser?.visibility = View.VISIBLE
        advertiser?.text = nativeAd.advertiser
        advertiser?.setTextColor(onSurfaceVariantColor)
    } else {
        advertiser?.visibility = View.GONE
    }

    if (nativeAd.icon?.drawable != null) {
        icon?.visibility = View.VISIBLE
        icon?.setImageDrawable(nativeAd.icon?.drawable)
    } else {
        icon?.visibility = View.GONE
    }

    if (nativeAd.mediaContent != null) {
        media?.visibility = View.VISIBLE
        media?.mediaContent = nativeAd.mediaContent
    } else {
        media?.visibility = View.GONE
    }

    if (nativeAd.callToAction != null) {
        cta?.visibility = View.VISIBLE
        cta?.text = nativeAd.callToAction
        cta?.setTextColor(onPrimaryColor)
        (cta?.background as? GradientDrawable)?.setColor(primaryColor)
    } else {
        cta?.visibility = View.GONE
    }

    nativeAdView.setNativeAd(nativeAd)
}

// -----------------------------------------------------------------------------------------
// ROW STYLE BUILDER (Compact horizontal departure row look)
// -----------------------------------------------------------------------------------------
private fun createRowNativeAdView(
    context: Context,
    nativeAd: NativeAd,
    onSurfaceColor: Int,
    onSurfaceVariantColor: Int,
    primaryColor: Int,
    onPrimaryColor: Int
): NativeAdView {
    val nativeAdView = NativeAdView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }
    val rootLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    // App/Ad Icon on the left (or fallback subtle rounded icon) instead of a colored line bar
    val iconView = ImageView(context).apply {
        id = View.generateViewId()
        val size = dpToPx(context, 36)
        layoutParams = LinearLayout.LayoutParams(size, size).apply {
            marginEnd = dpToPx(context, 10)
        }
        val iconBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(context, 8).toFloat()
            setColor(AndroidColor.argb(20, AndroidColor.red(onSurfaceVariantColor), AndroidColor.green(onSurfaceVariantColor), AndroidColor.blue(onSurfaceVariantColor)))
        }
        background = iconBg
        clipToOutline = true
    }

    // Middle text: Headline (with inline [AD] badge) & Subtitle/Body (matches Destination and Platform details)
    val textLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dpToPx(context, 8)
        }
    }

    // Top text row: [AD] badge + Headline
    val headlineRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    val adBadge = TextView(context).apply {
        setText(com.androidfung.departureboard.R.string.ad_badge_label)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(onSurfaceVariantColor)
        gravity = Gravity.CENTER
        val pillBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(context, 4).toFloat()
            setColor(AndroidColor.argb(35, AndroidColor.red(onSurfaceVariantColor), AndroidColor.green(onSurfaceVariantColor), AndroidColor.blue(onSurfaceVariantColor)))
        }
        background = pillBg
        val padH = dpToPx(context, 5)
        val padV = dpToPx(context, 1)
        setPadding(padH, padV, padH, padV)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            marginEnd = dpToPx(context, 6)
        }
    }

    val headlineView = TextView(context).apply {
        id = View.generateViewId()
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(onSurfaceColor)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )
    }

    headlineRow.addView(adBadge)
    headlineRow.addView(headlineView)

    val bodyView = TextView(context).apply {
        id = View.generateViewId()
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTextColor(onSurfaceVariantColor)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setPadding(0, dpToPx(context, 3), 0, 0)
    }

    // Separate Advertiser text view
    val advertiserView = TextView(context).apply {
        id = View.generateViewId()
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        setTextColor(onSurfaceVariantColor)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setPadding(0, dpToPx(context, 1), 0, 0)
    }

    textLayout.addView(headlineRow)
    textLayout.addView(bodyView)
    textLayout.addView(advertiserView)

    // Right CTA Pill Button (matches the size, padding, and font of UnifiedDepartureTimeBadge)
    val ctaButton = Button(context).apply {
        id = View.generateViewId()
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(onPrimaryColor)
        stateListAnimator = null // Remove default elevation drop shadow for flat M3 pill look
        val btnBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(context, 8).toFloat()
            setColor(primaryColor)
        }
        background = btnBg
        val padH = dpToPx(context, 12)
        setPadding(padH, 0, padH, 0)
        // Leave 20dp marginEnd to clear the AdChoices overlay icon placed at ADCHOICES_TOP_RIGHT
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            dpToPx(context, 36)
        ).apply {
            marginEnd = dpToPx(context, 20)
        }
    }

    rootLayout.addView(iconView)
    rootLayout.addView(textLayout)
    rootLayout.addView(ctaButton)

    nativeAdView.addView(rootLayout)

    nativeAdView.headlineView = headlineView
    nativeAdView.bodyView = bodyView
    nativeAdView.advertiserView = advertiserView
    nativeAdView.iconView = iconView
    nativeAdView.callToActionView = ctaButton

    populateRowNativeAdView(nativeAdView, nativeAd, onSurfaceColor, onSurfaceVariantColor, primaryColor, onPrimaryColor)
    return nativeAdView
}

private fun populateRowNativeAdView(
    nativeAdView: NativeAdView,
    nativeAd: NativeAd,
    onSurfaceColor: Int,
    onSurfaceVariantColor: Int,
    primaryColor: Int,
    onPrimaryColor: Int
) {
    val headline = nativeAdView.headlineView as? TextView
    val body = nativeAdView.bodyView as? TextView
    val advertiser = nativeAdView.advertiserView as? TextView
    val icon = nativeAdView.iconView as? ImageView
    val cta = nativeAdView.callToActionView as? Button

    headline?.text = nativeAd.headline
    headline?.setTextColor(onSurfaceColor)

    if (nativeAd.icon?.drawable != null) {
        icon?.visibility = View.VISIBLE
        icon?.setImageDrawable(nativeAd.icon?.drawable)
    } else {
        icon?.visibility = View.GONE
    }

    if (!nativeAd.body.isNullOrBlank()) {
        body?.visibility = View.VISIBLE
        body?.text = nativeAd.body
        body?.setTextColor(onSurfaceVariantColor)
    } else {
        body?.visibility = View.GONE
    }

    if (!nativeAd.advertiser.isNullOrBlank() && nativeAd.body.isNullOrBlank()) {
        // Only show advertiser in the compact row if body is not available
        advertiser?.visibility = View.VISIBLE
        advertiser?.text = nativeAd.advertiser
        advertiser?.setTextColor(onSurfaceVariantColor)
    } else {
        advertiser?.visibility = View.GONE
    }

    if (nativeAd.callToAction != null) {
        cta?.visibility = View.VISIBLE
        cta?.text = nativeAd.callToAction
        cta?.setTextColor(onPrimaryColor)
        (cta?.background as? GradientDrawable)?.setColor(primaryColor)
    } else {
        cta?.visibility = View.GONE
    }

    nativeAdView.setNativeAd(nativeAd)
}

// -----------------------------------------------------------------------------------------
// TIMELINE STYLE BUILDER (Designed specifically for Train Journey calling points timeline)
// -----------------------------------------------------------------------------------------
private fun createTimelineNativeAdView(
    context: Context,
    nativeAd: NativeAd,
    onSurfaceColor: Int,
    onSurfaceVariantColor: Int,
    primaryColor: Int,
    onPrimaryColor: Int
): NativeAdView {
    val nativeAdView = NativeAdView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }
    val rootLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    // Small timeline node dot + icon
    val iconView = ImageView(context).apply {
        id = View.generateViewId()
        val size = dpToPx(context, 26)
        layoutParams = LinearLayout.LayoutParams(size, size).apply {
            marginStart = dpToPx(context, 4)
            marginEnd = dpToPx(context, 10)
        }
    }

    // Center layout: Badge + Headline + Body
    val textLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dpToPx(context, 8)
        }
    }

    val headlineRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    val adBadge = TextView(context).apply {
        setText(com.androidfung.departureboard.R.string.ad_badge_label)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f)
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(onSurfaceVariantColor)
        val pillBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(context, 3).toFloat()
            setColor(AndroidColor.argb(30, AndroidColor.red(onSurfaceVariantColor), AndroidColor.green(onSurfaceVariantColor), AndroidColor.blue(onSurfaceVariantColor)))
        }
        background = pillBg
        val padH = dpToPx(context, 4)
        val padV = dpToPx(context, 1)
        setPadding(padH, padV, padH, padV)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            marginEnd = dpToPx(context, 5)
        }
    }

    val headlineView = TextView(context).apply {
        id = View.generateViewId()
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(onSurfaceColor)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }

    headlineRow.addView(adBadge)
    headlineRow.addView(headlineView)

    val bodyView = TextView(context).apply {
        id = View.generateViewId()
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        setTextColor(onSurfaceVariantColor)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setPadding(0, dpToPx(context, 2), 0, 0)
    }

    val advertiserView = TextView(context).apply {
        id = View.generateViewId()
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        setTextColor(onSurfaceVariantColor)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    textLayout.addView(headlineRow)
    textLayout.addView(bodyView)
    textLayout.addView(advertiserView)

    // Right CTA Button (compact pill)
    val ctaButton = Button(context).apply {
        id = View.generateViewId()
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(onPrimaryColor)
        stateListAnimator = null
        val btnBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(context, 6).toFloat()
            setColor(primaryColor)
        }
        background = btnBg
        val padH = dpToPx(context, 10)
        setPadding(padH, 0, padH, 0)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            dpToPx(context, 30)
        ).apply {
            marginEnd = dpToPx(context, 18) // AdChoices clearance
        }
    }

    rootLayout.addView(iconView)
    rootLayout.addView(textLayout)
    rootLayout.addView(ctaButton)

    nativeAdView.addView(rootLayout)

    nativeAdView.headlineView = headlineView
    nativeAdView.bodyView = bodyView
    nativeAdView.advertiserView = advertiserView
    nativeAdView.iconView = iconView
    nativeAdView.callToActionView = ctaButton

    populateTimelineNativeAdView(nativeAdView, nativeAd, onSurfaceColor, onSurfaceVariantColor, primaryColor, onPrimaryColor)
    return nativeAdView
}

private fun populateTimelineNativeAdView(
    nativeAdView: NativeAdView,
    nativeAd: NativeAd,
    onSurfaceColor: Int,
    onSurfaceVariantColor: Int,
    primaryColor: Int,
    onPrimaryColor: Int
) {
    val headline = nativeAdView.headlineView as? TextView
    val body = nativeAdView.bodyView as? TextView
    val advertiser = nativeAdView.advertiserView as? TextView
    val icon = nativeAdView.iconView as? ImageView
    val cta = nativeAdView.callToActionView as? Button

    headline?.text = nativeAd.headline
    headline?.setTextColor(onSurfaceColor)

    if (nativeAd.icon?.drawable != null) {
        icon?.visibility = View.VISIBLE
        icon?.setImageDrawable(nativeAd.icon?.drawable)
    } else {
        icon?.visibility = View.GONE
    }

    if (!nativeAd.body.isNullOrBlank()) {
        body?.visibility = View.VISIBLE
        body?.text = nativeAd.body
        body?.setTextColor(onSurfaceVariantColor)
    } else {
        body?.visibility = View.GONE
    }

    if (!nativeAd.advertiser.isNullOrBlank() && nativeAd.body.isNullOrBlank()) {
        advertiser?.visibility = View.VISIBLE
        advertiser?.text = nativeAd.advertiser
        advertiser?.setTextColor(onSurfaceVariantColor)
    } else {
        advertiser?.visibility = View.GONE
    }

    if (nativeAd.callToAction != null) {
        cta?.visibility = View.VISIBLE
        cta?.text = nativeAd.callToAction
        cta?.setTextColor(onPrimaryColor)
        (cta?.background as? GradientDrawable)?.setColor(primaryColor)
    } else {
        cta?.visibility = View.GONE
    }

    nativeAdView.setNativeAd(nativeAd)
}

private fun dpToPx(context: Context, dp: Int): Int {
    return (dp * context.resources.displayMetrics.density).toInt()
}
